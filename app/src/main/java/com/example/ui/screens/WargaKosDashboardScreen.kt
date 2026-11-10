package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.SyncStatus
import com.example.data.model.WargaKosResident
import com.example.data.preferences.ThemeMode
import com.example.export.ReportExporter
import com.example.ui.components.ResidentFormSheet
import com.example.ui.viewmodel.ResidentFilter
import com.example.ui.viewmodel.WargaKosUiState
import com.example.ui.viewmodel.WargaKosViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WargaKosDashboardScreen(
    viewModel: WargaKosViewModel,
    username: String,
    themeMode: ThemeMode,
    isDarkTheme: Boolean,
    biometricEnabled: Boolean,
    twoFactorEnabled: Boolean,
    totpSecret: String?,
    onThemeModeChange: (ThemeMode) -> Unit,
    onToggleQuickDarkTheme: () -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
    onToggleTwoFactor: (Boolean) -> Unit,
    onLockOrSignOut: () -> Unit,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val activeFilter by viewModel.filter.collectAsStateWithLifecycle()
    val reportTitle by viewModel.reportTitle.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val isCloudConnected by viewModel.isCloudConnected.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val snackbarMsg by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val lastExportResult by viewModel.lastExportResult.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showFormSheet by remember { mutableStateOf(false) }
    var editingResident by remember { mutableStateOf<WargaKosResident?>(null) }
    var deleteCandidate by remember { mutableStateOf<WargaKosResident?>(null) }
    var localToastMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(snackbarMsg, localToastMsg) {
        val msg = snackbarMsg ?: localToastMsg
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
            localToastMsg = null
        }
    }

    val allResidents = (uiState as? WargaKosUiState.Success)?.allResidents ?: emptyList()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (useNavRail) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight(),
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    NavigationRailItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = {
                            Icon(
                                if (selectedTab == 0) Icons.Filled.Groups else Icons.Outlined.Groups,
                                contentDescription = "Data Warga",
                            )
                        },
                        label = { Text("Warga") },
                    )
                    NavigationRailItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = {
                            Icon(
                                if (selectedTab == 1) Icons.Filled.Assessment else Icons.Outlined.Assessment,
                                contentDescription = "Laporan & Ekspor",
                            )
                        },
                        label = { Text("Laporan") },
                    )
                    NavigationRailItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = {
                            Icon(
                                if (selectedTab == 2) Icons.Filled.Security else Icons.Outlined.Security,
                                contentDescription = "Keamanan & Tema",
                            )
                        },
                        label = { Text("Keamanan") },
                    )
                }
            }

            Scaffold(
                modifier = Modifier.weight(1f),
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = when (selectedTab) {
                                        0 -> "Data Warga Kos"
                                        1 -> "Laporan & Ekspor"
                                        else -> "Keamanan & Tema"
                                    },
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = when {
                                        isCloudConnected && isOnline -> "Cloud Firestore Tersinkron • @$username"
                                        isCloudConnected -> "Mode Offline (SQLite E2EE) • @$username"
                                        else -> "SQLite Lokal Terenkripsi E2EE • @$username"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isCloudConnected && isOnline) {
                                        MaterialTheme.colorScheme.tertiary
                                    } else {
                                        MaterialTheme.colorScheme.secondary
                                    },
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { viewModel.syncNow() },
                                modifier = Modifier.testTag("sync_now_button"),
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.Sync,
                                        contentDescription = "Sinkronkan Cloud",
                                    )
                                }
                            }
                            IconButton(
                                onClick = onToggleQuickDarkTheme,
                                modifier = Modifier.testTag("topbar_theme_toggle"),
                            ) {
                                Icon(
                                    imageVector = if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                                    contentDescription = if (isDarkTheme) "Mode Terang" else "Mode Gelap",
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                },
                bottomBar = {
                    if (!useNavRail) {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = {
                                    Icon(
                                        if (selectedTab == 0) Icons.Filled.Groups else Icons.Outlined.Groups,
                                        contentDescription = "Data Warga",
                                    )
                                },
                                label = { Text("Data Warga") },
                                modifier = Modifier.testTag("nav_tab_residents"),
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = {
                                    Icon(
                                        if (selectedTab == 1) Icons.Filled.Assessment else Icons.Outlined.Assessment,
                                        contentDescription = "Laporan & Ekspor",
                                    )
                                },
                                label = { Text("Laporan") },
                                modifier = Modifier.testTag("nav_tab_reports"),
                            )
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                icon = {
                                    Icon(
                                        if (selectedTab == 2) Icons.Filled.Security else Icons.Outlined.Security,
                                        contentDescription = "Keamanan & Tema",
                                    )
                                },
                                label = { Text("Keamanan") },
                                modifier = Modifier.testTag("nav_tab_security"),
                            )
                        }
                    }
                },
                floatingActionButton = {
                    if (selectedTab == 0) {
                        ExtendedFloatingActionButton(
                            onClick = {
                                editingResident = null
                                showFormSheet = true
                            },
                            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                            text = { Text("Tambah Warga") },
                            modifier = Modifier.testTag("add_resident_fab"),
                        )
                    }
                },
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (selectedTab) {
                        0 -> ResidentLedgerTab(
                            uiState = uiState,
                            searchQuery = searchQuery,
                            activeFilter = activeFilter,
                            isOnline = isOnline,
                            isCloudConnected = isCloudConnected,
                            onSearchChange = viewModel::updateSearchQuery,
                            onFilterChange = viewModel::updateFilter,
                            onAddClick = {
                                editingResident = null
                                showFormSheet = true
                            },
                            onEditClick = { resident ->
                                editingResident = resident
                                showFormSheet = true
                            },
                            onDeleteClick = { resident -> deleteCandidate = resident },
                            onShareSingle = { resident ->
                                val txt = ReportExporter.buildShareSummaryText(reportTitle, listOf(resident))
                                context.startActivity(
                                    ReportExporter.shareTextIntent(txt, "Bagikan Data ${resident.nama}")
                                )
                            },
                        )

                        1 -> ReportsExportTab(
                            reportTitle = reportTitle,
                            residents = allResidents,
                            lastExportResult = lastExportResult,
                            onReportTitleChange = viewModel::updateReportTitle,
                            onExportExcel = { viewModel.exportExcel(context) },
                            onExportPdf = { viewModel.exportPdf(context) },
                            onShowMessage = { localToastMsg = it },
                        )

                        2 -> SecuritySettingsTab(
                            username = username,
                            themeMode = themeMode,
                            biometricEnabled = biometricEnabled,
                            twoFactorEnabled = twoFactorEnabled,
                            totpSecret = totpSecret,
                            isCloudConnected = isCloudConnected,
                            onThemeModeChange = onThemeModeChange,
                            onToggleBiometric = onToggleBiometric,
                            onToggleTwoFactor = onToggleTwoFactor,
                            onLockOrSignOut = onLockOrSignOut,
                            onShowMessage = { localToastMsg = it },
                        )
                    }
                }
            }
        }
    }

    if (showFormSheet) {
        ResidentFormSheet(
            initialResident = editingResident,
            onDismiss = { showFormSheet = false },
            onSave = { resident ->
                viewModel.saveResident(resident)
                showFormSheet = false
            },
        )
    }

    deleteCandidate?.let { resident ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Hapus Data Warga") },
            text = { Text("Yakin ingin menghapus catatan \"${resident.nama}\" (Kamar ${resident.noKamar.ifBlank { "-" }})?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteResident(resident.id)
                        deleteCandidate = null
                    },
                    modifier = Modifier.testTag("confirm_delete_button"),
                ) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text("Batal")
                }
            },
        )
    }
}

