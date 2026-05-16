package com.akrapovic.soundkit.community.ui.scan

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akrapovic.soundkit.community.domain.SoundKitDevice
import com.akrapovic.soundkit.community.ui.SoundKitUiState
import com.akrapovic.soundkit.community.ui.theme.AkraColors

/**
 * Opening screen.
 *
 * Visual concept:
 *   - "Pit lane at night" — deep ink page that fades into carbon at the
 *     bottom edge to give the layout vertical depth without ornament.
 *   - HUD vocabulary: tracked uppercase eyebrows, hairline dividers, an
 *     instrument-cluster status strip, sharp 2.dp corners, hero typography.
 *   - One signature amber accent. Used as a signal (status LED, scan CTA,
 *     verified-receiver edge bar) — never as bulk colour.
 *
 * Functional contract — preserved unchanged:
 *   - All callbacks (onStartScan, onStopScan, onConnect, onRequestPermissions).
 *   - Permission rationale gating.
 *   - Sort order coming from the ViewModel (likely receivers first).
 *   - Visible labels that downstream Compose smoke tests assert against
 *     ("Bluetooth permission required", "Grant permissions",
 *      "Scan for receiver", "No receiver selected") are kept verbatim.
 */
@Composable
fun ScanScreen(
    modifier: Modifier = Modifier,
    state: SoundKitUiState,
    permissions: List<String>,
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (SoundKitDevice) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            // Vertical depth: ink at the top fades to carbon at the bottom.
            // Reads as atmosphere, not as a styled surface.
            .background(
                Brush.verticalGradient(
                    0f to AkraColors.Ink,
                    0.65f to AkraColors.Ink,
                    1f to AkraColors.Carbon,
                ),
            )
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        // Eyebrow → hero → subtitle. Strict hierarchy, lots of breathing room.
        Eyebrow(label = "// SCAN")
        Text(
            text = "FIND RECEIVER",
            style = MaterialTheme.typography.displayMedium,
            color = AkraColors.Pearl,
        )
        Text(
            text = "Bluetooth Low Energy · local control only",
            style = MaterialTheme.typography.bodyMedium,
            color = AkraColors.Mist,
        )

        if (!permissionsGranted) {
            // Stop short. Don't render the scan UI at all until the user has
            // granted permissions — keeps focus single-purpose.
            Spacer(Modifier.height(4.dp))
            PermissionRationaleCard(
                permissions = permissions,
                onRequestPermissions = onRequestPermissions,
            )
            return@Column
        }

        StatusStrip(
            isScanning = state.isScanning,
            count = state.devices.size,
        )

        ScanCta(
            isScanning = state.isScanning,
            onStartScan = onStartScan,
            onStopScan = onStopScan,
        )

        SectionDivider()

        if (state.devices.isEmpty()) {
            EmptyScanCard(isScanning = state.isScanning)
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.devices, key = { it.address }) { device ->
                    DeviceRow(device = device, onConnect = { onConnect(device) })
                }
            }
        }
    }
}

/**
 * Tracked uppercase tag. Used to label sections without consuming the visual
 * weight of a real heading. Always amber so the eye anchors here first.
 */
@Composable
private fun Eyebrow(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = AkraColors.Amber,
    )
}

/**
 * Hairline with an amber kiss in the middle.
 * Almost invisible, but it gives layout sections a hard edge.
 */
@Composable
private fun SectionDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.5f to AkraColors.Amber.copy(alpha = 0.35f),
                    1f to Color.Transparent,
                ),
            ),
    )
}

/**
 * Two-column instrument-cluster strip:
 *   STATUS | RECEIVERS
 *
 * Why a strip instead of separate cards:
 *   - Grouping these two values into a single hairline-bordered band reads
 *     like a dashboard cluster, not a stack of UI components.
 *   - The vertical hairline between cells is the strongest cue that this is
 *     telemetry, not chrome.
 */
@Composable
private fun StatusStrip(isScanning: Boolean, count: Int) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(AkraColors.Carbon)
            .border(1.dp, AkraColors.Titanium),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(72.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusCell(
                modifier = Modifier.weight(1f),
                title = "STATUS",
                value = if (isScanning) "SCANNING" else "IDLE",
                indicatorColor = if (isScanning) AkraColors.Amber else AkraColors.Mist,
                pulse = isScanning,
            )
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(AkraColors.Titanium),
            )
            StatusCell(
                modifier = Modifier.weight(1f),
                title = "RECEIVERS",
                value = count.toString().padStart(2, '0'),
                indicatorColor = if (count > 0) AkraColors.Amber else AkraColors.Mist,
                pulse = false,
            )
        }
    }
}

