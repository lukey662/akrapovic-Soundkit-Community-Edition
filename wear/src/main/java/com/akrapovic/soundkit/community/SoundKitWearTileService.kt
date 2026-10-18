package com.akrapovic.soundkit.community

import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Chip
import androidx.wear.protolayout.material.ChipColors
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.remote.interactions.RemoteActivityHelper
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Valve tile: shows the state the phone last published and sends Open/Close to the phone,
 * which runs them through its command coordinator. Use only while parked.
 */
class SoundKitWearTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        when (requestParams.currentState.lastClickableId) {
            CLICK_OPEN -> sendToPhone(WearProtocol.PATH_OPEN)
            CLICK_CLOSE -> sendToPhone(WearProtocol.PATH_CLOSE)
            CLICK_PHONE -> openPhoneApp()
        }
        val state = WearStateStore.read(this)
        val layout = layout(state, requestParams.deviceConfiguration)
        return Futures.immediateFuture(
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setFreshnessIntervalMillis(60_000)
                .setTileTimeline(
                    TimelineBuilders.Timeline.Builder()
                        .addTimelineEntry(
                            TimelineBuilders.TimelineEntry.Builder()
                                .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(layout).build())
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
            ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build(),
        )
    }

    private fun layout(state: WearState, device: DeviceParameters): LayoutElementBuilders.LayoutElement {
        val now = System.currentTimeMillis()
        val headline = when {
            !state.connected -> "Phone not connected"
            !state.ready -> "Checking"
            state.valve == "OPEN" -> "Open"
            state.valve == "CLOSED" -> "Closed"
            else -> "Checking"
        }
        val detail = when {
            state.lastResult != null && now - state.lastResultAt < RESULT_FRESH_MS -> state.lastResult
            state.updatedAt == 0L -> "Waiting for your phone"
            now - state.updatedAt > STALE_MS -> "Last known"
            now - state.updatedAt < 60_000L -> "Updated just now"
            else -> "Updated ${(now - state.updatedAt) / 60_000L} min ago"
        }
        val (chipLabel, chipId) = when {
            !state.connected || !state.ready -> "Open phone app" to CLICK_PHONE
            state.valve == "OPEN" -> "Close" to CLICK_CLOSE
            else -> "Open" to CLICK_OPEN
        }
        val glow = argb(state.glow)
        val chip = Chip.Builder(
            this,
            ModifiersBuilders.Clickable.Builder()
                .setId(chipId)
                .setOnClick(ActionBuilders.LoadAction.Builder().build())
                .build(),
            device,
        )
            .setPrimaryLabelContent(chipLabel)
            .setChipColors(
                if (chipId == CLICK_PHONE) {
                    ChipColors(argb(0xFF202124.toInt()), argb(0xFFFFFFFF.toInt()))
                } else {
                    ChipColors(glow, argb(0xFF0A0A0A.toInt()))
                },
            )
            .build()
        val content = LayoutElementBuilders.Column.Builder()
            .addContent(
                Text.Builder(this, headline)
                    .setTypography(Typography.TYPOGRAPHY_TITLE1)
                    .setColor(argb(0xFFFFFFFF.toInt()))
                    .build(),
            )
            .addContent(
                Text.Builder(this, detail)
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(argb(0xFFA1A1A8.toInt()))
                    .build(),
            )
            .build()
        return PrimaryLayout.Builder(device)
            .setPrimaryLabelTextContent(
                Text.Builder(this, "Valves")
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(glow)
                    .build(),
            )
            .setContent(content)
            .setSecondaryLabelTextContent(
                Text.Builder(this, "Use only while parked")
                    .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                    .setColor(argb(0xFF8E8E96.toInt()))
                    .build(),
            )
            .setPrimaryChipContent(chip)
            .build()
    }

    private fun sendToPhone(path: String) {
        WearStateStore.writeResult(this, "Sending…")
        val messageClient = Wearable.getMessageClient(this)
        Wearable.getCapabilityClient(this)
            .getCapability(WearProtocol.CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE)
            .addOnSuccessListener { info ->
                if (info.nodes.isEmpty()) {
                    WearStateStore.writeResult(this, "Phone not reachable")
                    getUpdater(this).requestUpdate(SoundKitWearTileService::class.java)
                }
                info.nodes.forEach { node -> messageClient.sendMessage(node.id, path, ByteArray(0)) }
            }
            .addOnFailureListener {
                WearStateStore.writeResult(this, "Phone not reachable")
                getUpdater(this).requestUpdate(SoundKitWearTileService::class.java)
            }
    }

    private fun openPhoneApp() {
        val intent = Intent(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(Uri.parse("soundkit://open"))
        RemoteActivityHelper(this, ContextCompat.getMainExecutor(this)).startRemoteActivity(intent)
    }

    companion object {
        private const val RESOURCES_VERSION = "1"
        private const val CLICK_OPEN = "open"
        private const val CLICK_CLOSE = "close"
        private const val CLICK_PHONE = "phone"
        private const val RESULT_FRESH_MS = 30_000L
        private const val STALE_MS = 10L * 60L * 1000L
    }
}
