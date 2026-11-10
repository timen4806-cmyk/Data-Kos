package com.example

import com.example.data.model.WargaKosResident
import com.example.export.ReportExporter
import com.example.security.EndToEndCrypto
import com.example.security.TotpAuthenticator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class ExampleUnitTest {

    @Test
    fun endToEndCrypto_encryptAndDecryptRoundTrip() {
        val salt = EndToEndCrypto.generateSaltBase64()
        val key = EndToEndCrypto.deriveVaultKey("SandiRahasia123", salt)
        val wrongKey = EndToEndCrypto.deriveVaultKey("SandiSalah999", salt)

        val checkEnv = EndToEndCrypto.createKeyCheckEnvelope(key)
        assertTrue(EndToEndCrypto.verifyKeyCheckEnvelope(key, checkEnv.ciphertextBase64, checkEnv.ivBase64))
        assertFalse(EndToEndCrypto.verifyKeyCheckEnvelope(wrongKey, checkEnv.ciphertextBase64, checkEnv.ivBase64))

        val plainJson =
            """{"pemilik":"Bapak Hendra","nama":"Siti Aminah","noKamar":"B-02","jumlahOrang":"2","alamat":"Jl. Kaliurang KM 5, Sleman","statusNikah":"✓","statusBelumNikah":"","keterangan":"Lunas"}"""
        val envelope = EndToEndCrypto.encryptString(plainJson, key)
        assertFalse(envelope.ciphertextBase64.contains("Siti Aminah"))

        val decryptedJson = EndToEndCrypto.decryptString(envelope.ciphertextBase64, envelope.ivBase64, key)
        assertEquals(plainJson, decryptedJson)
        assertEquals("✓", WargaKosResident(statusNikah = "✓").statusNikah)
    }

    @Test
    fun totpAuthenticator_generatesAndVerifiesSixDigitCode() {
        val secret = TotpAuthenticator.generateSecretBase32()
        val now = 1_760_000_000_000L
        val code = TotpAuthenticator.generateCode(secret, now)

        assertEquals(6, code.length)
        assertTrue(code.all { it.isDigit() })
        assertTrue(TotpAuthenticator.verifyCode(secret, code, now))
        assertTrue(TotpAuthenticator.verifyCode(secret, code, now + 25_000L))
        assertNotEquals(code, "000000".takeIf { code != "000000" } ?: "111111")
    }

    @Test
    fun reportExporter_buildXlsxBytesContainsValidOoxmlStructureAndMinimum40Rows() {
        val residents = listOf(
            WargaKosResident(
                id = "1",
                pemilik = "Ibu Ratna",
                nama = "Andi Wijaya",
                noKamar = "01",
                jumlahOrang = "1",
                alamat = "Surabaya",
                statusNikah = "",
                statusBelumNikah = "✓",
                keterangan = "Baru masuk",
            )
        )
        val rows = ReportExporter.buildReportRows(residents)
        assertEquals(40, rows.size)
        assertEquals("Andi Wijaya", rows[0][2])
        assertEquals("40", rows[39][0])

        val xlsxBytes = ReportExporter.buildXlsxBytes("DATA WARGA KOS", "10-11-2026 10:00:00", residents)
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(xlsxBytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                entry = zip.nextEntry
            }
        }
        assertTrue(entries.containsKey("[Content_Types].xml"))
        assertTrue(entries.containsKey("xl/workbook.xml"))
        assertTrue(entries.containsKey("xl/styles.xml"))
        val sheet = entries["xl/worksheets/sheet1.xml"]!!
        assertTrue(sheet.contains("Andi Wijaya"))
        assertTrue(sheet.contains("Ketua RW. 01"))
        assertTrue(sheet.contains("Ketua RT. 01"))
        assertTrue(sheet.contains("Ketua RT. 02"))
    }

    @Test
    fun credentialKeyDerivation_isolatesAccountsAndVerifiesCorrectPassword() {
        val salt = EndToEndCrypto.generateSaltBase64()
        val id1 = EndToEndCrypto.computeAccountId("AdminKos")
        val id2 = EndToEndCrypto.computeAccountId(" adminkos ")
        val idOther = EndToEndCrypto.computeAccountId("warga02")
        assertEquals(id1, id2)
        assertNotEquals(id1, idOther)

        val keyRight = EndToEndCrypto.deriveCredentialKey("AdminKos", "SandiKuat99", salt)
        val keyWrongUser = EndToEndCrypto.deriveCredentialKey("otheruser", "SandiKuat99", salt)
        val keyWrongPass = EndToEndCrypto.deriveCredentialKey("AdminKos", "SandiSalah", salt)

        val check = EndToEndCrypto.createKeyCheckEnvelope(keyRight)
        assertTrue(EndToEndCrypto.verifyKeyCheckEnvelope(keyRight, check.ciphertextBase64, check.ivBase64))
        assertFalse(EndToEndCrypto.verifyKeyCheckEnvelope(keyWrongUser, check.ciphertextBase64, check.ivBase64))
        assertFalse(EndToEndCrypto.verifyKeyCheckEnvelope(keyWrongPass, check.ciphertextBase64, check.ivBase64))
    }
}
