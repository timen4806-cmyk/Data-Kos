package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhonelinkLock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.R
import com.example.ui.auth.BiometricAuthHelper
import com.example.ui.auth.BiometricAvailability
import com.example.ui.viewmodel.AuthSessionState

@Composable
fun AuthScreen(
    unauthenticatedState: AuthSessionState.Unauthenticated,
    isBusy: Boolean,
    errorMessage: String?,
    isDarkTheme: Boolean,
    onToggleDarkTheme: () -> Unit,
    onLoginUsernameChanged: (String) -> Unit,
    onLogin: (username: String, password: String, totpCode: String) -> Unit,
    onRegister: (
        username: String,
        password: String,
        confirmPassword: String,
        enableBiometric: Boolean,
        enableTwoFactor: Boolean,
    ) -> Unit,
    onBiometricLoginSuccess: (accountId: String) -> Unit,
    onClearError: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val biometricAvailability = remember(context) { BiometricAuthHelper.checkAvailability(context) }

    // Default to "Daftar" (tab 1) if user hasn't created an account yet, otherwise "Masuk" (tab 0)
    var selectedMode by rememberSaveable(unauthenticatedState.hasAnyAccount) {
        mutableIntStateOf(if (unauthenticatedState.hasAnyAccount) 0 else 1)
    }

    var loginUsername by rememberSaveable { mutableStateOf(unauthenticatedState.lastUsername) }
    var loginPassword by remember { mutableStateOf("") }
    var loginTotp by remember { mutableStateOf("") }
    var showLoginPass by remember { mutableStateOf(false) }

    var regUsername by rememberSaveable { mutableStateOf("") }
    var regPassword by remember { mutableStateOf("") }
    var regConfirmPassword by remember { mutableStateOf("") }
    var showRegPass by remember { mutableStateOf(false) }
    var enableBiometric by rememberSaveable {
        mutableStateOf(biometricAvailability == BiometricAvailability.AVAILABLE)
    }
    var enableTwoFactor by rememberSaveable { mutableStateOf(false) }
    var bioError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(unauthenticatedState.lastUsername) {
        if (loginUsername.isBlank() && unauthenticatedState.lastUsername.isNotBlank()) {
            loginUsername = unauthenticatedState.lastUsername
        }
    }

    val biometricAccount = unauthenticatedState.biometricAccount
    val canUseBiometric = biometricAccount != null &&
        biometricAvailability == BiometricAvailability.AVAILABLE &&
        activity != null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        MaterialTheme.colorScheme.background,
                    )
                )
            )
            .safeDrawingPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Terenkripsi",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "E2EE AES-256-GCM",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }

                IconButton(
                    onClick = onToggleDarkTheme,
                    modifier = Modifier.testTag("auth_theme_toggle"),
                ) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                        contentDescription = if (isDarkTheme) "Mode Terang" else "Mode Gelap",
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .height(175.dp)
                    .clip(RoundedCornerShape(24.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_warga_kos_hero),
                    contentDescription = "Ilustrasi rumah kos modern WargaKos",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.68f))
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(18.dp)
                ) {
                    Text(
                        text = "WargaKos",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                    )
                    Text(
                        text = "Brankas Data Penghuni Kos Terenkripsi",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = selectedMode == 0,
                            onClick = {
                                selectedMode = 0
                                bioError = null
                                onClearError()
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            modifier = Modifier.testTag("auth_tab_login"),
                        ) {
                            Text("Masuk")
                        }
                        SegmentedButton(
                            selected = selectedMode == 1,
                            onClick = {
                                selectedMode = 1
                                bioError = null
                                onClearError()
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            modifier = Modifier.testTag("auth_tab_register"),
                        ) {
                            Text("Daftar")
                        }
                    }

                    if (selectedMode == 0) {
                        Text(
                            text = "Masuk ke Akun WargaKos",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Gunakan username & password terenkripsi end-to-end atau verifikasi sidik jari.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        OutlinedTextField(
                            value = loginUsername,
                            onValueChange = {
                                loginUsername = it
                                onLoginUsernameChanged(it)
                            },
                            label = { Text("Username") },
                            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_username_input"),
                        )

                        OutlinedTextField(
                            value = loginPassword,
                            onValueChange = { loginPassword = it },
                            label = { Text("Password") },
                            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                            singleLine = true,
                            visualTransformation = if (showLoginPass) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showLoginPass = !showLoginPass }) {
                                    Icon(
                                        if (showLoginPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = "Tampilkan password",
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_password_input"),
                        )

                        AnimatedVisibility(visible = unauthenticatedState.twoFactorRequiredForAccount) {
                            OutlinedTextField(
                                value = loginTotp,
                                onValueChange = { if (it.length <= 6) loginTotp = it.filter(Char::isDigit) },
                                label = { Text("Kode 2FA Authenticator (6 digit)") },
                                leadingIcon = { Icon(Icons.Filled.PhonelinkLock, contentDescription = null) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("login_totp_input"),
                            )
                        }

                        val shownError = bioError ?: errorMessage
                        if (shownError != null) {
                            ErrorBanner(shownError)
                        }

                        Button(
                            onClick = {
                                bioError = null
                                onLogin(loginUsername, loginPassword, loginTotp)
                            },
                            enabled = !isBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("login_submit_button"),
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Memverifikasi...")
                            } else {
                                Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Masuk")
                            }
                        }

                        if (canUseBiometric) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                HorizontalDivider(modifier = Modifier.weight(1f))
                                Text(
                                    text = "  atau  ",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                HorizontalDivider(modifier = Modifier.weight(1f))
                            }

                            OutlinedButton(
                                onClick = {
                                    bioError = null
                                    val acc = biometricAccount!!
                                    BiometricAuthHelper.authenticate(
                                        activity = activity!!,
                                        title = "Masuk dengan Sidik Jari",
                                        subtitle = "Verifikasi biometrik untuk akun @${acc.usernameNormalized}",
                                        onSuccess = { onBiometricLoginSuccess(acc.accountId) },
                                        onError = { bioError = it },
                                    )
                                },
                                enabled = !isBusy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("biometric_login_button"),
                            ) {
                                Icon(Icons.Filled.Fingerprint, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Masuk dengan Sidik Jari (@${biometricAccount!!.usernameNormalized})")
                            }
                        }
                    } else {
                        Text(
                            text = "Daftar Akun Baru",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Username dan password diturunkan menjadi kunci enkripsi AES-256-GCM sehingga data Anda hanya bisa dibuka oleh Anda.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        OutlinedTextField(
                            value = regUsername,
                            onValueChange = { regUsername = it },
                            label = { Text("Username") },
                            placeholder = { Text("mis. adminkos") },
                            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("register_username_input"),
                        )

                        OutlinedTextField(
                            value = regPassword,
                            onValueChange = { regPassword = it },
                            label = { Text("Password (min. 6 karakter)") },
                            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                            singleLine = true,
                            visualTransformation = if (showRegPass) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showRegPass = !showRegPass }) {
                                    Icon(
                                        if (showRegPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = "Tampilkan password",
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("register_password_input"),
                        )

                        OutlinedTextField(
                            value = regConfirmPassword,
                            onValueChange = { regConfirmPassword = it },
                            label = { Text("Konfirmasi Password") },
                            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                            singleLine = true,
                            visualTransformation = if (showRegPass) VisualTransformation.None else PasswordVisualTransformation(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("register_confirm_password_input"),
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Aktifkan Login Sidik Jari", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = if (biometricAvailability == BiometricAvailability.AVAILABLE) {
                                        "Buka cepat dengan biometrik perangkat"
                                    } else {
                                        "Sensor biometrik tidak tersedia / belum terdaftar"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = enableBiometric,
                                onCheckedChange = { enableBiometric = it },
                                enabled = biometricAvailability == BiometricAvailability.AVAILABLE,
                                modifier = Modifier.testTag("register_biometric_switch"),
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.PhonelinkLock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Aktifkan 2FA (Kode OTP)", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Tambahkan kode 6 digit Authenticator saat login",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = enableTwoFactor,
                                onCheckedChange = { enableTwoFactor = it },
                                modifier = Modifier.testTag("register_2fa_switch"),
                            )
                        }

                        val shownError = bioError ?: errorMessage
                        if (shownError != null) {
                            ErrorBanner(shownError)
                        }

                        Button(
                            onClick = {
                                bioError = null
                                onRegister(
                                    regUsername,
                                    regPassword,
                                    regConfirmPassword,
                                    enableBiometric && biometricAvailability == BiometricAvailability.AVAILABLE,
                                    enableTwoFactor,
                                )
                            },
                            enabled = !isBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("register_submit_button"),
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Mengenkripsi Akun...")
                            } else {
                                Icon(Icons.Filled.PersonAdd, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Daftar & Enkripsi Akun")
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Keamanan E2EE: Password diproses dengan PBKDF2-HMAC-SHA256 (100.000 iterasi) & AES-256-GCM. Password tidak pernah disimpan dalam bentuk teks biasa.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(12.dp),
        )
    }
}
