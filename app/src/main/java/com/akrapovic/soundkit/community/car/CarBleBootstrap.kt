package com.akrapovic.soundkit.community.car

import android.content.Context
import com.akrapovic.soundkit.community.data.BleRepository
import com.akrapovic.soundkit.community.domain.RememberedDeviceConnector
import com.akrapovic.soundkit.community.domain.SoundKitSettings
import com.akrapovic.soundkit.community.service.BleConnectionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Ensures BLE foreground service is running and reconnects to the default saved receiver when the
 * Android Auto / car session opens, if the separate car connection preference permits it.
 */
object CarBleBootstrap {
    /**
     * @return false when the foreground service could not start, so the car screen can retry.
     */
    fun onCarEntry(
        context: Context,
        repository: BleRepository,
        settings: SoundKitSettings,
        scope: CoroutineScope,
    ): Boolean {
        return try {
            BleConnectionService.start(context.applicationContext)
            if (!settings.onboardingCompleted) return true
            val device = RememberedDeviceConnector.defaultDevice(settings) ?: return true
            scope.launch {
                if (RememberedDeviceConnector.shouldConnectInCar(repository.connectionState.value, settings)) {
                    repository.connect(device, userInitiated = false)
                }
            }
            true
        } catch (_: RuntimeException) {
            false
        }
    }
}
