package com.akrapovic.soundkit.community.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.akrapovic.soundkit.community.R
import com.akrapovic.soundkit.community.domain.ValveState
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Layered vector valve hero: ring + two blades + accent core, with spring open/close,
 * flow lines when open, shimmer while a command is in flight, and a one-shot success ripple.
 *
 * Decorative only — adjacent text remains the accessibility source of truth.
 */
@Composable
fun ValveVisual(
    state: ValveState,
    modifier: Modifier = Modifier,
    commandInFlight: Boolean = false,
    successRippleTrigger: Int = 0,
) {
    val accent = MaterialTheme.colorScheme.primary
    val ringTint = MaterialTheme.colorScheme.outline
    val bladeTint = MaterialTheme.colorScheme.onSurface
    val context = LocalContext.current
    val reduceMotion = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }

    val unknownBreath = rememberInfiniteTransition(label = "valveUnknownBreath")
    val breathGap by unknownBreath.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "valveBreathGap",
    )

    val baseGap = when {
        state == ValveState.Unknown && !reduceMotion -> breathGap
        else -> ValveGapMath.targetGap(state)
    }

    val gapSpring = spring<Float>(dampingRatio = 0.72f, stiffness = 380f)
    val gapTween = tween<Float>(durationMillis = if (reduceMotion) 0 else 420)

    val animatedGap by animateFloatAsState(
        targetValue = baseGap,
        animationSpec = if (reduceMotion) gapTween else gapSpring,
        label = "valveGap",
    )

    val bladeRotation = ValveGapMath.bladeRotationDegrees(animatedGap)

    val infinite = rememberInfiniteTransition(label = "valvePulse")
    val pulseAlpha by infinite.animateFloat(
        initialValue = if (state == ValveState.Open && !reduceMotion) 0.16f else 0.0f,
        targetValue = if (state == ValveState.Open && !reduceMotion) 0.38f else 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "valvePulseAlpha",
    )

    val shimmer by infinite.animateFloat(
        initialValue = if (commandInFlight && !reduceMotion) 0.92f else 1f,
        targetValue = if (commandInFlight && !reduceMotion) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "valveShimmer",
    )

    val flowPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "valveFlowPhase",
    )

    val glowColor by animateColorAsState(
        targetValue = when (state) {
            ValveState.Open -> accent
            ValveState.Closed -> Color.Transparent
            ValveState.Unknown -> ringTint.copy(alpha = 0.5f)
        },
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 420),
        label = "valveGlow",
    )

    val coreDotColor by animateColorAsState(
        targetValue = when (state) {
            ValveState.Open -> accent
            ValveState.Closed -> ringTint
            ValveState.Unknown -> ringTint.copy(alpha = 0.7f)
        },
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 420),
        label = "valveCoreDot",
    )

    var lastRippleTrigger by remember { mutableIntStateOf(0) }
    val rippleAnimatable = remember { Animatable(0f) }
    LaunchedEffect(successRippleTrigger, reduceMotion) {
        if (successRippleTrigger > lastRippleTrigger) {
            lastRippleTrigger = successRippleTrigger
            if (!reduceMotion) {
                rippleAnimatable.snapTo(0f)
                rippleAnimatable.animateTo(1f, tween(durationMillis = 520))
                rippleAnimatable.snapTo(0f)
            }
        }
    }

    val rippleProgress = rippleAnimatable.value

    val contentAlpha = when (state) {
        ValveState.Unknown -> 0.82f
        else -> 1f
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f
            val outerRadius = min(w, h) / 2f - 4.dp.toPx()

            if (glowColor != Color.Transparent && pulseAlpha > 0f) {
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
            }

            if (state == ValveState.Open && !reduceMotion && !commandInFlight) {
                drawFlowLines(
                    center = Offset(cx, cy),
                    radius = outerRadius * 0.55f,
                    phase = flowPhase,
                    color = accent.copy(alpha = 0.35f),
                )
            }

            if (rippleProgress > 0f) {
                drawCircle(
                    color = accent.copy(alpha = (1f - rippleProgress) * 0.45f),
                    radius = outerRadius * (0.35f + rippleProgress * 0.55f),
                    center = Offset(cx, cy),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }

        Image(
            painter = painterResource(R.drawable.valve_ring),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize(0.92f)
                .graphicsLayer {
                    scaleX = shimmer
                    scaleY = shimmer
                    alpha = contentAlpha
                },
            colorFilter = ColorFilter.tint(ringTint),
        )

        Image(
            painter = painterResource(R.drawable.valve_blade_top),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize(0.92f)
                .graphicsLayer {
                    rotationZ = -bladeRotation
                    alpha = contentAlpha
                },
            colorFilter = ColorFilter.tint(bladeTint),
        )

        Image(
            painter = painterResource(R.drawable.valve_blade_bottom),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize(0.92f)
                .graphicsLayer {
                    rotationZ = bladeRotation
                    alpha = contentAlpha
                },
            colorFilter = ColorFilter.tint(bladeTint),
        )

        if (state == ValveState.Open && glowColor != Color.Transparent) {
            Image(
                painter = painterResource(R.drawable.valve_core_glow),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize(0.92f)
                    .graphicsLayer { alpha = pulseAlpha.coerceIn(0f, 1f) },
                colorFilter = ColorFilter.tint(glowColor),
            )
        }

        Image(
            painter = painterResource(R.drawable.valve_core_dot),
            contentDescription = null,
            modifier = Modifier
                .size(28.dp)
                .graphicsLayer { alpha = contentAlpha },
            colorFilter = ColorFilter.tint(coreDotColor),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFlowLines(
    center: Offset,
    radius: Float,
    phase: Float,
    color: Color,
) {
    val lineCount = 4
    for (index in 0 until lineCount) {
        val angle = ((index / lineCount.toFloat()) * 360f + phase * 360f) % 360f
        val radians = Math.toRadians(angle.toDouble())
        val startR = radius * 0.15f
        val endR = radius * (0.55f + (index % 2) * 0.12f)
        val start = Offset(
            center.x + cos(radians).toFloat() * startR,
            center.y + sin(radians).toFloat() * startR,
        )
        val end = Offset(
            center.x + cos(radians).toFloat() * endR,
            center.y + sin(radians).toFloat() * endR,
        )
        drawLine(
            color = color.copy(alpha = color.alpha * (0.5f + (index % 2) * 0.25f)),
            start = start,
            end = end,
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}