@Composable
private fun ResidentLedgerTab(
    uiState: WargaKosUiState,
    searchQuery: String,
    activeFilter: ResidentFilter,
    isOnline: Boolean,
    isCloudConnected: Boolean,
    onSearchChange: (String) -> Unit,
    onFilterChange: (ResidentFilter) -> Unit,
    onAddClick: () -> Unit,
    onEditClick: (WargaKosResident) -> Unit,
    onDeleteClick: (WargaKosResident) -> Unit,
    onShareSingle: (WargaKosResident) -> Unit,
) {
    when (uiState) {
        WargaKosUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is WargaKosUiState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = uiState.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        is WargaKosUiState.Success -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 92.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    SummaryStatsCard(
                        totalResidents = uiState.allResidents.size,
                        totalOccupants = uiState.totalOccupants,
                        pendingCount = uiState.pendingCount,
                        isOnline = isOnline,
                        isCloudConnected = isCloudConnected,
                    )
                }

                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        placeholder = { Text("Cari nama, no kamar, pemilik, alamat...") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("resident_search_input"),
                    )
                }

                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ResidentFilter.entries) { filter ->
                            FilterChip(
                                selected = activeFilter == filter,
                                onClick = { onFilterChange(filter) },
                                label = { Text(filter.label) },
                            )
                        }
                    }
                }

                if (uiState.filteredResidents.isEmpty()) {
                    item {
                        EmptyResidentsCard(
                            hasAnyData = uiState.allResidents.isNotEmpty(),
                            onAddClick = onAddClick,
                        )
                    }
                } else {
                    items(uiState.filteredResidents, key = { it.id }) { resident ->
                        ResidentCard(
                            resident = resident,
                            isCloudConnected = isCloudConnected,
                            onEditClick = { onEditClick(resident) },
                            onDeleteClick = { onDeleteClick(resident) },
                            onShareClick = { onShareSingle(resident) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryStatsCard(
    totalResidents: Int,
    totalOccupants: Int,
    pendingCount: Int,
    isOnline: Boolean,
    isCloudConnected: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isCloudConnected) "Brankas SQLite & Cloud E2EE" else "Brankas SQLite E2EE Lokal",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "$totalResidents Catatan • $totalOccupants Penghuni",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            Surface(
                shape = RoundedCornerShape(50),
                color = if (!isCloudConnected || pendingCount > 0 || !isOnline) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.tertiaryContainer
                },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (isCloudConnected && isOnline && pendingCount == 0) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = when {
                            !isCloudConnected -> "Lokal E2EE"
                            pendingCount > 0 -> "$pendingCount tertunda"
                            isOnline -> "Sinkron"
                            else -> "Offline"
                        },
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResidentCard(
    resident: WargaKosResident,
    isCloudConnected: Boolean,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onShareClick: () -> Unit,
) {
    Card(
        onClick = onEditClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("resident_card_${resident.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = if (resident.noKamar.isNotBlank()) "Kmr ${resident.noKamar}" else "Kmr -",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = resident.nama,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (resident.pemilik.isNotBlank()) {
                            Text(
                                text = "Pemilik: ${resident.pemilik}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = isCloudConnected && resident.syncStatus != SyncStatus.SYNCED) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = "Offline",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            if (resident.alamat.isNotBlank()) {
                Text(
                    text = resident.alamat,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (resident.jumlahOrang.isNotBlank()) {
                    InfoChip("${resident.jumlahOrang} Orang")
                }
                if (resident.statusNikah.isNotBlank()) {
                    InfoChip("Nikah: ${resident.statusNikah}")
                }
                if (resident.statusBelumNikah.isNotBlank()) {
                    InfoChip("Belum Nikah: ${resident.statusBelumNikah}")
                }
            }

            if (resident.keterangan.isNotBlank()) {
                Text(
                    text = "Ket: ${resident.keterangan}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onShareClick) {
                    Icon(Icons.Filled.Share, contentDescription = "Bagikan data ${resident.nama}")
                }
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit data ${resident.nama}")
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Hapus data ${resident.nama}",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoChip(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun EmptyResidentsCard(
    hasAnyData: Boolean,
    onAddClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_records),
                contentDescription = "Belum ada catatan warga kos",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .height(170.dp)
                    .clip(RoundedCornerShape(18.dp)),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (hasAnyData) "Tidak Ada Hasil Pencarian" else "Belum Ada Data Warga Kos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (hasAnyData) {
                    "Coba ubah kata kunci pencarian atau pilih filter status lainnya."
                } else {
                    "Tambahkan penghuni kos pertama Anda. Seluruh data tersimpan terenkripsi di SQLite lokal dan disinkronkan ke Cloud Firestore."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!hasAnyData) {
                Spacer(modifier = Modifier.height(14.dp))
                TextButton(onClick = onAddClick) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Tambah Warga Sekarang")
                }
            }
        }
    }
}
