package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.WargaKosDatabase
import com.example.data.local.WargaKosEntity
import com.example.data.model.SyncStatus
import com.example.data.model.WargaKosResident
import com.example.export.ReportExporter
import com.example.security.EndToEndCrypto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun readAppNameFromContext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("WargaKos", appName)
    }

    @Test
    fun roomSqlite_storesEncryptedRecordOffline() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, WargaKosDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val salt = EndToEndCrypto.generateSaltBase64()
            val key = EndToEndCrypto.deriveVaultKey("offlinePin123", salt)
            val resident = WargaKosResident(
                id = "offline-1",
                pemilik = "Pak Joko",
                nama = "Rina Kusuma",
                noKamar = "C-07",
                jumlahOrang = "1",
                alamat = "Semarang",
                statusNikah = "",
                statusBelumNikah = "✓",
                keterangan = "Offline test",
            )
            val env = EndToEndCrypto.encryptString(resident.toJsonString(), key)
            db.wargaKosDao().upsert(
                WargaKosEntity(
                    id = resident.id,
                    userId = "user_local",
                    encryptedPayload = env.ciphertextBase64,
                    iv = env.ivBase64,
                    syncStatus = SyncStatus.PENDING_UPLOAD.name,
                )
            )
            val loaded = db.wargaKosDao().observeActiveRecords("user_local").first()
            assertEquals(1, loaded.size)
            val plain = EndToEndCrypto.decryptString(loaded[0].encryptedPayload, loaded[0].iv, key)
            val restored = WargaKosResident.fromJsonString(
                id = loaded[0].id,
                json = plain,
                syncStatus = SyncStatus.valueOf(loaded[0].syncStatus),
                createdAtMillis = loaded[0].createdAtMillis,
                updatedAtMillis = loaded[0].updatedAtMillis,
            )
            assertEquals("Rina Kusuma", restored.nama)
            assertEquals("C-07", restored.noKamar)
            assertEquals("✓", restored.statusBelumNikah)
        } finally {
            db.close()
        }
    }

    @Test
    fun reportExporter_generatesPdfAndExcelFiles() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val residents = listOf(
            WargaKosResident(
                id = "1",
                pemilik = "Ibu Dewi",
                nama = "Dian Pratama",
                noKamar = "102",
                jumlahOrang = "2",
                alamat = "Jl. Sudirman No. 45",
                statusNikah = "✓",
                statusBelumNikah = "",
                keterangan = "Aktif",
            )
        )

        val pdfResult = ReportExporter.exportPdf(context, "DATA WARGA KOS", residents)
        assertTrue(pdfResult.internalFile.exists())
        val pdfHeader = pdfResult.internalFile.inputStream().use { stream ->
            val buf = ByteArray(4)
            stream.read(buf)
            String(buf, Charsets.US_ASCII)
        }
        assertEquals("%PDF", pdfHeader)

        val xlsxResult = ReportExporter.exportExcel(context, "DATA WARGA KOS", residents)
        assertTrue(xlsxResult.internalFile.exists())
        assertTrue(xlsxResult.internalFile.length() > 500)
    }

    @Test
    fun securityViewModel_registerAndLoginWithEncryptedUsernamePasswordAnd2fa() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = com.example.data.preferences.AppPreferences(context)
        val vm = com.example.ui.viewmodel.SecurityViewModel(prefs, context)

        suspend fun awaitUntil(predicate: () -> Boolean) {
            var attempts = 0
            while (!predicate() && attempts < 100) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                kotlinx.coroutines.delay(25)
                attempts++
            }
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        }

        awaitUntil { vm.sessionState.value is com.example.ui.viewmodel.AuthSessionState.Unauthenticated }

        vm.registerAccount(
            username = "pengelolakos",
            password = "PasswordRahasia1",
            confirmPassword = "PasswordRahasia1",
            enableBiometric = false,
            enableTwoFactor = true,
        )

        awaitUntil { vm.sessionState.value is com.example.ui.viewmodel.AuthSessionState.Authenticated }
        val authed = vm.sessionState.value as com.example.ui.viewmodel.AuthSessionState.Authenticated
        assertEquals("pengelolakos", authed.username)
        val activeTotp = com.example.security.TotpAuthenticator.generateCode(authed.totpSecret!!)

        vm.lockOrSignOut()
        awaitUntil { vm.sessionState.value is com.example.ui.viewmodel.AuthSessionState.Unauthenticated }

        // Wrong password must fail
        vm.loginWithCredentials("pengelolakos", "PasswordSalah", activeTotp)
        awaitUntil { !vm.isBusy.value && vm.errorMessage.value != null }
        assertTrue(vm.sessionState.value is com.example.ui.viewmodel.AuthSessionState.Unauthenticated)

        // Correct password + valid 2FA OTP must succeed
        vm.loginWithCredentials("pengelolakos", "PasswordRahasia1", activeTotp)
        awaitUntil { vm.sessionState.value is com.example.ui.viewmodel.AuthSessionState.Authenticated }
        assertTrue(vm.sessionState.value is com.example.ui.viewmodel.AuthSessionState.Authenticated)
    }
}
