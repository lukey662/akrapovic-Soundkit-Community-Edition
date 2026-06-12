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
import com.akrapovic.soundkit.community.ui.AppScreen
import com.akrapovic.soundkit.community.ui.components.AkraHeroHeader
import com.akrapovic.soundkit.community.ui.components.AkraListDivider
import com.akrapovic.soundkit.community.ui.components.AkraListGroup
import com.akrapovic.soundkit.community.ui.components.AkraListRow
import com.akrapovic.soundkit.community.ui.components.AkraScreen
import com.akrapovic.soundkit.community.ui.components.AkraSectionTitle

@Composable
fun MoreScreen(
    modifier: Modifier = Modifier,
    onNavigate: (AppScreen) -> Unit,
) {
    AkraScreen(modifier = modifier) {
        AkraHeroHeader(
            title = "More",
            subtitle = null,
            compact = true,
            titleModifier = Modifier.semantics { heading() },
        )

        AkraListGroup {
            AkraListRow(
                title = "Settings",
                subtitle = "Receivers, drive mode, and connection",
                showChevron = true,
                onClick = { onNavigate(AppScreen.Settings) },
            )
            AkraListDivider()
            AkraListRow(
                title = "Appearance",
                subtitle = "Garage themes",
                showChevron = true,
                onClick = { onNavigate(AppScreen.GarageThemes) },
            )
            AkraListDivider()
            AkraListRow(
                title = "Roadmap",
                subtitle = "What's shipped and what's next",
                showChevron = true,
                onClick = { onNavigate(AppScreen.Roadmap) },
            )
            AkraListDivider()
            AkraListRow(
                title = "Diagnostics",
                subtitle = "Help with troubleshooting",
                showChevron = true,
                onClick = { onNavigate(AppScreen.Diagnostics) },
            )
            AkraListDivider()
            AkraListRow(
                title = "Android Auto",
                subtitle = "Use Sound Kit on your car display",
                showChevron = true,
                onClick = { onNavigate(AppScreen.AndroidAutoSetup) },
            )
        }

        AkraSectionTitle("Advanced")
        AkraListGroup {
            AkraListRow(
                title = "Developer",
                subtitle = "Detailed logs and technical options",
                showChevron = true,
                onClick = { onNavigate(AppScreen.Developer) },
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "Everything stays on this phone. No account, no cloud.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
