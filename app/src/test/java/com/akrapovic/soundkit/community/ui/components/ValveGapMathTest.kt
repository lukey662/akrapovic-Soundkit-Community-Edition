package com.akrapovic.soundkit.community.ui.components

import com.akrapovic.soundkit.community.domain.ValveState
import org.junit.Assert.assertEquals
import org.junit.Test

class ValveGapMathTest {
    @Test
    fun targetGapMatchesValveStates() {
        assertEquals(0.42f, ValveGapMath.targetGap(ValveState.Open), 0.001f)
        assertEquals(0.04f, ValveGapMath.targetGap(ValveState.Closed), 0.001f)
        assertEquals(0.18f, ValveGapMath.targetGap(ValveState.Unknown), 0.001f)
    }

    @Test
    fun bladeRotationScalesWithGap() {
        assertEquals(0f, ValveGapMath.bladeRotationDegrees(0f), 0.001f)
        assertEquals(15.96f, ValveGapMath.bladeRotationDegrees(0.42f), 0.01f)
    }
}
