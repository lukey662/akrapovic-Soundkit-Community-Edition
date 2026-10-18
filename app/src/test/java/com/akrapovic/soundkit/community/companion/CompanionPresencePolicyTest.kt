package com.akrapovic.soundkit.community.companion

import com.akrapovic.soundkit.community.domain.AwayReason
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.SavedReceiver
import com.akrapovic.soundkit.community.domain.SoundKitSettings
import com.akrapovic.soundkit.community.test.testDevice
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionPresencePolicyTest {
    private val address = "00:11:22:33:44:55"
    private val ready = SoundKitSettings(
        savedReceivers = listOf(SavedReceiver(address, "RS3", isDefault = true)),
        onboardingCompletedAt = 1L,
    )

    @Test
    fun connectsTheDefaultReceiverWhenIdle() {
        assertTrue(CompanionPresencePolicy.shouldConnect(address, ready, ConnectionState.Disconnected))
        assertTrue(CompanionPresencePolicy.shouldConnect(address.lowercase(), ready, ConnectionState.Away(1L, AwayReason.LeftCar)))
        assertTrue(CompanionPresencePolicy.shouldConnect(address, ready, ConnectionState.Error("x", recoverable = true)))
    }

    @Test
    fun ignoresOtherDevicesBusyLinksAndIncompleteSetup() {
        assertFalse(CompanionPresencePolicy.shouldConnect("AA:BB:CC:DD:EE:FF", ready, ConnectionState.Disconnected))
        assertFalse(CompanionPresencePolicy.shouldConnect(address, ready, ConnectionState.Connected(testDevice())))
        assertFalse(CompanionPresencePolicy.shouldConnect(address, ready, ConnectionState.Connecting(testDevice())))
        assertFalse(CompanionPresencePolicy.shouldConnect(address, ready.copy(onboardingCompletedAt = 0L), ConnectionState.Disconnected))
        assertFalse(
            CompanionPresencePolicy.shouldConnect(
                address,
                ready.copy(connectInCar = false, autoReconnect = false),
                ConnectionState.Disconnected,
            ),
        )
    }
}
