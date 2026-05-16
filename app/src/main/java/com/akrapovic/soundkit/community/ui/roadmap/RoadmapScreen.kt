package com.akrapovic.soundkit.community.ui.roadmap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akrapovic.soundkit.community.ui.theme.AkraColors

@Composable
fun RoadmapScreen(
    modifier: Modifier = Modifier,
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
        Eyebrow("// ROADMAP")
        Text(
            text = "Built so far",
            style = MaterialTheme.typography.displaySmall,
            color = AkraColors.Pearl,
        )
        Text(
            text = "Feature progress stays visible in the app, with valve commands still fail-closed until the protocol is verified.",
            style = MaterialTheme.typography.bodyMedium,
            color = AkraColors.Silver,
            modifier = Modifier.padding(top = 6.dp),
        )
        RoadmapSection(
            title = "Done",
            accent = AkraColors.Signal,
            items = doneItems,
        )
        RoadmapSection(
            title = "Next",
            accent = AkraColors.Amber,
            items = nextItems,
        )
        RoadmapSection(
            title = "Later",
            accent = AkraColors.Mist,
            items = laterItems,
        )
    }
}

@Composable
private fun RoadmapSection(
    title: String,
    accent: Color,
    items: List<RoadmapItem>,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(2.dp))
            .background(AkraColors.Carbon)
            .border(1.dp, AkraColors.Titanium, RoundedCornerShape(2.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(accent),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = AkraColors.Pearl,
            )
        }
        items.forEach { item ->
            RoadmapRow(item = item, accent = accent)
        }
    }
}

@Composable
private fun RoadmapRow(
    item: RoadmapItem,
    accent: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(6.dp)
                .background(accent),
        )
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = AkraColors.Pearl,
            )
            Text(
                text = item.body,
                style = MaterialTheme.typography.bodyMedium,
                color = AkraColors.Silver,
            )
        }
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

private data class RoadmapItem(
    val title: String,
    val body: String,
)

private val doneItems = listOf(
    RoadmapItem(
        title = "Real SoundKit receiver discovered",
        body = "Scan now finds the receiver by name and RSSI, including the confirmed SoundKit device.",
    ),
    RoadmapItem(
        title = "GATT connection reaches service discovery",
        body = "Android connects to the receiver and discovers services while keeping command writes disabled.",
    ),
    RoadmapItem(
        title = "Fail-closed valve safety",
        body = "OPEN and CLOSE remain blocked until UUIDs, command bytes, and write type are verified.",
    ),
    RoadmapItem(
        title = "Diagnostics and local-only controls",
        body = "Diagnostics export, foreground service, notification actions, Quick Settings, and Android Auto scaffold are in place.",
    ),
    RoadmapItem(
        title = "Premium scan shell",
        body = "The dark industrial scan surface and Akrapovic amber HUD language are live.",
    ),
)

private val nextItems = listOf(
    RoadmapItem(
        title = "GATT profile capture",
        body = "Log services, characteristics, descriptors, and properties in a copy-ready block.",
    ),
    RoadmapItem(
        title = "Control screen redesign",
        body = "Bring the same HUD panel system to the connected valve screen.",
    ),
    RoadmapItem(
        title = "Reconnect polish",
        body = "Avoid duplicate same-device connect requests triggering unnecessary reconnect cycles.",
    ),
    RoadmapItem(
        title = "Garage themes",
        body = "Start with Audi RS3 White Sportback and other brand-inspired community presets.",
    ),
)

private val laterItems = listOf(
    RoadmapItem(
        title = "Favorites",
        body = "Pin receivers, add nicknames, and choose a default receiver for faster reconnect.",
    ),
    RoadmapItem(
        title = "Rules and automations",
        body = "Add explicit, opt-in rules for time windows and geofences after command behavior is safe.",
    ),
    RoadmapItem(
        title = "Richer Android Auto and quick controls",
        body = "Show rule pause state and last automation reason without adding distraction.",
    ),
)
