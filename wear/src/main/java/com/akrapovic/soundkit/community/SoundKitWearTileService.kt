package com.akrapovic.soundkit.community

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.akrapovic.soundkit.community.wear.BuildConfig
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class SoundKitWearTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val phoneConnectIntent = Intent(ACTION_SHORTCUT_CONNECT)
            .setPackage(BuildConfig.PHONE_PACKAGE)
            .setClassName(BuildConfig.PHONE_PACKAGE, MAIN_ACTIVITY_CLASS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val phoneConnectPendingIntent = PendingIntent.getActivity(
            this,
            0,
            phoneConnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return Futures.immediateFuture(
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(
                    TimelineBuilders.Timeline.Builder()
                        .addTimelineEntry(
                            TimelineBuilders.TimelineEntry.Builder()
                                .setLayout(
                                    LayoutElementBuilders.Layout.Builder()
                                        .setRoot(
                                            LayoutElementBuilders.Column.Builder()
                                                .setModifiers(
                                                    ModifiersBuilders.Modifiers.Builder()
                                                        .setClickable(
                                                            ModifiersBuilders.Clickable.Builder()
                                                                .setOnClick(phoneConnectPendingIntent)
                                                                .build(),
                                                        )
                                                        .build(),
                                                )
                                                .addContent(
                                                    LayoutElementBuilders.Text.Builder()
                                                        .setText("Connect")
                                                        .build(),
                                                )
                                                .build(),
                                        )
                                        .build(),
                                )
                                .build(),
                        )
                        .build(),
                )
                .build(),
        )
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> {
        return Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .build(),
        )
    }

    companion object {
        private const val RESOURCES_VERSION = "0"
        const val ACTION_SHORTCUT_CONNECT =
            "com.akrapovic.soundkit.community.action.SHORTCUT_CONNECT"
        private const val MAIN_ACTIVITY_CLASS =
            "com.akrapovic.soundkit.community.MainActivity"
    }
}
