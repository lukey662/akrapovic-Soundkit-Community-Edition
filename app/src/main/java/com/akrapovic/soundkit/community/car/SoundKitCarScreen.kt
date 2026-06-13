package com.akrapovic.soundkit.community.car

import android.content.Intent
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.akrapovic.soundkit.community.MainActivity
import com.akrapovic.soundkit.community.ble.SoundKitProtocol
import com.akrapovic.soundkit.community.data.BleRepository
import com.akrapovic.soundkit.community.data.SettingsStore
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.DriveModeEngine
import com.akrapovic.soundkit.community.domain.RememberedDeviceConnector
import com.akrapovic.soundkit.community.domain.ValveState
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SoundKitCarScreen(
    carContext: CarContext,
) : Screen(carContext) {
    private val entryPoint = EntryPointAccessors.fromApplication(
        carContext.applicationContext,
        CarDependenciesEntryPoint::class.java,
    )
    private val repository: BleRepository = entryPoint.bleRepository()
    private val settingsStore: SettingsStore = entryPoint.settingsStore()
    private val driveModeEngine: DriveModeEngine = entryPoint.driveModeEngine()
    private val carSessionTracker: CarSessionTracker = entryPoint.carSessionTracker()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var hasDefaultReceiver = false
    private var autoReconnectEnabled = true

    init {
        carSessionTracker.beginSession()
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    carSessionTracker.endSession()
                }
            },
        )
        scope.launch {
            val settings = settingsStore.settings.first()
            hasDefaultReceiver = RememberedDeviceConnector.defaultDevice(settings) != null
            autoReconnectEnabled = settings.autoReconnect
            CarBleBootstrap.onCarEntry(carContext, repository, settings, scope)
        }
        scope.launch {
            settingsStore.settings.collect { settings ->
                hasDefaultReceiver = RememberedDeviceConnector.defaultDevice(settings) != null
                autoReconnectEnabled = settings.autoReconnect
            }
        }
        scope.launch {
            combine(
                repository.connectionState,
                repository.valveState,
                repository.receiverStatusMessage,
            ) { _, _, _ -> Unit }
                .collect { invalidate() }
        }
    }

    override fun onGetTemplate(): Template {
        if (!SoundKitProtocol.VERIFIED) {
            return MessageTemplate.Builder("Controls are unavailable in this build.")
                .setTitle("Sound Kit")
                .setHeaderAction(Action.APP_ICON)
                .build()
        }

        val connectionState = repository.connectionState.value
        val valveState = repository.valveState.value
        val receiverStatusMessage = repository.receiverStatusMessage.value
        val controlsEnabled = connectionState is ConnectionState.Connected &&
            valveState != ValveState.Unknown &&
            receiverStatusMessage == null

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
                    .addText(valveState.asCarText(receiverStatusMessage))
                    .build(),
            )

        if (receiverStatusMessage != null) {
            paneBuilder.addRow(
                Row.Builder()
                    .setTitle("Status")
                    .addText(receiverStatusMessage)
                    .build(),
            )
        }

        when {
            controlsEnabled -> {
                val toggleLabel = when (valveState) {
                    ValveState.Open -> "Close valves"
                    ValveState.Closed -> "Open valves"
                    ValveState.Unknown -> "Toggle valves"
                }
                paneBuilder.addAction(
                    Action.Builder()
                        .setTitle(toggleLabel)
                        .setOnClickListener { scope.launch { toggleValve(valveState) } }
                        .build(),
                )
            }
            connectionState is ConnectionState.Connected && valveState == ValveState.Unknown -> {
                paneBuilder.addRow(
                    Row.Builder()
                        .setTitle("Controls")
                        .addText("Waiting for receiver status. Use while parked.")
                        .build(),
                )
            }
        }

        if (!hasDefaultReceiver && connectionState is ConnectionState.Disconnected) {
            paneBuilder.addAction(openPhoneAppAction())
        } else if (connectionState is ConnectionState.Error) {
            paneBuilder.addAction(openPhoneAppAction())
        }

        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle("Sound Kit")
            .setHeaderAction(Action.APP_ICON)
            .build()
    }

    private fun openPhoneAppAction(): Action {
        return Action.Builder()
            .setTitle("Open on phone")
            .setOnClickListener {
                val intent = Intent(carContext, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                carContext.startActivity(intent)
            }
            .build()
    }

    private suspend fun toggleValve(valveState: ValveState) {
        driveModeEngine.onUserValveAdjustment()
        when (valveState) {
            ValveState.Open -> repository.closeValve()
            ValveState.Closed -> repository.openValve()
            ValveState.Unknown -> Unit
        }
    }

    private fun ConnectionState.asCarText(): String {
        return when (this) {
            ConnectionState.Disconnected ->
                if (autoReconnectEnabled && hasDefaultReceiver) {
                    "Disconnected. Reconnecting to your default receiver, or open the phone app to scan."
                } else {
                    "Disconnected. Open the phone app to connect."
                }
            ConnectionState.Scanning -> "Scanning for receivers on phone."
            is ConnectionState.Connecting -> "Connecting to ${device.name}…"
            is ConnectionState.Connected -> "Connected to ${device.name}"
            is ConnectionState.Reconnecting -> "Reconnecting to ${device.name} (attempt $attempt)…"
            is ConnectionState.Error -> "Connection failed: $message. Open the phone app to retry."
        }
    }

    private fun ValveState.asCarText(receiverNotReady: String?): String {
        if (receiverNotReady != null) return "Not ready"
        return when (this) {
            ValveState.Open -> "Open"
            ValveState.Closed -> "Closed"
            ValveState.Unknown -> "Checking status"
        }
    }
}
