package com.akrapovic.soundkit.community.ui.components

import android.provider.Settings
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.akrapovic.soundkit.community.domain.ValveState
import com.akrapovic.soundkit.community.ui.theme.AkraColors
import com.akrapovic.soundkit.community.ui.theme.LocalAkraTheme
import kotlin.math.min

/**
 * Wide Akrapovic-style exhaust tip cross-section: carbon sleeve, titanium lip,
 * deep bore, and butterfly valve plates. Decorative — adjacent text is a11y truth.
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

    val unknownBreath = rememberInfiniteTransition(label = "valveUnknownBreath")
    val breathGap by unknownBreath.animateFloat(
        initialValue = 0.14f,
        targetValue = 0.20f,
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

    val animatedGap by animateFloatAsState(
        targetValue = baseGap,
        animationSpec = if (reduceMotion) {
            tween(durationMillis = 0)
        } else {
            spring(dampingRatio = 0.78f, stiffness = 420f)
        },
        label = "valveGap",
    )

    val infinite = rememberInfiniteTransition(label = "valvePulse")
    val pulseAlpha by infinite.animateFloat(
        initialValue = if (state == ValveState.Open && !reduceMotion) 0.14f else 0f,
        targetValue = if (state == ValveState.Open && !reduceMotion) 0.32f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "valvePulseAlpha",
    )

    val busyAlpha by infinite.animateFloat(
        initialValue = if (commandInFlight && !reduceMotion) 0.55f else 1f,
        targetValue = if (commandInFlight && !reduceMotion) 1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "valveBusyAlpha",
    )

    val heatAccent by animateColorAsState(
        targetValue = if (state == ValveState.Open) accent else Color.Transparent,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 380),
        label = "valveHeat",
    )

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

    val contentAlpha = when (state) {
        ValveState.Unknown -> 0.78f
        else -> 1f
    } * busyAlpha

    val plateRotation = ValveGapMath.plateRotation(animatedGap)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val padX = size.width * 0.04f
            val padY = size.height * 0.11f
            val sleeveW = size.width - padX * 2f
            val sleeveH = size.height - padY * 2f
            val sleeveTopLeft = Offset(padX, padY)
            val sleeveSize = Size(sleeveW, sleeveH)
            val center = Offset(size.width / 2f, size.height / 2f)
            val sleeveRx = sleeveW / 2f
            val sleeveRy = sleeveH / 2f

            val lipInsetX = sleeveW * 0.08f
            val lipInsetY = sleeveH * 0.10f
            val boreRx = sleeveRx - lipInsetX
            val boreRy = sleeveRy - lipInsetY

            drawExhaustTipShadow(center, sleeveRx, sleeveRy, contentAlpha)
            drawCarbonSleeve(sleeveTopLeft, sleeveSize, contentAlpha)
            drawTitaniumLip(sleeveTopLeft, sleeveSize, lipInsetX, lipInsetY, contentAlpha)

            val borePath = ovalPath(center, boreRx, boreRy)
            clipPath(borePath) {
                drawOval(
                    brush = ExhaustTipPalette.boreInterior(
                        center = center,
                        radiusX = boreRx,
                        radiusY = boreRy,
                        closed = state == ValveState.Closed,
                    ),
                    topLeft = Offset(center.x - boreRx, center.y - boreRy),
                    size = Size(boreRx * 2f, boreRy * 2f),
                    alpha = contentAlpha,
                )

                if (state == ValveState.Open && heatAccent != Color.Transparent && pulseAlpha > 0f) {
                    drawOval(
                        brush = ExhaustTipPalette.heatGlow(
                            center = center,
                            radiusX = boreRx,
                            radiusY = boreRy,
                            accent = heatAccent,
                            alpha = pulseAlpha * contentAlpha,
                        ),
                        topLeft = Offset(center.x - boreRx, center.y - boreRy),
                        size = Size(boreRx * 2f, boreRy * 2f),
                    )
                }

                drawButterflyPlates(
                    center = center,
                    boreRx = boreRx,
                    boreRy = boreRy,
                    rotation = plateRotation,
                    alpha = contentAlpha,
                )
            }

            if (commandInFlight && !reduceMotion) {
                drawOval(
                    color = accent.copy(alpha = 0.35f * busyAlpha),
                    topLeft = Offset(center.x - sleeveRx, center.y - sleeveRy),
                    size = Size(sleeveRx * 2f, sleeveRy * 2f),
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

private fun DrawScope.drawExhaustTipShadow(
    center: Offset,
    radiusX: Float,
    radiusY: Float,
    alpha: Float,
) {
    drawOval(
        color = ExhaustTipPalette.shadowColor.copy(alpha = ExhaustTipPalette.shadowColor.alpha * alpha),
        topLeft = Offset(center.x - radiusX * 1.02f, center.y - radiusY * 0.92f),
        size = Size(radiusX * 2.04f, radiusY * 2.08f),
    )
}

private fun DrawScope.drawCarbonSleeve(
    topLeft: Offset,
    size: Size,
    alpha: Float,
) {
    drawOval(
        brush = ExhaustTipPalette.carbonSleeve(topLeft, size),
        topLeft = topLeft,
        size = size,
        alpha = alpha,
    )
    val hatch = ExhaustTipPalette.hatchColor.copy(alpha = ExhaustTipPalette.hatchColor.alpha * alpha)
    val step = 14.dp.toPx()
    var x = topLeft.x - size.height
    while (x < topLeft.x + size.width + size.height) {
        drawLine(
            color = hatch,
            start = Offset(x, topLeft.y),
            end = Offset(x + size.height, topLeft.y + size.height),
            strokeWidth = 1.dp.toPx(),
        )
        x += step
    }
}

private fun DrawScope.drawTitaniumLip(
    sleeveTopLeft: Offset,
    sleeveSize: Size,
    lipInsetX: Float,
    lipInsetY: Float,
    alpha: Float,
) {
    val lipTopLeft = Offset(
        sleeveTopLeft.x + lipInsetX * 0.55f,
        sleeveTopLeft.y + lipInsetY * 0.55f,
    )
    val lipSize = Size(
        sleeveSize.width - lipInsetX * 1.1f,
        sleeveSize.height - lipInsetY * 1.1f,
    )
    drawOval(
        brush = ExhaustTipPalette.titaniumLip(lipTopLeft, lipSize),
        topLeft = lipTopLeft,
        size = lipSize,
        alpha = alpha,
    )
    drawOval(
        color = ExhaustTipPalette.specularColor.copy(alpha = ExhaustTipPalette.specularColor.alpha * alpha),
        topLeft = lipTopLeft,
        size = lipSize,
        style = Stroke(width = 2.dp.toPx()),
    )
    drawArc(
        color = AkraColors.Pearl.copy(alpha = 0.22f * alpha),
        startAngle = 210f,
        sweepAngle = 80f,
        useCenter = false,
        topLeft = lipTopLeft,
        size = lipSize,
        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
    )
}

private fun DrawScope.drawButterflyPlates(
    center: Offset,
    boreRx: Float,
    boreRy: Float,
    rotation: Float,
    alpha: Float,
) {
    val plateHeight = boreRy * 0.52f
    val plateWidth = boreRx * 1.85f
    val corner = min(plateHeight, boreRx) * 0.18f

    val topPlate = platePath(
        left = center.x - plateWidth / 2f,
        top = center.y - boreRy,
        width = plateWidth,
        height = plateHeight,
        corner = corner,
    )
    val bottomPlate = platePath(
        left = center.x - plateWidth / 2f,
        top = center.y + boreRy - plateHeight,
        width = plateWidth,
        height = plateHeight,
        corner = corner,
    )

    rotate(degrees = -rotation, pivot = center) {
        drawPath(topPlate, brush = ExhaustTipPalette.plateColor, alpha = alpha)
    }
    rotate(degrees = rotation, pivot = center) {
        drawPath(bottomPlate, brush = ExhaustTipPalette.plateColor, alpha = alpha)
    }

    drawLine(
        color = AkraColors.Graphite.copy(alpha = 0.8f * alpha),
        start = Offset(center.x - boreRx * 0.75f, center.y),
        end = Offset(center.x + boreRx * 0.75f, center.y),
        strokeWidth = 1.5.dp.toPx(),
        cap = StrokeCap.Round,
    )
}

private fun platePath(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    corner: Float,
): Path {
    return Path().apply {
        addRoundRect(
            RoundRect(
                left = left,
                top = top,
                right = left + width,
                bottom = top + height,
                cornerRadius = CornerRadius(corner, corner),
            ),
        )
    }
}

private fun ovalPath(center: Offset, radiusX: Float, radiusY: Float): Path {
    return Path().apply {
        addOval(Rect(center.x - radiusX, center.y - radiusY, center.x + radiusX, center.y + radiusY))
    }
}
