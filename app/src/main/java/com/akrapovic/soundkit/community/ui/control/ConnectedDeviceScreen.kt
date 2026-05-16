package com.akrapovic.soundkit.community.ui.control

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.SoundKitDevice
import com.akrapovic.soundkit.community.domain.ValveState
import com.akrapovic.soundkit.community.ui.SoundKitUiState
import com.akrapovic.soundkit.community.ui.components.AkraActionButton
import com.akrapovic.soundkit.community.ui.components.AkraButtonRow
import com.akrapovic.soundkit.community.ui.components.AkraCard
import com.akrapovic.soundkit.community.ui.components.AkraScreen
import com.akrapovic.soundkit.community.ui.components.AkraStatusPill
import com.akrapovic.soundkit.community.ui.components.ValveVisual
import com.akrapovic.soundkit.community.ui.theme.AkraColors
import com.akrapovic.soundkit.community.ui.theme.LocalAkraTheme

@Composable
fun ConnectedDeviceScreen(
    modifier: Modifier = Modifier,
    state: SoundKitUiState,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val connectedDevice = (state.connectionState as? ConnectionState.Connected)?.device
    val action = state.primaryValveAction()
    val controlsEnabled = connectedDevice != null &&
        !state.commandInFlight &&
        state.protocolVerified &&
        state.valveState != ValveState.Unknown

    AkraScreen(modifier = modifier) {
        ControlHeroHeader(state = state)

        ValveStateCard(state = state)
        PrimaryActionCard(
            action = action,
            enabled = controlsEnabled,
            inFlight = state.commandInFlight,
            onOpen = onOpen,
            onClose = onClose,
        )
        ReceiverCard(device = connectedDevice, onDisconnect = onDisconnect)

        if (state.lastError != null) {
            AkraCard(accent = AkraColors.Danger) {
                Text(
                    text = state.lastError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ControlHeroHeader(state: SoundKitUiState) {
    val theme = LocalAkraTheme.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Drive mode",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Sound Kit",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = state.controlSubtitle(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (theme.brandMark != 0) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
                    .padding(9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(theme.brandMark),
                    contentDescription = "${theme.name} brand mark",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ValveStateCard(state: SoundKitUiState) {
    AkraCard(accent = state.valveState.accent()) {
        AkraStatusPill(
            text = state.connectionState.shortText(),
            color = if (state.connectionState is ConnectionState.Connected) AkraColors.Signal else AkraColors.Mist,
        )
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            ValveVisual(state = state.valveState)
        }
        Text(
            text = state.valveState.displayTitle(),
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = state.valveState.helperText(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PrimaryActionCard(
    action: ValveAction,
    enabled: Boolean,
    inFlight: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
) {
    AkraCard(accent = MaterialTheme.colorScheme.primary) {
        Text(
            text = action.title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = action.body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AkraActionButton(
            label = if (inFlight) "Changing..." else action.button,
            enabled = enabled && !inFlight,
            contentDescription = action.contentDescription,
            onClick = {
                when (action) {
                    ValveAction.Open -> onOpen()
                    ValveAction.Close -> onClose()
                    ValveAction.Wait -> Unit
                }
            },
        )
        if (inFlight) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                )
                Text(
                    text = "Waiting for the receiver to confirm the new state.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ReceiverCard(
    device: SoundKitDevice?,
    onDisconnect: () -> Unit,
) {
    val showConfirm = remember { mutableStateOf(false) }

    AkraCard(accent = AkraColors.Mist) {
        Text(
            text = device?.name ?: "No receiver connected",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = device?.address ?: "Go to Find your Sound Kit to connect.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AkraButtonRow {
            AkraActionButton(
                label = "Disconnect",
                modifier = Modifier.weight(1f),
                enabled = device != null,
                filled = false,
                onClick = { showConfirm.value = true },
            )
        }
    }

    if (showConfirm.value) {
        AlertDialog(
            onDismissRequest = { showConfirm.value = false },
            title = { Text("Disconnect from receiver?") },
            text = {
                Text("The valves will keep their current position. You can reconnect any time from Find.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm.value = false
                    onDisconnect()
                }) { Text("Disconnect") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm.value = false }) { Text("Cancel") }
            },
        )
    }
}

private enum class ValveAction(
    val title: String,
    val body: String,
    val button: String,
    val contentDescription: String,
) {
    Open(
        title = "Ready for a sportier sound",
        body = "The receiver reports the valves are closed. Opening sends the verified toggle command once.",
        button = "Open valves",
        contentDescription = "Open exhaust valves",
    ),
    Close(
        title = "Ready to quiet things down",
        body = "The receiver reports the valves are open. Closing sends the verified toggle command once.",
        button = "Close valves",
        contentDescription = "Close exhaust valves",
    ),
    Wait(
        title = "Waiting for receiver status",
        body = "Controls unlock after the receiver reports whether the valves are open or closed.",
        button = "Waiting for status",
        contentDescription = "Valve controls waiting for receiver status",
    ),
}

private fun SoundKitUiState.primaryValveAction(): ValveAction {
    return when (valveState) {
        ValveState.Closed -> ValveAction.Open
        ValveState.Open -> ValveAction.Close
        ValveState.Unknown -> ValveAction.Wait
    }
}

private fun SoundKitUiState.controlSubtitle(): String {
    return when (connectionState) {
        is ConnectionState.Connected -> "Connected. Commands are only sent after the receiver confirms its current state."
        is ConnectionState.Connecting -> "Pairing and connecting with your receiver."
        is ConnectionState.Reconnecting -> "Trying to reconnect to your receiver."
        is ConnectionState.Error -> connectionState.message
        ConnectionState.Disconnected -> "Connect to your receiver to control the valves."
        ConnectionState.Scanning -> "Looking for your receiver."
    }
}

private fun ValveState.displayTitle(): String {
    return when (this) {
        ValveState.Open -> "Valves open"
        ValveState.Closed -> "Valves closed"
        ValveState.Unknown -> "Checking valves"
    }
}

private fun ValveState.helperText(): String {
    return when (this) {
        ValveState.Open -> "Sport mode is active."
        ValveState.Closed -> "Quiet mode is active."
        ValveState.Unknown -> "Waiting for a status update from the receiver."
    }
}

@Composable
private fun ValveState.accent() = when (this) {
    ValveState.Open -> MaterialTheme.colorScheme.primary
    ValveState.Closed -> AkraColors.Signal
    ValveState.Unknown -> AkraColors.Mist
}

private fun ConnectionState.shortText(): String {
    return when (this) {
        ConnectionState.Disconnected -> "Disconnected"
        ConnectionState.Scanning -> "Scanning"
        is ConnectionState.Connecting -> "Connecting"
        is ConnectionState.Connected -> "Connected"
        is ConnectionState.Reconnecting -> "Reconnecting"
        is ConnectionState.Error -> "Needs attention"
    }
}

