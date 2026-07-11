package com.akrapovic.soundkit.community.ui.components

import android.provider.Settings
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.akrapovic.soundkit.community.domain.ValveState
import com.akrapovic.soundkit.community.ui.theme.AkraColors
import com.akrapovic.soundkit.community.ui.theme.LocalAkraTheme

/**
 * Home exhaust-tip hero matching the launcher mark:
 * carbon sleeve, titanium lip, hinged disc on a horizontal axis, amber heat when open.
 * Decorative — adjacent text is the accessibility truth.
 */
@Composable
fun ValveVisual(
    state: ValveState,
    modifier: Modifier = Modifier,
    commandInFlight: Boolean = false,
    successRippleTrigger: Int = 0,
) {
    val accent = LocalAkraTheme.current.accent
    val context = LocalContext.current
    val reduceMotion = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }

    val breathOpen = if (state == ValveState.Unknown && !reduceMotion) {
        val unknownBreath = rememberInfiniteTransition(label = "valveBreath")
        val value by unknownBreath.animateFloat(
            initialValue = 0.18f,
            targetValue = 0.38f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "valveBreathOpen",
        )
        value
    } else {
        0f
    }

    val targetOpen = when {
        state == ValveState.Unknown && !reduceMotion -> breathOpen
        else -> ValveGapMath.targetOpenAmount(state)
    }

    val openAmount by animateFloatAsState(
        targetValue = targetOpen,
        animationSpec = if (reduceMotion) {
            tween(durationMillis = 0)
        } else {
            spring(dampingRatio = 0.82f, stiffness = 320f)
        },
        label = "valveOpenAmount",
    )

    val busyAlpha = if (commandInFlight && !reduceMotion) {
        val busyTransition = rememberInfiniteTransition(label = "valveBusy")
        val value by busyTransition.animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 700, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "valveBusyAlpha",
        )
        value
    } else {
        1f
    }

    var lastRippleTrigger by remember { mutableIntStateOf(0) }
    val rippleAnimatable = remember { Animatable(0f) }
    LaunchedEffect(successRippleTrigger, reduceMotion) {
        if (successRippleTrigger > lastRippleTrigger) {
            lastRippleTrigger = successRippleTrigger
            if (!reduceMotion) {
                rippleAnimatable.snapTo(0f)
                rippleAnimatable.animateTo(1f, tween(durationMillis = 480))
                rippleAnimatable.snapTo(0f)
            }
        }
    }
    val rippleProgress = rippleAnimatable.value
    val contentAlpha = (if (state == ValveState.Unknown) 0.86f else 1f) * busyAlpha
    val discScaleY = ValveGapMath.discHeightScale(openAmount)
    val heat = ValveGapMath.heatAlpha(openAmount)

    Box(
        modifier = modifier.semantics { invisibleToUser() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val minSide = minOf(size.width, size.height)
            val outerR = minSide * 0.42f
            val center = Offset(size.width / 2f, size.height / 2f)
            val sleeveWidth = outerR * 0.16f
            val lipWidth = outerR * 0.07f
            val lipR = outerR - sleeveWidth * 0.55f
            val boreR = lipR - lipWidth * 0.85f
            val discR = boreR * 0.92f

            // Soft amber bloom behind the tip when open
            if (heat > 0.02f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.28f * heat * contentAlpha),
                            AkraColors.Amber.copy(alpha = 0.12f * heat * contentAlpha),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = outerR * 1.15f,
                    ),
                    radius = outerR * 1.15f,
                    center = center,
                )
            }

            // Carbon sleeve (filled annulus via outer disc + punch with bore color later)
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF2E2E32),
                        Color(0xFF121214),
                        Color(0xFF050505),
                    ),
                    start = Offset(center.x - outerR, center.y - outerR),
                    end = Offset(center.x + outerR, center.y + outerR),
                ),
                radius = outerR,
                center = center,
                alpha = contentAlpha,
            )
            // Carbon weave suggestion — diagonal hatch ticks
            val hatchStep = outerR * 0.11f
            var hx = center.x - outerR
            while (hx < center.x + outerR) {
                drawLine(
                    color = Color.White.copy(alpha = 0.035f * contentAlpha),
                    start = Offset(hx, center.y - outerR),
                    end = Offset(hx + outerR * 0.35f, center.y + outerR),
                    strokeWidth = 1.2f,
                )
                hx += hatchStep
            }
            // Cut sleeve inner with dark fill for lip seating
            drawCircle(
                color = Color(0xFF0A0A0C),
                radius = lipR + lipWidth * 0.15f,
                center = center,
                alpha = contentAlpha,
            )
            // Outer rim highlight
            drawCircle(
                color = Color.White.copy(alpha = 0.18f * contentAlpha),
                radius = outerR,
                center = center,
                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round),
            )

            // Titanium lip ring
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFE8E8EA),
                        Color(0xFF9A9A9E),
                        Color(0xFF4A4A4E),
                    ),
                    start = Offset(center.x - lipR, center.y - lipR),
                    end = Offset(center.x + lipR, center.y + lipR),
                ),
                radius = lipR,
                center = center,
                style = Stroke(width = lipWidth, cap = StrokeCap.Round),
                alpha = contentAlpha,
            )
            drawCircle(
                color = Color.White.copy(alpha = (0.22f + openAmount * 0.18f) * contentAlpha),
                radius = lipR + lipWidth * 0.15f,
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )

            // Bore interior
            drawCircle(
                brush = ExhaustTipPalette.boreInterior(center, boreR, boreR, closed = openAmount < 0.45f),
                radius = boreR,
                center = center,
                alpha = contentAlpha,
            )

            // Amber heat inside the bore (crescent intensifies as disc tilts)
            if (heat > 0.02f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.75f * heat * contentAlpha),
                            AkraColors.Amber.copy(alpha = 0.45f * heat * contentAlpha),
                            Color.Transparent,
                        ),
                        center = Offset(center.x, center.y + boreR * 0.18f),
                        radius = boreR * 0.95f,
                    ),
                    radius = boreR * 0.95f,
                    center = Offset(center.x, center.y + boreR * 0.12f),
                )
            }

            // Hinged disc — scales in Y to simulate rotation on horizontal axis
            val discH = discR * 2f * discScaleY
            val discTopLeft = Offset(center.x - discR, center.y - discH / 2f)
            drawOval(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF4A4A4E),
                        Color(0xFF1C1C1E),
                        Color(0xFF0E0E10),
                    ),
                    start = discTopLeft,
                    end = Offset(discTopLeft.x + discR * 2f, discTopLeft.y + discH),
                ),
                topLeft = discTopLeft,
                size = Size(discR * 2f, discH),
                alpha = contentAlpha,
            )
            drawOval(
                color = Color.White.copy(alpha = 0.22f * contentAlpha * (0.4f + discScaleY * 0.6f)),
                topLeft = discTopLeft,
                size = Size(discR * 2f, discH),
                style = Stroke(width = 1.2.dp.toPx()),
            )
            // Vertical brush lines on the disc face when mostly closed
            if (discScaleY > 0.35f) {
                val lines = 5
                for (i in 1 until lines) {
                    val x = center.x - discR + (discR * 2f * i / lines)
                    val halfH = discH * 0.38f
                    drawLine(
                        color = Color.White.copy(alpha = 0.06f * contentAlpha),
                        start = Offset(x, center.y - halfH),
                        end = Offset(x, center.y + halfH),
                        strokeWidth = 1f,
                    )
                }
            }

            // Pivot pins (left / right)
            val pinR = outerR * 0.045f
            val pinY = center.y
            listOf(center.x - boreR * 0.98f, center.x + boreR * 0.98f).forEach { px ->
                drawCircle(
                    color = Color(0xFFC8C8CC),
                    radius = pinR,
                    center = Offset(px, pinY),
                    alpha = contentAlpha,
                )
                drawCircle(
                    color = Color(0xFF2C2C2E),
                    radius = pinR * 0.45f,
                    center = Offset(px, pinY),
                    alpha = contentAlpha,
                )
            }

            if (commandInFlight && !reduceMotion) {
                drawCircle(
                    color = accent.copy(alpha = 0.35f * busyAlpha),
                    radius = outerR,
                    center = center,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                )
            }

            if (rippleProgress > 0f) {
                val rippleR = boreR * (0.9f + rippleProgress * 0.25f)
                drawCircle(
                    color = accent.copy(alpha = (1f - rippleProgress) * 0.35f),
                    radius = rippleR,
                    center = center,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
    }
}
