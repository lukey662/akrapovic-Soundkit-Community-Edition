package com.akrapovic.soundkit.community.domain

/**
 * Decides whether this phone should auto-connect or auto-reconnect based on head-unit priority.
 * Primary is inferred locally from an active Car App session on this device.
 */
object ConnectionPriorityPolicy {
    fun isPrimaryController(carSessionActive: Boolean): Boolean = carSessionActive

    fun shouldAutoConnectOnLaunch(
        settings: SoundKitSettings,
        connectionState: ConnectionState,
        carSessionActive: Boolean,
    ): Boolean {
        if (!settings.headUnitPriorityEnabled) {
            return RememberedDeviceConnector.shouldAutoConnect(connectionState, settings)
        }
        if (!isPrimaryController(carSessionActive)) return false
        return RememberedDeviceConnector.shouldAutoConnect(connectionState, settings)
    }

    fun shouldAutoReconnect(
        settings: SoundKitSettings,
        carSessionActive: Boolean,
        userRequestedControl: Boolean,
        yieldState: ConnectionYieldState,
    ): Boolean {
        if (!settings.autoReconnect) return false
        if (yieldState is ConnectionYieldState.Yielded) return false
        if (!settings.headUnitPriorityEnabled) return true
        if (isPrimaryController(carSessionActive)) return true
        return userRequestedControl
    }

    fun shouldEnterYieldOnContention(
        settings: SoundKitSettings,
        carSessionActive: Boolean,
    ): Boolean {
        if (!settings.headUnitPriorityEnabled) return false
        return !isPrimaryController(carSessionActive)
    }
}
