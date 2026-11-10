package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.WargaKosResident
import com.example.security.EndToEndCrypto
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class WargaKosRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun saveResidentOfflineFirst_authenticatedOwner_encryptsAndSyncsToFirestoreAndSqlite() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = WargaKosRepository(firestore, localDb.wargaKosDao())
        val salt = EndToEndCrypto.generateSaltBase64()
        val vaultKey = EndToEndCrypto.deriveVaultKey("rahasia123", salt)

        val recordId = "warga_${UUID.randomUUID()}"
        val resident = WargaKosResident(
            id = recordId,
            pemilik = "Ibu Sari",
            nama = "Budi Santoso",
            noKamar = "A-04",
            jumlahOrang = "1",
            alamat = "Jl. Merdeka No. 12, Bandung",
            statusNikah = "",
            statusBelumNikah = "✓",
            keterangan = "Lunas Oktober",
        )

        val saved = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveResidentOfflineFirst(uid, resident, vaultKey)
        }
        assertEquals(recordId, saved.id)

        val cloudDocs = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.fetchCloudRecords().getOrThrow()
        }
        val fetched = cloudDocs.firstOrNull { it.id == recordId }
        assertNotNull(fetched)
        // Verify data in Firestore is encrypted (not plaintext)
        assertTrue(!fetched!!.encryptedPayload.contains("Budi Santoso"))

        val decryptedJson = EndToEndCrypto.decryptString(fetched.encryptedPayload, fetched.iv, vaultKey)
        val decryptedResident = WargaKosResident.fromJsonString(
            id = fetched.id,
            json = decryptedJson,
            syncStatus = saved.syncStatus,
            createdAtMillis = saved.createdAtMillis,
            updatedAtMillis = saved.updatedAtMillis,
        )
        assertEquals("Budi Santoso", decryptedResident.nama)
        assertEquals("A-04", decryptedResident.noKamar)
    }

    @Test
    fun observeCloudSync_authenticatedOwner_emitsRealtimeUpdates() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = WargaKosRepository(firestore, localDb.wargaKosDao())
        val salt = EndToEndCrypto.generateSaltBase64()
        val vaultKey = EndToEndCrypto.deriveVaultKey("rahasia123", salt)
        val envelope = EndToEndCrypto.encryptString("""{"nama":"Ani"}""", vaultKey)
        val recordId = "sync_${UUID.randomUUID()}"

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createCloudRecord(recordId, envelope.ciphertextBase64, envelope.ivBase64).getOrThrow()
        }

        val docs = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeCloudSync().first { list -> list.any { it.id == recordId } }
        }
        assertTrue(docs.any { it.id == recordId })
        assertTrue(localDb.wargaKosDao().getAllForUser(uid).any { it.id == recordId })
    }

    @Test
    fun getCloudRecordById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = WargaKosRepository(firestore, localDb.wargaKosDao())
        val recordId = "alice_${UUID.randomUUID()}"
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createCloudRecord(recordId, "Q2lwaGVydGV4dEVuY3J5cHRlZDEyMzQ=", "MTIzNDU2Nzg5MDEyMzQ1Ng==").getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = WargaKosRepository(firestore, localDb.wargaKosDao())
        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getCloudRecordById(aliceUid, recordId)
        }
        assertTrue(result.isFailure)
        val firestoreEx = result.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx!!.code)
    }

    @Test
    fun observeCloudSync_unauthenticatedUser_failsGracefully() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = WargaKosRepository(firestore, localDb.wargaKosDao())
        val recordId = "unauth_${UUID.randomUUID()}"
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createCloudRecord(recordId, "Q2lwaGVydGV4dEVuY3J5cHRlZDEyMzQ=", "MTIzNDU2Nzg5MDEyMzQ1Ng==").getOrThrow()
        }

        auth.signOut()
        val unauthResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.getCloudRecordById(aliceUid, recordId)
        }
        assertTrue(unauthResult.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            unauthResult.exceptionOrNull()?.unwrapFirestoreException()?.code
        )

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                aliceRepo.observeCloudSync().first()
            }
            fail("Expected failure when unauthenticated")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("login"))
        }
    }

    @Test
    fun securityVault_ownerCreateAndRead_andCrossUserDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = WargaKosRepository(firestore, localDb.wargaKosDao())
        val salt = EndToEndCrypto.generateSaltBase64()
        val key = EndToEndCrypto.deriveVaultKey("pin654321", salt)
        val check = EndToEndCrypto.createKeyCheckEnvelope(key)

        val saveRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.saveSecurityVault(
                userId = aliceUid,
                salt = salt,
                keyCheckCiphertext = check.ciphertextBase64,
                keyCheckIv = check.ivBase64,
                twoFactorEnabled = true,
                biometricEnabled = true,
            )
        }
        assertTrue(saveRes.isSuccess)

        val fetchedVault = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.getSecurityVault(aliceUid).getOrThrow()
        }
        assertNotNull(fetchedVault)
        assertTrue(fetchedVault!!.twoFactorEnabled)

        signInTestUser(BOB_EMAIL)
        val bobRepo = WargaKosRepository(firestore, localDb.wargaKosDao())
        val crossRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getSecurityVault(aliceUid)
        }
        assertTrue(crossRes.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            crossRes.exceptionOrNull()?.unwrapFirestoreException()?.code
        )
    }

    private companion object {
        const val ALICE_EMAIL = "alice@wargakos.test"
        const val BOB_EMAIL = "bob@wargakos.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
