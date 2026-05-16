package com.akrapovic.soundkit.community.ui.control

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.SoundKitDevice
import com.akrapovic.soundkit.community.ui.SoundKitUiState

@Composable
fun ConnectedDeviceScreen(
    modifier: Modifier = Modifier,
    state: SoundKitUiState,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val connectedDevice = (state.connectionState as? ConnectionState.Connected)?.device
    val controlsEnabled = connectedDevice != null && !state.commandInFlight && state.protocolVerified

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Valve Control",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        StatusCard(state.connectionState, state.valveState.name)
        ProtocolCard(protocolVerified = state.protocolVerified, lastError = state.lastError)
        DeviceCard(device = connectedDevice, onDisconnect = onDisconnect)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Button(
                modifier = Modifier
                    .weight(1f)
                    .height(96.dp)
                    .semantics { contentDescription = "Open exhaust valve" },
                enabled = controlsEnabled,
                onClick = onOpen,
            ) {
                Text("OPEN", style = MaterialTheme.typography.headlineSmall)
            }
            Button(
                modifier = Modifier
                    .weight(1f)
                    .height(96.dp)
                    .semantics { contentDescription = "Close exhaust valve" },
                enabled = controlsEnabled,
                onClick = onClose,
            ) {
                Text("CLOSE", style = MaterialTheme.typography.headlineSmall)
            }
        }

        if (state.commandInFlight) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text("Sending BLE command...")
            }
        }
    }
}

@Composable
private fun StatusCard(connectionState: ConnectionState, valveState: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Connection", style = MaterialTheme.typography.titleLarge)
            Text(connectionState.asText())
            Text("Last requested valve state: $valveState")
        }
    }
}

@Composable
private fun ProtocolCard(protocolVerified: Boolean, lastError: String?) {
    val color = if (protocolVerified) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = color),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (protocolVerified) "Protocol verified" else "Protocol verification required",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (protocolVerified) {
                    "Valve writes are enabled for the verified service and characteristic."
                } else {
                    "OPEN and CLOSE are disabled until BLE_PROTOCOL.md contains verified UUIDs, command bytes, and write type."
                },
            )
            if (lastError != null) {
                Text("Last error: $lastError")
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: SoundKitDevice?,
    onDisconnect: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Receiver", style = MaterialTheme.typography.titleLarge)
            Text(device?.name ?: "Not connected")
            Text(device?.address ?: "Scan and connect to a receiver first.")
            OutlinedButton(
                enabled = device != null,
                onClick = onDisconnect,
            ) {
                Text("Disconnect")
            }
        }
    }
}

private fun ConnectionState.asText(): String {
    return when (this) {
        ConnectionState.Disconnected -> "Disconnected"
        ConnectionState.Scanning -> "Scanning"
        is ConnectionState.Connecting -> "Connecting to ${device.name}"
        is ConnectionState.Connected -> "Connected to ${device.name}"
        is ConnectionState.Reconnecting -> "Reconnecting to ${device.name}, attempt $attempt"
        is ConnectionState.Error -> message
    }
}

