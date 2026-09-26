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
        assertTrue(ConnectionPriorityPolicy.isPrimaryController(inCar = true))
        assertFalse(ConnectionPriorityPolicy.isPrimaryController(inCar = false))
    }

    @Test
    fun autoConnectOnLaunchRequiresCarSessionWhenHeadUnitPriorityEnabled() {
        assertTrue(
            ConnectionPriorityPolicy.shouldAutoConnectOnLaunch(
                settings = settings,
                connectionState = ConnectionState.Disconnected,
                inCar = true,
            ),
        )
        assertFalse(
            ConnectionPriorityPolicy.shouldAutoConnectOnLaunch(
                settings = settings,
                connectionState = ConnectionState.Disconnected,
                inCar = false,
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
                inCar = false,
            ),
        )
    }

    @Test
    fun autoReconnectAllowedForPrimaryOrManualControl() {
        assertTrue(
            ConnectionPriorityPolicy.shouldAutoReconnect(
                settings = settings,
                inCar = true,
                userRequestedControl = false,
                yieldState = ConnectionYieldState.None,
            ),
        )
        assertFalse(
            ConnectionPriorityPolicy.shouldAutoReconnect(
                settings = settings,
                inCar = false,
                userRequestedControl = false,
                yieldState = ConnectionYieldState.None,
            ),
        )
        assertTrue(
            ConnectionPriorityPolicy.shouldAutoReconnect(
                settings = settings,
                inCar = false,
                userRequestedControl = true,
                yieldState = ConnectionYieldState.None,
            ),
        )
        assertFalse(
            ConnectionPriorityPolicy.shouldAutoReconnect(
                settings = settings,
                inCar = false,
                userRequestedControl = true,
                yieldState = ConnectionYieldState.Yielded(ConnectionYieldReason.HeadUnitMayBeActive),
            ),
        )
    }
}
