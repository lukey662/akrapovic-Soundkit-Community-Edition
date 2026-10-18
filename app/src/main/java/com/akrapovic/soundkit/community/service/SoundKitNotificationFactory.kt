package com.akrapovic.soundkit.community.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.akrapovic.soundkit.community.MainActivity
import com.akrapovic.soundkit.community.R
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.RuleExecutionEntry
import com.akrapovic.soundkit.community.domain.SavedReceiver
import com.akrapovic.soundkit.community.domain.ValveState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
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
        receiverStatusMessage: String? = null,
        defaultReceiver: SavedReceiver? = null,
        automationPaused: Boolean = false,
        lastExecution: RuleExecutionEntry? = null,
        driveModeEnabled: Boolean = true,
        driveModePaused: Boolean = automationPaused,
        periodicScanWhenAway: Boolean = false,
        quietHoldEndsAtMillis: Long? = null,
        quietHoldMinutes: Int = 0,
        nowMillis: Long = System.currentTimeMillis(),
    ): Notification {
        val presentation = NotificationCopy.build(
            connectionState = connectionState,
            valveState = valveState,
            receiverStatusMessage = receiverStatusMessage,
            defaultReceiver = defaultReceiver,
            driveModeEnabled = driveModeEnabled,
            driveModePaused = driveModePaused,
            lastExecution = lastExecution,
            periodicScanWhenAway = periodicScanWhenAway,
            quietHoldEndsAtMillis = quietHoldEndsAtMillis,
        )
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_NAV_HOME, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(presentation.title)
            .setContentText(presentation.contentText)
            .setContentIntent(contentIntent)
            .setOngoing(presentation.ongoing)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        presentation.chronometerEndsAtMillis?.let { endsAt ->
            builder.setWhen(endsAt)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        }
        if (presentation.dismissibleAway) {
            builder.setDeleteIntent(
                PendingIntent.getService(
                    context,
                    7,
                    Intent(context, BleConnectionService::class.java)
                        .setAction(BleConnectionService.ACTION_DISMISS_AWAY),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }

        if (presentation.connectEnabled) {
            builder.addAction(action(R.string.notification_action_connect, BleConnectionService.ACTION_CONNECT, 6))
        }
        if (presentation.retryEnabled) {
            builder.addAction(action(R.string.notification_action_retry, BleConnectionService.ACTION_CONNECT, 8))
        }
        if (presentation.openValveEnabled) {
            builder.addAction(action(R.string.notification_action_open, BleConnectionService.ACTION_OPEN, 1))
        }
        if (presentation.closeValveEnabled) {
            builder.addAction(action(R.string.notification_action_close, BleConnectionService.ACTION_CLOSE, 2))
        }
        if (presentation.disconnectEnabled) {
            builder.addAction(
                action(R.string.notification_action_disconnect, BleConnectionService.ACTION_DISCONNECT, 3),
            )
        }
        if (presentation.pauseDriveModeEnabled) {
            builder.addAction(
                action(R.string.notification_action_pause_drive_mode, BleConnectionService.ACTION_PAUSE_DRIVE_MODE, 4),
            )
        }
        if (presentation.resumeDriveModeEnabled) {
            builder.addAction(
                action(
                    R.string.notification_action_resume_drive_mode,
                    BleConnectionService.ACTION_RESUME_DRIVE_MODE,
                    5,
                ),
            )
        }
        val notification = builder.build()
        val endsAt = presentation.chronometerEndsAtMillis
        if (endsAt != null && quietHoldMinutes > 0 && Build.VERSION.SDK_INT >= 36) {
            return quietStartProgress(notification, endsAt, quietHoldMinutes, nowMillis)
        }
        return notification
    }

    /**
     * Android 16 progress-style countdown for a quiet-start hold. The installed SDK has no API to
     * request promotion to a Live Update chip, so this stays an ongoing progress notification.
     */
    @RequiresApi(36)
    private fun quietStartProgress(
        notification: Notification,
        endsAtMillis: Long,
        holdMinutes: Int,
        nowMillis: Long,
    ): Notification {
        val totalSeconds = holdMinutes * 60
        val remaining = ((endsAtMillis - nowMillis) / 1_000L).toInt().coerceIn(0, totalSeconds)
        val style = Notification.ProgressStyle()
            .setProgressSegments(listOf(Notification.ProgressStyle.Segment(totalSeconds)))
            .setProgress(totalSeconds - remaining)
            .setProgressTrackerIcon(Icon.createWithResource(context, R.drawable.ic_valve_tile))
        return Notification.Builder.recoverBuilder(context, notification)
            .setStyle(style)
            .setShortCriticalText(String.format(Locale.US, "Opens %d:%02d", remaining / 60, remaining % 60))
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

    companion object {
        const val CHANNEL_ID = "soundkit_connection"
        const val NOTIFICATION_ID = 42
    }
}
