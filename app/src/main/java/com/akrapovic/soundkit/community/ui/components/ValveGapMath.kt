package com.akrapovic.soundkit.community.ui.components

import com.akrapovic.soundkit.community.domain.ValveState

/**
 * Maps valve state to a 0..1 open amount for the hinged exhaust-tip flap.
 * 0 = sealed (face-on disc), 1 = fully open (edge-on disc + full heat glow).
 */
internal object ValveGapMath {
    fun targetOpenAmount(state: ValveState): Float = when (state) {
        ValveState.Open -> 1f
        ValveState.Closed -> 0f
        ValveState.Unknown -> 0.28f
    }

    /** Visible disc height scale from open amount (cos of tilt). */
    fun discHeightScale(openAmount: Float): Float {
        val clamped = openAmount.coerceIn(0f, 1f)
        // Near-edge-on at full open, never quite zero so the hinge still reads.
        return (kotlin.math.cos((clamped * Math.PI / 2.0).toFloat()) * 0.96f + 0.04f)
            .coerceIn(0.04f, 1f)
    }

    fun heatAlpha(openAmount: Float): Float = (openAmount.coerceIn(0f, 1f) * 0.92f)
}
