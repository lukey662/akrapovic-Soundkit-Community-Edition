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

@Composable
fun AdvancedScreen(
    modifier: Modifier = Modifier,
    onNavigate: (AppScreen) -> Unit,
) {
    AkraScreen(modifier = modifier) {
        AkraHeroHeader(
            title = "Advanced",
            subtitle = "Troubleshooting, car setup, and technical options.",
            compact = true,
            titleModifier = Modifier.semantics { heading() },
        )

        AkraListGroup {
            AkraListRow(
                title = "Vehicle compatibility",
                subtitle = "Supported and Beta tiers — see COMPATIBILITY.md in the repo",
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
            AkraListDivider()
            AkraListRow(
                title = "Roadmap",
                subtitle = "What's already in the app",
                showChevron = true,
                onClick = { onNavigate(AppScreen.Roadmap) },
            )
            AkraListDivider()
            AkraListRow(
                title = "Developer",
                subtitle = "Detailed logs and technical options",
                showChevron = true,
                onClick = { onNavigate(AppScreen.Developer) },
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "Nothing here leaves your phone unless you choose to share a report.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
