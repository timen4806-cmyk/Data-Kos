package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.security.EndToEndCrypto
import com.example.security.KeystoreVaultWrapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

data class CachedVaultMetadata(
    val salt: String,
    val keyCheckCiphertext: String,
    val keyCheckIv: String,
    val twoFactorEnabled: Boolean,
    val biometricEnabled: Boolean,
    val totpSecretEncrypted: String,
    val totpSecretIv: String,
)

data class LocalAccountMetadata(
    val accountId: String,
    val usernameNormalized: String,
    val salt: String,
    val keyCheckCiphertext: String,
    val keyCheckIv: String,
    val encryptedProfile: String,
    val profileIv: String,
    val twoFactorEnabled: Boolean,
    val biometricEnabled: Boolean,
    val totpSecretEncrypted: String,
    val totpSecretIv: String,
)

private val Context.wargaKosDataStore: DataStore<Preferences> by preferencesDataStore(name = "wargakos_prefs")

class AppPreferences(private val context: Context) {

    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val reportTitleKey = stringPreferencesKey("report_title")
    private val registeredAccountIdsKey = stringSetPreferencesKey("registered_account_ids")
    private val lastUsernameKey = stringPreferencesKey("last_username")

    val themeMode: Flow<ThemeMode> = context.wargaKosDataStore.data.map { prefs ->
        when (prefs[themeModeKey]) {
            ThemeMode.DARK.name -> ThemeMode.DARK
            ThemeMode.LIGHT.name -> ThemeMode.LIGHT
            else -> ThemeMode.SYSTEM
        }
    }

    val reportTitle: Flow<String> = context.wargaKosDataStore.data.map { prefs ->
        prefs[reportTitleKey] ?: "DATA WARGA KOS"
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.wargaKosDataStore.edit { prefs ->
            prefs[themeModeKey] = mode.name
        }
    }

    suspend fun setReportTitle(title: String) {
        context.wargaKosDataStore.edit { prefs ->
            prefs[reportTitleKey] = title
        }
    }

    suspend fun saveLocalAccount(account: LocalAccountMetadata) {
        val id = account.accountId
        context.wargaKosDataStore.edit { prefs ->
            val currentIds = prefs[registeredAccountIdsKey] ?: emptySet()
            prefs[registeredAccountIdsKey] = currentIds + id
            prefs[lastUsernameKey] = account.usernameNormalized
            prefs[stringPreferencesKey("acc_user_$id")] = account.usernameNormalized
            prefs[stringPreferencesKey("acc_salt_$id")] = account.salt
            prefs[stringPreferencesKey("acc_check_$id")] = account.keyCheckCiphertext
            prefs[stringPreferencesKey("acc_check_iv_$id")] = account.keyCheckIv
            prefs[stringPreferencesKey("acc_prof_$id")] = account.encryptedProfile
            prefs[stringPreferencesKey("acc_prof_iv_$id")] = account.profileIv
            prefs[booleanPreferencesKey("acc_2fa_$id")] = account.twoFactorEnabled
            prefs[booleanPreferencesKey("acc_bio_$id")] = account.biometricEnabled
            prefs[stringPreferencesKey("acc_totp_$id")] = account.totpSecretEncrypted
            prefs[stringPreferencesKey("acc_totp_iv_$id")] = account.totpSecretIv
        }
    }

    suspend fun getLocalAccount(accountId: String): LocalAccountMetadata? {
        val prefs = context.wargaKosDataStore.data.first()
        val salt = prefs[stringPreferencesKey("acc_salt_$accountId")] ?: return null
        val check = prefs[stringPreferencesKey("acc_check_$accountId")] ?: return null
        val checkIv = prefs[stringPreferencesKey("acc_check_iv_$accountId")] ?: return null
        val usernameNorm = prefs[stringPreferencesKey("acc_user_$accountId")] ?: return null
        return LocalAccountMetadata(
            accountId = accountId,
            usernameNormalized = usernameNorm,
            salt = salt,
            keyCheckCiphertext = check,
            keyCheckIv = checkIv,
            encryptedProfile = prefs[stringPreferencesKey("acc_prof_$accountId")] ?: "",
            profileIv = prefs[stringPreferencesKey("acc_prof_iv_$accountId")] ?: "",
            twoFactorEnabled = prefs[booleanPreferencesKey("acc_2fa_$accountId")] ?: false,
            biometricEnabled = prefs[booleanPreferencesKey("acc_bio_$accountId")] ?: false,
            totpSecretEncrypted = prefs[stringPreferencesKey("acc_totp_$accountId")] ?: "",
            totpSecretIv = prefs[stringPreferencesKey("acc_totp_iv_$accountId")] ?: "",
        )
    }

