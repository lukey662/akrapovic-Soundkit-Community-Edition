package com.akrapovic.soundkit.community.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.akrapovic.soundkit.community.MainActivity
import com.akrapovic.soundkit.community.R
import com.akrapovic.soundkit.community.service.BleConnectionService

class SoundKitWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, buildViews(context))
        }
    }

    companion object {
        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, SoundKitWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return
            ids.forEach { id ->
                manager.updateAppWidget(id, buildViews(context))
            }
        }

        private fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_soundkit)
            views.setOnClickPendingIntent(
                R.id.widget_open_app,
                pendingActivity(context, 10),
            )
            views.setOnClickPendingIntent(
                R.id.widget_open_valves,
                pendingService(context, BleConnectionService.ACTION_OPEN, 11),
            )
            views.setOnClickPendingIntent(
                R.id.widget_close_valves,
                pendingService(context, BleConnectionService.ACTION_CLOSE, 12),
            )
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
            val intent = Intent(context, BleConnectionService::class.java).setAction(action)
            return PendingIntent.getService(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
