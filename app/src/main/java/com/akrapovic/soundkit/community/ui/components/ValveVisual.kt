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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.akrapovic.soundkit.community.domain.ValveState
import com.akrapovic.soundkit.community.ui.theme.LocalAkraTheme

/**
 * Minimal Home valve hero: titanium ring, dark bore, flat disc at 80% fill when closed.
 * Open = disc clears, ring brightens. Decorative — adjacent text is the a11y truth.
 */
@Composable
fun ValveVisual(
    state: ValveState,
    modifier: Modifier = Modifier,
    commandInFlight: Boolean = false,
    successRippleTrigger: Int = 0,
) {
    val accent = LocalAkraTheme.current.accent
    val rimShadow = LocalAkraTheme.current.highlight
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
            initialValue = 0.72f,
            targetValue = 0.88f,
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
        state == ValveState.Open -> 1f
        else -> 0f
    }

    val openAmount by animateFloatAsState(
        targetValue = targetOpen,
        animationSpec = if (reduceMotion) {
            tween(durationMillis = 0)
        } else {
            spring(dampingRatio = 0.82f, stiffness = 340f)
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
    val contentAlpha = (if (state == ValveState.Unknown) 0.78f else 1f) * busyAlpha

    Box(
        modifier = modifier.semantics { invisibleToUser() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val padX = size.width * 0.04f
            val padY = size.height * 0.11f
            val outerRx = (size.width - padX * 2f) / 2f
            val outerRy = (size.height - padY * 2f) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            val boreRx = outerRx * 0.89f
            val boreRy = outerRy * 0.89f
            val discRx = boreRx * 0.80f
            val discRy = boreRy * 0.80f
            val lipRx = outerRx - 6.dp.toPx().coerceAtMost(outerRx * 0.06f)
            val lipRy = outerRy - 6.dp.toPx().coerceAtMost(outerRy * 0.06f)

            val ringAlpha = (0.45f + openAmount * 0.50f) * contentAlpha
            val discScale = 1f - openAmount

            // Carbon outer rim
            drawOval(
                color = rimShadow.copy(alpha = 0.28f * contentAlpha),
                topLeft = Offset(center.x - outerRx * 1.02f, center.y - outerRy * 1.02f),
                size = Size(outerRx * 2.04f, outerRy * 2.04f),
            )
            drawOval(
                color = ExhaustTipPalette.carbonEdge.copy(alpha = contentAlpha),
                topLeft = Offset(center.x - outerRx, center.y - outerRy),
                size = Size(outerRx * 2f, outerRy * 2f),
                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round),
            )

            // Titanium lip — brighter when open
            drawOval(
                color = ExhaustTipPalette.titaniumStroke.copy(alpha = ringAlpha),
                topLeft = Offset(center.x - lipRx, center.y - lipRy),
                size = Size(lipRx * 2f, lipRy * 2f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )

            // Bore
            drawOval(
                brush = ExhaustTipPalette.boreInterior(center, boreRx, boreRy, closed = openAmount < 0.5f),
                topLeft = Offset(center.x - boreRx, center.y - boreRy),
                size = Size(boreRx * 2f, boreRy * 2f),
                alpha = contentAlpha,
            )

            // Flat disc — 80% fill, shrinks away when open
            if (discScale > 0.01f) {
                drawOval(
                    brush = ExhaustTipPalette.plateColor,
                    topLeft = Offset(
                        center.x - discRx * discScale,
                        center.y - discRy * discScale,
                    ),
                    size = Size(discRx * 2f * discScale, discRy * 2f * discScale),
                    alpha = contentAlpha,
                )
                drawOval(
                    color = ExhaustTipPalette.plateEdge.copy(alpha = 0.55f * contentAlpha),
                    topLeft = Offset(
                        center.x - discRx * discScale,
                        center.y - discRy * discScale,
                    ),
                    size = Size(discRx * 2f * discScale, discRy * 2f * discScale),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }

            if (commandInFlight && !reduceMotion) {
                drawOval(
                    color = accent.copy(alpha = 0.35f * busyAlpha),
                    topLeft = Offset(center.x - outerRx, center.y - outerRy),
                    size = Size(outerRx * 2f, outerRy * 2f),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                )
            }

            if (rippleProgress > 0f) {
                val rippleRx = boreRx * (0.85f + rippleProgress * 0.2f)
                val rippleRy = boreRy * (0.85f + rippleProgress * 0.2f)
                drawOval(
                    color = accent.copy(alpha = (1f - rippleProgress) * 0.35f),
                    topLeft = Offset(center.x - rippleRx, center.y - rippleRy),
                    size = Size(rippleRx * 2f, rippleRy * 2f),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
    }
}
