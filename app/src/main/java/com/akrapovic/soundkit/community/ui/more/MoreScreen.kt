package com.akrapovic.soundkit.community.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akrapovic.soundkit.community.ui.AppScreen
import com.akrapovic.soundkit.community.ui.theme.AkraColors

@Composable
fun MoreScreen(
    modifier: Modifier = Modifier,
    onNavigate: (AppScreen) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AkraColors.Ink)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Eyebrow("// MORE")
        Text(
            text = "Garage hub",
            style = MaterialTheme.typography.displaySmall,
            color = AkraColors.Pearl,
        )
        Text(
            text = "Secondary tools, diagnostics, roadmap progress, and visual presets stay out of the main driving controls.",
            style = MaterialTheme.typography.bodyMedium,
            color = AkraColors.Silver,
        )

        BuiltSoFarCard()

        MoreCard(
            title = "Diagnostics",
            body = "Copy local BLE logs and inspect scan, connect, and service-discovery events.",
            accent = AkraColors.Signal,
            onClick = { onNavigate(AppScreen.Diagnostics) },
        )
        MoreCard(
            title = "Settings",
            body = "Auto-reconnect, debug logging, remembered receiver, and battery guidance.",
            accent = AkraColors.Amber,
            onClick = { onNavigate(AppScreen.Settings) },
        )
        MoreCard(
            title = "Roadmap",
            body = "See what is done, what is next, and what stays intentionally out of scope.",
            accent = AkraColors.AmberHi,
            onClick = { onNavigate(AppScreen.Roadmap) },
        )
        MoreCard(
            title = "Garage / Themes",
            body = "Preview community visual presets, starting with Audi RS3 White Sportback.",
            accent = Color(0xFFE5E8EE),
            onClick = { onNavigate(AppScreen.GarageThemes) },
        )
    }
}

@Composable
private fun BuiltSoFarCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(2.dp))
            .background(AkraColors.Carbon)
            .border(1.dp, AkraColors.AmberDim, RoundedCornerShape(2.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Eyebrow("// NEW")
        Text(
            text = "Real SoundKit connection confirmed",
            style = MaterialTheme.typography.titleLarge,
            color = AkraColors.Pearl,
        )
        Text(
            text = "The app can discover and connect to the receiver, then reach GATT service discovery. Valve writes remain disabled until protocol evidence is complete.",
            style = MaterialTheme.typography.bodyMedium,
            color = AkraColors.Silver,
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(2.dp))
            .background(AkraColors.Carbon)
            .border(1.dp, AkraColors.Titanium, RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Open $title" }
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(accent),
        )
        Spacer(Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = AkraColors.Pearl,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = AkraColors.Silver,
            )
        }
        Text(
            text = ">",
            style = MaterialTheme.typography.titleLarge,
            color = AkraColors.Amber,
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
