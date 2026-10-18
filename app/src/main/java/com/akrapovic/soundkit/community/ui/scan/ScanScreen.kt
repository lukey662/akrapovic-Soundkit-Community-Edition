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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.akrapovic.soundkit.community.R
import com.akrapovic.soundkit.community.data.BleRepositoryImpl
import com.akrapovic.soundkit.community.domain.AwayCopy
import com.akrapovic.soundkit.community.domain.AwayReason
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.ConnectionYieldState
import com.akrapovic.soundkit.community.domain.SavedReceiver
import com.akrapovic.soundkit.community.domain.SoundKitDevice
import com.akrapovic.soundkit.community.domain.ValveState
import com.akrapovic.soundkit.community.service.ConnectionErrorCopy
import com.akrapovic.soundkit.community.ui.SoundKitUiState
import com.akrapovic.soundkit.community.ui.components.AkraActionButton
import com.akrapovic.soundkit.community.ui.components.AkraListDivider
import com.akrapovic.soundkit.community.ui.components.AkraListGroup
import com.akrapovic.soundkit.community.ui.components.AkraListRow
import com.akrapovic.soundkit.community.ui.components.AkraScreen
import com.akrapovic.soundkit.community.ui.components.AkraSectionTitle
import com.akrapovic.soundkit.community.ui.components.AkraSurface
import com.akrapovic.soundkit.community.ui.components.DriveModeRow
import com.akrapovic.soundkit.community.ui.components.HomeHeader
import com.akrapovic.soundkit.community.ui.components.QuietStartRow
import com.akrapovic.soundkit.community.ui.components.ValveVisual
import com.akrapovic.soundkit.community.ui.control.shortText
import com.akrapovic.soundkit.community.ui.theme.AkraColors

