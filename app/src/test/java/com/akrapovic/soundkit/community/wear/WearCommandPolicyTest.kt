package com.akrapovic.soundkit.community.wear

import com.akrapovic.soundkit.community.domain.AwayReason
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.test.testDevice
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearCommandPolicyTest {
    @Test
    fun onlyALiveLinkAcceptsWatchCommands() {
        assertTrue(WearCommandPolicy.allow(ConnectionState.Connected(testDevice())))
        assertFalse(WearCommandPolicy.allow(ConnectionState.Disconnected))
        assertFalse(WearCommandPolicy.allow(ConnectionState.Connecting(testDevice())))
        assertFalse(WearCommandPolicy.allow(ConnectionState.Away(1L, AwayReason.LeftCar)))
        assertFalse(WearCommandPolicy.allow(ConnectionState.Error("x", recoverable = true)))
    }
}
