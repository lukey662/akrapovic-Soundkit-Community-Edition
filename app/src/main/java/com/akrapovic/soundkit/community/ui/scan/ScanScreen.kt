package com.akrapovic.soundkit.community.ui.scan

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akrapovic.soundkit.community.data.BleRepositoryImpl
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.SavedReceiver
import com.akrapovic.soundkit.community.domain.SoundKitDevice
import com.akrapovic.soundkit.community.ui.SoundKitUiState
import com.akrapovic.soundkit.community.ui.components.AkraActionButton
import com.akrapovic.soundkit.community.ui.components.AkraBanner
import com.akrapovic.soundkit.community.ui.components.AkraElevated
import com.akrapovic.soundkit.community.ui.components.AkraHeroHeader
import com.akrapovic.soundkit.community.ui.components.AkraListDivider
import com.akrapovic.soundkit.community.ui.components.AkraListGroup
import com.akrapovic.soundkit.community.ui.components.AkraListRow
import com.akrapovic.soundkit.community.ui.components.AkraScreen
import com.akrapovic.soundkit.community.ui.components.AkraSectionTitle
import com.akrapovic.soundkit.community.ui.components.AkraSurface
import com.akrapovic.soundkit.community.ui.components.DriveModeShortcutRow
import com.akrapovic.soundkit.community.ui.theme.AkraColors

@Composable
fun ScanScreen(
    modifier: Modifier = Modifier,
    state: SoundKitUiState,
    permissions: List<String>,
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (SoundKitDevice) -> Unit,
    onSetDefaultReceiver: (String) -> Unit = {},
    onRetryConnection: () -> Unit = {},
    onOpenDriveMode: () -> Unit = {},
) {
    val savedByAddress = state.settings.savedReceivers.associateBy { it.address }

    AkraScreen(modifier = modifier) {
        AkraHeroHeader(
            title = "Connect",
            subtitle = "Bluetooth only — nothing leaves your phone.",
            titleModifier = Modifier.semantics { heading() },
            compact = true,
        )

        if (!permissionsGranted) {
            PermissionRationale(onRequestPermissions = onRequestPermissions, permissions = permissions)
            return@AkraScreen
        }

        val connectionError = state.connectionState as? ConnectionState.Error
        if (connectionError != null) {
            AkraBanner(
                title = if (connectionError.message == BleRepositoryImpl.RECONNECT_GAVE_UP_MESSAGE) {
                    "Receiver unreachable"
                } else {
                    "Could not connect"
                },
                body = connectionError.message,
                accent = AkraColors.Danger,
                actionLabel = "Try again",
                onAction = onRetryConnection,
            )
        }

        AkraActionButton(
            label = if (state.isScanning) "Stop scan" else "Scan nearby",
            contentDescription = if (state.isScanning) "Stop scanning" else "Scan for Sound Kit receiver",
            filled = true,
            onClick = if (state.isScanning) onStopScan else onStartScan,
        )

        if (state.settings.savedReceivers.isNotEmpty()) {
            AkraSectionTitle("Saved")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.settings.savedReceivers.forEach { receiver ->
                    SavedReceiverChip(
                        receiver = receiver,
                        onSetDefault = { onSetDefaultReceiver(receiver.address) },
                    )
                }
            }
        }

        AkraListGroup {
            DriveModeShortcutRow(settings = state.settings, onClick = onOpenDriveMode)
        }

        if (state.devices.isEmpty()) {
            AkraElevated {
                Text(
                    text = if (state.isScanning) "Scanning nearby…" else "No receivers yet",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (state.isScanning) {
                        "Keep the phone near the car."
                    } else {
                        "Turn the car on, then tap Scan nearby."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            AkraSectionTitle("Nearby")
            AkraListGroup {
                state.devices.forEachIndexed { index, device ->
                    if (index > 0) AkraListDivider()
                    DeviceRow(
                        device = device,
                        saved = savedByAddress[device.address],
                        onConnect = { onConnect(device) },
                        onSetDefault = { onSetDefaultReceiver(device.address) },
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SavedReceiverChip(
    receiver: SavedReceiver,
    onSetDefault: () -> Unit,
) {
    AkraSurface(
        onClick = if (!receiver.isDefault) onSetDefault else null,
        contentDescription = if (receiver.isDefault) {
            "${receiver.displayName()} default receiver"
        } else {
            "Set ${receiver.displayName()} as default"
        },
    ) {
        Text(
            text = if (receiver.isDefault) "★ ${receiver.displayName()}" else receiver.displayName(),
            style = MaterialTheme.typography.labelLarge,
            color = if (receiver.isDefault) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun PermissionRationale(
    permissions: List<String>,
    onRequestPermissions: () -> Unit,
) {
    AkraElevated {
        Text(
            text = "Bluetooth access needed",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = "We only use Bluetooth to find your receiver. Nothing is uploaded.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (permissions.isNotEmpty()) {
            Text(
                text = permissions.joinToString(" · ") { it.substringAfterLast('.') },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AkraActionButton(
            label = "Grant permissions",
            contentDescription = "Grant Bluetooth permissions",
            onClick = onRequestPermissions,
        )
    }
}

@Composable
private fun DeviceRow(
    device: SoundKitDevice,
    saved: SavedReceiver?,
    onConnect: () -> Unit,
    onSetDefault: () -> Unit,
) {
    AkraListRow(
        title = device.name,
        subtitle = listOfNotNull(
            if (device.isLikelySoundKit) "Sound Kit" else "BLE device",
            device.rssi?.let { signalText(it) },
            device.address,
        ).joinToString(" · "),
        trailing = if (saved?.isDefault == true) "★" else null,
        showChevron = true,
        onClick = onConnect,
        contentDescription = "Connect to ${device.name}",
    )
    if (saved != null && !saved.isDefault) {
        TextButton(
            onClick = onSetDefault,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
        ) {
            Text("Set as default")
        }
    }
}

private fun signalText(rssi: Int): String = when {
    rssi >= -55 -> "Strong signal"
    rssi >= -75 -> "Good signal"
    rssi >= -85 -> "Weak — move closer (~20 m)"
    else -> "Very weak — move closer"
}
