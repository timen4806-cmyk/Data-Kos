package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.preferences.AppPreferences
import com.example.data.preferences.LocalAccountMetadata
import com.example.security.EndToEndCrypto
import com.example.security.KeystoreVaultWrapper
import com.example.security.TotpAuthenticator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.crypto.SecretKey

sealed interface AuthSessionState {
    data object Loading : AuthSessionState
    data class Unauthenticated(
        val lastUsername: String = "",
        val biometricAccount: LocalAccountMetadata? = null,
        val hasAnyAccount: Boolean = false,
        val twoFactorRequiredForAccount: Boolean = false,
    ) : AuthSessionState

    data class Authenticated(
        val accountId: String,
        val username: String,
        val vaultKey: SecretKey,
        val metadata: LocalAccountMetadata,
        val totpSecret: String?,
    ) : AuthSessionState
}

class SecurityViewModel(
    private val preferences: AppPreferences,
    private val appContext: Context,
) : ViewModel() {

    private val _sessionState = MutableStateFlow<AuthSessionState>(AuthSessionState.Loading)
    val sessionState: StateFlow<AuthSessionState> = _sessionState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    init {
        refreshUnauthenticatedState()
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun refreshUnauthenticatedState() {
        viewModelScope.launch {
            val state = withContext(Dispatchers.IO) {
                buildUnauthenticatedState()
            }
            _sessionState.value = state
        }
    }

    private suspend fun buildUnauthenticatedState(
        twoFactorRequired: Boolean = false,
    ): AuthSessionState.Unauthenticated {
        val lastUser = preferences.getLastUsername()
        val bioAccount = preferences.getBiometricReadyAccount(appContext)
        val hasAny = preferences.hasAnyLocalAccount()
        val needs2fa = twoFactorRequired || (lastUser.isNotBlank() &&
            preferences.getLocalAccountByUsername(lastUser)?.twoFactorEnabled == true)
        return AuthSessionState.Unauthenticated(
            lastUsername = lastUser,
            biometricAccount = bioAccount,
            hasAnyAccount = hasAny,
            twoFactorRequiredForAccount = needs2fa,
        )
    }

    fun onLoginUsernameChanged(username: String) {
        val current = _sessionState.value as? AuthSessionState.Unauthenticated ?: return
        viewModelScope.launch {
            val trimmed = username.trim()
            val needs2fa = if (trimmed.length >= 3) {
                withContext(Dispatchers.IO) {
                    preferences.getLocalAccountByUsername(trimmed)?.twoFactorEnabled == true
                }
            } else {
                false
            }
            if (current.twoFactorRequiredForAccount != needs2fa) {
                _sessionState.value = current.copy(twoFactorRequiredForAccount = needs2fa)
            }
        }
    }

    fun registerAccount(
        username: String,
        password: String,
        confirmPassword: String,
        enableBiometric: Boolean,
        enableTwoFactor: Boolean,
    ) {
        val trimmedUser = username.trim()
        val normalizedUser = EndToEndCrypto.normalizeUsername(trimmedUser)

        when {
            normalizedUser.length < 3 -> {
                _errorMessage.value = "Username minimal 3 karakter."
                return
            }
            !normalizedUser.matches(Regex("^[a-z0-9._-]+$")) -> {
                _errorMessage.value = "Username hanya boleh berisi huruf, angka, titik (.), garis bawah (_), atau tanda hubung (-)."
                return
            }
            password.length < 6 -> {
                _errorMessage.value = "Password minimal 6 karakter."
                return
            }
            password != confirmPassword -> {
                _errorMessage.value = "Konfirmasi password tidak cocok."
                return
            }
        }

        viewModelScope.launch {
            _isBusy.value = true
            _errorMessage.value = null
            try {
                val accountId = EndToEndCrypto.computeAccountId(normalizedUser)
                val existing = withContext(Dispatchers.IO) {
                    preferences.getLocalAccount(accountId)
                }
                if (existing != null) {
                    _errorMessage.value = "Username '$trimmedUser' sudah terdaftar. Silakan masuk."
                    return@launch
                }

                val (derivedKey, metadata, totpPlain) = withContext(Dispatchers.Default) {
                    val salt = EndToEndCrypto.generateSaltBase64()
                    val key = EndToEndCrypto.deriveCredentialKey(normalizedUser, password, salt)
                    val checkEnv = EndToEndCrypto.createKeyCheckEnvelope(key)
                    val profileJson = JSONObject().apply {
                        put("username", trimmedUser)
                        put("createdAt", System.currentTimeMillis())
                    }.toString()
                    val profileEnv = EndToEndCrypto.encryptString(profileJson, key)

                    val totpSecret = if (enableTwoFactor) TotpAuthenticator.generateSecretBase32() else null
                    val totpEnv = totpSecret?.let { EndToEndCrypto.encryptString(it, key) }

                    val meta = LocalAccountMetadata(
                        accountId = accountId,
                        usernameNormalized = normalizedUser,
                        salt = salt,
                        keyCheckCiphertext = checkEnv.ciphertextBase64,
                        keyCheckIv = checkEnv.ivBase64,
                        encryptedProfile = profileEnv.ciphertextBase64,
                        profileIv = profileEnv.ivBase64,
                        twoFactorEnabled = enableTwoFactor,
                        biometricEnabled = enableBiometric,
                        totpSecretEncrypted = totpEnv?.ciphertextBase64 ?: "",
                        totpSecretIv = totpEnv?.ivBase64 ?: "",
                    )
                    Triple(key, meta, totpSecret)
                }

                withContext(Dispatchers.IO) {
                    if (enableBiometric) {
                        KeystoreVaultWrapper.saveWrappedVaultKey(appContext, accountId, derivedKey)
                    } else {
                        KeystoreVaultWrapper.clearWrappedVaultKey(appContext, accountId)
                    }
                    preferences.saveLocalAccount(metadata)
                }

                _sessionState.value = AuthSessionState.Authenticated(
                    accountId = accountId,
                    username = trimmedUser,
                    vaultKey = derivedKey,
                    metadata = metadata,
                    totpSecret = totpPlain,
                )
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Gagal mendaftarkan akun terenkripsi."
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun loginWithCredentials(
        username: String,
        password: String,
        totpCode: String,
    ) {
        val trimmedUser = username.trim()
        if (trimmedUser.isBlank() || password.isBlank()) {
            _errorMessage.value = "Username dan password wajib diisi."
            return
        }

        viewModelScope.launch {
            _isBusy.value = true
            _errorMessage.value = null
            try {
                val accountId = EndToEndCrypto.computeAccountId(trimmedUser)
                val meta = withContext(Dispatchers.IO) {
                    preferences.getLocalAccount(accountId)
                }
                if (meta == null) {
                    _errorMessage.value = "Akun '$trimmedUser' belum terdaftar. Silakan daftar terlebih dahulu."
                    return@launch
                }

                val derivedKey = withContext(Dispatchers.Default) {
                    EndToEndCrypto.deriveCredentialKey(trimmedUser, password, meta.salt)
                }
                val valid = EndToEndCrypto.verifyKeyCheckEnvelope(
                    derivedKey,
                    meta.keyCheckCiphertext,
                    meta.keyCheckIv,
                )
                if (!valid) {
                    _errorMessage.value = "Username atau password salah."
                    return@launch
                }

                val displayUsername = decryptUsername(meta, derivedKey) ?: trimmedUser
                val totpPlain = decryptTotpSecret(meta, derivedKey)

                if (meta.twoFactorEnabled && totpPlain != null) {
                    val curUnauth = _sessionState.value as? AuthSessionState.Unauthenticated
                    if (curUnauth != null && !curUnauth.twoFactorRequiredForAccount) {
                        _sessionState.value = curUnauth.copy(twoFactorRequiredForAccount = true)
                    }
                    if (!TotpAuthenticator.verifyCode(totpPlain, totpCode)) {
                        _errorMessage.value = if (totpCode.isBlank()) {
                            "Akun ini mengaktifkan 2FA. Masukkan kode OTP 6 digit."
                        } else {
                            "Kode 2FA (6 digit) tidak valid atau kedaluwarsa."
                        }
                        return@launch
                    }
                }

                withContext(Dispatchers.IO) {
                    if (meta.biometricEnabled) {
                        KeystoreVaultWrapper.saveWrappedVaultKey(appContext, accountId, derivedKey)
                    }
                    preferences.saveLocalAccount(meta)
                }

                _sessionState.value = AuthSessionState.Authenticated(
                    accountId = accountId,
                    username = displayUsername,
                    vaultKey = derivedKey,
                    metadata = meta,
                    totpSecret = totpPlain,
                )
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Gagal memverifikasi kredensial."
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun loginWithBiometricSucceeded(accountId: String) {
        viewModelScope.launch {
            _isBusy.value = true
            _errorMessage.value = null
            try {
                val meta = withContext(Dispatchers.IO) {
                    preferences.getLocalAccount(accountId)
                }
                if (meta == null) {
                    _errorMessage.value = "Data akun tidak ditemukan. Silakan masuk dengan username & password."
                    return@launch
                }

                val wrappedKey = withContext(Dispatchers.IO) {
                    KeystoreVaultWrapper.loadWrappedVaultKey(appContext, accountId)
                }
                if (wrappedKey == null) {
                    _errorMessage.value = "Kunci biometrik belum tersimpan. Masuk sekali dengan password untuk mengaktifkan."
                    return@launch
                }

                val valid = EndToEndCrypto.verifyKeyCheckEnvelope(
                    wrappedKey,
                    meta.keyCheckCiphertext,
                    meta.keyCheckIv,
                )
                if (!valid) {
                    _errorMessage.value = "Kunci biometrik tidak cocok. Silakan masuk dengan password."
                    return@launch
                }

                val displayUsername = decryptUsername(meta, wrappedKey) ?: meta.usernameNormalized
                val totpPlain = decryptTotpSecret(meta, wrappedKey)

                withContext(Dispatchers.IO) {
                    preferences.saveLocalAccount(meta)
                }

                _sessionState.value = AuthSessionState.Authenticated(
                    accountId = accountId,
                    username = displayUsername,
                    vaultKey = wrappedKey,
                    metadata = meta,
                    totpSecret = totpPlain,
                )
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Login biometrik gagal."
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun toggleBiometric(enabled: Boolean) {
        val current = _sessionState.value as? AuthSessionState.Authenticated ?: return
        viewModelScope.launch {
            val updated = current.metadata.copy(biometricEnabled = enabled)
            withContext(Dispatchers.IO) {
                if (enabled) {
                    KeystoreVaultWrapper.saveWrappedVaultKey(appContext, current.accountId, current.vaultKey)
                } else {
                    KeystoreVaultWrapper.clearWrappedVaultKey(appContext, current.accountId)
                }
                preferences.saveLocalAccount(updated)
            }
            _sessionState.value = current.copy(metadata = updated)
        }
    }

    fun toggleTwoFactor(enabled: Boolean) {
        val current = _sessionState.value as? AuthSessionState.Authenticated ?: return
        viewModelScope.launch {
            val newTotpPlain = if (enabled) {
                current.totpSecret ?: TotpAuthenticator.generateSecretBase32()
            } else {
                null
            }
            val totpEnv = newTotpPlain?.let {
                EndToEndCrypto.encryptString(it, current.vaultKey)
            }
            val updated = current.metadata.copy(
                twoFactorEnabled = enabled,
                totpSecretEncrypted = totpEnv?.ciphertextBase64 ?: "",
                totpSecretIv = totpEnv?.ivBase64 ?: "",
            )
            withContext(Dispatchers.IO) {
                preferences.saveLocalAccount(updated)
            }
            _sessionState.value = current.copy(metadata = updated, totpSecret = newTotpPlain)
        }
    }

    fun lockOrSignOut() {
        _errorMessage.value = null
        viewModelScope.launch {
            val unauth = withContext(Dispatchers.IO) {
                buildUnauthenticatedState()
            }
            _sessionState.value = unauth
        }
    }

    private fun decryptUsername(meta: LocalAccountMetadata, key: SecretKey): String? {
        if (meta.encryptedProfile.isBlank() || meta.profileIv.isBlank()) return null
        return try {
            val json = EndToEndCrypto.decryptString(meta.encryptedProfile, meta.profileIv, key)
            JSONObject(json).optString("username").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun decryptTotpSecret(meta: LocalAccountMetadata, key: SecretKey): String? {
        if (meta.totpSecretEncrypted.isBlank() || meta.totpSecretIv.isBlank()) return null
        return try {
            EndToEndCrypto.decryptString(meta.totpSecretEncrypted, meta.totpSecretIv, key)
        } catch (_: Exception) {
            null
        }
    }
}
