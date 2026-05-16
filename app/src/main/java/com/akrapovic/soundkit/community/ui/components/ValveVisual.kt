package com.akrapovic.soundkit.community.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.akrapovic.soundkit.community.domain.ValveState
import kotlin.math.min

/**
 * Visual indicator for the current [ValveState].
 *
 * Two opposing arc plates animate apart (open) or together (closed). A soft
 * radial glow pulses while the valve is open to communicate active flow.
 *
 * The component is decorative; the helper text near the visual remains the
 * source of truth for screen readers.
 */
@Composable
fun ValveVisual(
    state: ValveState,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val ringColor = MaterialTheme.colorScheme.outline
    val plateColor = MaterialTheme.colorScheme.onSurface

    val targetGap by animateFloatAsState(
        targetValue = when (state) {
            ValveState.Open -> 0.42f
            ValveState.Closed -> 0.04f
            ValveState.Unknown -> 0.18f
        },
        animationSpec = tween(durationMillis = 420),
        label = "valveGap",
    )

    val infinite = rememberInfiniteTransition(label = "valvePulse")
    val pulseAlpha by infinite.animateFloat(
        initialValue = if (state == ValveState.Open) 0.18f else 0.0f,
        targetValue = if (state == ValveState.Open) 0.42f else 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "valvePulseAlpha",
    )

    val glowColor by animateColorAsState(
        targetValue = when (state) {
            ValveState.Open -> accent
            ValveState.Closed -> Color.Transparent
            ValveState.Unknown -> ringColor
        },
        animationSpec = tween(durationMillis = 420),
        label = "valveGlow",
    )

    Box(
        modifier = modifier.size(180.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f
            val outerRadius = min(w, h) / 2f - 4.dp.toPx()
            val ringStroke = 2.dp.toPx()
            val plateStroke = 14.dp.toPx()
            val plateRadius = outerRadius - 18.dp.toPx()

            // Soft radial glow (only visible when accent != Transparent)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glowColor.copy(alpha = pulseAlpha),
                        Color.Transparent,
                    ),
                    center = Offset(cx, cy),
                    radius = outerRadius,
                ),
                radius = outerRadius,
                center = Offset(cx, cy),
            )

            // Outer ring
            drawCircle(
                color = ringColor,
                radius = outerRadius,
                center = Offset(cx, cy),
                style = Stroke(width = ringStroke),
            )

            // Two opposing arc plates with animated gap
            val gapDegrees = (targetGap * 180f).coerceIn(0f, 170f)
            val sweep = 180f - gapDegrees

            drawArc(
                color = plateColor,
                startAngle = -90f + (gapDegrees / 2f),
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(cx - plateRadius, cy - plateRadius),
                size = androidx.compose.ui.geometry.Size(plateRadius * 2, plateRadius * 2),
                style = Stroke(width = plateStroke),
            )

            drawArc(
                color = plateColor,
                startAngle = 90f + (gapDegrees / 2f),
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(cx - plateRadius, cy - plateRadius),
                size = androidx.compose.ui.geometry.Size(plateRadius * 2, plateRadius * 2),
                style = Stroke(width = plateStroke),
            )

            // Center dot — bright when open, dim when closed
            val dotRadius = 6.dp.toPx()
            val dotColor = when (state) {
                ValveState.Open -> accent
                ValveState.Closed -> ringColor
                ValveState.Unknown -> ringColor
            }
            drawCircle(
                color = dotColor,
                radius = dotRadius,
                center = Offset(cx, cy),
            )
        }
    }
}
