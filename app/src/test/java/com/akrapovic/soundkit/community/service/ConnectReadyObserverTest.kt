package com.akrapovic.soundkit.community.service

import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.ValveState
import com.akrapovic.soundkit.community.test.testDevice
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectReadyObserverTest {
    private val device = testDevice()

    @Test
    fun firstConnectReadyTransitionFiresOnce() {
        val transition = ConnectReadyObserver.evaluate(
            connection = ConnectionState.Connected(device),
            valve = ValveState.Closed,
            notReady = null,
            wasConnectReady = false,
        )

        assertTrue(transition.isConnectReady)
        assertTrue(transition.becameReady)
        assertFalse(transition.disconnected)
    }

    @Test
    fun valveStateChangeWhileConnectedDoesNotRefire() {
        val transition = ConnectReadyObserver.evaluate(
            connection = ConnectionState.Connected(device),
            valve = ValveState.Open,
            notReady = null,
            wasConnectReady = true,
        )

        assertTrue(transition.isConnectReady)
        assertFalse(transition.becameReady)
        assertFalse(transition.disconnected)
    }

    @Test
    fun disconnectResetsSession() {
        val transition = ConnectReadyObserver.evaluate(
            connection = ConnectionState.Disconnected,
            valve = ValveState.Open,
            notReady = null,
            wasConnectReady = true,
        )

        assertFalse(transition.isConnectReady)
        assertFalse(transition.becameReady)
        assertTrue(transition.disconnected)
    }

    @Test
    fun unknownValveDoesNotBecomeReady() {
        val transition = ConnectReadyObserver.evaluate(
            connection = ConnectionState.Connected(device),
            valve = ValveState.Unknown,
            notReady = null,
            wasConnectReady = false,
        )

        assertFalse(transition.isConnectReady)
        assertFalse(transition.becameReady)
        assertFalse(transition.disconnected)
    }

    @Test
    fun receiverNotReadyBlocksConnectReady() {
        val transition = ConnectReadyObserver.evaluate(
            connection = ConnectionState.Connected(device),
            valve = ValveState.Closed,
            notReady = "Receiver isn't ready",
            wasConnectReady = false,
        )

        assertFalse(transition.isConnectReady)
        assertFalse(transition.becameReady)
        assertFalse(transition.disconnected)
    }
}
