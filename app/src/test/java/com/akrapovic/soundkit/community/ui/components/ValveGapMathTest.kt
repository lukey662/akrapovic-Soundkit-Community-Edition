package com.akrapovic.soundkit.community.ui.components

import com.akrapovic.soundkit.community.domain.ValveState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValveGapMathTest {
    @Test
    fun targetOpenAmountMatchesValveStates() {
        assertEquals(1f, ValveGapMath.targetOpenAmount(ValveState.Open), 0.001f)
        assertEquals(0f, ValveGapMath.targetOpenAmount(ValveState.Closed), 0.001f)
        assertEquals(0.28f, ValveGapMath.targetOpenAmount(ValveState.Unknown), 0.001f)
    }

    @Test
    fun discHeightScaleIsFullWhenClosedAndThinWhenOpen() {
        assertEquals(1f, ValveGapMath.discHeightScale(0f), 0.02f)
        assertTrue(ValveGapMath.discHeightScale(1f) < 0.1f)
        assertTrue(ValveGapMath.discHeightScale(0.5f) in 0.65f..0.8f)
    }

    @Test
    fun heatAlphaTracksOpenAmount() {
        assertEquals(0f, ValveGapMath.heatAlpha(0f), 0.001f)
        assertEquals(0.92f, ValveGapMath.heatAlpha(1f), 0.001f)
    }
}
