package com.akrapovic.soundkit.community.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.akrapovic.soundkit.community.MainActivity
import com.akrapovic.soundkit.community.R
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.ValveState
import com.akrapovic.soundkit.community.service.BleConnectionService
import com.akrapovic.soundkit.community.service.QsTilePresenter
import com.akrapovic.soundkit.community.service.ValveTileAction

/**
 * Home-screen widget. Valve actions always bootstrap the foreground service first,
 * and are disabled unless the same gating rules as Quick Settings allow a command.
 */
class SoundKitWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, buildViews(context))
        }
    }

    companion object {
        fun requestUpdate(
            context: Context,
            connectionState: ConnectionState = ConnectionState.Disconnected,
            valveState: ValveState = ValveState.Unknown,
            receiverStatusMessage: String? = null,
            hasDefaultReceiver: Boolean = false,
        ) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, SoundKitWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return
            val views = buildViews(
                context = context,
                connectionState = connectionState,
                valveState = valveState,
                receiverStatusMessage = receiverStatusMessage,
                hasDefaultReceiver = hasDefaultReceiver,
            )
            ids.forEach { id -> manager.updateAppWidget(id, views) }
        }

        private fun buildViews(
            context: Context,
            connectionState: ConnectionState = ConnectionState.Disconnected,
            valveState: ValveState = ValveState.Unknown,
            receiverStatusMessage: String? = null,
            hasDefaultReceiver: Boolean = false,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_soundkit)
            views.setOnClickPendingIntent(
                R.id.widget_open_app,
                pendingActivity(context, 10),
            )

            val presentation = QsTilePresenter.present(
                connectionState = connectionState,
                valveState = valveState,
                receiverStatusMessage = receiverStatusMessage,
                defaultReceiver = if (hasDefaultReceiver) {
                    com.akrapovic.soundkit.community.domain.SavedReceiver(
                        address = "widget",
                        name = "Sound Kit",
                        isDefault = true,
                    )
                } else {
                    null
                },
            )
            val canOpen = presentation.valveAction == ValveTileAction.Open
            val canClose = presentation.valveAction == ValveTileAction.Close

            views.setViewVisibility(R.id.widget_open_valves, if (canOpen || canClose) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_close_valves, if (canOpen || canClose) View.VISIBLE else View.GONE)
            views.setBoolean(R.id.widget_open_valves, "setEnabled", canOpen)
            views.setBoolean(R.id.widget_close_valves, "setEnabled", canClose)
            views.setTextViewText(
                R.id.widget_status,
                presentation.subtitle,
            )

            if (canOpen) {
                views.setOnClickPendingIntent(
                    R.id.widget_open_valves,
                    pendingService(context, BleConnectionService.ACTION_OPEN, 11),
                )
            } else {
                views.setOnClickPendingIntent(R.id.widget_open_valves, pendingActivity(context, 11))
            }
            if (canClose) {
                views.setOnClickPendingIntent(
                    R.id.widget_close_valves,
                    pendingService(context, BleConnectionService.ACTION_CLOSE, 12),
                )
            } else {
                views.setOnClickPendingIntent(R.id.widget_close_valves, pendingActivity(context, 12))
            }
            return views
        }

        private fun pendingActivity(context: Context, requestCode: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun pendingService(context: Context, action: String, requestCode: Int): PendingIntent {
            // Always start as a foreground service so cold-process widget taps are legal on Android 12+.
            val intent = Intent(context, BleConnectionService::class.java).setAction(action)
            return PendingIntent.getForegroundService(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
