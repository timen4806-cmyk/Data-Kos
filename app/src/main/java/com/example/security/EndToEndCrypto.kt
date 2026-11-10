package com.example.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedEnvelope(
    val ciphertextBase64: String,
    val ivBase64: String,
    val version: Int = 1,
)

/**
 * End-to-End Encryption engine using AES-256-GCM + PBKDF2WithHmacSHA256.
 * All resident PII is encrypted client-side before being saved to local SQLite (Room)
 * or transmitted to Cloud Firestore.
 */
object EndToEndCrypto {
    private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val KEY_CHECK_MARKER = "WARGAKOS_VAULT_V1"

    private val secureRandom = SecureRandom()

    fun generateSaltBase64(lengthBytes: Int = 16): String {
        val salt = ByteArray(lengthBytes)
        secureRandom.nextBytes(salt)
        return CodecUtil.encodeBase64(salt)
    }

    fun deriveVaultKey(passphrase: String, saltBase64: String): SecretKey {
        val salt = CodecUtil.decodeBase64(saltBase64)
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val keyBytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(keyBytes, "AES")
    }

    fun normalizeUsername(username: String): String = username.trim().lowercase()

    fun computeAccountId(username: String): String {
        val normalized = normalizeUsername(username)
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        return "usr_${hex.take(24)}"
    }

    fun deriveCredentialKey(username: String, password: String, saltBase64: String): SecretKey {
        val combined = "${normalizeUsername(username)}:$password"
        return deriveVaultKey(combined, saltBase64)
    }

    fun secretKeyFromBytes(bytes: ByteArray): SecretKey = SecretKeySpec(bytes, "AES")

    fun createKeyCheckEnvelope(key: SecretKey): EncryptedEnvelope {
        return encryptString(KEY_CHECK_MARKER, key)
    }

    fun verifyKeyCheckEnvelope(key: SecretKey, ciphertextBase64: String, ivBase64: String): Boolean {
        return try {
            decryptString(ciphertextBase64, ivBase64, key) == KEY_CHECK_MARKER
        } catch (_: Exception) {
            false
        }
    }

    fun encryptString(plaintext: String, key: SecretKey): EncryptedEnvelope {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return EncryptedEnvelope(
            ciphertextBase64 = CodecUtil.encodeBase64(ciphertext),
            ivBase64 = CodecUtil.encodeBase64(iv),
            version = 1,
        )
    }

    fun decryptString(ciphertextBase64: String, ivBase64: String, key: SecretKey): String {
        val iv = CodecUtil.decodeBase64(ivBase64)
        val ciphertext = CodecUtil.decodeBase64(ciphertextBase64)
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val plainBytes = cipher.doFinal(ciphertext)
        return String(plainBytes, Charsets.UTF_8)
    }
}

/**
 * Hardware-backed Android Keystore wrapper for securely caching the user's unlocked
 * 256-bit E2EE Vault Key locally so BiometricPrompt unlock can restore it instantly.
 */
object KeystoreVaultWrapper {
    private const val TAG = "KeystoreVaultWrapper"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val PREFS_NAME = "wargakos_keystore_vault"
    private const val KEY_ALIAS_PREFIX = "wargakos_wrap_"
    private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"

    private fun getOrCreateWrapKey(userId: String): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val alias = "$KEY_ALIAS_PREFIX$userId"
            val existingKey = keyStore.getKey(alias, null) as? SecretKey
            if (existingKey != null) return existingKey

            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val spec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        } catch (e: Exception) {
            Log.w(TAG, "AndroidKeyStore unavailable in this environment: ${e.message}")
            null
        }
    }

    fun saveWrappedVaultKey(context: Context, userId: String, vaultKey: SecretKey) {
        val wrapKey = getOrCreateWrapKey(userId) ?: return
        try {
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, wrapKey)
            val encrypted = cipher.doFinal(vaultKey.encoded)
            val iv = cipher.iv
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString("key_$userId", CodecUtil.encodeBase64(encrypted))
                .putString("iv_$userId", CodecUtil.encodeBase64(iv))
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to wrap vault key: ${e.message}")
        }
    }

    fun loadWrappedVaultKey(context: Context, userId: String): SecretKey? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cipherTextB64 = prefs.getString("key_$userId", null) ?: return null
        val ivB64 = prefs.getString("iv_$userId", null) ?: return null
        val wrapKey = getOrCreateWrapKey(userId) ?: return null
        return try {
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            val iv = CodecUtil.decodeBase64(ivB64)
            cipher.init(Cipher.DECRYPT_MODE, wrapKey, GCMParameterSpec(128, iv))
            val keyBytes = cipher.doFinal(CodecUtil.decodeBase64(cipherTextB64))
            EndToEndCrypto.secretKeyFromBytes(keyBytes)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unwrap vault key: ${e.message}")
            null
        }
    }

    fun hasWrappedVaultKey(context: Context, userId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains("key_$userId") && prefs.contains("iv_$userId")
    }

    fun clearWrappedVaultKey(context: Context, userId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove("key_$userId").remove("iv_$userId").apply()
    }
}

/**
 * RFC 6238 TOTP (Time-based One-Time Password) helper for 2-Factor Authentication.
 * Compatible with Google Authenticator, Authy, and Microsoft Authenticator.
 */
