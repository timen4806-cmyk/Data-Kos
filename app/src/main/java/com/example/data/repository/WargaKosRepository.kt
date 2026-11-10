package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.local.WargaKosDao
import com.example.data.local.WargaKosDatabase
import com.example.data.local.WargaKosEntity
import com.example.data.model.SecurityVaultDoc
import com.example.data.model.SyncStatus
import com.example.data.model.WargaKosFirestoreDoc
import com.example.data.model.WargaKosResident
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.example.security.EndToEndCrypto
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.crypto.SecretKey

class WargaKosRepository(
    private val db: FirebaseFirestore,
    private val dao: WargaKosDao,
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        dao = WargaKosDatabase.getInstance(context.applicationContext).wargaKosDao(),
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Pengguna harus login sebelum mengakses sinkronisasi cloud.")
    }

    fun isCloudAuthenticated(): Boolean = auth.currentUser != null

    private fun recordsCollection(cloudUid: String) =
        db.collection("users").document(cloudUid).collection("warga_kos")

    private fun vaultDocRef(cloudUid: String) =
        db.collection("users").document(cloudUid).collection("security").document("vault")

    /**
     * Local SQLite reactive stream (works offline at all times).
     */
    fun observeLocalEncryptedRecords(userId: String): Flow<List<WargaKosEntity>> =
        dao.observeActiveRecords(userId)

    /**
     * Real-time Firestore listener aligned with security rules (`whereEqualTo("userId", uid)`),
     * reconciling incoming cloud documents into local Room SQLite without overwriting
     * pending offline edits.
     */
    fun observeCloudSync(localUserId: String? = null): Flow<List<WargaKosFirestoreDoc>> = flow {
        val cloudUid = requireUserId()
        val roomOwnerId = localUserId ?: cloudUid
        val path = "users/$cloudUid/warga_kos"
        emitAll(
            recordsCollection(cloudUid)
                .whereEqualTo("userId", cloudUid)
                .snapshots()
                .map { snapshot ->
                    val docs = snapshot.toObjects(
                        WargaKosFirestoreDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                    reconcileCloudToLocal(roomOwnerId, docs)
                    docs
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == cloudUid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    private suspend fun reconcileCloudToLocal(
        roomOwnerId: String,
        cloudDocs: List<WargaKosFirestoreDoc>,
    ) {
        val localById = dao.getAllForUser(roomOwnerId).associateBy { it.id }
        val cloudIds = cloudDocs.map { it.id }.filter { it.isNotBlank() }

        val toUpsert = cloudDocs.mapNotNull { doc ->
            if (doc.id.isBlank()) return@mapNotNull null
            val existingLocal = localById[doc.id]
            // Preserve unsynced local mutations until pushed
            if (existingLocal != null && existingLocal.syncStatus != SyncStatus.SYNCED.name) {
                return@mapNotNull null
            }
            val createdMs = doc.createdAt?.toDate()?.time ?: existingLocal?.createdAtMillis ?: System.currentTimeMillis()
            val updatedMs = doc.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
            WargaKosEntity(
                id = doc.id,
                userId = roomOwnerId,
                encryptedPayload = doc.encryptedPayload,
                iv = doc.iv,
                encryptionVersion = doc.encryptionVersion.toInt(),
                syncStatus = SyncStatus.SYNCED.name,
                createdAtMillis = createdMs,
                updatedAtMillis = updatedMs,
            )
        }
        if (toUpsert.isNotEmpty()) {
            dao.upsertAll(toUpsert)
        }
        if (cloudIds.isEmpty()) {
            dao.deleteAllSyncedForUser(roomOwnerId)
        } else {
            dao.deleteSyncedNotIn(roomOwnerId, cloudIds)
        }
    }

    /**
     * Encrypts resident data end-to-end, saves to local SQLite immediately for offline access,
     * and attempts cloud sync to Firestore if cloud sync session is active.
     */
    suspend fun saveResidentOfflineFirst(
        userId: String,
        resident: WargaKosResident,
        vaultKey: SecretKey,
    ): WargaKosResident {
        val recordId = resident.id.ifBlank { UUID.randomUUID().toString() }
        val now = System.currentTimeMillis()
        val existing = dao.getById(recordId)
        val createdAtMillis = if (resident.createdAtMillis > 0L) {
            resident.createdAtMillis
        } else {
            existing?.createdAtMillis ?: now
        }

        val envelope = EndToEndCrypto.encryptString(resident.toJsonString(), vaultKey)
        val pendingEntity = WargaKosEntity(
            id = recordId,
            userId = userId,
            encryptedPayload = envelope.ciphertextBase64,
            iv = envelope.ivBase64,
            encryptionVersion = envelope.version,
            syncStatus = SyncStatus.PENDING_UPLOAD.name,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = now,
        )
        dao.upsert(pendingEntity)

        val cloudUid = auth.currentUser?.uid
        val finalSyncStatus = if (cloudUid != null) {
            val syncResult = pushEntityToCloud(cloudUid, pendingEntity, isNew = existing == null)
            if (syncResult.isSuccess) {
                dao.upsert(pendingEntity.copy(syncStatus = SyncStatus.SYNCED.name))
                SyncStatus.SYNCED
            } else {
                SyncStatus.PENDING_UPLOAD
            }
        } else {
            SyncStatus.PENDING_UPLOAD
        }

        return resident.copy(
            id = recordId,
            syncStatus = finalSyncStatus,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = now,
        )
    }

    suspend fun deleteResidentOfflineFirst(userId: String, recordId: String) {
        val existing = dao.getById(recordId) ?: return
        val cloudUid = auth.currentUser?.uid
        if (cloudUid == null) {
            dao.deleteById(recordId)
            return
        }
        dao.upsert(existing.copy(syncStatus = SyncStatus.PENDING_DELETE.name, updatedAtMillis = System.currentTimeMillis()))
        val cloudDelete = deleteCloudRecord(recordId)
        if (cloudDelete.isSuccess) {
            dao.deleteById(recordId)
        }
    }

    suspend fun syncPendingChanges(userId: String): Result<Int> {
        val cloudUid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("LOCAL_ONLY"))

        var syncedCount = 0
        val pendingUploads = dao.getPendingUploads(userId)
        for (entity in pendingUploads) {
            val cloudExists = try {
                recordsCollection(cloudUid).document(entity.id).get().await().exists()
            } catch (e: Exception) {
                return Result.failure(e)
            }
            val pushRes = pushEntityToCloud(cloudUid, entity, isNew = !cloudExists)
            if (pushRes.isSuccess) {
                dao.upsert(entity.copy(syncStatus = SyncStatus.SYNCED.name))
                syncedCount++
            } else {
                return Result.failure(pushRes.exceptionOrNull() ?: IllegalStateException("Sync failed"))
            }
        }

        val pendingDeletes = dao.getPendingDeletes(userId)
        for (entity in pendingDeletes) {
            val delRes = deleteCloudRecord(entity.id)
            if (delRes.isSuccess) {
                dao.deleteById(entity.id)
                syncedCount++
            } else {
                return Result.failure(delRes.exceptionOrNull() ?: IllegalStateException("Delete sync failed"))
            }
        }
        return Result.success(syncedCount)
    }

    private suspend fun pushEntityToCloud(
        cloudUid: String,
        entity: WargaKosEntity,
        isNew: Boolean,
    ): Result<Unit> {
        val docRef = recordsCollection(cloudUid).document(entity.id)
        return try {
            if (isNew) {
                val payload = mapOf(
                    "userId" to cloudUid,
                    "encryptedPayload" to entity.encryptedPayload,
                    "iv" to entity.iv,
                    "encryptionVersion" to entity.encryptionVersion,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                docRef.set(payload).await()
            } else {
                val updatePayload = mapOf(
                    "encryptedPayload" to entity.encryptedPayload,
                    "iv" to entity.iv,
                    "encryptionVersion" to entity.encryptionVersion,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(
                e,
                if (isNew) OperationType.CREATE else OperationType.UPDATE,
                docRef.path
            )
            Result.failure(e)
        }
    }

    // Direct cloud methods used by sync and Robolectric Firestore rules tests
    suspend fun createCloudRecord(
        recordId: String,
        encryptedPayload: String,
        iv: String,
        encryptionVersion: Int = 1,
    ): Result<String> {
        val uid = requireUserId()
        val docRef = recordsCollection(uid).document(recordId)
        val payload = mapOf(
            "userId" to uid,
            "encryptedPayload" to encryptedPayload,
            "iv" to iv,
            "encryptionVersion" to encryptionVersion,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        return try {
            docRef.set(payload).await()
            Result.success(recordId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getCloudRecordById(ownerUid: String, recordId: String): Result<WargaKosFirestoreDoc?> {
        val docRef = recordsCollection(ownerUid).document(recordId)
        return try {
            val snap = docRef.get().await()
            Result.success(
                snap.toObject(
                    WargaKosFirestoreDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun fetchCloudRecords(): Result<List<WargaKosFirestoreDoc>> {
        val uid = requireUserId()
        val path = "users/$uid/warga_kos"
        return try {
            val snap = recordsCollection(uid)
                .whereEqualTo("userId", uid)
                .get()
                .await()
            Result.success(
                snap.toObjects(
                    WargaKosFirestoreDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun deleteCloudRecord(recordId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = recordsCollection(uid).document(recordId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getSecurityVault(userId: String): Result<SecurityVaultDoc?> {
        val docRef = vaultDocRef(userId)
        return try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                Result.success(null)
            } else {
                Result.success(
                    snap.toObject(
                        SecurityVaultDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun saveSecurityVault(
        userId: String,
        salt: String,
        keyCheckCiphertext: String,
        keyCheckIv: String,
        twoFactorEnabled: Boolean,
        biometricEnabled: Boolean,
        totpSecretEncrypted: String = "",
        totpSecretIv: String = "",
    ): Result<Unit> {
        val docRef = vaultDocRef(userId)
        return try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val createPayload = mutableMapOf<String, Any>(
                    "userId" to userId,
                    "salt" to salt,
                    "keyCheckCiphertext" to keyCheckCiphertext,
                    "keyCheckIv" to keyCheckIv,
                    "twoFactorEnabled" to twoFactorEnabled,
                    "biometricEnabled" to biometricEnabled,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                if (totpSecretEncrypted.isNotEmpty()) {
                    createPayload["totpSecretEncrypted"] = totpSecretEncrypted
                    createPayload["totpSecretIv"] = totpSecretIv
                }
                docRef.set(createPayload).await()
            } else {
                val updatePayload = mutableMapOf<String, Any>(
                    "salt" to salt,
                    "keyCheckCiphertext" to keyCheckCiphertext,
                    "keyCheckIv" to keyCheckIv,
                    "twoFactorEnabled" to twoFactorEnabled,
                    "biometricEnabled" to biometricEnabled,
                    "totpSecretEncrypted" to totpSecretEncrypted,
                    "totpSecretIv" to totpSecretIv,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }
}
