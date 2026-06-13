package com.akrapovic.soundkit.community.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.akrapovic.soundkit.community.domain.SavedReceiver
import com.akrapovic.soundkit.community.ui.SoundKitUiState
import com.akrapovic.soundkit.community.ui.components.AkraActionButton
import com.akrapovic.soundkit.community.ui.components.AkraListDivider
import com.akrapovic.soundkit.community.ui.components.AkraListGroup
import com.akrapovic.soundkit.community.ui.components.AkraListRow
import com.akrapovic.soundkit.community.ui.components.AkraScreen
import com.akrapovic.soundkit.community.ui.components.AkraSectionTitle
import com.akrapovic.soundkit.community.ui.components.AkraSwitchRow

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    state: SoundKitUiState,
    onAutoReconnectChanged: (Boolean) -> Unit,
    onConnectOnLaunchChanged: (Boolean) -> Unit,
    onSetDefaultReceiver: (String) -> Unit,
    onRemoveReceiver: (String) -> Unit,
    onUpdateNickname: (String, String?) -> Unit,
    onForgetAll: () -> Unit,
    onDriveModeEnabledChanged: (Boolean) -> Unit,
    onPreferredModeChanged: (com.akrapovic.soundkit.community.domain.PreferredValveMode) -> Unit,
    onQuietStartChanged: (com.akrapovic.soundkit.community.domain.QuietStartSettings) -> Unit,
    onDriveModePausedChanged: (Boolean) -> Unit,
    onExportSettingsBackup: () -> String = { "{}" },
    onImportSettingsBackup: (String) -> Unit = {},
    onApplyDriveModeProfile: (com.akrapovic.soundkit.community.data.DriveModeProfile) -> Unit = {},
) {
    val context = LocalContext.current
    val powerManager = remember { context.getSystemService(PowerManager::class.java) }
    val ignoringOptimizations =
        powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    val removeTarget = remember { mutableStateOf<SavedReceiver?>(null) }
    val showForgetAllConfirm = remember { mutableStateOf(false) }
    val savedReceivers = state.settings.savedReceivers

    AkraScreen(modifier = modifier) {
        AkraSectionTitle("Connection")
        AkraListGroup {
            AkraSwitchRow(
                title = "Connect on launch",
                subtitle = "Try your default receiver when the app opens",
                checked = state.settings.connectOnLaunch,
                onCheckedChange = onConnectOnLaunchChanged,
            )
            AkraListDivider()
            AkraSwitchRow(
                title = "Auto reconnect",
                subtitle = "Retry if the Bluetooth link drops",
                checked = state.settings.autoReconnect,
                onCheckedChange = onAutoReconnectChanged,
            )
        }

        DriveModeSettingsSection(
            settings = state.settings,
            onDriveModeEnabledChanged = onDriveModeEnabledChanged,
            onPreferredModeChanged = onPreferredModeChanged,
            onQuietStartChanged = onQuietStartChanged,
            onDriveModePausedChanged = onDriveModePausedChanged,
            onApplyProfile = onApplyDriveModeProfile,
        )

        SettingsBackupSection(
            onExportBackup = onExportSettingsBackup,
            onImportBackup = onImportSettingsBackup,
        )

        AkraSectionTitle("Saved receivers")
        AkraListGroup {
            if (savedReceivers.isEmpty()) {
                AkraListRow(
                    title = "No receivers saved",
                    subtitle = "Connect from Home to save one",
                )
            } else {
                savedReceivers.forEachIndexed { index, receiver ->
                    if (index > 0) AkraListDivider()
                    SavedReceiverRow(
                        receiver = receiver,
                        onSetDefault = { onSetDefaultReceiver(receiver.address) },
                        onEditNickname = { nickname -> onUpdateNickname(receiver.address, nickname) },
                        onRemove = { removeTarget.value = receiver },
                    )
                }
            }
        }
        if (savedReceivers.isNotEmpty()) {
            AkraActionButton(
                label = "Forget all receivers",
                filled = false,
                onClick = { showForgetAllConfirm.value = true },
                contentDescription = "Forget all saved receivers",
            )
        }

        AkraSectionTitle("Background")
        AkraListGroup {
            AkraListRow(
                title = "Background connection",
                subtitle = if (ignoringOptimizations) {
                    "Battery optimization disabled — good for reliability"
                } else {
                    "Allow background use for a steadier link"
                },
                trailing = if (ignoringOptimizations) "OK" else "Set",
                showChevron = !ignoringOptimizations,
                onClick = if (!ignoringOptimizations) {
                    {
                        val intent = Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .apply { data = Uri.parse("package:${context.packageName}") }
                        context.startActivity(intent)
                    }
                } else {
                    null
                },
            )
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

    Column(modifier = Modifier.fillMaxWidth()) {
        AkraListRow(
            title = receiver.displayName(),
            subtitle = receiver.address,
            trailing = if (receiver.isDefault) "★ Default" else null,
        )
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { showNicknameDialog.value = true }) { Text("Nickname") }
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
                Text(if (receiver.isDefault) "Default" else "Set default")
            }
            TextButton(onClick = onRemove) { Text("Remove") }
        }
    }
}
