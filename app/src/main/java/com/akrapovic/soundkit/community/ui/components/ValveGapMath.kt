package com.akrapovic.soundkit.community.ui.components

import com.akrapovic.soundkit.community.domain.ValveState

/** Gap fraction (0 = closed, ~0.42 = open) and blade rotation helpers for [ValveVisual]. */
internal object ValveGapMath {
    fun targetGap(state: ValveState): Float = when (state) {
        ValveState.Open -> 0.42f
        ValveState.Closed -> 0.04f
        ValveState.Unknown -> 0.18f
    }

    /** Each blade rotates this many degrees at full open gap. */
    fun bladeRotationDegrees(gapFraction: Float): Float = gapFraction * 38f

    fun gapDegrees(gapFraction: Float): Float = (gapFraction * 180f).coerceIn(0f, 170f)
}
