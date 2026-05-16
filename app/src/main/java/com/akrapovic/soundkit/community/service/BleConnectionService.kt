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
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BleConnectionService : LifecycleService() {
    @Inject lateinit var bleRepository: BleRepository
    @Inject lateinit var notificationFactory: SoundKitNotificationFactory

    override fun onCreate() {
        super.onCreate()
        notificationFactory.ensureChannel()
        lifecycleScope.launch {
            combine(
                bleRepository.connectionState,
                bleRepository.valveState,
            ) { connectionState, valveState ->
                notificationFactory.build(connectionState, valveState)
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_OPEN -> lifecycleScope.launch { bleRepository.openValve() }
            ACTION_CLOSE -> lifecycleScope.launch { bleRepository.closeValve() }
            ACTION_DISCONNECT -> lifecycleScope.launch { bleRepository.disconnect() }
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
        const val ACTION_STOP = "com.akrapovic.soundkit.community.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, BleConnectionService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}

