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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import com.akrapovic.soundkit.community.domain.ValveState
import com.akrapovic.soundkit.community.ui.theme.LocalAkraTheme

/**
 * Home exhaust-tip hero matching the launcher mark:
 * carbon sleeve, titanium lip, butterfly disc on a steel shaft, brand-colored heat when open.
 * Decorative — adjacent text is the accessibility truth.
 */
@Composable
fun ValveVisual(
    state: ValveState,
    modifier: Modifier = Modifier,
    commandInFlight: Boolean = false,
    successRippleTrigger: Int = 0,
) {
    val theme = LocalAkraTheme.current
    val palette = remember(theme) { ValveArtPalette.from(theme) }
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
    val busyRing = if (commandInFlight && !reduceMotion) theme.glow.copy(alpha = 0.35f * busyAlpha) else null

    Box(
        modifier = modifier.semantics { invisibleToUser() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            with(ValveArt) {
                drawValve(
                    openAmount = openAmount,
                    palette = palette,
                    contentAlpha = contentAlpha,
                    busyRing = busyRing,
                    ripple = rippleProgress,
                )
            }
        }
    }
}
