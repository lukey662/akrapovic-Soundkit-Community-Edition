package com.akrapovic.soundkit.community.ui.control

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akrapovic.soundkit.community.ble.SoundKitProtocol
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.SoundKitDevice
import com.akrapovic.soundkit.community.domain.ValveState
import com.akrapovic.soundkit.community.ui.SoundKitUiState
import com.akrapovic.soundkit.community.ui.components.AkraActionButton
import com.akrapovic.soundkit.community.ui.components.AkraBanner
import com.akrapovic.soundkit.community.ui.components.AkraInlineStatus
import com.akrapovic.soundkit.community.ui.components.AkraListDivider
import com.akrapovic.soundkit.community.ui.components.AkraListGroup
import com.akrapovic.soundkit.community.ui.components.AkraListRow
import com.akrapovic.soundkit.community.ui.components.AkraScreen
import com.akrapovic.soundkit.community.ui.components.DriveModeShortcutRow
import com.akrapovic.soundkit.community.ui.components.ValveVisual
import com.akrapovic.soundkit.community.ui.theme.AkraColors

@Composable
fun ConnectedDeviceScreen(
    modifier: Modifier = Modifier,
    state: SoundKitUiState,
    onToggleValve: () -> Unit,
    onDisconnect: () -> Unit,
    onRetryConnection: () -> Unit = {},
    onOpenDriveMode: () -> Unit = {},
) {
    val view = LocalView.current
    val showReceiverLearnMore = remember { mutableStateOf(false) }
    val showDisconnectConfirm = remember { mutableStateOf(false) }
    val wasCommandInFlight = remember { mutableStateOf(false) }
    val successRippleTrigger = remember { mutableIntStateOf(0) }

    LaunchedEffect(state.commandInFlight, state.lastError) {
        if (wasCommandInFlight.value && !state.commandInFlight && state.lastError == null) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            successRippleTrigger.intValue += 1
        }
        wasCommandInFlight.value = state.commandInFlight
    }

    val connectedDevice = state.activeDevice()
    val action = state.primaryValveAction()
    val controlsEnabled = connectedDevice != null &&
        state.connectionState is ConnectionState.Connected &&
        !state.commandInFlight &&
        state.protocolVerified &&
        state.valveState != ValveState.Unknown &&
        state.receiverStatusMessage == null

    AkraScreen(modifier = modifier) {
        InlineStatusLine(state = state, device = connectedDevice)

        if (state.connectionState is ConnectionState.Reconnecting) {
            val reconnecting = state.connectionState as ConnectionState.Reconnecting
            AkraBanner(
                title = "Reconnecting · attempt ${reconnecting.attempt}",
                body = "Checking the link to ${reconnecting.device.name}.",
                accent = AkraColors.Amber,
                actionLabel = "Try again",
                onAction = onRetryConnection,
            )
        }

        val bannerMessage = state.receiverStatusMessage ?: state.lastError
        if (bannerMessage != null) {
            AkraBanner(
                title = if (state.receiverStatusMessage != null) "Receiver not ready" else "Connection issue",
                body = bannerMessage,
                accent = if (state.receiverStatusMessage != null) AkraColors.Amber else AkraColors.Danger,
                actionLabel = if (state.receiverStatusMessage != null) "Learn more" else null,
                onAction = if (state.receiverStatusMessage != null) {
                    { showReceiverLearnMore.value = true }
                } else {
                    null
                },
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .semantics { contentDescription = action.contentDescription },
                contentAlignment = Alignment.Center,
            ) {
                ValveVisual(
                    state = state.valveState,
                    modifier = Modifier.size(220.dp),
                    commandInFlight = state.commandInFlight,
                    successRippleTrigger = successRippleTrigger.intValue,
                )
            }
            Text(
                text = state.valveState.displayTitle(),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = state.valveState.helperText(state.receiverStatusMessage),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AkraActionButton(
            label = if (state.commandInFlight) "Changing..." else action.button,
            enabled = controlsEnabled && !state.commandInFlight,
            contentDescription = action.contentDescription,
            onClick = onToggleValve,
        )

        if (state.commandInFlight) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                )
            }
        }

        AkraListGroup {
            DriveModeShortcutRow(
                settings = state.settings,
                onClick = onOpenDriveMode,
            )
            AkraListDivider()
            AkraListRow(
                title = connectedDevice?.name ?: "Receiver",
                subtitle = connectedDevice?.address,
                trailing = "Disconnect",
                onClick = { if (connectedDevice != null) showDisconnectConfirm.value = true },
                contentDescription = "Disconnect from receiver",
            )
        }

        if (showReceiverLearnMore.value) {
            AlertDialog(
                onDismissRequest = { showReceiverLearnMore.value = false },
                title = { Text("Receiver not ready") },
                text = { Text(SoundKitProtocol.RECEIVER_NOT_READY_MESSAGE) },
                confirmButton = {
                    TextButton(onClick = { showReceiverLearnMore.value = false }) {
                        Text("OK")
                    }
                },
            )
        }

        if (showDisconnectConfirm.value) {
            AlertDialog(
                onDismissRequest = { showDisconnectConfirm.value = false },
                title = { Text("Disconnect from receiver?") },
                text = {
                    Text("The valves will keep their current position. You can reconnect from Home.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        showDisconnectConfirm.value = false
                        onDisconnect()
                    }) { Text("Disconnect") }
                },
                dismissButton = {
                    TextButton(onClick = { showDisconnectConfirm.value = false }) { Text("Cancel") }
                },
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun InlineStatusLine(
    state: SoundKitUiState,
    device: SoundKitDevice?,
) {
    RowStatus(
        label = buildString {
            append(device?.name ?: "Sound Kit")
            append(" · ")
            append(state.connectionState.shortText())
        },
        color = when (state.connectionState) {
            is ConnectionState.Connected -> AkraColors.Signal
            is ConnectionState.Reconnecting -> AkraColors.Amber
            is ConnectionState.Error -> AkraColors.Danger
            else -> AkraColors.Mist
        },
    )
}

@Composable
private fun RowStatus(label: String, color: androidx.compose.ui.graphics.Color) {
    AkraInlineStatus(
        label = label,
        color = color,
        modifier = Modifier.padding(top = 8.dp),
    )
}

private enum class ValveAction(
    val button: String,
    val contentDescription: String,
) {
    Open("Open valves", "Open exhaust valves"),
    Close("Close valves", "Close exhaust valves"),
    Wait("Waiting for status", "Valve controls waiting for receiver status"),
    NotReady("Receiver not ready", "Receiver not ready for valve control"),
}

private fun SoundKitUiState.primaryValveAction(): ValveAction {
    if (receiverStatusMessage != null) return ValveAction.NotReady
    return when (valveState) {
        ValveState.Closed -> ValveAction.Open
        ValveState.Open -> ValveAction.Close
        ValveState.Unknown -> ValveAction.Wait
    }
}

private fun SoundKitUiState.activeDevice(): SoundKitDevice? {
    return when (val connection = connectionState) {
        is ConnectionState.Connected -> connection.device
        is ConnectionState.Connecting -> connection.device
        is ConnectionState.Reconnecting -> connection.device
        else -> null
    }
}

private fun ValveState.displayTitle(): String = when (this) {
    ValveState.Open -> "Open"
    ValveState.Closed -> "Closed"
    ValveState.Unknown -> "Checking…"
}

private fun ValveState.helperText(receiverNotReady: String?): String {
    if (receiverNotReady != null) return receiverNotReady
    return when (this) {
        ValveState.Open -> "Sport mode — valves are open."
        ValveState.Closed -> "Quiet mode — valves are closed."
        ValveState.Unknown -> "Waiting for the receiver."
    }
}

private fun ConnectionState.shortText(): String = when (this) {
    ConnectionState.Disconnected -> "Disconnected"
    ConnectionState.Scanning -> "Scanning"
    is ConnectionState.Connecting -> "Connecting"
    is ConnectionState.Connected -> "Connected"
    is ConnectionState.Reconnecting -> "Reconnecting"
    is ConnectionState.Error -> "Needs attention"
}
