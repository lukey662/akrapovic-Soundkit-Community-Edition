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
import com.akrapovic.soundkit.community.ui.components.AkraCardShape
import com.akrapovic.soundkit.community.ui.components.AkraHeroHeader
import com.akrapovic.soundkit.community.ui.theme.AkraColors

@Composable
fun RoadmapScreen(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AkraHeroHeader(
            eyebrow = "Roadmap",
            title = "Built so far",
            subtitle = "Progress stays visible. Valve writes remain locked until protocol evidence is complete.",
        )
        RoadmapSection(
            title = "Done",
            accent = AkraColors.Signal,
            items = doneItems,
        )
        RoadmapSection(
            title = "Next",
            accent = MaterialTheme.colorScheme.primary,
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
            .clip(AkraCardShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, AkraColors.Titanium, AkraCardShape)
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
                color = MaterialTheme.colorScheme.onSurface,
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
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = item.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Eyebrow(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

private data class RoadmapItem(
    val title: String,
    val body: String,
)

private val doneItems = listOf(
    RoadmapItem(
        title = "Real SoundKit receiver discovered",
        body = "Scan finds the receiver by name and RSSI. Confirmed against DC:F3:1C:16:EE:DA.",
    ),
    RoadmapItem(
        title = "GATT connection and service discovery",
        body = "Android connects and reaches GATT service discovery. GATT profile is copy-ready from More → Diagnostics.",
    ),
    RoadmapItem(
        title = "Fail-closed valve safety",
        body = "OPEN and CLOSE stay blocked until UUIDs, command bytes, and write type are verified in BLE_PROTOCOL.md.",
    ),
    RoadmapItem(
        title = "Diagnostics and local-only controls",
        body = "Diagnostics export, foreground BLE service, notification actions, Quick Settings tile, and Android Auto scaffold.",
    ),
    RoadmapItem(
        title = "Premium HUD scan and control shell",
        body = "Dark industrial surface, amber accent, instrument-cluster status strips, and HUD typography throughout.",
    ),
    RoadmapItem(
        title = "Reconnect polish",
        body = "Same-device reconnect guard prevents duplicate connect requests and unnecessary reconnect cycles.",
    ),
    RoadmapItem(
        title = "Garage themes",
        body = "Eight brand-inspired palettes. Active now — tap a preset in Appearance to apply immediately.",
    ),
    RoadmapItem(
        title = "Back navigation",
        body = "System back gesture and back chevron work correctly from all More sub-screens.",
    ),
)

private val nextItems = listOf(
    RoadmapItem(
        title = "BLE protocol evidence",
        body = "Paste GATT PROFILE block from Diagnostics into BLE_PROTOCOL.md, then capture command bytes via JADX or HCI snoop.",
    ),
    RoadmapItem(
        title = "Verified valve writes",
        body = "Once UUIDs, write type, and payload bytes are documented, enable OPEN / CLOSE in SoundKitProtocol.kt.",
    ),
    RoadmapItem(
        title = "Theme persistence",
        body = "Save selected Garage theme to DataStore so it restores after the app restarts.",
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
