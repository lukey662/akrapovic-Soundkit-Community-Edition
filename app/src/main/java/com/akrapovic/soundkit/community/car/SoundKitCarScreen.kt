package com.akrapovic.soundkit.community.car

import android.content.pm.PackageManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.OnClickListener
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.akrapovic.soundkit.community.R
import com.akrapovic.soundkit.community.ble.PermissionPolicy
import com.akrapovic.soundkit.community.ble.SoundKitProtocol
import com.akrapovic.soundkit.community.data.BleRepository
import com.akrapovic.soundkit.community.data.SettingsStore
import com.akrapovic.soundkit.community.diagnostics.CarErrorLog
import com.akrapovic.soundkit.community.domain.CommandPhase
import com.akrapovic.soundkit.community.domain.CommandSource
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.DriveModeEngine
import com.akrapovic.soundkit.community.domain.RememberedDeviceConnector
import com.akrapovic.soundkit.community.domain.SoundKitSettings
import com.akrapovic.soundkit.community.domain.ValveCommandCoordinator
import com.akrapovic.soundkit.community.domain.ValveState
import com.akrapovic.soundkit.community.service.BleConnectionService
import com.akrapovic.soundkit.community.ui.components.ValveArtPalette
import com.akrapovic.soundkit.community.ui.components.ValveBitmaps
import com.akrapovic.soundkit.community.ui.theme.darkVariantFor
import com.akrapovic.soundkit.community.domain.SavedReceiver
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
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
    private val carErrorLog: CarErrorLog = entryPoint.carErrorLog()

    /** A failing collector is recorded and the screen stays alive; the host must never see a throw. */
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        carErrorLog.record("screenScope", throwable)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + exceptionHandler)
    private var settingsSnapshot = SoundKitSettings()
    private var settingsReady = false
    private var didBootstrap = false
    private var quietHoldEndsAt: Long? = null

    /**
     * Valve images keyed by theme and look. The quiet-hold countdown invalidates every second, and
     * each invalidate must reuse the same bitmap rather than render a new one.
     */
    private val iconCache = HashMap<ValveLook, CarIcon>()

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
                settingsReady = true
                ensureBootstrap(settings)
            }
        }
        scope.launch {
            val render = combine(
                settingsStore.settings,
                repository.connectionState,
                repository.valveState,
                repository.receiverStatusMessage,
                valveCommandCoordinator.commandPhase,
            ) { settings, connection, valve, status, commandPhase ->
                CarRenderState(settings, connection, valve, status, commandPhase)
            }
            combine(render, driveModeEngine.quietHoldEndsAt) { state, endsAt ->
                quietHoldEndsAt = endsAt
                state
            }.collect { invalidate() }
        }
        scope.launch {
            while (isActive) {
                delay(1_000)
                if (quietHoldEndsAt != null) invalidate()
            }
        }
    }

    /**
     * Never throws. A throw here, or a template the host rejects, makes the host replace the app
     * with its "unexpected error" page while the process lives on, so nothing else would record it.
     */
    override fun onGetTemplate(): Template {
        return runCatching { buildTemplate() }.getOrElse { throwable ->
            carErrorLog.record("onGetTemplate", throwable)
            runCatching { messageTemplate(ERROR_MESSAGE) }.getOrElse {
                MessageTemplate.Builder(ERROR_MESSAGE).setTitle(CarScreenPresenter.TITLE).build()
            }
        }
    }

    private fun buildTemplate(): Template {
        // Loading pane first: the host treats loading -> anything as a refresh, so the first real
        // state costs nothing.
        if (!settingsReady) return loadingTemplate()
        ensureBootstrap(settingsSnapshot)
        if (!SoundKitProtocol.VERIFIED) {
            return messageTemplate("Controls are unavailable in this build.")
        }

        val model = CarScreenPresenter.present(
            onboardingCompleted = settingsSnapshot.onboardingCompleted,
            permissionsGranted = hasBlePermissions(),
            hasDefaultReceiver = RememberedDeviceConnector.defaultDevice(settingsSnapshot) != null,
            connectionState = repository.connectionState.value,
            valveState = repository.valveState.value,
            receiverStatusMessage = repository.receiverStatusMessage.value,
            commandPhase = valveCommandCoordinator.commandPhase.value,
            quietHoldEndsAtMillis = driveModeEngine.quietHoldEndsAt.value,
            nowMillis = System.currentTimeMillis(),
        )
        return when (model) {
            is CarScreenModel.SetupRequired -> messageTemplate(model.message)
            is CarScreenModel.Controls -> paneTemplate(model)
        }
    }

    private fun messageTemplate(message: String): Template {
        return MessageTemplate.Builder(message)
            .setTitle(CarScreenPresenter.TITLE)
            .setHeaderAction(Action.APP_ICON)
            .build()
    }

    @Suppress("DEPRECATION") // Header.Builder needs car API 7; the app supports hosts from API 6.
    private fun loadingTemplate(): Template {
        return PaneTemplate.Builder(Pane.Builder().setLoading(true).build())
            .setTitle(CarScreenPresenter.TITLE)
            .setHeaderAction(Action.APP_ICON)
            .build()
    }

    /**
     * Header title and the single row's title (the receiver name) never change, so every update is
     * a refresh; the buttons are free to change. Open, Close and Connect use plain click listeners
     * so they work while driving.
     */
    @Suppress("DEPRECATION") // Header.Builder needs car API 7; the app supports hosts from API 6.
    private fun paneTemplate(model: CarScreenModel.Controls): Template {
        val pane = Pane.Builder()
            .setImage(valveImage(model))
            .addRow(
                Row.Builder()
                    .setTitle(receiverName())
                    .addText(model.rowText)
                    .build(),
            )
        when (val buttons = model.buttons) {
            CarButtons.None -> Unit
            CarButtons.Connect -> pane.addAction(
                primaryAction("Connect") { retryConnection() },
            )
            is CarButtons.OpenClose -> {
                pane.addAction(
                    valveAction("Open", buttons.primary == ValveTarget.Open, buttons.openEnabled) { openValve() },
                )
                pane.addAction(
                    valveAction("Close", buttons.primary == ValveTarget.Close, buttons.closeEnabled) { closeValve() },
                )
            }
        }
        return PaneTemplate.Builder(pane.build())
            .setTitle(CarScreenPresenter.TITLE)
            .setHeaderAction(Action.APP_ICON)
            .build()
    }

    private fun receiverName(): String {
        val saved: SavedReceiver? = settingsSnapshot.savedReceivers.firstOrNull { it.isDefault }
        return saved?.displayName() ?: "Receiver"
    }

    private fun valveAction(title: String, primary: Boolean, enabled: Boolean, command: suspend () -> Unit): Action {
        val builder = Action.Builder()
            .setTitle(title)
            .setEnabled(enabled)
            .setOnClickListener(OnClickListener { scope.launch { command() } })
        if (primary) builder.setFlags(Action.FLAG_PRIMARY).setBackgroundColor(buttonColor())
        return builder.build()
    }

    private fun primaryAction(title: String, onClick: () -> Unit): Action {
        return Action.Builder()
            .setTitle(title)
            .setFlags(Action.FLAG_PRIMARY)
            .setBackgroundColor(buttonColor())
            .setOnClickListener(OnClickListener { onClick() })
            .build()
    }

    private fun buttonColor(): CarColor {
        val argb = darkVariantFor(settingsSnapshot.garageThemeId).button.toArgb()
        return CarColor.createCustom(argb, argb)
    }

    /**
     * The same exhaust-tip art as Home, rendered large for the pane hero image on a transparent
     * background so the open glow sits on the car's dark surface. Not connected = closed and faded,
     * in flight = busy ring. If rendering fails the pane falls back to the tinted vector glyph.
     */
    private fun valveImage(model: CarScreenModel.Controls): CarIcon {
        val themeId = settingsSnapshot.garageThemeId
        val look = ValveLook(themeId, model.valveOpen, model.dimmed, model.busy)
        return iconCache.getOrPut(look) {
            val theme = darkVariantFor(themeId)
            val palette = ValveArtPalette.from(theme)
            runCatching {
                val metrics = carContext.resources.displayMetrics
                val bitmap = ValveBitmaps.render(
                    sizePx = (PANE_IMAGE_DP * metrics.density).toInt(),
                    openAmount = if (model.valveOpen) 1f else 0f,
                    palette = palette,
                    density = metrics.density,
                    maxPx = PANE_IMAGE_MAX_PX,
                    contentAlpha = if (model.dimmed) DIMMED_ALPHA else 1f,
                    busyRing = if (model.busy) theme.glow.copy(alpha = 0.6f) else null,
                )
                CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build()
            }.getOrElse { throwable ->
                carErrorLog.record("valveImage", throwable)
                val glowArgb = palette.glow.toArgb()
                CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_valve_tile))
                    .setTint(CarColor.createCustom(glowArgb, glowArgb))
                    .build()
            }
        }
    }

    private fun retryConnection() {
        val device = RememberedDeviceConnector.defaultDevice(settingsSnapshot) ?: return
        if (!BleConnectionService.start(carContext.applicationContext)) return
        scope.launch { repository.connect(device, userInitiated = true) }
    }

    private suspend fun openValve() {
        driveModeEngine.onUserValveAdjustment()
        valveCommandCoordinator.open(CommandSource.AndroidAuto)
    }

    private suspend fun closeValve() {
        driveModeEngine.onUserValveAdjustment()
        valveCommandCoordinator.close(CommandSource.AndroidAuto)
    }

    private fun ensureBootstrap(settings: SoundKitSettings) {
        if (didBootstrap) return
        didBootstrap = CarBleBootstrap.onCarEntry(carContext, repository, settings, scope)
    }

    private fun hasBlePermissions(): Boolean {
        return PermissionPolicy.requiredBlePermissions().all { permission ->
            ContextCompat.checkSelfPermission(carContext, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private companion object {
        /** One 256px ARGB hero image (~260 KB) per template stays well inside the 1 MB binder budget. */
        const val PANE_IMAGE_DP = 200
        const val PANE_IMAGE_MAX_PX = 256
        const val DIMMED_ALPHA = 0.45f
        const val ERROR_MESSAGE = "Something went wrong. Open Car Sound Kit on the phone and export diagnostics."
    }

    private data class ValveLook(
        val themeId: String?,
        val open: Boolean,
        val dimmed: Boolean,
        val busy: Boolean,
    )

    private data class CarRenderState(
        val settings: SoundKitSettings,
        val connectionState: ConnectionState,
        val valveState: ValveState,
        val receiverStatusMessage: String?,
        val commandPhase: CommandPhase,
    )
}
