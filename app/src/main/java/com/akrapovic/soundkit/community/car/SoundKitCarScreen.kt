package com.akrapovic.soundkit.community.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.akrapovic.soundkit.community.ble.SoundKitProtocol
import com.akrapovic.soundkit.community.data.BleRepository
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.ValveState
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class SoundKitCarScreen(
    carContext: CarContext,
) : Screen(carContext) {
    private val repository: BleRepository = EntryPointAccessors.fromApplication(
        carContext.applicationContext,
        CarDependenciesEntryPoint::class.java,
    ).bleRepository()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        scope.launch {
            combine(repository.connectionState, repository.valveState) { _, _ -> Unit }
                .collect { invalidate() }
        }
    }

    override fun onGetTemplate(): Template {
        val connectionState = repository.connectionState.value
        if (!SoundKitProtocol.VERIFIED) {
            return MessageTemplate.Builder("Controls are unavailable in this build.")
                .setTitle("Sound Kit")
                .setHeaderAction(Action.APP_ICON)
                .build()
        }
        val valveState = repository.valveState.value

        val paneBuilder = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle("Receiver")
                    .addText(connectionState.asCarText())
                    .build(),
            )
            .addRow(
                Row.Builder()
                    .setTitle("Valves")
                    .addText(valveState.asCarText())
                    .build(),
            )

        if (connectionState is ConnectionState.Connected) {
            when (valveState) {
                ValveState.Closed -> paneBuilder.addAction(
                    Action.Builder()
                        .setTitle("Open")
                        .setOnClickListener { scope.launch { repository.openValve() } }
                        .build(),
                )
                ValveState.Open -> paneBuilder.addAction(
                    Action.Builder()
                        .setTitle("Close")
                        .setOnClickListener { scope.launch { repository.closeValve() } }
                        .build(),
                )
                ValveState.Unknown -> Unit
            }
        }

        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle("Sound Kit")
            .setHeaderAction(Action.APP_ICON)
            .build()
    }

    private fun ConnectionState.asCarText(): String {
        return when (this) {
            ConnectionState.Disconnected -> "Disconnected. Open the phone app to scan and connect."
            ConnectionState.Scanning -> "Scanning"
            is ConnectionState.Connecting -> "Connecting to ${device.name}"
            is ConnectionState.Connected -> "Connected to ${device.name}"
            is ConnectionState.Reconnecting -> "Reconnecting, attempt $attempt"
            is ConnectionState.Error -> message
        }
    }

    private fun ValveState.asCarText(): String {
        return when (this) {
            ValveState.Open -> "Open"
            ValveState.Closed -> "Closed"
            ValveState.Unknown -> "Checking status"
        }
    }
}

