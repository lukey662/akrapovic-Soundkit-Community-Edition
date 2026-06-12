package com.akrapovic.soundkit.community.ui.roadmap

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akrapovic.soundkit.community.ui.components.AkraCard
import com.akrapovic.soundkit.community.ui.components.AkraHeroHeader
import com.akrapovic.soundkit.community.ui.components.AkraScreen
import com.akrapovic.soundkit.community.ui.components.AkraStatusPill
import com.akrapovic.soundkit.community.ui.theme.AkraColors
import com.akrapovic.soundkit.community.ui.theme.LocalAkraTheme

@Composable
fun RoadmapScreen(
    modifier: Modifier = Modifier,
) {
    AkraScreen(modifier = modifier) {
        AkraHeroHeader(
            eyebrow = "Roadmap",
            title = "Roadmap",
            subtitle = "What's already in the app.",
        )

        RoadmapSummaryStrip(shippedCount = shippedItems.size)

        if (upNextItems.isNotEmpty()) {
            RoadmapSection(
                title = "Up next",
                subtitle = "Near-term focus",
                accent = MaterialTheme.colorScheme.primary,
                items = upNextItems,
                style = RoadmapSectionStyle.Featured,
            )
        }

        if (laterItems.isNotEmpty()) {
            RoadmapSection(
                title = "On the horizon",
                subtitle = "Product ideas after the foundation is solid",
                accent = LocalAkraTheme.current.secondaryAccent,
                items = laterItems,
                style = RoadmapSectionStyle.Standard,
            )
        }

        if (upNextItems.isEmpty() && laterItems.isEmpty()) {
            Text(
                text = "No queued work — fixes and polish land as feedback arrives.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        NonGoalsCard()

        ShippedSection(
            items = shippedItems,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}

@Composable
private fun RoadmapSummaryStrip(
    shippedCount: Int,
) {
    AkraCard(accent = MaterialTheme.colorScheme.primary) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            SummaryStat(value = shippedCount.toString(), label = "Shipped")
        }
    }
}

@Composable
private fun SummaryStat(value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private enum class RoadmapSectionStyle {
    Featured,
    Standard,
}

@Composable
private fun RoadmapSection(
    title: String,
    subtitle: String,
    accent: Color,
    items: List<RoadmapItem>,
    style: RoadmapSectionStyle,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            title = title,
            subtitle = subtitle,
            accent = accent,
            count = items.size,
        )
        items.forEachIndexed { index, item ->
            RoadmapItemCard(
                item = item,
                accent = accent,
                index = index + 1,
                emphasized = style == RoadmapSectionStyle.Featured,
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    accent: Color,
    count: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.linearGradient(
                        0f to accent.copy(alpha = 0.22f),
                        1f to accent.copy(alpha = 0.06f),
                    ),
                )
                .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = accent,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RoadmapItemCard(
    item: RoadmapItem,
    accent: Color,
    index: Int,
    emphasized: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (emphasized) 88.dp else 76.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(
                if (emphasized) {
                    Brush.linearGradient(
                        0f to MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        1f to accent.copy(alpha = 0.06f),
                    )
                } else {
                    Brush.linearGradient(
                        0f to MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                        1f to MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                    )
                },
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = if (emphasized) 0.45f else 0.28f),
                shape = RoundedCornerShape(22.dp),
            )
            .padding(start = 0.dp),
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(
                    Brush.verticalGradient(
                        0f to accent,
                        1f to accent.copy(alpha = 0.35f),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "%02d".format(index),
                    style = MaterialTheme.typography.labelSmall,
                    color = accent.copy(alpha = 0.85f),
                )
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = item.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NonGoalsCard() {
    AkraCard(accent = AkraColors.Mist) {
        AkraStatusPill(text = "Out of scope for now", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = "Not planned",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        nonGoalItems.forEach { item ->
            Text(
                text = "•  ${item.title} — ${item.body}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ShippedSection(
    items: List<RoadmapItem>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val accent = AkraColors.Signal

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable(role = Role.Button) { expanded = !expanded }
                .semantics {
                    contentDescription = if (expanded) {
                        "Collapse shipped features list"
                    } else {
                        "Expand shipped features list"
                    }
                }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.12f))
                    .border(1.dp, accent.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                CheckmarkIcon(color = accent, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Shipped",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (expanded) "Tap to collapse" else "${items.size} features already in the app",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = if (expanded) "−" else "+",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { item ->
                    ShippedItemRow(item = item, accent = accent)
                }
            }
        }
    }
}

@Composable
private fun ShippedItemRow(
    item: RoadmapItem,
    accent: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        CheckmarkIcon(color = accent, modifier = Modifier.padding(top = 2.dp).size(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
            )
            Text(
                text = item.body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
            )
        }
    }
}

@Composable
private fun CheckmarkIcon(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val stroke = 2.2.dp.toPx()
        val path = Path().apply {
            moveTo(size.width * 0.12f, size.height * 0.52f)
            lineTo(size.width * 0.38f, size.height * 0.78f)
            lineTo(size.width * 0.88f, size.height * 0.22f)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

private data class RoadmapItem(
    val title: String,
    val body: String,
)

private val shippedItems = listOf(
    RoadmapItem("BLE protocol", "Verified toggle, advertising scan, notifications, state-gated OPEN/CLOSE, pairing."),
    RoadmapItem("BLE stability", "Status 0x04 handled without reconnect storms; honest retry counter."),
    RoadmapItem("Physical receiver", "Real-car connect, pair, and open/close validated."),
    RoadmapItem("Unified Home", "One tab for scan and valve control — no separate Find / Control."),
    RoadmapItem("Valve control card", "Animated visual plus a single Open / Close action."),
    RoadmapItem("Consumer UI", "Calm Home, More, Settings, and Diagnostics flows."),
    RoadmapItem("Themes", "Brand families with Light/Dark variants and Studio Blue default."),
    RoadmapItem("Unified onboarding", "Risk, Bluetooth, notifications, and battery steps in one first-run flow."),
    RoadmapItem("Empty and error states", "AkraStatePanel recovery on scan, reconnect, and diagnostics."),
    RoadmapItem("Theme polish", "Studio/Audi light contrast and labeled Appearance swatches."),
    RoadmapItem("CI reliability", "Gradle wrapper JAR committed; Actions use ./gradlew."),
    RoadmapItem("Accessibility pass", "Semantics and instrumented tests on Home and onboarding."),
    RoadmapItem("Diagnostics export", "Copy, Save-to-file, and file-only Share."),
    RoadmapItem("Launcher icon", "Adaptive valve-glyph icon set."),
    RoadmapItem("Android Auto base", "IoT Car App service, manifest descriptor, and DHU testing path."),
    RoadmapItem(
        "Projected AA sideload",
        "Auto-reconnect on car entry, toggle on template, status-04 copy, notification/QS fallback.",
    ),
    RoadmapItem("Confirmations", "Disconnect and Forget receiver dialogs."),
    RoadmapItem(
        "Favorites",
        "Up to eight saved receivers, default star, nicknames, and connect on launch.",
    ),
    RoadmapItem(
        "Smarter notifications",
        "Gated Open/Close, nickname titles, and honest not-ready copy on status 04.",
    ),
    RoadmapItem(
        "Drive mode",
        "Preferred Open/Closed on connect; quiet neighbours with editable hours and overnight windows.",
    ),
    RoadmapItem(
        "Reconnect cap",
        "Auto-reconnect stops after eight attempts with honest retry copy.",
    ),
    RoadmapItem(
        "Quiet neighbours hours",
        "Start/end time pickers, overnight windows, and manual override per connect session.",
    ),
    RoadmapItem(
        "Theme contrast",
        "Readable primary buttons on dark garage themes, especially Audi RS Dark.",
    ),
    RoadmapItem(
        "More screen",
        "Settings and Appearance on More; Advanced hub for Diagnostics, Android Auto, Roadmap, and Developer.",
    ),
    RoadmapItem(
        "Rules engine (design)",
        "Evaluator and precedence spec only — execution and persistence come later.",
    ),
    RoadmapItem(
        "Beta automation",
        "Rules, schedules, geofences, execution log, and notification pause — under Settings.",
    ),
)

private val upNextItems = emptyList<RoadmapItem>()

private val laterItems = emptyList<RoadmapItem>()

private val nonGoalItems = listOf(
    RoadmapItem("Cloud control", "No accounts, remote valve control, or telemetry backends."),
    RoadmapItem("Official integration", "Not affiliated with or supported by Akrapovič d.d."),
    RoadmapItem(
        "Play Store projected AA",
        "Public Play listing in projected Android Auto — policy does not fit a valve controller.",
    ),
)
