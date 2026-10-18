package com.akrapovic.soundkit.community.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
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
                        // Tile.setSubtitle is API 29+; minSdk is 26.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            subtitle = presentation.subtitle
                        }
                        state = when (presentation.tileState) {
                            QsTileState.Active -> Tile.STATE_ACTIVE
                            QsTileState.Inactive -> Tile.STATE_INACTIVE
                            QsTileState.Unavailable -> Tile.STATE_UNAVAILABLE
                        }
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
            openApp()
            return
        }
        val action = when (presentation.valveAction) {
            ValveTileAction.Open -> BleConnectionService.ACTION_OPEN
            ValveTileAction.Close -> BleConnectionService.ACTION_CLOSE
            null -> return
        }
        // Cold-process QS taps must start as a foreground service on Android 12+.
        BleConnectionService.startAction(this, action)
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // The Intent overload throws UnsupportedOperationException when targeting API 34+.
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        } else {
            // Only reached below API 34, where the Intent overload is still supported.
            @Suppress("DEPRECATION")
            @SuppressLint("StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}
