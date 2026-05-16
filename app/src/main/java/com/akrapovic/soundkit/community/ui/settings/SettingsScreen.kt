package com.akrapovic.soundkit.community.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.akrapovic.soundkit.community.ui.SoundKitUiState
import com.akrapovic.soundkit.community.ui.components.AkraActionButton
import com.akrapovic.soundkit.community.ui.components.AkraCard
import com.akrapovic.soundkit.community.ui.components.AkraHeroHeader
import com.akrapovic.soundkit.community.ui.components.AkraScreen
import com.akrapovic.soundkit.community.ui.components.AkraStatusPill
import com.akrapovic.soundkit.community.ui.theme.AkraColors

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    state: SoundKitUiState,
    onAutoReconnectChanged: (Boolean) -> Unit,
    onDebugLoggingChanged: (Boolean) -> Unit,
    onForgetDevice: () -> Unit,
) {
    val context = LocalContext.current
    val powerManager = remember { context.getSystemService(PowerManager::class.java) }
    val ignoringOptimizations =
        powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    val showForgetConfirm = remember { mutableStateOf(false) }

    AkraScreen(modifier = modifier) {
        AkraHeroHeader(
            eyebrow = "Settings",
            title = "Preferences",
            subtitle = "A few simple controls for connection reliability and privacy.",
        )

        TogglePanel(
            accent = MaterialTheme.colorScheme.primary,
            title = "Auto reconnect",
            body = "Reconnect to your saved receiver if the Bluetooth link drops.",
            checked = state.settings.autoReconnect,
            onCheckedChange = onAutoReconnectChanged,
        )

        TogglePanel(
            accent = MaterialTheme.colorScheme.primary,
            title = "Detailed logs",
            body = "Keep local troubleshooting logs on this phone. They are never uploaded.",
            checked = state.settings.debugLoggingEnabled,
            onCheckedChange = onDebugLoggingChanged,
        )

        AkraCard(accent = AkraColors.Titanium) {
            AkraStatusPill(text = "Saved receiver", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = state.settings.rememberedDeviceName ?: "No receiver remembered",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = state.settings.rememberedDeviceAddress
                    ?: "Connect to a receiver from Find your Sound Kit.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            AkraActionButton(
                label = "Forget receiver",
                enabled = state.settings.rememberedDeviceAddress != null,
                filled = false,
                onClick = { showForgetConfirm.value = true },
                contentDescription = "Forget remembered receiver",
            )
        }

        if (showForgetConfirm.value) {
            AlertDialog(
                onDismissRequest = { showForgetConfirm.value = false },
                title = { Text("Forget this receiver?") },
                text = {
                    Text("The app will stop auto-reconnecting and will need a manual scan to find your Sound Kit again.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        showForgetConfirm.value = false
                        onForgetDevice()
                    }) { Text("Forget") }
                },
                dismissButton = {
                    TextButton(onClick = { showForgetConfirm.value = false }) { Text("Cancel") }
                },
            )
        }

        AkraCard(accent = if (ignoringOptimizations) AkraColors.Signal else MaterialTheme.colorScheme.primary) {
            AkraStatusPill(
                text = if (ignoringOptimizations) "BATTERY OK" else "BATTERY OPTIMIZATION",
                color = if (ignoringOptimizations) AkraColors.Signal else MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Background connection",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (ignoringOptimizations) {
                    "Allowed. The app can keep the receiver connection more reliably when the screen is off."
                } else {
                    "Allow background use for a more reliable connection when the screen is off."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!ignoringOptimizations) {
                Spacer(Modifier.height(4.dp))
                AkraActionButton(
                    label = "Allow background use",
                    enabled = true,
                    onClick = {
                        val intent = Intent(
                            AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        ).apply { data = Uri.parse("package:${context.packageName}") }
                        context.startActivity(intent)
                    },
                    contentDescription = "Open battery optimization settings",
                )
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun TogglePanel(
    accent: Color,
    title: String,
    body: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AkraCard(accent = if (checked) accent else AkraColors.Mist) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.background,
                        checkedTrackColor = accent,
                        uncheckedThumbColor = AkraColors.Mist,
                        uncheckedTrackColor = AkraColors.Titanium,
                        uncheckedBorderColor = AkraColors.Steel,
                    ),
                )
            }
        }
    }
}
