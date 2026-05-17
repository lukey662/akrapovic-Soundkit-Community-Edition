package com.akrapovic.soundkit.community.service

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.akrapovic.soundkit.community.MainActivity
import com.akrapovic.soundkit.community.data.BleRepository
import com.akrapovic.soundkit.community.data.SettingsStore
import com.akrapovic.soundkit.community.domain.SoundKitSettings
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ValveQuickSettingsTileService : TileService() {
    @Inject lateinit var bleRepository: BleRepository
    @Inject lateinit var settingsStore: SettingsStore

    private var scope: CoroutineScope? = null
    private var latestSettings: SoundKitSettings = SoundKitSettings()

    override fun onStartListening() {
        super.onStartListening()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { tileScope ->
            tileScope.launch {
                latestSettings = settingsStore.settings.first()
            }
            tileScope.launch {
                combine(
                    bleRepository.connectionState,
                    bleRepository.valveState,
                    bleRepository.receiverStatusMessage,
                    settingsStore.settings,
                ) { connectionState, valveState, receiverStatusMessage, settings ->
                    latestSettings = settings
                    QsTilePresenter.present(
                        connectionState = connectionState,
                        valveState = valveState,
                        receiverStatusMessage = receiverStatusMessage,
                        defaultReceiver = settings.defaultReceiver,
                    )
                }.collect { presentation ->
                    qsTile?.apply {
                        label = "Valve"
                        subtitle = presentation.subtitle
                        state = if (presentation.active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                        updateTile()
                    }
                }
            }
        }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val presentation = QsTilePresenter.present(
            connectionState = bleRepository.connectionState.value,
            valveState = bleRepository.valveState.value,
            receiverStatusMessage = bleRepository.receiverStatusMessage.value,
            defaultReceiver = latestSettings.defaultReceiver,
        )
        if (presentation.clickOpensApp) {
            startActivityAndCollapse(
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        val action = when (presentation.valveAction) {
            ValveTileAction.Open -> BleConnectionService.ACTION_OPEN
            ValveTileAction.Close -> BleConnectionService.ACTION_CLOSE
            null -> return
        }
        startService(Intent(this, BleConnectionService::class.java).setAction(action))
    }
}
