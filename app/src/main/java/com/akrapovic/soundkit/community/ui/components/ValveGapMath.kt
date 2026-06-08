package com.akrapovic.soundkit.community.ui.components

import com.akrapovic.soundkit.community.domain.ValveState

/** Gap fraction and butterfly plate mapping for [ValveVisual]. */
internal object ValveGapMath {
    private const val CLOSED_GAP = 0.04f
    private const val OPEN_GAP = 0.42f
    private const val MAX_PLATE_ROTATION = 22f

    fun targetGap(state: ValveState): Float = when (state) {
        ValveState.Open -> OPEN_GAP
        ValveState.Closed -> CLOSED_GAP
        ValveState.Unknown -> 0.18f
    }

    /** Normalized 0 (sealed) → 1 (fully open) from raw gap fraction. */
    fun plateSeparation(gapFraction: Float): Float {
        return ((gapFraction - CLOSED_GAP) / (OPEN_GAP - CLOSED_GAP)).coerceIn(0f, 1f)
    }

    /** Degrees each plate rotates from centre (top +, bottom -). */
    fun plateRotation(gapFraction: Float): Float = plateSeparation(gapFraction) * MAX_PLATE_ROTATION
}
