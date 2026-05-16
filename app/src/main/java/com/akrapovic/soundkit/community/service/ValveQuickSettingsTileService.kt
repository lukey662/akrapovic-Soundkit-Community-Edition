package com.akrapovic.soundkit.community.service

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.akrapovic.soundkit.community.MainActivity
import com.akrapovic.soundkit.community.data.BleRepository
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.ValveState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ValveQuickSettingsTileService : TileService() {
    @Inject lateinit var bleRepository: BleRepository

    private var scope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { tileScope ->
            tileScope.launch {
                combine(
                    bleRepository.connectionState,
                    bleRepository.valveState,
                ) { connectionState, valveState -> connectionState to valveState }
                    .collect { (connectionState, valveState) ->
                        updateTile(connectionState, valveState)
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
        val state = bleRepository.connectionState.value
        if (state !is ConnectionState.Connected) {
            startActivityAndCollapse(
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        val action = when (bleRepository.valveState.value) {
            ValveState.Open -> BleConnectionService.ACTION_CLOSE
            ValveState.Closed, ValveState.Unknown -> BleConnectionService.ACTION_OPEN
        }
        startService(Intent(this, BleConnectionService::class.java).setAction(action))
    }

    private fun updateTile(connectionState: ConnectionState, valveState: ValveState) {
        qsTile?.apply {
            label = "Valve"
            subtitle = when (connectionState) {
                is ConnectionState.Connected -> valveState.name.lowercase()
                is ConnectionState.Connecting -> "connecting"
                is ConnectionState.Reconnecting -> "reconnecting"
                else -> "open app"
            }
            state = if (connectionState is ConnectionState.Connected) {
                Tile.STATE_ACTIVE
            } else {
                Tile.STATE_INACTIVE
            }
            updateTile()
        }
    }
}

