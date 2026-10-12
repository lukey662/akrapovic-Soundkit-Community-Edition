package com.akrapovic.soundkit.community.service

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.akrapovic.soundkit.community.car.CarPresenceSource
import com.akrapovic.soundkit.community.car.CarSessionTracker
import com.akrapovic.soundkit.community.data.BleRepository
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

    private var connectSessionId = 0L
    private var lastNotificationKey: String? = null
    private var detachedForAway = false
    private val carWake = MutableStateFlow(false)
    private var notificationsSuppressed = false

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
                combine(carWake, bleRepository.startupSettled) { wake, settled -> wake to settled },
            ) { snapshot, quietHoldEndsAt, presence, sessionActive, wakeAndSettled ->
                val (wake, settled) = wakeAndSettled
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    SoundKitNotificationFactory.NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
                )
            } else {
                startForeground(SoundKitNotificationFactory.NOTIFICATION_ID, notification)
            }
            return
        }
        if (!detachedForAway) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    SoundKitNotificationFactory.NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
                )
            } else {
                startForeground(SoundKitNotificationFactory.NOTIFICATION_ID, notification)
            }
            stopForeground(STOP_FOREGROUND_DETACH)
            detachedForAway = true
        } else {
            getSystemService(NotificationManager::class.java)
                .notify(SoundKitNotificationFactory.NOTIFICATION_ID, notification)
        }
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

        @Deprecated("Use ACTION_PAUSE_DRIVE_MODE", ReplaceWith("ACTION_PAUSE_DRIVE_MODE"))
        const val ACTION_PAUSE_AUTOMATION = ACTION_PAUSE_DRIVE_MODE

        @Deprecated("Use ACTION_RESUME_DRIVE_MODE", ReplaceWith("ACTION_RESUME_DRIVE_MODE"))
        const val ACTION_RESUME_AUTOMATION = ACTION_RESUME_DRIVE_MODE

        fun start(context: Context) {
            val intent = Intent(context, BleConnectionService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun startFromCarWake(context: Context) {
            val intent = Intent(context, BleConnectionService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_CAR_WAKE, true)
            ContextCompat.startForegroundService(context, intent)
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
