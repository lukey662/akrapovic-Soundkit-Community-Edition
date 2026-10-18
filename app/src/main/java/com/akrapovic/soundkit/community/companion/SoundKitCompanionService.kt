package com.akrapovic.soundkit.community.companion

import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.companion.DevicePresenceEvent
import android.os.Build
import androidx.annotation.RequiresApi
import com.akrapovic.soundkit.community.data.BleRepository
import com.akrapovic.soundkit.community.data.DiagnosticsRepository
import com.akrapovic.soundkit.community.data.SettingsStore
import com.akrapovic.soundkit.community.domain.RememberedDeviceConnector
import com.akrapovic.soundkit.community.service.BleConnectionService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Bound by Android when the associated receiver comes into range. Connects only when
 * [CompanionPresencePolicy] allows; leaving is still handled by the car-presence logic.
 */
@RequiresApi(Build.VERSION_CODES.S)
@AndroidEntryPoint
class SoundKitCompanionService : CompanionDeviceService() {
    @Inject lateinit var settingsStore: SettingsStore
    @Inject lateinit var bleRepository: BleRepository
    @Inject lateinit var diagnosticsRepository: DiagnosticsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Deprecated("Android 12 reports presence by address.")
    override fun onDeviceAppeared(address: String) {
        handleAppeared(address)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        associationInfo.deviceMacAddress?.toString()?.let(::handleAppeared)
    }

    @RequiresApi(36)
    override fun onDevicePresenceEvent(event: DevicePresenceEvent) {
        val appeared = event.event == DevicePresenceEvent.EVENT_BLE_APPEARED ||
            event.event == DevicePresenceEvent.EVENT_BT_CONNECTED
        if (!appeared) {
            diagnosticsRepository.info("Companion receiver presence event ${event.event}")
            return
        }
        CompanionPairing.addressFor(this, event.associationId)?.let(::handleAppeared)
    }

    private fun handleAppeared(address: String) {
        scope.launch {
            val settings = settingsStore.settings.first()
            if (!CompanionPresencePolicy.shouldConnect(address, settings, bleRepository.connectionState.value)) {
                diagnosticsRepository.debug("Companion receiver nearby; no connection needed")
                return@launch
            }
            val device = RememberedDeviceConnector.defaultDevice(settings) ?: return@launch
            if (!BleConnectionService.startFromCarWake(applicationContext)) {
                diagnosticsRepository.warning("Companion receiver nearby but the connection service could not start")
                return@launch
            }
            diagnosticsRepository.info("Companion receiver nearby; connecting to ${device.name}")
            bleRepository.connect(device, userInitiated = false)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
