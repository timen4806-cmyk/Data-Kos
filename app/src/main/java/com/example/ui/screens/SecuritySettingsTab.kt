package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.PhonelinkLock
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.data.preferences.ThemeMode
import com.example.security.TotpAuthenticator
import com.example.ui.auth.BiometricAuthHelper
import com.example.ui.auth.BiometricAvailability
import com.example.ui.auth.GoogleAuthHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay

@Composable
fun SecuritySettingsTab(
    username: String,
    themeMode: ThemeMode,
    biometricEnabled: Boolean,
    twoFactorEnabled: Boolean,
    totpSecret: String?,
    isCloudConnected: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
    onToggleTwoFactor: (Boolean) -> Unit,
    onLockOrSignOut: () -> Unit,
    onShowMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val biometricAvailability = remember(context) { BiometricAuthHelper.checkAvailability(context) }

    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(twoFactorEnabled, totpSecret) {
        while (twoFactorEnabled && totpSecret != null) {
            nowMillis = System.currentTimeMillis()
            delay(1000L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Appearance / Dark Mode Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tampilan & Tema Gelap",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = "Aktifkan tema gelap untuk kenyamanan mata saat penggunaan di malam hari.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                val options = listOf(
                    ThemeMode.SYSTEM to "Sistem",
                    ThemeMode.LIGHT to "Terang",
                    ThemeMode.DARK to "Gelap",
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    options.forEachIndexed { idx, (mode, label) ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = idx, count = options.size),
                            selected = themeMode == mode,
                            onClick = { onThemeModeChange(mode) },
                            modifier = Modifier.testTag("theme_mode_${mode.name.lowercase()}"),
                        ) {
                            Text(label)
                        }
                    }
                }
            }
        }

        // E2EE + Biometric + 2FA Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Enkripsi End-to-End & 2FA",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Status: AES-256-GCM + PBKDF2 Aktif",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Text(
                            text = "Username: @${username.ifBlank { "-" }}\nKredensial dan data penghuni kos di SQLite lokal & cloud tersimpan sebagai ciphertext terenkripsi.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Login Sidik Jari / Biometrik",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = when (biometricAvailability) {
                                BiometricAvailability.AVAILABLE -> "Gunakan sidik jari atau pengenal wajah untuk masuk cepat"
                                BiometricAvailability.NONE_ENROLLED -> "Belum ada sidik jari terdaftar di perangkat"
                                BiometricAvailability.UNAVAILABLE -> "Sensor biometrik tidak tersedia"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = biometricEnabled,
                        onCheckedChange = onToggleBiometric,
                        enabled = biometricAvailability == BiometricAvailability.AVAILABLE,
                        modifier = Modifier.testTag("settings_biometric_switch"),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.PhonelinkLock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "2-Factor Authentication (TOTP)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Wajibkan kode OTP 6 digit saat login dengan username & password",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = twoFactorEnabled,
                        onCheckedChange = onToggleTwoFactor,
                        modifier = Modifier.testTag("settings_2fa_switch"),
                    )
                }

                if (twoFactorEnabled && totpSecret != null) {
                    val currentOtp = TotpAuthenticator.generateCode(totpSecret, nowMillis)
                    val remainingSec = 30 - ((nowMillis / 1000L) % 30L)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "Kunci Setup Authenticator (TOTP)",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = totpSecret,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("WargaKos 2FA Secret", totpSecret))
                                        onShowMessage("Secret Key 2FA disalin ke clipboard.")
                                    }
                                ) {
                                    Icon(
                                        Icons.Filled.ContentCopy,
                                        contentDescription = "Salin Secret Key 2FA",
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                }
                            }
                            Text(
                                text = "Kode OTP Aktif Saat Ini: $currentOtp (reset dalam ${remainingSec}d)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
            }
        }

        // Optional Cloud Firestore Sync Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isCloudConnected) Icons.Filled.CloudDone else Icons.Filled.CloudSync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sinkronisasi Cloud Firestore (Opsional)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Text(
                    text = if (isCloudConnected) {
                        val cloudEmail = Firebase.auth.currentUser?.email.orEmpty()
                        "Cadangan Cloud aktif${if (cloudEmail.isNotBlank()) " ($cloudEmail)" else ""}. Catatan terenkripsi disinkronkan real-time antar perangkat."
                    } else {
                        "Aplikasi berjalan mandiri dengan database SQLite lokal terenkripsi. Anda tetap bisa menghubungkan Cloud Sync secara opsional untuk cadangan lintas perangkat."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (isCloudConnected) {
                    OutlinedButton(
                        onClick = {
                            GoogleAuthHelper.signOut(credentialManager = credentialManager, scope = scope) {
                                onShowMessage("Sinkronisasi Cloud diputuskan. Data tetap aman di SQLite lokal.")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("disconnect_cloud_sync_button"),
                    ) {
                        Text("Putuskan Cloud Sync")
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            GoogleAuthHelper.onGoogleSignInClicked(
                                context = context,
                                credentialManager = credentialManager,
                                onAuthSuccess = {
                                    onShowMessage("Cloud Firestore terhubung untuk sinkronisasi terenkripsi.")
                                },
                                onAuthError = { onShowMessage(it) },
                                scope = scope,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("connect_cloud_sync_button"),
                    ) {
                        Icon(Icons.Filled.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Hubungkan Cloud Sync")
                    }
                }
            }
        }

        // Session Controls
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onLockOrSignOut,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("sign_out_button"),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Kunci & Keluar dari Akun")
                }
            }
        }
    }
}
