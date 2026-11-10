package com.example.ui.viewmodel

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.SyncStatus
import com.example.data.model.WargaKosResident
import com.example.data.preferences.AppPreferences
import com.example.data.repository.WargaKosRepository
import com.example.export.ExportResult
import com.example.export.ReportExporter
import com.example.security.EndToEndCrypto
import com.example.ui.auth.authStateFlow
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.crypto.SecretKey

enum class ResidentFilter(val label: String) {
    ALL("Semua"),
    MARRIED("Nikah"),
    SINGLE("Belum Nikah"),
    PENDING_SYNC("Tertunda"),
}

sealed interface WargaKosUiState {
    data object Loading : WargaKosUiState
    data class Success(
        val allResidents: List<WargaKosResident>,
        val filteredResidents: List<WargaKosResident>,
        val searchQuery: String,
        val filter: ResidentFilter,
        val pendingCount: Int,
        val totalOccupants: Int,
    ) : WargaKosUiState

    data class Error(val message: String) : WargaKosUiState
}

class WargaKosViewModel(
    private val repository: WargaKosRepository,
    private val preferences: AppPreferences,
    appContext: Context,
    private val userId: String,
    private val vaultKey: SecretKey,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filter = MutableStateFlow(ResidentFilter.ALL)
    val filter: StateFlow<ResidentFilter> = _filter.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _lastExportResult = MutableStateFlow<ExportResult?>(null)
    val lastExportResult: StateFlow<ExportResult?> = _lastExportResult.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    val reportTitle: StateFlow<String> = preferences.reportTitle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "DATA WARGA KOS")

    val isOnline: StateFlow<Boolean> = observeConnectivity(appContext)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), true)

    val uiState: StateFlow<WargaKosUiState> = combine(
        repository.observeLocalEncryptedRecords(userId),
        _searchQuery,
        _filter,
    ) { entities, query, activeFilter ->
        val decrypted = entities.mapNotNull { entity ->
            try {
                val json = EndToEndCrypto.decryptString(entity.encryptedPayload, entity.iv, vaultKey)
                val status = SyncStatus.entries.firstOrNull { it.name == entity.syncStatus } ?: SyncStatus.SYNCED
                WargaKosResident.fromJsonString(
                    id = entity.id,
                    json = json,
                    syncStatus = status,
                    createdAtMillis = entity.createdAtMillis,
                    updatedAtMillis = entity.updatedAtMillis,
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed decrypting record ${entity.id}: ${e.message}")
                null
            }
        }

        val q = query.trim().lowercase()
        val filtered = decrypted.filter { resident ->
            val matchesQuery = q.isEmpty() ||
                resident.nama.lowercase().contains(q) ||
                resident.pemilik.lowercase().contains(q) ||
                resident.noKamar.lowercase().contains(q) ||
                resident.alamat.lowercase().contains(q) ||
                resident.keterangan.lowercase().contains(q)

            val matchesFilter = when (activeFilter) {
                ResidentFilter.ALL -> true
                ResidentFilter.MARRIED -> resident.statusNikah == "✓" || resident.statusNikah.equals("Ya", ignoreCase = true)
                ResidentFilter.SINGLE -> resident.statusBelumNikah == "✓" || resident.statusBelumNikah.equals("Ya", ignoreCase = true)
                ResidentFilter.PENDING_SYNC -> resident.syncStatus != SyncStatus.SYNCED
            }
            matchesQuery && matchesFilter
        }

        val pendingCount = decrypted.count { it.syncStatus != SyncStatus.SYNCED }
        val totalOccupants = decrypted.sumOf { it.jumlahOrang.trim().toIntOrNull() ?: 1 }

        WargaKosUiState.Success(
            allResidents = decrypted,
            filteredResidents = filtered,
            searchQuery = query,
            filter = activeFilter,
            pendingCount = pendingCount,
            totalOccupants = totalOccupants,
        ) as WargaKosUiState
    }.catch { error ->
        emit(WargaKosUiState.Error(error.localizedMessage ?: "Gagal memuat data warga kos"))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = WargaKosUiState.Loading,
    )

    val isCloudConnected: StateFlow<Boolean> = Firebase.auth.authStateFlow()
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), repository.isCloudAuthenticated())

    init {
        startRealtimeCloudSyncIfConnected()
        observeNetworkForAutoSync()
    }

    private fun startRealtimeCloudSyncIfConnected() {
        viewModelScope.launch {
            isCloudConnected.collect { connected ->
                if (connected) {
                    repository.observeCloudSync(localUserId = userId)
                        .catch { error ->
                            val firestoreError = generateSequence(error) { it.cause }
                                .filterIsInstance<FirebaseFirestoreException>()
                                .firstOrNull()
                            if (firestoreError == null && error is CancellationException) throw error
                            Log.w(TAG, "Cloud sync stream warning (offline fallback active)", firestoreError ?: error)
                        }
                        .collect {
                            // Snapshot reconciliation to Room happens inside repository.observeCloudSync()
                        }
                }
            }
        }
    }

    private fun observeNetworkForAutoSync() {
        viewModelScope.launch {
            combine(isOnline, isCloudConnected) { online, cloud -> online && cloud }
                .collect { ready ->
                    if (ready) {
                        syncNow(silent = true)
                    }
                }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateFilter(filter: ResidentFilter) {
        _filter.value = filter
    }

    fun updateReportTitle(title: String) {
        viewModelScope.launch {
            preferences.setReportTitle(title)
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun saveResident(resident: WargaKosResident) {
        if (resident.nama.isBlank()) {
            _snackbarMessage.value = "Nama warga kos wajib diisi."
            return
        }
        viewModelScope.launch {
            val isEdit = resident.id.isNotBlank()
            val saved = withContext(Dispatchers.IO) {
                repository.saveResidentOfflineFirst(userId, resident, vaultKey)
            }
            _snackbarMessage.value = when {
                saved.syncStatus == SyncStatus.SYNCED && isEdit -> "Data berhasil diperbarui & tersinkron ke cloud."
                saved.syncStatus == SyncStatus.SYNCED -> "Data terenkripsi berhasil disimpan & disinkronkan."
                else -> "Tersimpan aman secara offline (SQLite terenkripsi AES-256-GCM)."
            }
        }
    }

    fun deleteResident(recordId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.deleteResidentOfflineFirst(userId, recordId)
            }
            _snackbarMessage.value = "Data warga kos dihapus."
        }
    }

    fun syncNow(silent: Boolean = false) {
        if (!repository.isCloudAuthenticated()) {
            if (!silent) {
                _snackbarMessage.value = "Mode lokal E2EE aktif (SQLite). Hubungkan Cloud Sync di tab Keamanan jika ingin cadangan online."
            }
            return
        }
        viewModelScope.launch {
            if (_isSyncing.value) return@launch
            _isSyncing.value = true
            val res = withContext(Dispatchers.IO) {
                repository.syncPendingChanges(userId)
            }
            _isSyncing.value = false
            if (!silent) {
                _snackbarMessage.value = if (res.isSuccess) {
                    val count = res.getOrDefault(0)
                    if (count > 0) "Berhasil menyinkronkan $count data ke cloud." else "Semua data sudah sinkron dengan cloud."
                } else {
                    "Mode offline aktif. Data lokal tetap aman."
                }
            }
        }
    }

    fun exportExcel(context: Context, onReady: (ExportResult) -> Unit = {}) {
        val residents = (uiState.value as? WargaKosUiState.Success)?.allResidents ?: emptyList()
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    ReportExporter.exportExcel(context, reportTitle.value, residents)
                }
                _lastExportResult.value = result
                _snackbarMessage.value = "Excel tersimpan: ${result.publicDisplayPath}"
                onReady(result)
            } catch (e: Exception) {
                _snackbarMessage.value = "Gagal ekspor Excel: ${e.localizedMessage}"
            }
        }
    }

    fun exportPdf(context: Context, onReady: (ExportResult) -> Unit = {}) {
        val residents = (uiState.value as? WargaKosUiState.Success)?.allResidents ?: emptyList()
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    ReportExporter.exportPdf(context, reportTitle.value, residents)
                }
                _lastExportResult.value = result
                _snackbarMessage.value = "PDF tersimpan: ${result.publicDisplayPath}"
                onReady(result)
            } catch (e: Exception) {
                _snackbarMessage.value = "Gagal membuat PDF: ${e.localizedMessage}"
            }
        }
    }

    private fun observeConnectivity(context: Context) = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            trySend(true)
            awaitClose { }
            return@callbackFlow
        }

        fun currentStatus(): Boolean {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

        trySend(currentStatus())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(currentStatus())
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            cm.registerNetworkCallback(request, callback)
        } catch (_: Exception) {
            trySend(true)
        }

        awaitClose {
            try {
                cm.unregisterNetworkCallback(callback)
            } catch (_: Exception) {
            }
        }
    }

    private companion object {
        const val TAG = "WargaKosVM"
    }
}
