package com.akrapovic.soundkit.community.domain

import com.akrapovic.soundkit.community.test.testDevice
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionPriorityPolicyTest {
    private val device = testDevice()
    private val settings = SoundKitSettings(
        connectOnLaunch = true,
        headUnitPriorityEnabled = true,
        savedReceivers = listOf(
            SavedReceiver(device.address, device.name, isDefault = true),
        ),
    )

    @Test
    fun primaryControllerFollowsCarSession() {
        assertTrue(ConnectionPriorityPolicy.isPrimaryController(carSessionActive = true))
        assertFalse(ConnectionPriorityPolicy.isPrimaryController(carSessionActive = false))
    }

    @Test
    fun autoConnectOnLaunchRequiresCarSessionWhenHeadUnitPriorityEnabled() {
        assertTrue(
            ConnectionPriorityPolicy.shouldAutoConnectOnLaunch(
                settings = settings,
                connectionState = ConnectionState.Disconnected,
                carSessionActive = true,
            ),
        )
        assertFalse(
            ConnectionPriorityPolicy.shouldAutoConnectOnLaunch(
                settings = settings,
                connectionState = ConnectionState.Disconnected,
                carSessionActive = false,
            ),
        )
    }

    @Test
    fun autoConnectOnLaunchIgnoresCarSessionWhenHeadUnitPriorityDisabled() {
        val legacy = settings.copy(headUnitPriorityEnabled = false)
        assertTrue(
            ConnectionPriorityPolicy.shouldAutoConnectOnLaunch(
                settings = legacy,
                connectionState = ConnectionState.Disconnected,
                carSessionActive = false,
            ),
        )
    }

    @Test
    fun autoReconnectAllowedForPrimaryOrManualControl() {
        assertTrue(
            ConnectionPriorityPolicy.shouldAutoReconnect(
                settings = settings,
                carSessionActive = true,
                userRequestedControl = false,
                yieldState = ConnectionYieldState.None,
            ),
        )
        assertFalse(
            ConnectionPriorityPolicy.shouldAutoReconnect(
                settings = settings,
                carSessionActive = false,
                userRequestedControl = false,
                yieldState = ConnectionYieldState.None,
            ),
        )
        assertTrue(
            ConnectionPriorityPolicy.shouldAutoReconnect(
                settings = settings,
                carSessionActive = false,
                userRequestedControl = true,
                yieldState = ConnectionYieldState.None,
            ),
        )
        assertFalse(
            ConnectionPriorityPolicy.shouldAutoReconnect(
                settings = settings,
                carSessionActive = false,
                userRequestedControl = true,
                yieldState = ConnectionYieldState.Yielded(ConnectionYieldReason.HeadUnitMayBeActive),
            ),
        )
    }
}
