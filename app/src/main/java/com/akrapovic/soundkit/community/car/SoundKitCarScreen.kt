package com.akrapovic.soundkit.community.car

import android.content.pm.PackageManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.akrapovic.soundkit.community.R
import com.akrapovic.soundkit.community.ble.PermissionPolicy
import com.akrapovic.soundkit.community.ble.SoundKitProtocol
import com.akrapovic.soundkit.community.data.BleRepository
import com.akrapovic.soundkit.community.data.SettingsStore
import com.akrapovic.soundkit.community.domain.CommandPhase
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.DriveModeEngine
import com.akrapovic.soundkit.community.domain.RememberedDeviceConnector
import com.akrapovic.soundkit.community.domain.SoundKitSettings
import com.akrapovic.soundkit.community.domain.ValveCommandCoordinator
import com.akrapovic.soundkit.community.domain.ValveState
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
    private val valveCommandCoordinator: ValveCommandCoordinator = entryPoint.valveCommandCoordinator()
    private val carSessionTracker: CarSessionTracker = entryPoint.carSessionTracker()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var settingsSnapshot = SoundKitSettings()
    private var didBootstrap = false

    init {
        carSessionTracker.beginSession()
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    scope.cancel()
                    carSessionTracker.endSession()
                }
            },
        )
        scope.launch {
            settingsStore.settings.collect { settings ->
                settingsSnapshot = settings
                if (!didBootstrap) {
                    didBootstrap = true
                    CarBleBootstrap.onCarEntry(carContext, repository, settings, scope)
                }
            }
        }
        scope.launch {
            combine(
                settingsStore.settings,
                repository.connectionState,
                repository.valveState,
                repository.receiverStatusMessage,
                valveCommandCoordinator.commandPhase,
            ) { settings, connection, valve, status, commandPhase ->
                CarRenderState(settings, connection, valve, status, commandPhase)
            }
                .distinctUntilChanged()
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
        return when (
            val model = CarScreenPresenter.present(
                onboardingCompleted = settingsSnapshot.onboardingCompleted,
                permissionsGranted = hasBlePermissions(),
                hasDefaultReceiver = RememberedDeviceConnector.defaultDevice(settingsSnapshot) != null,
                connectionState = connectionState,
                valveState = valveState,
                receiverStatusMessage = receiverStatusMessage,
                commandPhase = valveCommandCoordinator.commandPhase.value,
            )
        ) {
            is CarScreenModel.SetupRequired -> MessageTemplate.Builder(model.message)
                .setTitle("Sound Kit")
                .setHeaderAction(Action.APP_ICON)
                .build()
            is CarScreenModel.Controls -> buildGridTemplate(model, connectionState)
        }
    }

    private fun buildGridTemplate(
        model: CarScreenModel.Controls,
        connectionState: ConnectionState,
    ): Template {
        val builder = GridTemplate.Builder()
            .setTitle("Sound Kit")
            .setHeaderAction(Action.APP_ICON)
            .setLoading(model.loading)
        if (model.showControls) {
            builder.setSingleList(
                ItemList.Builder()
                    .addItem(
                valveActionItem("Open", model.openEnabled) {
                    scope.launch { openValve() }
                },
                    )
                    .addItem(
                valveActionItem("Close", model.closeEnabled) {
                    scope.launch { closeValve() }
                },
                    )
                    .build(),
            )
        }
        return builder.build()
    }

    private fun valveActionItem(title: String, enabled: Boolean, onClick: () -> Unit): GridItem {
        val builder = GridItem.Builder()
            .setTitle(title)
            .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_valve_tile)).build())
        // GridItem has no enabled API. Omitting the click listener makes the host render it inert.
        if (enabled) builder.setOnClickListener(onClick)
        return builder.build()
    }

    private suspend fun openValve() {
        driveModeEngine.onUserValveAdjustment()
        valveCommandCoordinator.open()
    }

    private suspend fun closeValve() {
        driveModeEngine.onUserValveAdjustment()
        valveCommandCoordinator.close()
    }

    private fun hasBlePermissions(): Boolean {
        return PermissionPolicy.requiredBlePermissions().all { permission ->
            ContextCompat.checkSelfPermission(carContext, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private data class CarRenderState(
        val settings: SoundKitSettings,
        val connectionState: ConnectionState,
        val valveState: ValveState,
        val receiverStatusMessage: String?,
        val commandPhase: CommandPhase,
    )
}
