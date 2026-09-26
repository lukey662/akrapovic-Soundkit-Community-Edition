package com.akrapovic.soundkit.community.domain

/**
 * Decides whether this phone should auto-connect or auto-reconnect based on head-unit priority.
 * Primary means this phone is with the car: an open Car App session, car Bluetooth, or Android Auto projection.
 */
object ConnectionPriorityPolicy {
    fun isPrimaryController(inCar: Boolean): Boolean = inCar

    fun shouldAutoConnectOnLaunch(
        settings: SoundKitSettings,
        connectionState: ConnectionState,
        inCar: Boolean,
    ): Boolean {
        if (!settings.headUnitPriorityEnabled) {
            return RememberedDeviceConnector.shouldAutoConnect(connectionState, settings)
        }
        if (!isPrimaryController(inCar)) return false
        return RememberedDeviceConnector.shouldAutoConnect(connectionState, settings)
    }

    fun shouldAutoReconnect(
        settings: SoundKitSettings,
        inCar: Boolean,
        userRequestedControl: Boolean,
        yieldState: ConnectionYieldState,
    ): Boolean {
        if (!settings.autoReconnect) return false
        if (yieldState is ConnectionYieldState.Yielded) return false
        if (!settings.headUnitPriorityEnabled) return true
        if (isPrimaryController(inCar)) return true
        return userRequestedControl
    }

    fun shouldEnterYieldOnContention(
        settings: SoundKitSettings,
        inCar: Boolean,
    ): Boolean {
        if (!settings.headUnitPriorityEnabled) return false
        return !isPrimaryController(inCar)
    }

    /**
     * One connect when the phone comes back to the car after the short retry burst has already stopped.
     * A deliberate user disconnect is suppressed by the repository, not here.
     */
    fun shouldReconnectOnReturn(
        settings: SoundKitSettings,
        connectionState: ConnectionState,
        yieldState: ConnectionYieldState,
    ): Boolean {
        if (!settings.autoReconnect || !settings.connectInCar) return false
        if (settings.defaultReceiver == null) return false
        if (yieldState is ConnectionYieldState.Yielded) return false
        return when (connectionState) {
            ConnectionState.Disconnected,
            is ConnectionState.Away,
            is ConnectionState.Error,
            -> true
            ConnectionState.Scanning,
            is ConnectionState.Connecting,
            is ConnectionState.Connected,
            is ConnectionState.Reconnecting,
            -> false
        }
    }
}
