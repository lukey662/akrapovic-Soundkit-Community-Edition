package com.akrapovic.soundkit.community.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akrapovic.soundkit.community.ui.SoundKitUiState

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    state: SoundKitUiState,
    onAutoReconnectChanged: (Boolean) -> Unit,
    onDebugLoggingChanged: (Boolean) -> Unit,
    onForgetDevice: () -> Unit,
) {
    val context = LocalContext.current
    val powerManager = remember {
        context.getSystemService(PowerManager::class.java)
    }
    val ignoringOptimizations = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        SettingCard(
            title = "Auto reconnect",
            body = "Reconnect to the remembered receiver when the BLE link drops.",
            trailing = {
                Switch(
                    checked = state.settings.autoReconnect,
                    onCheckedChange = onAutoReconnectChanged,
                )
            },
        )

        SettingCard(
            title = "Debug BLE logging",
            body = "Keep local diagnostics for troubleshooting reverse-engineering and receiver compatibility.",
            trailing = {
                Switch(
                    checked = state.settings.debugLoggingEnabled,
                    onCheckedChange = onDebugLoggingChanged,
                )
            },
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Remembered receiver", style = MaterialTheme.typography.titleLarge)
                Text(state.settings.rememberedDeviceName ?: "No receiver remembered")
                Text(state.settings.rememberedDeviceAddress ?: "Connect to a receiver from the scan screen.")
                OutlinedButton(onClick = onForgetDevice) {
                    Text("Forget receiver")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Battery optimization", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (ignoringOptimizations) {
                        "Battery optimization exemption is already enabled for this app."
                    } else {
                        "For reliable screen-off BLE connections, allow this app to run without aggressive battery optimization."
                    },
                )
                Button(
                    enabled = !ignoringOptimizations,
                    onClick = {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    },
                ) {
                    Text("Request exemption")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Safety", style = MaterialTheme.typography.titleLarge)
                Text("Test commands while parked. The app sends only local BLE commands and will not send valve writes until the protocol is verified.")
            }
        }
    }
}

@Composable
private fun SettingCard(
    title: String,
    body: String,
    trailing: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(body)
            }
            trailing()
        }
    }
}

