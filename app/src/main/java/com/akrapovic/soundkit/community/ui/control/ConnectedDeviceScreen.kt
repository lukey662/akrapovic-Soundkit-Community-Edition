package com.akrapovic.soundkit.community.ui.control

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.SoundKitDevice
import com.akrapovic.soundkit.community.ui.SoundKitUiState
import com.akrapovic.soundkit.community.ui.theme.AkraColors

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
            .background(
                Brush.verticalGradient(
                    0f to AkraColors.Ink,
                    0.7f to AkraColors.Ink,
                    1f to AkraColors.Carbon,
                ),
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Eyebrow("// CONTROL")
        Text(
            text = "Valve Control".uppercase(),
            style = MaterialTheme.typography.displaySmall,
            color = AkraColors.Pearl,
        )

        ControlStatusStrip(
            connectionState = state.connectionState,
            valveState = state.valveState.name,
        )
        ProtocolPanel(protocolVerified = state.protocolVerified, lastError = state.lastError)
        ReceiverPanel(device = connectedDevice, onDisconnect = onDisconnect)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ValveButton(
                modifier = Modifier
                    .weight(1f)
                    .height(88.dp)
                    .semantics { contentDescription = "Open exhaust valve" },
                label = "OPEN",
                enabled = controlsEnabled,
                onClick = onOpen,
                primary = true,
            )
            ValveButton(
                modifier = Modifier
                    .weight(1f)
                    .height(88.dp)
                    .semantics { contentDescription = "Close exhaust valve" },
                label = "CLOSE",
                enabled = controlsEnabled,
                onClick = onClose,
                primary = false,
            )
        }

        if (state.commandInFlight) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(color = AkraColors.Amber)
                Text(
                    text = "Sending BLE command...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AkraColors.Silver,
                )
            }
        }
    }
}

@Composable
private fun ControlStatusStrip(connectionState: ConnectionState, valveState: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AkraColors.Carbon)
            .border(1.dp, AkraColors.Titanium, RoundedCornerShape(10.dp))
            .padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusCell(
            modifier = Modifier.weight(1f),
            title = "CONNECTION",
            value = connectionState.asText(),
            accent = if (connectionState is ConnectionState.Connected) AkraColors.Signal else AkraColors.Amber,
        )
        Box(
            modifier = Modifier
                .height(44.dp)
                .width(1.dp)
                .background(AkraColors.Titanium),
        )
        StatusCell(
            modifier = Modifier.weight(1f),
            title = "VALVE",
            value = valveState.uppercase(),
            accent = AkraColors.Amber,
        )
    }
}

@Composable
private fun StatusCell(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    accent: Color,
) {
    Column(
        modifier = modifier.padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(accent),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = AkraColors.Mist,
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = AkraColors.Pearl,
        )
    }
}

@Composable
private fun ProtocolPanel(protocolVerified: Boolean, lastError: String?) {
    val accent = if (protocolVerified) AkraColors.Signal else AkraColors.Danger
    PremiumPanel(accent = accent) {
        Eyebrow(if (protocolVerified) "// PROTOCOL VERIFIED" else "// PROTOCOL LOCKED")
        Text(
            text = if (protocolVerified) "Protocol verified" else "Protocol verification required",
            style = MaterialTheme.typography.titleLarge,
            color = AkraColors.Pearl,
        )
        Text(
            text = if (protocolVerified) {
                "Valve writes are enabled for the verified service and characteristic."
            } else {
                "OPEN and CLOSE are disabled until BLE_PROTOCOL.md contains verified UUIDs, command bytes, and write type."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = AkraColors.Silver,
        )
        if (lastError != null) {
            Text(
                text = "Last error: $lastError",
                style = MaterialTheme.typography.bodyMedium,
                color = AkraColors.Danger,
            )
        }
    }
}

@Composable
private fun ReceiverPanel(
    device: SoundKitDevice?,
    onDisconnect: () -> Unit,
) {
    PremiumPanel(accent = if (device != null) AkraColors.Amber else AkraColors.Titanium) {
        Eyebrow("// RECEIVER")
        Text(
            text = device?.name ?: "Not connected",
            style = MaterialTheme.typography.titleLarge,
            color = AkraColors.Pearl,
        )
        Text(
            text = device?.address ?: "Scan and connect to a receiver first.",
            style = MaterialTheme.typography.labelSmall,
            color = AkraColors.Mist,
        )
        OutlinedButton(
            enabled = device != null,
            onClick = onDisconnect,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = AkraColors.Amber,
                disabledContentColor = AkraColors.Mist,
            ),
        ) {
            Text("Disconnect", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun PremiumPanel(
    accent: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    0f to AkraColors.Graphite,
                    1f to AkraColors.Carbon,
                ),
            )
            .border(1.dp, AkraColors.Titanium, RoundedCornerShape(12.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    Brush.horizontalGradient(
                        0f to accent,
                        1f to Color.Transparent,
                    ),
                ),
        )
        content()
    }
}

@Composable
private fun ValveButton(
    modifier: Modifier = Modifier,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    primary: Boolean,
) {
    Button(
        modifier = modifier,
        enabled = enabled,
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) AkraColors.Amber else AkraColors.Graphite,
            contentColor = if (primary) AkraColors.Ink else AkraColors.Pearl,
            disabledContainerColor = AkraColors.Titanium.copy(alpha = 0.55f),
            disabledContentColor = AkraColors.Mist,
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun Eyebrow(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = AkraColors.Amber,
    )
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

