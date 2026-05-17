package com.akrapovic.soundkit.community.ui.beta

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.akrapovic.soundkit.community.ui.AppScreen
import com.akrapovic.soundkit.community.ui.components.AkraActionButton
import com.akrapovic.soundkit.community.ui.components.AkraCard
import com.akrapovic.soundkit.community.ui.components.AkraHeroHeader
import com.akrapovic.soundkit.community.ui.components.AkraScreen
import com.akrapovic.soundkit.community.ui.components.AkraStatusPill

@Composable
fun BetaHubScreen(
    modifier: Modifier = Modifier,
    state: BetaUiState,
    onAcceptDisclaimer: () -> Unit,
    onAutomationPausedChanged: (Boolean) -> Unit,
    onNavigate: (AppScreen) -> Unit,
) {
    AkraScreen(modifier = modifier) {
        AkraHeroHeader(
            eyebrow = "Beta",
            title = "Automation",
            subtitle = "Experimental schedules and geofences. Use only while parked with ignition on if your kit requires it.",
        )

        if (!state.settings.betaDisclaimerAccepted) {
            AkraCard(accent = MaterialTheme.colorScheme.primary) {
                AkraStatusPill(text = "Disclaimer")
                Text(
                    text = "Automation can open or close valves when connected. Manual controls always win. You are responsible for safe use.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AkraActionButton(
                    label = "I understand",
                    onClick = onAcceptDisclaimer,
                    contentDescription = "Accept beta automation disclaimer",
                )
            }
        } else {
            AkraCard(accent = MaterialTheme.colorScheme.primary) {
                RowToggle(
                    title = "Pause all automation",
                    body = "Stops schedule and geofence rules until you resume.",
                    checked = state.settings.automationPaused,
                    onCheckedChange = onAutomationPausedChanged,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HubLink(
                    title = "Rules",
                    body = "${state.rules.size} saved · schedules and geofence triggers",
                    onClick = { onNavigate(AppScreen.Rules) },
                )
                HubLink(
                    title = "Geofence zones",
                    body = "${state.zones.size} zones · up to 4",
                    onClick = { onNavigate(AppScreen.GeofenceZones) },
                )
                HubLink(
                    title = "Activity log",
                    body = "Recent automation attempts",
                    onClick = { onNavigate(AppScreen.AutomationLog) },
                )
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun HubLink(title: String, body: String, onClick: () -> Unit) {
    AkraCard(accent = MaterialTheme.colorScheme.secondary, onClick = onClick, contentDescription = title) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RowToggle(
    title: String,
    body: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
