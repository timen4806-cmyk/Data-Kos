package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import org.json.JSONObject

enum class SyncStatus {
    SYNCED,
    PENDING_UPLOAD,
    PENDING_DELETE,
}

/**
 * Decrypted domain model matching all columns from the reference Data Warga Kos script.
 */
data class WargaKosResident(
    val id: String = "",
    val pemilik: String = "",
    val nama: String = "",
    val noKamar: String = "",
    val jumlahOrang: String = "",
    val alamat: String = "",
    val statusNikah: String = "",
    val statusBelumNikah: String = "",
    val keterangan: String = "",
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
) {
    fun toJsonString(): String {
        return JSONObject().apply {
            put("pemilik", pemilik)
            put("nama", nama)
            put("noKamar", noKamar)
            put("jumlahOrang", jumlahOrang)
            put("alamat", alamat)
            put("statusNikah", statusNikah)
            put("statusBelumNikah", statusBelumNikah)
            put("keterangan", keterangan)
        }.toString()
    }

    fun toReportRow(rowNumber: Int): List<String> = listOf(
        rowNumber.toString(),
        pemilik,
        nama,
        noKamar,
        jumlahOrang,
        alamat,
        statusNikah,
        statusBelumNikah,
        keterangan,
    )

    companion object {
        val STATUS_OPTIONS = listOf("", "✓", "Ya", "Tidak")

        fun fromJsonString(
            id: String,
            json: String,
            syncStatus: SyncStatus,
            createdAtMillis: Long,
            updatedAtMillis: Long,
        ): WargaKosResident {
            val obj = JSONObject(json)
            return WargaKosResident(
                id = id,
                pemilik = obj.optString("pemilik", ""),
                nama = obj.optString("nama", ""),
                noKamar = obj.optString("noKamar", ""),
                jumlahOrang = obj.optString("jumlahOrang", ""),
                alamat = obj.optString("alamat", ""),
                statusNikah = obj.optString("statusNikah", ""),
                statusBelumNikah = obj.optString("statusBelumNikah", ""),
                keterangan = obj.optString("keterangan", ""),
                syncStatus = syncStatus,
                createdAtMillis = createdAtMillis,
                updatedAtMillis = updatedAtMillis,
            )
        }
    }
}

/**
 * Encrypted Firestore document stored at /users/{userId}/warga_kos/{recordId}.
 */
@IgnoreExtraProperties
data class WargaKosFirestoreDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val encryptedPayload: String = "",
    val iv: String = "",
    val encryptionVersion: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

/**
 * Encrypted security vault metadata stored at /users/{userId}/security/vault.
 */
@IgnoreExtraProperties
data class SecurityVaultDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val salt: String = "",
    val keyCheckCiphertext: String = "",
    val keyCheckIv: String = "",
    val twoFactorEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val totpSecretEncrypted: String = "",
    val totpSecretIv: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)
