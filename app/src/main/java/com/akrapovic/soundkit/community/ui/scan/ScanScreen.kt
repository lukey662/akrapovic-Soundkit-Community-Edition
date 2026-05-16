package com.akrapovic.soundkit.community.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akrapovic.soundkit.community.domain.SoundKitDevice
import com.akrapovic.soundkit.community.ui.SoundKitUiState
import com.akrapovic.soundkit.community.ui.components.AkraActionButton
import com.akrapovic.soundkit.community.ui.components.AkraCard
import com.akrapovic.soundkit.community.ui.components.AkraHeroHeader
import com.akrapovic.soundkit.community.ui.components.AkraScreen
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
) {
    AkraScreen(modifier = modifier) {
        AkraHeroHeader(
            eyebrow = "Sound Kit",
            title = "Find your Sound Kit",
            subtitle = "Connect over Bluetooth. No account, no cloud, no tracking.",
        )

        if (!permissionsGranted) {
            PermissionRationaleCard(
                permissions = permissions,
                onRequestPermissions = onRequestPermissions,
            )
            return@AkraScreen
        }

        AkraActionButton(
            label = if (state.isScanning) "Stop scan" else "Scan for receiver",
            contentDescription = if (state.isScanning) "Stop scanning" else "Scan for Sound Kit receiver",
            filled = !state.isScanning,
            onClick = if (state.isScanning) onStopScan else onStartScan,
        )

        if (state.devices.isEmpty()) {
            EmptyScanCard(isScanning = state.isScanning)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Nearby",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                state.devices.forEach { device ->
                    DeviceCard(device = device, onConnect = { onConnect(device) })
                }
            }
        }

        Spacer(Modifier.height(8.dp))
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
            contentDescription = "Grant Bluetooth and notification permissions",
            onClick = onRequestPermissions,
        )
    }
}

@Composable
private fun EmptyScanCard(isScanning: Boolean) {
    AkraCard(accent = MaterialTheme.colorScheme.onSurfaceVariant) {
        AkraStatusPill(
            text = if (isScanning) "Searching" else "Ready",
            color = if (isScanning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (isScanning) "Scanning nearby BLE devices..." else "No receiver selected",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = if (isScanning) {
                "Keep the phone near the car while we look."
            } else {
                "Turn the car on, then scan while parked."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DeviceCard(
    device: SoundKitDevice,
    onConnect: () -> Unit,
) {
    AkraCard(
        accent = if (device.isLikelySoundKit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        onClick = onConnect,
        contentDescription = "Connect to ${device.name} ${device.address}",
    ) {
        Text(
            text = device.name,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = listOfNotNull(
                if (device.isLikelySoundKit) "Sound Kit receiver" else "Bluetooth device",
                device.rssi?.let { signalText(it) },
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