object TotpAuthenticator {
    private const val TIME_STEP_SECONDS = 30L
    private const val CODE_DIGITS = 6
    private val secureRandom = SecureRandom()

    fun generateSecretBase32(byteLength: Int = 20): String {
        val bytes = ByteArray(byteLength)
        secureRandom.nextBytes(bytes)
        return CodecUtil.encodeBase32(bytes)
    }

    fun buildOtpAuthUri(accountEmail: String, secretBase32: String, issuer: String = "WargaKos"): String {
        val label = if (accountEmail.isNotBlank()) "$issuer:$accountEmail" else issuer
        return "otpauth://totp/$label?secret=$secretBase32&issuer=$issuer&algorithm=SHA1&digits=$CODE_DIGITS&period=$TIME_STEP_SECONDS"
    }

    fun generateCode(secretBase32: String, timeMillis: Long = System.currentTimeMillis()): String {
        val secretBytes = CodecUtil.decodeBase32(secretBase32)
        val counter = (timeMillis / 1000L) / TIME_STEP_SECONDS
        val data = ByteBuffer.allocate(8).putLong(counter).array()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(secretBytes, "HmacSHA1"))
        val hash = mac.doFinal(data)
        val offset = hash[hash.size - 1].toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)
        val otp = binary % 1_000_000
        return otp.toString().padStart(CODE_DIGITS, '0')
    }

    fun verifyCode(
        secretBase32: String,
        inputCode: String,
        timeMillis: Long = System.currentTimeMillis(),
        windowSteps: Int = 1,
    ): Boolean {
        val normalized = inputCode.trim()
        if (normalized.length != CODE_DIGITS || !normalized.all { it.isDigit() }) return false
        for (offset in -windowSteps..windowSteps) {
            val checkTime = timeMillis + offset * TIME_STEP_SECONDS * 1000L
            if (generateCode(secretBase32, checkTime) == normalized) {
                return true
            }
        }
        return false
    }
}

/**
 * Pure-Kotlin Base64 and Base32 codecs compatible with minSdk 24 and JVM unit tests.
 */
object CodecUtil {
    private const val B64_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private const val B32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun encodeBase64(input: ByteArray): String {
        val out = StringBuilder((input.size + 2) / 3 * 4)
        var i = 0
        while (i < input.size) {
            val b0 = input[i++].toInt() and 0xFF
            val hasB1 = i < input.size
            val b1 = if (hasB1) input[i++].toInt() and 0xFF else 0
            val hasB2 = i < input.size
            val b2 = if (hasB2) input[i++].toInt() and 0xFF else 0

            out.append(B64_CHARS[b0 ushr 2])
            out.append(B64_CHARS[((b0 and 0x03) shl 4) or (b1 ushr 4)])
            out.append(if (hasB1) B64_CHARS[((b1 and 0x0F) shl 2) or (b2 ushr 6)] else '=')
            out.append(if (hasB2) B64_CHARS[b2 and 0x3F] else '=')
        }
        return out.toString()
    }

    fun decodeBase64(input: String): ByteArray {
        val clean = input.trim().replace("\n", "").replace("\r", "")
        if (clean.isEmpty()) return ByteArray(0)
        val padding = clean.count { it == '=' }
        val out = ByteArray((clean.length * 3) / 4 - padding)
        var outIdx = 0
        var i = 0
        while (i < clean.length) {
            val c0 = B64_CHARS.indexOf(clean[i++])
            val c1 = B64_CHARS.indexOf(clean[i++])
            val c2Char = clean[i++]
            val c3Char = clean[i++]
            val c2 = if (c2Char == '=') 0 else B64_CHARS.indexOf(c2Char)
            val c3 = if (c3Char == '=') 0 else B64_CHARS.indexOf(c3Char)

            val triple = (c0 shl 18) or (c1 shl 12) or (c2 shl 6) or c3
            if (outIdx < out.size) out[outIdx++] = ((triple ushr 16) and 0xFF).toByte()
            if (outIdx < out.size) out[outIdx++] = ((triple ushr 8) and 0xFF).toByte()
            if (outIdx < out.size) out[outIdx++] = (triple and 0xFF).toByte()
        }
        return out
    }

    fun encodeBase32(bytes: ByteArray): String {
        val out = StringBuilder((bytes.size * 8 + 4) / 5)
        var buffer = 0
        var bitsLeft = 0
        for (byte in bytes) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                val index = (buffer shr (bitsLeft - 5)) and 0x1F
                bitsLeft -= 5
                out.append(B32_CHARS[index])
            }
        }
        if (bitsLeft > 0) {
            val index = (buffer shl (5 - bitsLeft)) and 0x1F
            out.append(B32_CHARS[index])
        }
        return out.toString()
    }

    fun decodeBase32(base32: String): ByteArray {
        val clean = base32.uppercase().filter { it in B32_CHARS }
        val out = ByteArray((clean.length * 5) / 8)
        var buffer = 0
        var bitsLeft = 0
        var outIdx = 0
        for (ch in clean) {
            val value = B32_CHARS.indexOf(ch)
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            if (bitsLeft >= 8) {
                if (outIdx < out.size) {
                    out[outIdx++] = ((buffer shr (bitsLeft - 8)) and 0xFF).toByte()
                }
                bitsLeft -= 8
            }
        }
        return out
    }
}
