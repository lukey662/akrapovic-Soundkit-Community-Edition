package com.akrapovic.soundkit.community.car

import android.content.Context
import com.akrapovic.soundkit.community.data.BleRepository
import com.akrapovic.soundkit.community.domain.SoundKitSettings
import com.akrapovic.soundkit.community.service.BleConnectionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Ensures BLE foreground service is running and reconnects to the remembered receiver when the
 * Android Auto / car session opens.
 */
object CarBleBootstrap {
    fun onCarEntry(
        context: Context,
        repository: BleRepository,
        settings: SoundKitSettings,
        scope: CoroutineScope,
    ) {
        BleConnectionService.start(context)
        val device = CarRememberedDeviceConnector.rememberedDevice(settings) ?: return
        scope.launch {
            if (CarRememberedDeviceConnector.shouldAutoConnect(repository.connectionState.value, settings)) {
                repository.connect(device)
            }
        }
    }
}
