package com.akrapovic.soundkit.community.ui.more

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.akrapovic.soundkit.community.ui.components.AkraHeroHeader
import com.akrapovic.soundkit.community.ui.components.AkraListGroup
import com.akrapovic.soundkit.community.ui.components.AkraScreen
import com.akrapovic.soundkit.community.ui.components.AkraSwitchRow

@Composable
fun DeveloperScreen(
    modifier: Modifier = Modifier,
    debugLoggingEnabled: Boolean,
    onDebugLoggingChanged: (Boolean) -> Unit,
) {
    AkraScreen(modifier = modifier) {
        AkraHeroHeader(
            title = "Developer",
            subtitle = "Optional tools for troubleshooting. Nothing here leaves your phone.",
            compact = true,
            titleModifier = Modifier.semantics { heading() },
        )

        AkraListGroup {
            AkraSwitchRow(
                title = "Detailed logs",
                subtitle = "Extra connection detail in Diagnostics",
                checked = debugLoggingEnabled,
                onCheckedChange = onDebugLoggingChanged,
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "Most people can leave this off. Turn it on only if you are sharing a diagnostics report.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
