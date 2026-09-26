package com.akrapovic.soundkit.community.domain

import android.bluetooth.BluetoothClass

data class CarBluetoothCandidate(
    val address: String,
    val name: String,
    val likelyCar: Boolean,
)

/**
 * Suggests paired devices that are usually a car head unit.
 * Handsfree includes some headsets, so the user still confirms the device.
 */
object CarBluetoothClassifier {
    fun isLikelyCar(deviceClass: Int?): Boolean {
        if (deviceClass == null) return false
        return deviceClass == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO ||
            deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE
    }
}
