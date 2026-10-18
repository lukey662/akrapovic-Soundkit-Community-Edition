package com.akrapovic.soundkit.community.wear

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import com.akrapovic.soundkit.community.data.BleRepository
import com.akrapovic.soundkit.community.data.SettingsStore
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.ValveState
import com.akrapovic.soundkit.community.ui.theme.darkVariantFor
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Mirrors valve state to a paired watch. A phone without Play services skips it silently. */
@Singleton
class WearStatePublisher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bleRepository: BleRepository,
    private val settingsStore: SettingsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var started = false

    @OptIn(FlowPreview::class)
    fun start() {
        if (started) return
        started = true
        scope.launch {
            combine(
                bleRepository.connectionState,
                bleRepository.valveState,
                bleRepository.receiverStatusMessage,
                settingsStore.settings,
            ) { connection, valve, notReady, settings ->
                WearState(
                    connected = connection is ConnectionState.Connected,
                    ready = connection is ConnectionState.Connected && notReady == null && valve != ValveState.Unknown,
                    valve = valve.name.uppercase(),
                    glow = darkVariantFor(settings.garageThemeId).glow.toArgb(),
                )
            }
                .distinctUntilChanged()
                .debounce(300)
                .collect { state -> publish(state) }
        }
    }

    private suspend fun publish(state: WearState) {
        runCatching {
            val request = PutDataMapRequest.create(WearProtocol.PATH_STATE).apply {
                dataMap.putBoolean(WearProtocol.KEY_CONNECTED, state.connected)
                dataMap.putBoolean(WearProtocol.KEY_READY, state.ready)
                dataMap.putString(WearProtocol.KEY_VALVE, state.valve)
                dataMap.putInt(WearProtocol.KEY_GLOW, state.glow)
                dataMap.putLong(WearProtocol.KEY_UPDATED_AT, System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(request).await()
        }
    }

    private data class WearState(
        val connected: Boolean,
        val ready: Boolean,
        val valve: String,
        val glow: Int,
    )
}