/**
 * Home while no receiver is connected. Same shell as the connected screen — header, valve, state,
 * one primary action, the three rows — so the app does not change shape when the link drops.
 */
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
    onTakeControl: () -> Unit = {},
    onOpenDriveMode: () -> Unit = {},
    onPeriodicScanAnswer: (Boolean) -> Unit = {},
) {
    val savedByAddress = state.settings.savedReceivers.associateBy { it.address }
    val defaultReceiver = state.settings.savedReceivers.firstOrNull { it.isDefault }
    val showTakeControlConfirm = remember { mutableStateOf(false) }
    var scanCompletedWithoutReceiver by remember { mutableStateOf(false) }
    var wasScanning by remember { mutableStateOf(state.isScanning) }

    LaunchedEffect(state.isScanning, state.devices.isEmpty()) {
        if (wasScanning && !state.isScanning && state.devices.isEmpty()) {
            scanCompletedWithoutReceiver = true
        }
        if (state.devices.isNotEmpty()) scanCompletedWithoutReceiver = false
        wasScanning = state.isScanning
    }

    if (showTakeControlConfirm.value) {
        AlertDialog(
            onDismissRequest = { showTakeControlConfirm.value = false },
            title = { Text("Take control?") },
            text = {
                Text("This may disconnect the other phone that is using the Sound Kit receiver.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showTakeControlConfirm.value = false
                    onTakeControl()
                }) { Text("Take control") }
            },
            dismissButton = {
                TextButton(onClick = { showTakeControlConfirm.value = false }) { Text("Cancel") }
            },
        )
    }

    val away = state.connectionState as? ConnectionState.Away
    val connectionError = state.connectionState as? ConnectionState.Error
    val yielded = state.connectionYieldState is ConnectionYieldState.Yielded

    AkraScreen(modifier = modifier, padTopForStatusBar = true) {
        HomeHeader(
            receiverName = defaultReceiver?.displayName() ?: "No receiver",
            statusLabel = if (defaultReceiver == null) "Scan to add" else state.connectionState.shortText(),
            dot = when {
                connectionError != null -> AkraColors.Danger
                away != null || state.isScanning -> AkraColors.Amber
                else -> AkraColors.Mist
            },
        )

        ValveVisual(
            state = ValveState.Closed,
            dimmed = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = when {
                    yielded -> "Receiver in use"
                    state.isScanning -> "Scanning…"
                    away?.reason == AwayReason.LeftCar -> "Left the car"
                    away?.reason == AwayReason.OutOfRange -> "Out of range"
                    connectionError != null -> "Couldn't connect"
                    else -> "Not connected"
                },
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = when {
                    yielded -> BleRepositoryImpl.YIELD_MESSAGE
                    !permissionsGranted -> "Bluetooth access is needed to find the receiver."
                    state.isScanning -> "Keep the phone near the car."
                    away != null -> AwayCopy.statusText(away.reason, away.sinceMillis)
                    connectionError != null -> ConnectionErrorCopy.friendly(connectionError.message)
                    scanCompletedWithoutReceiver -> "No receiver found. Turn the car on, move closer, then try again."
                    defaultReceiver == null -> "Turn the car on, then scan for the receiver."
                    else -> "Turn the car on, then connect."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (away != null && !state.settings.periodicScanPromptAnswered) {
                Text(
                    text = "Scan for the receiver occasionally while you're away?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onPeriodicScanAnswer(true) }) { Text("Scan occasionally") }
                    TextButton(onClick = { onPeriodicScanAnswer(false) }) { Text("Not now") }
                }
            }
        }

        val scanLabel = when {
            state.isScanning -> "Stop scan"
            scanCompletedWithoutReceiver -> "Try scan again"
            else -> "Scan nearby"
        }
        val scanDescription = when {
            state.isScanning -> "Stop scanning for Sound Kit receivers"
            scanCompletedWithoutReceiver -> "Retry scan: no Sound Kit receiver found"
            else -> "Scan for Sound Kit receiver"
        }
        when {
            !permissionsGranted -> AkraActionButton(
                label = "Allow Bluetooth",
                contentDescription = "Grant Bluetooth permissions",
                onClick = onRequestPermissions,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            yielded -> AkraActionButton(
                label = "Take control",
                onClick = { showTakeControlConfirm.value = true },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            defaultReceiver != null && !state.isScanning -> {
                AkraActionButton(
                    label = "Connect",
                    contentDescription = "Connect to ${defaultReceiver.displayName()}",
                    onClick = onRetryConnection,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
                AkraActionButton(
                    label = scanLabel,
                    contentDescription = scanDescription,
                    filled = false,
                    onClick = onStartScan,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            else -> AkraActionButton(
                label = scanLabel,
                contentDescription = scanDescription,
                onClick = if (state.isScanning) onStopScan else onStartScan,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
        if (!permissionsGranted && permissions.isNotEmpty()) {
            Text(
                text = permissions.joinToString(" · ") { it.substringAfterLast('.') },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }

        AkraListGroup {
            DriveModeRow(settings = state.settings, onClick = onOpenDriveMode)
            AkraListDivider()
            QuietStartRow(settings = state.settings, onClick = onOpenDriveMode)
            AkraListDivider()
            AkraListRow(
                title = "Receiver",
                trailing = defaultReceiver?.displayName() ?: "None",
                leadingIcon = ImageVector.vectorResource(R.drawable.ic_row_receiver),
                contentDescription = defaultReceiver?.let { "Default receiver ${it.displayName()}" } ?: "No default receiver",
            )
        }

        if (state.settings.savedReceivers.size > 1) {
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

        if (state.devices.isNotEmpty()) {
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
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
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
            Text("Set as default", color = MaterialTheme.colorScheme.secondary)
        }
    }
}

private fun signalText(rssi: Int): String = when {
    rssi >= -55 -> "Strong signal"
    rssi >= -75 -> "Good signal"
    rssi >= -85 -> "Weak. Move closer (~20 m)"
    else -> "Very weak. Move closer"
}
