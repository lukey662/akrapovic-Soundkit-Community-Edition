package com.akrapovic.soundkit.community.service

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.content.ContextCompat
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.RememberedDeviceConnector
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.akrapovic.soundkit.community.data.BleRepository
import com.akrapovic.soundkit.community.data.RuleExecutionLogStore
import com.akrapovic.soundkit.community.data.SettingsStore
import com.akrapovic.soundkit.community.domain.DriveModeEngine
import com.akrapovic.soundkit.community.domain.ValveCommandCoordinator
import com.akrapovic.soundkit.community.widget.SoundKitWidgetProvider
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
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

    private var connectSessionId = 0L
    private var lastNotificationKey: String? = null
    private var detachedForAway = false

    override fun onCreate() {
        super.onCreate()
        notificationFactory.ensureChannel()
        lifecycleScope.launch {
            combine(
                bleRepository.connectionState,
                bleRepository.valveState,
                bleRepository.receiverStatusMessage,
                settingsStore.settings,
                executionLog.lastExecution,
            ) { connectionState, valveState, receiverStatusMessage, settings, lastExecution ->
                val notification = notificationFactory.build(
                    connectionState = connectionState,
                    valveState = valveState,
                    receiverStatusMessage = receiverStatusMessage,
                    defaultReceiver = settings.defaultReceiver,
                    driveModeEnabled = settings.driveModeEnabled,
                    driveModePaused = settings.automationPaused,
                    lastExecution = lastExecution,
                    periodicScanWhenAway = settings.periodicScanWhenAway,
                )
                val key = listOf(
                    connectionState.toString(),
                    valveState.name,
                    receiverStatusMessage.orEmpty(),
                    settings.defaultReceiver?.address.orEmpty(),
                    settings.driveModeEnabled.toString(),
                    settings.automationPaused.toString(),
                    settings.periodicScanWhenAway.toString(),
                    lastExecution?.timestampMillis?.toString().orEmpty(),
                ).joinToString("|")
                val quietAway = connectionState is ConnectionState.Away && !settings.periodicScanWhenAway
                Triple(notification, key, quietAway)
            }.collect { (notification, key, quietAway) ->
                if (key == lastNotificationKey) return@collect
                lastNotificationKey = key
                publishNotification(notification, quietAway)
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
        when (intent?.action) {
            ACTION_OPEN -> lifecycleScope.launch {
                driveModeEngine.onUserValveAdjustment()
                valveCommandCoordinator.open()
            }
            ACTION_CLOSE -> lifecycleScope.launch {
                driveModeEngine.onUserValveAdjustment()
                valveCommandCoordinator.close()
            }
            ACTION_DISCONNECT -> lifecycleScope.launch { bleRepository.disconnect() }
            ACTION_CONNECT -> lifecycleScope.launch {
                val settings = settingsStore.settings.first()
                val device = RememberedDeviceConnector.defaultDevice(settings) ?: return@launch
                bleRepository.connect(device, userInitiated = true)
            }
            ACTION_PAUSE_DRIVE_MODE -> lifecycleScope.launch {
                settingsStore.setAutomationPaused(true)
            }
            ACTION_RESUME_DRIVE_MODE -> lifecycleScope.launch {
                settingsStore.setAutomationPaused(false)
                connectSessionId += 1
                driveModeEngine.onConnectReady(connectSessionId, lifecycleScope)
            }
            ACTION_STOP -> {
                lifecycleScope.launch { bleRepository.disconnect() }
                stopForeground(Service.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_START, null -> Unit
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
        const val ACTION_STOP = "com.akrapovic.soundkit.community.action.STOP"

        @Deprecated("Use ACTION_PAUSE_DRIVE_MODE", ReplaceWith("ACTION_PAUSE_DRIVE_MODE"))
        const val ACTION_PAUSE_AUTOMATION = ACTION_PAUSE_DRIVE_MODE

        @Deprecated("Use ACTION_RESUME_DRIVE_MODE", ReplaceWith("ACTION_RESUME_DRIVE_MODE"))
        const val ACTION_RESUME_AUTOMATION = ACTION_RESUME_DRIVE_MODE

        fun start(context: Context) {
            val intent = Intent(context, BleConnectionService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
