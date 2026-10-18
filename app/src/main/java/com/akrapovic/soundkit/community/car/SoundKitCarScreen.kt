package com.akrapovic.soundkit.community.car

import android.content.pm.PackageManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.OnClickListener
import androidx.car.app.model.ParkedOnlyOnClickListener
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
     * Icons keyed by (themeId, openAmount). The quiet-hold countdown invalidates every second, and
     * each invalidate must not re-render or re-send bitmaps, so the three tiles reuse these.
     */
    private val iconCache = HashMap<Pair<String?, Float>, CarIcon>()

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
        if (settingsReady) ensureBootstrap(settingsSnapshot)
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
            is CarScreenModel.Controls -> buildGridTemplate(model)
        }
    }

    private fun messageTemplate(message: String): Template {
        return MessageTemplate.Builder(message)
            .setTitle(CarScreenPresenter.TITLE)
            .setHeaderAction(Action.APP_ICON)
            .build()
    }

    /**
     * Title and item titles are constant, so each invalidate is a refresh rather than a new step.
     * Status lives in item text.
     */
    private fun buildGridTemplate(model: CarScreenModel.Controls): Template {
        return GridTemplate.Builder()
            .setTitle(CarScreenPresenter.TITLE)
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(
                ItemList.Builder()
                    .addItem(
                        gridItem(
                            title = CarScreenPresenter.STATUS_ITEM_TITLE,
                            text = model.statusText,
                            icon = valveIcon(if (repository.valveState.value == ValveState.Open) 1f else 0f),
                            onClick = if (model.retryEnabled) OnClickListener { retryConnection() } else null,
                        ),
                    )
                    .addItem(
                        gridItem(
                            title = CarScreenPresenter.OPEN_ITEM_TITLE,
                            text = null,
                            icon = valveIcon(1f),
                            onClick = if (model.openEnabled) valveClick { openValve() } else null,
                        ),
                    )
                    .addItem(
                        gridItem(
                            title = CarScreenPresenter.CLOSE_ITEM_TITLE,
                            text = null,
                            icon = valveIcon(0f),
                            onClick = if (model.closeEnabled) valveClick { closeValve() } else null,
                        ),
                    )
                    .build(),
            )
            .build()
    }

    /**
     * Untinted valve art in the theme's dark glow; the car display is always dark.
     *
     * Rendered once per (theme, openAmount) at a size the host can carry over binder. If rendering
     * fails for any reason the tile falls back to the tinted vector glyph rather than failing the
     * whole template.
     */
    private fun valveIcon(openAmount: Float): CarIcon {
        val themeId = settingsSnapshot.garageThemeId
        return iconCache.getOrPut(themeId to openAmount) {
            val palette = ValveArtPalette.from(darkVariantFor(themeId))
            runCatching {
                val metrics = carContext.resources.displayMetrics
                val bitmap = ValveBitmaps.render(
                    sizePx = (TILE_IMAGE_DP * metrics.density).toInt(),
                    openAmount = openAmount,
                    palette = palette,
                    density = metrics.density,
                    maxPx = TILE_IMAGE_MAX_PX,
                )
                CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build()
            }.getOrElse { throwable ->
                carErrorLog.record("valveIcon", throwable)
                val glowArgb = palette.glow.toArgb()
                CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_valve_tile))
                    .setTint(CarColor.createCustom(glowArgb, glowArgb))
                    .build()
            }
        }
    }

    private fun gridItem(title: String, text: String?, icon: CarIcon, onClick: OnClickListener?): GridItem {
        val builder = GridItem.Builder()
            .setTitle(title)
            .setImage(icon, GridItem.IMAGE_TYPE_LARGE)
        if (text != null) builder.setText(text)
        // GridItem has no enabled API. Omitting the click listener makes the host render it inert.
        if (onClick != null) builder.setOnClickListener(onClick)
        return builder.build()
    }

    /** Valve changes are parked-only; the host refuses the tap while the car is moving. */
    private fun valveClick(command: suspend () -> Unit): OnClickListener {
        return ParkedOnlyOnClickListener.create { scope.launch { command() } }
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
        /** Three ARGB tiles at 56dp capped at 144px stay far below the host's 1MB binder budget. */
        const val TILE_IMAGE_DP = 56
        const val TILE_IMAGE_MAX_PX = 144
        const val ERROR_MESSAGE = "Something went wrong. Open Car Sound Kit on the phone and export diagnostics."
    }

    private data class CarRenderState(
        val settings: SoundKitSettings,
        val connectionState: ConnectionState,
        val valveState: ValveState,
        val receiverStatusMessage: String?,
        val commandPhase: CommandPhase,
    )
}
