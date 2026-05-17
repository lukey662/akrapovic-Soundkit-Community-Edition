package com.akrapovic.soundkit.community.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.SavedReceiver
import com.akrapovic.soundkit.community.domain.SoundKitDevice
import com.akrapovic.soundkit.community.ui.SoundKitUiState
import com.akrapovic.soundkit.community.ui.components.AkraActionButton
import com.akrapovic.soundkit.community.ui.components.AkraCard
import com.akrapovic.soundkit.community.ui.components.AkraHeroHeader
import com.akrapovic.soundkit.community.ui.components.AkraScreen
import com.akrapovic.soundkit.community.ui.components.AkraStatePanel
import com.akrapovic.soundkit.community.ui.components.AkraStatusPill

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
) {
    val savedByAddress = state.settings.savedReceivers.associateBy { it.address }

    AkraScreen(modifier = modifier) {
        AkraHeroHeader(
            eyebrow = "Sound Kit",
            title = "Find your Sound Kit",
            subtitle = "Connect over Bluetooth. No account, no cloud, no tracking.",
            titleModifier = Modifier.semantics { heading() },
        )

        if (!permissionsGranted) {
            PermissionRationaleCard(
                permissions = permissions,
                onRequestPermissions = onRequestPermissions,
            )
            return@AkraScreen
        }

        val connectionError = state.connectionState as? ConnectionState.Error
        if (connectionError != null) {
            AkraStatePanel(
                eyebrow = "Connection",
                title = "Could not connect",
                body = connectionError.message,
                primaryLabel = "Try again",
                primaryContentDescription = "Retry connection to remembered receiver",
                onPrimary = onRetryConnection,
            )
        }

        if (state.settings.savedReceivers.isNotEmpty()) {
            AkraCard(accent = MaterialTheme.colorScheme.primary) {
                AkraStatusPill(text = "Saved")
                state.settings.savedReceivers.forEach { receiver ->
                    SavedReceiverChip(
                        receiver = receiver,
                        onSetDefault = { onSetDefaultReceiver(receiver.address) },
                    )
                }
            }
        }

        AkraActionButton(
            label = if (state.isScanning) "Stop scan" else "Scan for receiver",
            contentDescription = if (state.isScanning) "Stop scanning" else "Scan for Sound Kit receiver",
            filled = !state.isScanning,
            onClick = if (state.isScanning) onStopScan else onStartScan,
        )

        if (state.devices.isEmpty()) {
            AkraStatePanel(
                eyebrow = if (state.isScanning) "Searching" else "Ready",
                title = if (state.isScanning) "Scanning nearby BLE devices..." else "No receiver selected",
                body = if (state.isScanning) {
                    "Keep the phone near the car while we look."
                } else {
                    "Turn the car on, then scan while parked."
                },
                primaryLabel = if (state.isScanning) null else "Scan for receiver",
                primaryContentDescription = "Scan for Sound Kit receiver",
                onPrimary = if (state.isScanning) null else onStartScan,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Nearby",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                state.devices.forEach { device ->
                    DeviceCard(
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
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
        if (receiver.isDefault) {
            Text(
                text = "★ Default",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            TextButton(onClick = onSetDefault) {
                Text("Set default")
            }
        }
    }
}

@Composable
private fun PermissionRationaleCard(
    permissions: List<String>,
    onRequestPermissions: () -> Unit,
) {
    AkraCard(accent = MaterialTheme.colorScheme.primary) {
        AkraStatusPill(text = "Bluetooth")
        Text(
            text = "Bluetooth permission required",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = "Allow Bluetooth so the app can find your receiver nearby. Everything stays on this phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (permissions.isNotEmpty()) {
            Text(
                text = permissions.joinToString(separator = " · ") { it.substringAfterLast('.') },
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
private fun DeviceCard(
    device: SoundKitDevice,
    saved: SavedReceiver?,
    onConnect: () -> Unit,
    onSetDefault: () -> Unit,
) {
    AkraCard(
        accent = if (device.isLikelySoundKit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        onClick = onConnect,
        contentDescription = "Connect to ${device.name} ${device.address}",
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = device.name,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            when {
                saved?.isDefault == true -> Text(
                    text = "★",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { contentDescription = "Default saved receiver" },
                )
                saved != null -> TextButton(onClick = onSetDefault) {
                    Text("★ Saved")
                }
            }
        }
        Text(
            text = listOfNotNull(
                if (device.isLikelySoundKit) "Sound Kit receiver" else "Bluetooth device",
                device.rssi?.let { signalText(it) },
                saved?.nickname?.let { "“$it”" },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = device.address,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AkraActionButton(
            label = "Connect",
            filled = false,
            contentDescription = "Connect to ${device.name}",
            onClick = onConnect,
        )
    }
}

private fun signalText(rssi: Int): String {
    return when {
        rssi >= -55 -> "Strong signal"
        rssi >= -75 -> "Good signal"
        else -> "Weak signal"
    }
}
