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
    fun plateSeparationScalesFromClosedToOpen() {
        assertEquals(0f, ValveGapMath.plateSeparation(0.04f), 0.001f)
        assertEquals(1f, ValveGapMath.plateSeparation(0.42f), 0.001f)
        assertEquals(0.5f, ValveGapMath.plateSeparation(0.23f), 0.02f)
    }

    @Test
    fun plateRotationMaxAtFullOpen() {
        assertEquals(0f, ValveGapMath.plateRotation(0.04f), 0.001f)
        assertEquals(22f, ValveGapMath.plateRotation(0.42f), 0.001f)
    }
}
