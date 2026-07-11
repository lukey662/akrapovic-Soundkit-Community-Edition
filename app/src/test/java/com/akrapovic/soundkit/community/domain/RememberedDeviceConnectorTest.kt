package com.akrapovic.soundkit.community.domain

import com.akrapovic.soundkit.community.test.testDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RememberedDeviceConnectorTest {
    @Test
    fun defaultDeviceReturnsNullWhenNoDefault() {
        assertNull(RememberedDeviceConnector.defaultDevice(SoundKitSettings()))
    }

    @Test
    fun defaultDeviceBuildsFromSavedReceiver() {
        val settings = SoundKitSettings(
            savedReceivers = listOf(
                SavedReceiver(
                    address = "AA:BB:CC:DD:EE:FF",
                    name = "My Kit",
                    nickname = "Garage",
                    isDefault = true,
                ),
            ),
        )
        val device = RememberedDeviceConnector.defaultDevice(settings)

        assertEquals("Garage", device?.name)
        assertEquals("AA:BB:CC:DD:EE:FF", device?.address)
    }

    @Test
    fun shouldAutoConnectWhenDisconnectedWithDefaultAndConnectOnLaunch() {
        val settings = SoundKitSettings(
            connectOnLaunch = true,
            savedReceivers = listOf(
                SavedReceiver(address = "00:11:22:33:44:55", name = "Kit", isDefault = true),
            ),
        )

        assertTrue(
            RememberedDeviceConnector.shouldAutoConnect(ConnectionState.Disconnected, settings),
        )
        assertTrue(
            RememberedDeviceConnector.shouldAutoConnect(
                ConnectionState.Error("timeout", recoverable = true),
                settings,
            ),
        )
    }

    @Test
    fun shouldNotAutoConnectWhenConnectOnLaunchDisabled() {
        val settings = SoundKitSettings(
            connectOnLaunch = false,
            savedReceivers = listOf(
                SavedReceiver(address = "00:11:22:33:44:55", name = "Kit", isDefault = true),
            ),
        )

        assertFalse(
            RememberedDeviceConnector.shouldAutoConnect(ConnectionState.Disconnected, settings),
        )
    }

    @Test
    fun carConnectionPolicyIsIndependentFromPhoneLaunchPolicy() {
        val settings = SoundKitSettings(
            connectOnLaunch = false,
            connectInCar = true,
            savedReceivers = listOf(
                SavedReceiver(address = "00:11:22:33:44:55", name = "Kit", isDefault = true),
            ),
        )

        assertFalse(RememberedDeviceConnector.shouldAutoConnect(ConnectionState.Disconnected, settings))
        assertTrue(RememberedDeviceConnector.shouldConnectInCar(ConnectionState.Disconnected, settings))
    }

    @Test
    fun carConnectionPolicyHonorsConnectInCar() {
        val settings = SoundKitSettings(
            connectOnLaunch = true,
            connectInCar = false,
            savedReceivers = listOf(
                SavedReceiver(address = "00:11:22:33:44:55", name = "Kit", isDefault = true),
            ),
        )

        assertFalse(RememberedDeviceConnector.shouldConnectInCar(ConnectionState.Disconnected, settings))
    }

    @Test
    fun carConnectionPolicyRequiresAnEligibleConnectionStateAndDefaultReceiver() {
        val settings = SoundKitSettings(
            connectInCar = true,
            savedReceivers = listOf(
                SavedReceiver(address = "00:11:22:33:44:55", name = "Kit", isDefault = true),
            ),
        )
        val activeDevice = testDevice()

        assertTrue(
            RememberedDeviceConnector.shouldConnectInCar(
                ConnectionState.Error("timed out", recoverable = true),
                settings,
            ),
        )
        assertFalse(
            RememberedDeviceConnector.shouldConnectInCar(ConnectionState.Connected(activeDevice), settings),
        )
        assertFalse(
            RememberedDeviceConnector.shouldConnectInCar(
                ConnectionState.Disconnected,
                settings.copy(savedReceivers = emptyList()),
            ),
        )
    }

    @Test
    fun shouldNotAutoConnectWhenAlreadyActive() {
        val settings = SoundKitSettings(
            savedReceivers = listOf(
                SavedReceiver(address = "00:11:22:33:44:55", name = "Kit", isDefault = true),
            ),
        )
        val device = testDevice()

        assertFalse(
            RememberedDeviceConnector.shouldAutoConnect(ConnectionState.Connected(device), settings),
        )
        assertFalse(
            RememberedDeviceConnector.shouldAutoConnect(ConnectionState.Connecting(device), settings),
        )
        assertFalse(
            RememberedDeviceConnector.shouldAutoConnect(
                ConnectionState.Reconnecting(device, attempt = 1, nextDelayMs = 1000L),
                settings,
            ),
        )
    }
}