@Composable
private fun StatusCell(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    indicatorColor: Color,
    pulse: Boolean,
) {
    Column(
        modifier = modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 6.dp LED dot. Pulses while scanning to signal a live state.
            // The pulse is slow (900ms) — premium products don't flicker.
            val dotAlpha = if (pulse) {
                val transition = rememberInfiniteTransition(label = "ledPulse")
                val anim by transition.animateFloat(
                    initialValue = 0.35f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(900, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "ledAlpha",
                )
                anim
            } else 1f

            Box(
                Modifier
                    .size(6.dp)
                    .alpha(dotAlpha)
                    .background(indicatorColor),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = AkraColors.Mist,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.SemiBold,
            ),
            color = AkraColors.Pearl,
        )
    }
}

/**
 * Hero call-to-action.
 *
 * Two distinct visual states convey scan state without relying only on the
 * label change:
 *   - Idle:     ink fill, amber border, amber label.
 *   - Scanning: amber fill, ink label — visually "lit".
 *
 * Sharp 2.dp corners (not pill-shaped) anchor the motorsport vocabulary.
 *
 * The radar glyph is drawn with Canvas — no icon font dependency.
 */
@Composable
private fun ScanCta(
    isScanning: Boolean,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
) {
    val containerColor = if (isScanning) AkraColors.Amber else AkraColors.Ink
    val labelColor = if (isScanning) AkraColors.Ink else AkraColors.Amber
    // Visible label — kept verbatim for downstream tests:
    val text = if (isScanning) "Stop scan" else "Scan for receiver"
    val accessibilityLabel =
        if (isScanning) "Stop scanning" else "Scan for Sound Kit receiver"

    Box(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(containerColor)
            .border(1.dp, AkraColors.Amber, RoundedCornerShape(2.dp))
            .clickable(onClick = if (isScanning) onStopScan else onStartScan)
            .semantics { contentDescription = accessibilityLabel },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadarGlyph(active = isScanning, color = labelColor)
            Spacer(Modifier.width(14.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = labelColor,
            )
        }
    }
}

/**
 * Tiny radar mark drawn with Canvas. Two concentric rings + a centre dot.
 * The inner ring breathes when active so the button feels alive without
 * overstaying its welcome.
 */
@Composable
private fun RadarGlyph(active: Boolean, color: Color) {
    val transition = rememberInfiniteTransition(label = "radar")
    val sweepAlpha by transition.animateFloat(
        initialValue = if (active) 0.15f else 0.7f,
        targetValue = if (active) 1f else 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "radarAlpha",
    )
    Canvas(Modifier.size(18.dp)) {
        val strokePx = 1.5.dp.toPx()
        val stroke = Stroke(width = strokePx)
        val center = Offset(size.minDimension / 2, size.minDimension / 2)
        drawCircle(
            color = color.copy(alpha = 0.35f),
            radius = size.minDimension / 2 - strokePx / 2,
            center = center,
            style = stroke,
        )
        drawCircle(
            color = color.copy(alpha = sweepAlpha),
            radius = size.minDimension / 4,
            center = center,
            style = stroke,
        )
        drawCircle(
            color = color,
            radius = strokePx,
            center = center,
        )
    }
}

/**
 * Permission rationale.
 *
 * Replaces the generic Material card with a panel that has a 3.dp amber
 * left edge bar — the same visual device used for verified Sound Kit
 * receivers in the device list. Reusing the cue ties the two surfaces
 * together visually.
 */
