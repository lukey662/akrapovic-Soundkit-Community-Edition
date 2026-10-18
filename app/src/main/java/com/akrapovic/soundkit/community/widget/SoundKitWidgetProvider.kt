package com.akrapovic.soundkit.community.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.akrapovic.soundkit.community.MainActivity
import com.akrapovic.soundkit.community.R
import com.akrapovic.soundkit.community.domain.CommandSource
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.ValveState
import com.akrapovic.soundkit.community.service.BleConnectionService
import com.akrapovic.soundkit.community.service.QsTilePresenter
import com.akrapovic.soundkit.community.service.ValveTileAction
import com.akrapovic.soundkit.community.ui.components.ValveArtPalette
import com.akrapovic.soundkit.community.ui.components.ValveBitmaps
import com.akrapovic.soundkit.community.ui.theme.darkVariantFor

/**
 * Home-screen widget: the valve art, a status line, and one action.
 * The action follows the same gating as Quick Settings and always starts the foreground service.
 */
class SoundKitWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, buildViews(context))
        }
    }

    companion object {
        private const val VALVE_DP = 64

        fun requestUpdate(
            context: Context,
            connectionState: ConnectionState = ConnectionState.Disconnected,
            valveState: ValveState = ValveState.Unknown,
            receiverStatusMessage: String? = null,
            hasDefaultReceiver: Boolean = false,
            themeId: String? = null,
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
                themeId = themeId,
            )
            ids.forEach { id -> manager.updateAppWidget(id, views) }
        }

        @Suppress("UNUSED_PARAMETER")
        private fun buildViews(
            context: Context,
            connectionState: ConnectionState = ConnectionState.Disconnected,
            valveState: ValveState = ValveState.Unknown,
            receiverStatusMessage: String? = null,
            hasDefaultReceiver: Boolean = false,
            themeId: String? = null,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_soundkit)
            val theme = darkVariantFor(themeId)
            val presentation = QsTilePresenter.present(
                connectionState = connectionState,
                valveState = valveState,
                receiverStatusMessage = receiverStatusMessage,
                defaultReceiver = null,
            )
            val connected = connectionState is ConnectionState.Connected

            views.setOnClickPendingIntent(R.id.widget_root, pendingActivity(context, 10))

            val density = context.resources.displayMetrics.density
            val valve = ValveBitmaps.render(
                sizePx = (VALVE_DP * density).toInt(),
                openAmount = if (connected && valveState == ValveState.Open) 1f else 0f,
                palette = ValveArtPalette.from(theme),
                density = density,
            )
            views.setImageViewBitmap(R.id.widget_valve, valve)
            views.setInt(R.id.widget_valve, "setImageAlpha", if (connected) 255 else 128)

            val status = when {
                !connected -> "Not connected · tap to open"
                receiverStatusMessage != null -> "Receiver not ready"
                valveState == ValveState.Open -> "Valves open"
                valveState == ValveState.Closed -> "Valves closed"
                else -> "Checking the valves"
            }
            views.setTextViewText(R.id.widget_status, status)

            val action = presentation.valveAction
            if (action == null) {
                views.setViewVisibility(R.id.widget_action, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_action, View.VISIBLE)
                views.setTextViewText(
                    R.id.widget_action,
                    if (action == ValveTileAction.Open) "Open valves" else "Close valves",
                )
                val serviceAction = when (action) {
                    ValveTileAction.Open -> BleConnectionService.ACTION_OPEN
                    ValveTileAction.Close -> BleConnectionService.ACTION_CLOSE
                }
                views.setOnClickPendingIntent(R.id.widget_action, pendingService(context, serviceAction, 11))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    views.setColorStateList(
                        R.id.widget_action,
                        "setBackgroundTintList",
                        ColorStateList.valueOf(theme.button.toArgb()),
                    )
                    views.setTextColor(R.id.widget_action, theme.onButton.toArgb())
                }
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
            val intent = Intent(context, BleConnectionService::class.java)
                .setAction(action)
                .putExtra(BleConnectionService.EXTRA_SOURCE, CommandSource.Widget.name)
            return PendingIntent.getForegroundService(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
