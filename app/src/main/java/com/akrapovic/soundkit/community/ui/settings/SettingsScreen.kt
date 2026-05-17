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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.akrapovic.soundkit.community.domain.SavedReceiver
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
    onConnectOnLaunchChanged: (Boolean) -> Unit,
    onDebugLoggingChanged: (Boolean) -> Unit,
    onSetDefaultReceiver: (String) -> Unit,
    onRemoveReceiver: (String) -> Unit,
    onUpdateNickname: (String, String?) -> Unit,
    onForgetAll: () -> Unit,
    onOpenBeta: () -> Unit,
) {
    val context = LocalContext.current
    val powerManager = remember { context.getSystemService(PowerManager::class.java) }
    val ignoringOptimizations =
        powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    val removeTarget = remember { mutableStateOf<SavedReceiver?>(null) }
    val showForgetAllConfirm = remember { mutableStateOf(false) }
    val savedReceivers = state.settings.savedReceivers

    AkraScreen(modifier = modifier) {
        AkraHeroHeader(
            eyebrow = "Settings",
            title = "Preferences",
            subtitle = "Connection reliability, saved receivers, and privacy.",
        )

        TogglePanel(
            accent = MaterialTheme.colorScheme.primary,
            title = "Connect on launch",
            body = "Try your default saved receiver when the app opens (after onboarding).",
            checked = state.settings.connectOnLaunch,
            onCheckedChange = onConnectOnLaunchChanged,
        )

        TogglePanel(
            accent = MaterialTheme.colorScheme.primary,
            title = "Auto reconnect",
            body = "Reconnect to your default receiver if the Bluetooth link drops.",
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
            AkraStatusPill(text = "Saved receivers", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = if (savedReceivers.isEmpty()) {
                    "No receivers saved yet"
                } else {
                    "${savedReceivers.size} saved · up to 8"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (savedReceivers.isEmpty()) {
                Text(
                    text = "Connect from Home to save a receiver. The first connection becomes your default.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    savedReceivers.forEach { receiver ->
                        SavedReceiverRow(
                            receiver = receiver,
                            onSetDefault = { onSetDefaultReceiver(receiver.address) },
                            onEditNickname = { nickname -> onUpdateNickname(receiver.address, nickname) },
                            onRemove = { removeTarget.value = receiver },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                AkraActionButton(
                    label = "Forget all receivers",
                    enabled = true,
                    filled = false,
                    onClick = { showForgetAllConfirm.value = true },
                    contentDescription = "Forget all saved receivers",
                )
            }
        }

        removeTarget.value?.let { receiver ->
            AlertDialog(
                onDismissRequest = { removeTarget.value = null },
                title = { Text("Remove ${receiver.displayName()}?") },
                text = {
                    Text("This receiver will be removed from your saved list. You can scan and connect again anytime.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        onRemoveReceiver(receiver.address)
                        removeTarget.value = null
                    }) { Text("Remove") }
                },
                dismissButton = {
                    TextButton(onClick = { removeTarget.value = null }) { Text("Cancel") }
                },
            )
        }

        AkraCard(accent = MaterialTheme.colorScheme.secondary, onClick = onOpenBeta, contentDescription = "Automation Beta") {
            AkraStatusPill(text = "Beta", color = MaterialTheme.colorScheme.secondary)
            Text(
                text = "Automation (Beta)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Schedules, geofences, and rules — experimental. Parked use only.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (showForgetAllConfirm.value) {
            AlertDialog(
                onDismissRequest = { showForgetAllConfirm.value = false },
                title = { Text("Forget all receivers?") },
                text = {
                    Text("Clears every saved receiver and stops auto-reconnect. You will need to scan again.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        showForgetAllConfirm.value = false
                        onForgetAll()
                    }) { Text("Forget all") }
                },
                dismissButton = {
                    TextButton(onClick = { showForgetAllConfirm.value = false }) { Text("Cancel") }
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
private fun SavedReceiverRow(
    receiver: SavedReceiver,
    onSetDefault: () -> Unit,
    onEditNickname: (String?) -> Unit,
    onRemove: () -> Unit,
) {
    val showNicknameDialog = remember { mutableStateOf(false) }
    var nicknameDraft by remember(receiver.address) { mutableStateOf(receiver.nickname.orEmpty()) }

    if (showNicknameDialog.value) {
        AlertDialog(
            onDismissRequest = { showNicknameDialog.value = false },
            title = { Text("Nickname") },
            text = {
                OutlinedTextField(
                    value = nicknameDraft,
                    onValueChange = { nicknameDraft = it },
                    label = { Text("Nickname (optional)") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onEditNickname(nicknameDraft.trim().takeIf { it.isNotEmpty() })
                    showNicknameDialog.value = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showNicknameDialog.value = false }) { Text("Cancel") }
            },
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = receiver.displayName(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = receiver.address,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = { showNicknameDialog.value = true }) {
            Text("Nickname")
        }
        TextButton(
            onClick = onSetDefault,
            enabled = !receiver.isDefault,
            modifier = Modifier.semantics {
                contentDescription = if (receiver.isDefault) {
                    "Default receiver"
                } else {
                    "Set ${receiver.displayName()} as default"
                }
            },
        ) {
            Text(if (receiver.isDefault) "★ Default" else "Set default")
        }
        TextButton(onClick = onRemove) {
            Text("Remove")
        }
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
        modifier = Modifier.fillMaxWidth(),
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
