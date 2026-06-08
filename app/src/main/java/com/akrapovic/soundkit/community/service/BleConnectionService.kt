package com.akrapovic.soundkit.community.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.akrapovic.soundkit.community.data.BleRepository
import com.akrapovic.soundkit.community.data.RuleExecutionLogStore
import com.akrapovic.soundkit.community.data.SettingsStore
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.DriveModeEngine
import com.akrapovic.soundkit.community.domain.ValveState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BleConnectionService : LifecycleService() {
    @Inject lateinit var bleRepository: BleRepository
    @Inject lateinit var settingsStore: SettingsStore
    @Inject lateinit var executionLog: RuleExecutionLogStore
    @Inject lateinit var notificationFactory: SoundKitNotificationFactory
    @Inject lateinit var driveModeEngine: DriveModeEngine

    private var connectSessionId = 0L

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
                notificationFactory.build(
                    connectionState = connectionState,
                    valveState = valveState,
                    receiverStatusMessage = receiverStatusMessage,
                    defaultReceiver = settings.defaultReceiver,
                    driveModeEnabled = settings.driveModeEnabled,
                    driveModePaused = settings.automationPaused,
                    lastExecution = lastExecution,
                )
            }.collect { notification ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        SoundKitNotificationFactory.NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
                    )
                } else {
                    startForeground(SoundKitNotificationFactory.NOTIFICATION_ID, notification)
                }
            }
        }
        lifecycleScope.launch {
            combine(
                bleRepository.connectionState,
                bleRepository.valveState,
                bleRepository.receiverStatusMessage,
            ) { connection, valve, notReady ->
                Triple(connection, valve, notReady)
            }
                .distinctUntilChanged()
                .collect { (connection, valve, notReady) ->
                    when (connection) {
                        is ConnectionState.Connected -> {
                            if (valve != ValveState.Unknown && notReady == null) {
                                connectSessionId += 1
                                driveModeEngine.onConnectReady(connectSessionId, lifecycleScope)
                            }
                        }
                        ConnectionState.Disconnected,
                        is ConnectionState.Error,
                        -> driveModeEngine.onDisconnect()
                        else -> Unit
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_OPEN -> lifecycleScope.launch { bleRepository.openValve() }
            ACTION_CLOSE -> lifecycleScope.launch { bleRepository.closeValve() }
            ACTION_DISCONNECT -> lifecycleScope.launch { bleRepository.disconnect() }
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

    companion object {
        const val ACTION_START = "com.akrapovic.soundkit.community.action.START"
        const val ACTION_OPEN = "com.akrapovic.soundkit.community.action.OPEN"
        const val ACTION_CLOSE = "com.akrapovic.soundkit.community.action.CLOSE"
        const val ACTION_DISCONNECT = "com.akrapovic.soundkit.community.action.DISCONNECT"
        const val ACTION_PAUSE_DRIVE_MODE = "com.akrapovic.soundkit.community.action.PAUSE_DRIVE_MODE"
        const val ACTION_RESUME_DRIVE_MODE = "com.akrapovic.soundkit.community.action.RESUME_DRIVE_MODE"
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