@Composable
private fun PermissionRationaleCard(
    permissions: List<String>,
    onRequestPermissions: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(AkraColors.Carbon)
            .border(1.dp, AkraColors.Titanium),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(AkraColors.Amber),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Eyebrow(label = "// PERMISSION REQUIRED")
            // Visible label kept verbatim for tests.
            Text(
                text = "Bluetooth permission required",
                style = MaterialTheme.typography.titleLarge,
                color = AkraColors.Pearl,
            )
            Text(
                text = "Sound Kit Community talks to the receiver only over the phone’s local Bluetooth Low Energy radio. " +
                    "No internet, no accounts, no cloud, no telemetry.",
                style = MaterialTheme.typography.bodyMedium,
                color = AkraColors.Silver,
            )
            Text(
                text = "REQUESTING · " + permissions.joinToString(separator = " · ") {
                    it.substringAfterLast('.')
                },
                style = MaterialTheme.typography.labelMedium,
                color = AkraColors.Mist,
            )
            Spacer(Modifier.height(4.dp))
            // Filled amber CTA — strong contrast against the carbon panel.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(AkraColors.Amber)
                    .clickable(onClick = onRequestPermissions)
                    .semantics {
                        contentDescription = "Grant Bluetooth and notification permissions"
                    },
                contentAlignment = Alignment.Center,
            ) {
                // Visible label kept verbatim for tests.
                Text(
                    text = "Grant permissions",
                    style = MaterialTheme.typography.labelLarge,
                    color = AkraColors.Ink,
                )
            }
        }
    }
}

/**
 * Empty / awaiting state.
 *
 * Calm, informative, never apologetic. The eyebrow flips between
 * "// READY" and "// LISTENING" so the user always understands which mode
 * the radio is in.
 */
@Composable
private fun EmptyScanCard(isScanning: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(AkraColors.Carbon)
            .border(1.dp, AkraColors.Titanium)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Eyebrow(label = if (isScanning) "// LISTENING" else "// READY")
        // Visible label kept verbatim for tests.
        Text(
            text = if (isScanning) "Scanning nearby BLE devices..." else "No receiver selected",
            style = MaterialTheme.typography.titleMedium,
            color = AkraColors.Pearl,
        )
        Text(
            text = "Power on the Sound Kit receiver and keep the phone near the vehicle while parked.",
            style = MaterialTheme.typography.bodyMedium,
            color = AkraColors.Silver,
        )
    }
}

/**
 * One discovered device.
 *
 * Composition:
 *   - 3.dp left edge bar — amber for likely Sound Kit receivers, titanium
 *     for everything else. The amber edge IS the affordance: it tells the
 *     user "this is the one".
 *   - Device name in Pearl SemiBold so a known receiver is unmistakable.
 *   - Address in Monospace Mist — paired with a 4-segment RSSI bar that
 *     reads as a signal-strength gauge.
 *   - Trailing "CONNECT  ›" tracked label in amber.
 */
@Composable
private fun DeviceRow(
    device: SoundKitDevice,
    onConnect: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(2.dp))
            .background(AkraColors.Carbon)
            .border(1.dp, AkraColors.Titanium, RoundedCornerShape(2.dp))
            .clickable(onClick = onConnect)
            .semantics {
                contentDescription = "Connect to ${device.name} ${device.address}"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(
                    if (device.isLikelySoundKit) AkraColors.Amber else AkraColors.Titanium,
                ),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = device.name,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = AkraColors.Pearl,
            )
            Text(
                text = device.address,
                style = MaterialTheme.typography.labelSmall,
                color = AkraColors.Mist,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                RssiBars(rssi = device.rssi)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = device.rssi?.let { "$it dBm" } ?: "RSSI —",
                    style = MaterialTheme.typography.labelMedium,
                    color = AkraColors.Mist,
                )
                if (device.isLikelySoundKit) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "· LIKELY SOUND KIT",
                        style = MaterialTheme.typography.labelMedium,
                        color = AkraColors.Amber,
                    )
                }
            }
        }
        Text(
            text = "CONNECT  ›",
            style = MaterialTheme.typography.labelLarge,
            color = AkraColors.Amber,
            modifier = Modifier.padding(end = 20.dp),
        )
    }
}

/**
 * 4-segment RSSI bar. Each bar is taller than the last so the gauge has
 * built-in directionality. Lit segments use amber; unlit use titanium so
 * the silhouette reads even when reception is weak.
 */
@Composable
private fun RssiBars(rssi: Int?) {
    val signal = when {
        rssi == null -> 0
        rssi >= -55 -> 4
        rssi >= -65 -> 3
        rssi >= -75 -> 2
        rssi >= -85 -> 1
        else -> 0
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        repeat(4) { index ->
            val barHeight = (6 + index * 3).dp
            Box(
                Modifier
                    .width(4.dp)
                    .height(barHeight)
                    .background(
                        if (index < signal) AkraColors.Amber else AkraColors.Titanium,
                    ),
            )
        }
    }
}
