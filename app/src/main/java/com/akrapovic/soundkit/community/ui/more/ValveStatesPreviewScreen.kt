package com.akrapovic.soundkit.community.ui.more

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.akrapovic.soundkit.community.domain.ValveState
import com.akrapovic.soundkit.community.ui.components.AkraHeroHeader
import com.akrapovic.soundkit.community.ui.components.AkraScreen
import com.akrapovic.soundkit.community.ui.components.AkraSurface
import com.akrapovic.soundkit.community.ui.components.ValveVisual

/**
 * Design/QA gallery for the exhaust-tip valve mark across typed states.
 * Reachable from Developer — not a driving control surface.
 */
@Composable
fun ValveStatesPreviewScreen(
    modifier: Modifier = Modifier,
) {
    AkraScreen(modifier = modifier) {
        AkraHeroHeader(
            title = "Valve visual",
            subtitle = "Closed, open, checking, and busy states for the Home exhaust tip.",
            compact = true,
            titleModifier = Modifier.semantics { heading() },
        )

        ValveStateCard(
            title = "Closed",
            subtitle = "Quiet — disc seals the bore, no heat glow.",
            state = ValveState.Closed,
            commandInFlight = false,
        )
        ValveStateCard(
            title = "Open",
            subtitle = "Sport — disc tilts open, amber heat visible.",
            state = ValveState.Open,
            commandInFlight = false,
        )
        ValveStateCard(
            title = "Checking status",
            subtitle = "Unknown — soft breath animation until the receiver reports.",
            state = ValveState.Unknown,
            commandInFlight = false,
        )
        ValveStateCard(
            title = "Changing…",
            subtitle = "Command in flight — waiting for receiver confirmation.",
            state = ValveState.Closed,
            commandInFlight = true,
        )

        Spacer(Modifier.height(8.dp))
        Text(
            text = "This preview does not send BLE commands.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun ValveStateCard(
    title: String,
    subtitle: String,
    state: ValveState,
    commandInFlight: Boolean,
) {
    AkraSurface {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ValveVisual(
            state = state,
            commandInFlight = commandInFlight,
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .align(Alignment.CenterHorizontally),
        )
    }
    Spacer(Modifier.height(10.dp))
}
