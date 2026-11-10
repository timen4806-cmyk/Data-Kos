package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.WargaKosDatabase
import com.example.data.preferences.AppPreferences
import com.example.data.preferences.ThemeMode
import com.example.data.repository.WargaKosRepository
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.WargaKosDashboardScreen
import com.example.ui.theme.WargaKosTheme
import com.example.ui.viewmodel.AuthSessionState
import com.example.ui.viewmodel.SecurityViewModel
import com.example.ui.viewmodel.WargaKosViewModel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val context = LocalContext.current
            val prefs = remember(context) { AppPreferences(context.applicationContext) }
            val themeMode by prefs.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val isDarkTheme = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            val scope = rememberCoroutineScope()

            WargaKosTheme(darkTheme = isDarkTheme) {
                AppNavigation(
                    themeMode = themeMode,
                    isDarkTheme = isDarkTheme,
                    onThemeModeChange = { mode ->
                        scope.launch { prefs.setThemeMode(mode) }
                    },
                    onToggleQuickDarkTheme = {
                        scope.launch {
                            prefs.setThemeMode(if (isDarkTheme) ThemeMode.LIGHT else ThemeMode.DARK)
                        }
                    },
                )
            }
        }
    }
}

@Composable
fun AppNavigation(
    themeMode: ThemeMode,
    isDarkTheme: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onToggleQuickDarkTheme: () -> Unit,
) {
    val securityViewModel: SecurityViewModel = viewModel(
        key = "wargakos_auth_security",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                SecurityViewModel(
                    preferences = AppPreferences(app),
                    appContext = app,
                )
            }
        }
    )

    val sessionState by securityViewModel.sessionState.collectAsStateWithLifecycle()
    val isBusy by securityViewModel.isBusy.collectAsStateWithLifecycle()
    val errorMsg by securityViewModel.errorMessage.collectAsStateWithLifecycle()

    when (val state = sessionState) {
        AuthSessionState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is AuthSessionState.Unauthenticated -> {
            AuthScreen(
                unauthenticatedState = state,
                isBusy = isBusy,
                errorMessage = errorMsg,
                isDarkTheme = isDarkTheme,
                onToggleDarkTheme = onToggleQuickDarkTheme,
                onLoginUsernameChanged = securityViewModel::onLoginUsernameChanged,
                onLogin = securityViewModel::loginWithCredentials,
                onRegister = securityViewModel::registerAccount,
                onBiometricLoginSuccess = securityViewModel::loginWithBiometricSucceeded,
                onClearError = securityViewModel::clearError,
            )
        }

        is AuthSessionState.Authenticated -> {
            val wargaKosViewModel: WargaKosViewModel = viewModel(
                key = "wargakos_${state.accountId}_${state.vaultKey.hashCode()}",
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        val dbId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(dbId)
                        val dao = WargaKosDatabase.getInstance(app).wargaKosDao()
                        val repo = WargaKosRepository(db, dao)
                        WargaKosViewModel(
                            repository = repo,
                            preferences = AppPreferences(app),
                            appContext = app,
                            userId = state.accountId,
                            vaultKey = state.vaultKey,
                        )
                    }
                }
            )

            WargaKosDashboardScreen(
                viewModel = wargaKosViewModel,
                username = state.username,
                themeMode = themeMode,
                isDarkTheme = isDarkTheme,
                biometricEnabled = state.metadata.biometricEnabled,
                twoFactorEnabled = state.metadata.twoFactorEnabled,
                totpSecret = state.totpSecret,
                onThemeModeChange = onThemeModeChange,
                onToggleQuickDarkTheme = onToggleQuickDarkTheme,
                onToggleBiometric = securityViewModel::toggleBiometric,
                onToggleTwoFactor = securityViewModel::toggleTwoFactor,
                onLockOrSignOut = securityViewModel::lockOrSignOut,
            )
        }
    }
}
