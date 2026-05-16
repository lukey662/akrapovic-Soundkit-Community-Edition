package com.akrapovic.soundkit.community.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akrapovic.soundkit.community.ui.AppScreen
import com.akrapovic.soundkit.community.ui.components.AkraCard
import com.akrapovic.soundkit.community.ui.components.AkraHeroHeader
import com.akrapovic.soundkit.community.ui.components.AkraScreen
import com.akrapovic.soundkit.community.ui.components.AkraStatusPill
import com.akrapovic.soundkit.community.ui.theme.AkraColors

@Composable
fun MoreScreen(
    modifier: Modifier = Modifier,
    onNavigate: (AppScreen) -> Unit,
) {
    AkraScreen(modifier = modifier) {
        AkraHeroHeader(
            eyebrow = "More",
            title = "App",
            subtitle = "Preferences, appearance, help, and troubleshooting.",
        )

        BuiltSoFarCard()

        MoreCard(
            title = "Diagnostics",
            body = "Share a local report if you need help with pairing or connection issues.",
            accent = AkraColors.Signal,
            onClick = { onNavigate(AppScreen.Diagnostics) },
        )
        MoreCard(
            title = "Settings",
            body = "Saved receiver, reconnect behavior, logs, and background connection.",
            accent = MaterialTheme.colorScheme.primary,
            onClick = { onNavigate(AppScreen.Settings) },
        )
        MoreCard(
            title = "Roadmap",
            body = "See what is finished and what is planned next.",
            accent = MaterialTheme.colorScheme.primary,
            onClick = { onNavigate(AppScreen.Roadmap) },
        )
        MoreCard(
            title = "Appearance",
            body = "Choose a refined color theme for the app.",
            accent = MaterialTheme.colorScheme.secondary,
            onClick = { onNavigate(AppScreen.GarageThemes) },
        )
    }
}

@Composable
private fun BuiltSoFarCard() {
    AkraCard(accent = MaterialTheme.colorScheme.primary) {
        AkraStatusPill(text = "Ready")
        Text(
            text = "Sound Kit control is local",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "The app connects directly to your receiver over Bluetooth. No account, no cloud, no telemetry.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MoreCard(
    title: String,
    body: String,
    accent: Color,
    onClick: () -> Unit,
) {
    AkraCard(
        accent = accent,
        onClick = onClick,
        contentDescription = "Open $title",
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(accent.copy(alpha = 0.16f), androidx.compose.foundation.shape.RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                    color = accent,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "›",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
