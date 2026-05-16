package com.akrapovic.soundkit.community.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.akrapovic.soundkit.community.MainActivity
import com.akrapovic.soundkit.community.R
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.ValveState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class SoundKitNotificationFactory @Inject constructor(
    @ApplicationContext
    private val context: Context,
) {
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun build(
        connectionState: ConnectionState,
        valveState: ValveState,
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Sound Kit Community")
            .setContentText("${connectionState.asNotificationText()} · valve ${valveState.name.lowercase()}")
            .setContentIntent(contentIntent)
            .setOngoing(connectionState is ConnectionState.Connected || connectionState is ConnectionState.Connecting)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(action(R.string.notification_action_open, BleConnectionService.ACTION_OPEN, 1))
            .addAction(action(R.string.notification_action_close, BleConnectionService.ACTION_CLOSE, 2))
            .addAction(action(R.string.notification_action_disconnect, BleConnectionService.ACTION_DISCONNECT, 3))
            .build()
    }

    private fun action(labelRes: Int, action: String, requestCode: Int): NotificationCompat.Action {
        val intent = PendingIntent.getService(
            context,
            requestCode,
            Intent(context, BleConnectionService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(
            R.drawable.ic_valve_tile,
            context.getString(labelRes),
            intent,
        ).build()
    }

    private fun ConnectionState.asNotificationText(): String {
        return when (this) {
            ConnectionState.Disconnected -> "Disconnected"
            ConnectionState.Scanning -> "Scanning"
            is ConnectionState.Connecting -> "Connecting to ${device.name}"
            is ConnectionState.Connected -> "Connected to ${device.name}"
            is ConnectionState.Reconnecting -> "Reconnecting attempt $attempt"
            is ConnectionState.Error -> "Error: $message"
        }
    }

    companion object {
        const val CHANNEL_ID = "soundkit_connection"
        const val NOTIFICATION_ID = 42
    }
}

