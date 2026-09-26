package com.akrapovic.soundkit.community.car

import kotlinx.coroutines.flow.StateFlow

/**
 * Whether this phone is with the car, independent of the Sound Kit car screen being open.
 * Bluetooth is the paired Audi handsfree link. Projection is Android Auto, even if this app is not on the launcher.
 */
data class CarPresence(
    val bluetoothConnected: Boolean = false,
    val projectionConnected: Boolean = false,
) {
    val inCar: Boolean get() = bluetoothConnected || projectionConnected

    fun orSession(carSessionActive: Boolean): Boolean = inCar || carSessionActive
}

interface CarPresenceSource {
    val presence: StateFlow<CarPresence>
}
