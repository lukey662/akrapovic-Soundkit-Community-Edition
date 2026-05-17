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
import com.akrapovic.soundkit.community.data.RulesStore
import com.akrapovic.soundkit.community.data.SettingsStore
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.ValveState
import com.akrapovic.soundkit.community.domain.rules.RuleExecutionEngine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BleConnectionService : LifecycleService() {
    @Inject lateinit var bleRepository: BleRepository
    @Inject lateinit var settingsStore: SettingsStore
    @Inject lateinit var rulesStore: RulesStore
    @Inject lateinit var executionLog: RuleExecutionLogStore
    @Inject lateinit var notificationFactory: SoundKitNotificationFactory
    @Inject lateinit var ruleExecutionEngine: RuleExecutionEngine

    override fun onCreate() {
        super.onCreate()
        notificationFactory.ensureChannel()
        lifecycleScope.launch {
            val bleAndSettings = combine(
                bleRepository.connectionState,
                bleRepository.valveState,
                bleRepository.receiverStatusMessage,
                settingsStore.settings,
            ) { connectionState, valveState, receiverStatusMessage, settings ->
                NotificationBleSnapshot(connectionState, valveState, receiverStatusMessage, settings)
            }
            combine(bleAndSettings, rulesStore.rules, executionLog.lastExecution) { snapshot, rules, lastExecution ->
                notificationFactory.build(
                    connectionState = snapshot.connectionState,
                    valveState = snapshot.valveState,
                    receiverStatusMessage = snapshot.receiverStatusMessage,
                    defaultReceiver = snapshot.settings.defaultReceiver,
                    automationPaused = snapshot.settings.automationPaused,
                    lastExecution = lastExecution,
                    hasAutomationRules = rules.any { it.enabled },
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
                    if (connection is ConnectionState.Connected &&
                        valve != ValveState.Unknown &&
                        notReady == null
                    ) {
                        ruleExecutionEngine.evaluateNow(triggerReason = "connection")
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
            ACTION_PAUSE_AUTOMATION -> lifecycleScope.launch {
                settingsStore.setAutomationPaused(true)
            }
            ACTION_RESUME_AUTOMATION -> lifecycleScope.launch {
                settingsStore.setAutomationPaused(false)
                ruleExecutionEngine.evaluateNow(triggerReason = "resume")
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

    private data class NotificationBleSnapshot(
        val connectionState: ConnectionState,
        val valveState: ValveState,
        val receiverStatusMessage: String?,
        val settings: com.akrapovic.soundkit.community.domain.SoundKitSettings,
    )

    companion object {
        const val ACTION_START = "com.akrapovic.soundkit.community.action.START"
        const val ACTION_OPEN = "com.akrapovic.soundkit.community.action.OPEN"
        const val ACTION_CLOSE = "com.akrapovic.soundkit.community.action.CLOSE"
        const val ACTION_DISCONNECT = "com.akrapovic.soundkit.community.action.DISCONNECT"
        const val ACTION_PAUSE_AUTOMATION = "com.akrapovic.soundkit.community.action.PAUSE_AUTOMATION"
        const val ACTION_RESUME_AUTOMATION = "com.akrapovic.soundkit.community.action.RESUME_AUTOMATION"
        const val ACTION_STOP = "com.akrapovic.soundkit.community.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, BleConnectionService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
