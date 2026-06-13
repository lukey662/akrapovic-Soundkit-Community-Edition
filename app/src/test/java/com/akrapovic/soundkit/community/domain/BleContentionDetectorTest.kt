package com.akrapovic.soundkit.community.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BleContentionDetectorTest {
    private var now = 0L

    private fun detector() = BleContentionDetector(clock = { now })

    @Test
    fun quickDropAfterBriefConnectionSignalsContention() {
        val detector = detector()
        detector.onConnected()
        now += 2_000
        assertEquals(
            BleContentionDetector.ContentionSignal.QuickDrop,
            detector.onDisconnected(userInitiated = false),
        )
    }

    @Test
    fun userInitiatedDisconnectDoesNotSignalContention() {
        val detector = detector()
        detector.onConnected()
        now += 2_000
        assertNull(detector.onDisconnected(userInitiated = true))
    }

    @Test
    fun connectStormSignalsAfterRepeatedFailures() {
        val detector = detector()
        assertNull(detector.onConnectFailed())
        now += 5_000
        assertNull(detector.onConnectFailed())
        now += 5_000
        assertEquals(
            BleContentionDetector.ContentionSignal.ConnectStorm,
            detector.onConnectFailed(),
        )
    }

    @Test
    fun resetClearsPendingEvents() {
        val detector = detector()
        detector.onConnectFailed()
        now += 5_000
        detector.onConnectFailed()
        detector.reset()
        now += 5_000
        assertNull(detector.onConnectFailed())
    }
}