    suspend fun getLocalAccountByUsername(username: String): LocalAccountMetadata? {
        val accountId = EndToEndCrypto.computeAccountId(username)
        return getLocalAccount(accountId)
    }

    suspend fun getLastUsername(): String {
        val prefs = context.wargaKosDataStore.data.first()
        return prefs[lastUsernameKey] ?: ""
    }

    suspend fun hasAnyLocalAccount(): Boolean {
        val prefs = context.wargaKosDataStore.data.first()
        return (prefs[registeredAccountIdsKey] ?: emptySet()).isNotEmpty()
    }

    suspend fun getBiometricReadyAccount(appContext: Context): LocalAccountMetadata? {
        val prefs = context.wargaKosDataStore.data.first()
        val lastUser = prefs[lastUsernameKey].orEmpty()
        if (lastUser.isNotBlank()) {
            val lastAcc = getLocalAccountByUsername(lastUser)
            if (lastAcc != null && lastAcc.biometricEnabled &&
                KeystoreVaultWrapper.hasWrappedVaultKey(appContext, lastAcc.accountId)
            ) {
                return lastAcc
            }
        }
        val ids = prefs[registeredAccountIdsKey] ?: emptySet()
        for (id in ids) {
            val acc = getLocalAccount(id)
            if (acc != null && acc.biometricEnabled &&
                KeystoreVaultWrapper.hasWrappedVaultKey(appContext, acc.accountId)
            ) {
                return acc
            }
        }
        return null
    }

    suspend fun cacheVaultMetadata(userId: String, meta: CachedVaultMetadata) {
        context.wargaKosDataStore.edit { prefs ->
            prefs[stringPreferencesKey("vault_salt_$userId")] = meta.salt
            prefs[stringPreferencesKey("vault_check_$userId")] = meta.keyCheckCiphertext
            prefs[stringPreferencesKey("vault_check_iv_$userId")] = meta.keyCheckIv
            prefs[booleanPreferencesKey("vault_2fa_$userId")] = meta.twoFactorEnabled
            prefs[booleanPreferencesKey("vault_bio_$userId")] = meta.biometricEnabled
            prefs[stringPreferencesKey("vault_totp_$userId")] = meta.totpSecretEncrypted
            prefs[stringPreferencesKey("vault_totp_iv_$userId")] = meta.totpSecretIv
        }
    }

    suspend fun getCachedVaultMetadata(userId: String): CachedVaultMetadata? {
        val prefs = context.wargaKosDataStore.data.first()
        val salt = prefs[stringPreferencesKey("vault_salt_$userId")] ?: return null
        val check = prefs[stringPreferencesKey("vault_check_$userId")] ?: return null
        val checkIv = prefs[stringPreferencesKey("vault_check_iv_$userId")] ?: return null
        return CachedVaultMetadata(
            salt = salt,
            keyCheckCiphertext = check,
            keyCheckIv = checkIv,
            twoFactorEnabled = prefs[booleanPreferencesKey("vault_2fa_$userId")] ?: false,
            biometricEnabled = prefs[booleanPreferencesKey("vault_bio_$userId")] ?: false,
            totpSecretEncrypted = prefs[stringPreferencesKey("vault_totp_$userId")] ?: "",
            totpSecretIv = prefs[stringPreferencesKey("vault_totp_iv_$userId")] ?: "",
        )
    }
}
