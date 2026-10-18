package com.akrapovic.soundkit.community.service

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.akrapovic.soundkit.community.car.CarPresenceSource
import com.akrapovic.soundkit.community.car.CarSessionTracker
import com.akrapovic.soundkit.community.data.BleRepository
import com.akrapovic.soundkit.community.data.DiagnosticsRepository
import com.akrapovic.soundkit.community.data.RuleExecutionLogStore
import com.akrapovic.soundkit.community.data.SettingsStore
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.DriveModeEngine
import com.akrapovic.soundkit.community.domain.RememberedDeviceConnector
import com.akrapovic.soundkit.community.domain.ValveCommandCoordinator
import com.akrapovic.soundkit.community.domain.WallClock
import com.akrapovic.soundkit.community.widget.SoundKitWidgetProvider
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BleConnectionService : LifecycleService() {
    @Inject lateinit var bleRepository: BleRepository
    @Inject lateinit var settingsStore: SettingsStore
    @Inject lateinit var executionLog: RuleExecutionLogStore
    @Inject lateinit var notificationFactory: SoundKitNotificationFactory
    @Inject lateinit var driveModeEngine: DriveModeEngine
    @Inject lateinit var valveCommandCoordinator: ValveCommandCoordinator
    @Inject lateinit var carPresence: CarPresenceSource
    @Inject lateinit var carSessionTracker: CarSessionTracker
    @Inject lateinit var clock: WallClock
    @Inject lateinit var diagnosticsRepository: DiagnosticsRepository

    private var connectSessionId = 0L
    private var lastNotificationKey: String? = null
    private var detachedForAway = false
    private val carWake = MutableStateFlow(false)
    /** Ticks while a quiet-start hold runs so the progress notification stays current. */
    private val holdTick = MutableStateFlow(0L)
    private var notificationsSuppressed = false
    private var lastPublished: Notification? = null
    private var lastPublishedQuietAway = false

    override fun onCreate() {
        super.onCreate()
        notificationFactory.ensureChannel()
        lifecycleScope.launch {
            val status = combine(
                bleRepository.connectionState,
                bleRepository.valveState,
                bleRepository.receiverStatusMessage,
                settingsStore.settings,
                executionLog.lastExecution,
            ) { connectionState, valveState, receiverStatusMessage, settings, lastExecution ->
                NotificationSnapshot(
                    connectionState = connectionState,
                    valveState = valveState,
                    receiverStatusMessage = receiverStatusMessage,
                    settings = settings,
                    lastExecution = lastExecution,
                )
            }
            combine(
                status,
                driveModeEngine.quietHoldEndsAt,
                carPresence.presence,
                carSessionTracker.isCarSessionActive,
                combine(carWake, bleRepository.startupSettled, holdTick) { wake, settled, tick ->
                    Triple(wake, settled, tick)
                },
            ) { snapshot, quietHoldEndsAt, presence, sessionActive, wakeSettledTick ->
                val (wake, settled, tick) = wakeSettledTick
                val inCar = presence.orSession(sessionActive)
                val notification = notificationFactory.build(
                    connectionState = snapshot.connectionState,
                    valveState = snapshot.valveState,
                    receiverStatusMessage = snapshot.receiverStatusMessage,
                    defaultReceiver = snapshot.settings.defaultReceiver,
                    driveModeEnabled = snapshot.settings.driveModeEnabled,
                    driveModePaused = snapshot.settings.automationPaused,
                    lastExecution = snapshot.lastExecution,
                    periodicScanWhenAway = snapshot.settings.periodicScanWhenAway,
                    quietHoldEndsAtMillis = quietHoldEndsAt,
                    quietHoldMinutes = snapshot.settings.quietStart.holdClosedMinutes.coerceIn(1, 15),
                )
                val key = listOf(
                    snapshot.connectionState.toString(),
                    snapshot.valveState.name,
                    snapshot.receiverStatusMessage.orEmpty(),
                    snapshot.settings.defaultReceiver?.address.orEmpty(),
                    snapshot.settings.driveModeEnabled.toString(),
                    snapshot.settings.automationPaused.toString(),
                    snapshot.settings.periodicScanWhenAway.toString(),
                    snapshot.lastExecution?.timestampMillis?.toString().orEmpty(),
                    quietHoldEndsAt?.toString().orEmpty(),
                    inCar.toString(),
                    wake.toString(),
                    settled.toString(),
                    if (quietHoldEndsAt != null) tick.toString() else "",
                ).joinToString("|")
                val quietAway = snapshot.connectionState is ConnectionState.Away &&
                    !snapshot.settings.periodicScanWhenAway
                val release = ConnectionServicePresence.shouldReleaseWhenIdle(
                    carWake = wake,
                    connectionState = snapshot.connectionState,
                    inCar = inCar,
                    scanning = bleRepository.isScanning.value,
                    nowMillis = clock.nowMillis(),
                    startupSettled = settled,
                )
                PublishedNotification(notification, key, quietAway, release)
            }.collect { published ->
                if (notificationsSuppressed) return@collect
                lastPublished = published.notification
                lastPublishedQuietAway = published.quietAway
                if (published.release) {
                    notificationsSuppressed = true
                    publishNotification(published.notification, quietAway = false)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@collect
                }
                if (published.key == lastNotificationKey) return@collect
                lastNotificationKey = published.key
                publishNotification(published.notification, published.quietAway)
            }
        }
        lifecycleScope.launch {
            driveModeEngine.quietHoldEndsAt.collectLatest { endsAt ->
                while (endsAt != null && endsAt > clock.nowMillis()) {
                    delay(HOLD_TICK_MS)
                    holdTick.value = clock.nowMillis()
                }
            }
        }
        lifecycleScope.launch {
            combine(
                bleRepository.connectionState,
                bleRepository.valveState,
                bleRepository.receiverStatusMessage,
                settingsStore.settings,
            ) { connection, valve, notReady, settings ->
                SoundKitWidgetProvider.requestUpdate(
                    context = this@BleConnectionService,
                    connectionState = connection,
                    valveState = valve,
                    receiverStatusMessage = notReady,
                    hasDefaultReceiver = settings.defaultReceiver != null,
                )
            }
        }
        lifecycleScope.launch {
            var wasConnectReady = false
            combine(
                bleRepository.connectionState,
                bleRepository.valveState,
                bleRepository.receiverStatusMessage,
            ) { connection, valve, notReady ->
                Triple(connection, valve, notReady)
            }
                .distinctUntilChanged()
                .collect { (connection, valve, notReady) ->
                    val transition = ConnectReadyObserver.evaluate(
                        connection = connection,
                        valve = valve,
                        notReady = notReady,
                        wasConnectReady = wasConnectReady,
                    )
                    when {
                        transition.disconnected -> {
                            wasConnectReady = false
                            driveModeEngine.onDisconnect()
                        }
                        transition.becameReady -> {
                            connectSessionId += 1
                            driveModeEngine.onConnectReady(connectSessionId, lifecycleScope)
                            wasConnectReady = true
                        }
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val wokeFromCar = intent == null || intent.getBooleanExtra(EXTRA_CAR_WAKE, false)
        if (intent?.action != ACTION_DISMISS_AWAY) {
            // Every startForegroundService needs startForeground, even when the shade card is
            // detached for Away or the next state matches the last one published.
            notificationsSuppressed = false
            if (!promoteToForeground()) return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_DISMISS_AWAY -> {
                notificationsSuppressed = true
                carWake.value = false
                lifecycleScope.launch {
                    bleRepository.dismissAway()
                    getSystemService(NotificationManager::class.java)
                        .cancel(SoundKitNotificationFactory.NOTIFICATION_ID)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
            ACTION_OPEN -> {
                carWake.value = false
                lifecycleScope.launch {
                    driveModeEngine.onUserValveAdjustment()
                    valveCommandCoordinator.open()
                }
            }
            ACTION_CLOSE -> {
                carWake.value = false
                lifecycleScope.launch {
                    driveModeEngine.onUserValveAdjustment()
                    valveCommandCoordinator.close()
                }
            }
            ACTION_DISCONNECT -> {
                carWake.value = false
                lifecycleScope.launch { bleRepository.disconnect() }
            }
            ACTION_CONNECT -> {
                carWake.value = false
                lifecycleScope.launch {
                    val settings = settingsStore.settings.first()
                    val device = RememberedDeviceConnector.defaultDevice(settings) ?: return@launch
                    bleRepository.connect(device, userInitiated = true)
                }
            }
            ACTION_PAUSE_DRIVE_MODE -> {
                carWake.value = false
                lifecycleScope.launch {
                    settingsStore.setAutomationPaused(true)
                }
            }
            ACTION_RESUME_DRIVE_MODE -> {
                carWake.value = false
                lifecycleScope.launch {
                    settingsStore.setAutomationPaused(false)
                    connectSessionId += 1
                    driveModeEngine.onConnectReady(connectSessionId, lifecycleScope)
                }
            }
            ACTION_STOP -> {
                carWake.value = false
                lifecycleScope.launch { bleRepository.disconnect() }
                stopForeground(Service.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_START, null -> carWake.value = wokeFromCar
        }
        return START_STICKY
    }

    /**
     * A lost receiver is not a live session. Drop the foreground service and leave a dismissible
     * notification unless the user asked for an occasional scan, which needs the service.
     */
    private fun publishNotification(notification: Notification, quietAway: Boolean) {
        if (!quietAway) {
            detachedForAway = false
            startForegroundSafely(notification)
            return
        }
        if (!detachedForAway) {
            if (!startForegroundSafely(notification)) return
            stopForeground(STOP_FOREGROUND_DETACH)
            detachedForAway = true
        } else {
            getSystemService(NotificationManager::class.java)
                .notify(SoundKitNotificationFactory.NOTIFICATION_ID, notification)
        }
    }

    /**
     * Satisfies the startForegroundService contract for this start, then restores a detached
     * Away card. Returns false when the service could not become foreground and is stopping.
     */
    private fun promoteToForeground(): Boolean {
        val notification = lastPublished ?: notificationFactory.build(
            connectionState = bleRepository.connectionState.value,
            valveState = bleRepository.valveState.value,
            receiverStatusMessage = bleRepository.receiverStatusMessage.value,
        )
        if (!startForegroundSafely(notification)) return false
        if (detachedForAway && lastPublishedQuietAway) {
            stopForeground(STOP_FOREGROUND_DETACH)
        } else {
            detachedForAway = false
        }
        return true
    }

    /**
     * Android 14+ rejects a connectedDevice foreground service without Bluetooth permission, and
     * Android 12+ rejects background starts. Either must stop the service, not crash the process.
     */
    private fun startForegroundSafely(notification: Notification): Boolean {
        if (!hasForegroundPrerequisites(this)) {
            diagnosticsRepository.warning("Connection service stopped: Bluetooth permission is not granted")
            stopSelfAfterFailedPromotion()
            return false
        }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    SoundKitNotificationFactory.NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
                )
            } else {
                startForeground(SoundKitNotificationFactory.NOTIFICATION_ID, notification)
            }
            true
        } catch (error: SecurityException) {
            diagnosticsRepository.error("Connection service could not start in the foreground", error)
            stopSelfAfterFailedPromotion()
            false
        } catch (error: IllegalStateException) {
            diagnosticsRepository.error("Connection service could not start in the foreground", error)
            stopSelfAfterFailedPromotion()
            false
        }
    }

    private fun stopSelfAfterFailedPromotion() {
        notificationsSuppressed = true
        stopSelf()
    }

    override fun onDestroy() {
        // A stopped service no longer pushes widget state, so the last live valve label must go.
        SoundKitWidgetProvider.requestUpdate(context = this)
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.akrapovic.soundkit.community.action.START"
        const val ACTION_OPEN = "com.akrapovic.soundkit.community.action.OPEN"
        const val ACTION_CLOSE = "com.akrapovic.soundkit.community.action.CLOSE"
        const val ACTION_DISCONNECT = "com.akrapovic.soundkit.community.action.DISCONNECT"
        const val ACTION_PAUSE_DRIVE_MODE = "com.akrapovic.soundkit.community.action.PAUSE_DRIVE_MODE"
        const val ACTION_RESUME_DRIVE_MODE = "com.akrapovic.soundkit.community.action.RESUME_DRIVE_MODE"
        const val ACTION_CONNECT = "com.akrapovic.soundkit.community.action.CONNECT"
        const val ACTION_DISMISS_AWAY = "com.akrapovic.soundkit.community.action.DISMISS_AWAY"
        const val ACTION_STOP = "com.akrapovic.soundkit.community.action.STOP"
        private const val EXTRA_CAR_WAKE = "car_wake"
        private const val HOLD_TICK_MS = 15_000L

        @Deprecated("Use ACTION_PAUSE_DRIVE_MODE", ReplaceWith("ACTION_PAUSE_DRIVE_MODE"))
        const val ACTION_PAUSE_AUTOMATION = ACTION_PAUSE_DRIVE_MODE

        @Deprecated("Use ACTION_RESUME_DRIVE_MODE", ReplaceWith("ACTION_RESUME_DRIVE_MODE"))
        const val ACTION_RESUME_AUTOMATION = ACTION_RESUME_DRIVE_MODE

        /** @return false when the service was not started; callers may retry later. */
        fun start(context: Context): Boolean {
            val intent = Intent(context, BleConnectionService::class.java).setAction(ACTION_START)
            return startForegroundServiceSafely(context, intent)
        }

        fun startFromCarWake(context: Context): Boolean {
            val intent = Intent(context, BleConnectionService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_CAR_WAKE, true)
            return startForegroundServiceSafely(context, intent)
        }

        fun startAction(context: Context, action: String): Boolean {
            val intent = Intent(context, BleConnectionService::class.java).setAction(action)
            return startForegroundServiceSafely(context, intent)
        }

        /** The connectedDevice type needs Bluetooth connect permission on Android 12+. */
        fun hasForegroundPrerequisites(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
            return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        }

        private fun startForegroundServiceSafely(context: Context, intent: Intent): Boolean {
            if (!hasForegroundPrerequisites(context)) return false
            return try {
                ContextCompat.startForegroundService(context, intent)
                true
            } catch (_: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException: the app is in the background.
                false
            } catch (_: SecurityException) {
                false
            }
        }
    }
}

private data class NotificationSnapshot(
    val connectionState: ConnectionState,
    val valveState: com.akrapovic.soundkit.community.domain.ValveState,
    val receiverStatusMessage: String?,
    val settings: com.akrapovic.soundkit.community.domain.SoundKitSettings,
    val lastExecution: com.akrapovic.soundkit.community.domain.RuleExecutionEntry?,
)

private data class PublishedNotification(
    val notification: Notification,
    val key: String,
    val quietAway: Boolean,
    val release: Boolean,
)
