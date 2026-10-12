package com.akrapovic.soundkit.community.service

import com.akrapovic.soundkit.community.domain.AwayReason
import com.akrapovic.soundkit.community.domain.AwaySessionPolicy
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.test.testDevice
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionServicePresenceTest {
    private val now = 1_700_000_000_000L

    @Test
    fun carWakeReleasesAStaleDisconnectedSessionOutsideTheCar() {
        assertTrue(
            ConnectionServicePresence.shouldReleaseWhenIdle(
                carWake = true,
                connectionState = ConnectionState.Disconnected,
                inCar = false,
                scanning = false,
                nowMillis = now,
                startupSettled = true,
            ),
        )
    }

    @Test
    fun unsettledStartupDoesNotRelease() {
        assertFalse(
            ConnectionServicePresence.shouldReleaseWhenIdle(
                carWake = true,
                connectionState = ConnectionState.Disconnected,
                inCar = false,
                scanning = false,
                nowMillis = now,
                startupSettled = false,
            ),
        )
    }

    @Test
    fun freshAwayAndLiveSessionsStayUp() {
        assertFalse(
            ConnectionServicePresence.shouldReleaseWhenIdle(
                carWake = true,
                connectionState = ConnectionState.Away(now, AwayReason.LeftCar),
                inCar = false,
                scanning = false,
                nowMillis = now,
                startupSettled = true,
            ),
        )
        assertFalse(
            ConnectionServicePresence.shouldReleaseWhenIdle(
                carWake = true,
                connectionState = ConnectionState.Connected(testDevice()),
                inCar = false,
                scanning = false,
                nowMillis = now,
                startupSettled = true,
            ),
        )
        assertFalse(
            ConnectionServicePresence.shouldReleaseWhenIdle(
                carWake = false,
                connectionState = ConnectionState.Disconnected,
                inCar = false,
                scanning = false,
                nowMillis = now,
                startupSettled = true,
            ),
        )
    }

    @Test
    fun staleAwayIsReleasedWhenThePhoneIsNotInTheCar() {
        val stale = now - AwaySessionPolicy.MAX_AGE_MS - 1
        assertTrue(
            ConnectionServicePresence.shouldReleaseWhenIdle(
                carWake = true,
                connectionState = ConnectionState.Away(stale, AwayReason.LeftCar),
                inCar = false,
                scanning = false,
                nowMillis = now,
                startupSettled = true,
            ),
        )
    }
}
