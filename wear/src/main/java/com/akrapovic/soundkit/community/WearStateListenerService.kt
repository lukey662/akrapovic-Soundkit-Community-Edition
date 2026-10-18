package com.akrapovic.soundkit.community

import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/** Stores state and results from the phone, then asks the tile to redraw. */
class WearStateListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        var changed = false
        dataEvents.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach
            if (event.dataItem.uri.path != WearProtocol.PATH_STATE) return@forEach
            val map = DataMapItem.fromDataItem(event.dataItem).dataMap
            WearStateStore.writeState(
                context = this,
                connected = map.getBoolean(WearProtocol.KEY_CONNECTED),
                ready = map.getBoolean(WearProtocol.KEY_READY),
                valve = map.getString(WearProtocol.KEY_VALVE) ?: "UNKNOWN",
                glow = map.getInt(WearProtocol.KEY_GLOW, WearStateStore.DEFAULT_GLOW),
                updatedAt = map.getLong(WearProtocol.KEY_UPDATED_AT),
            )
            changed = true
        }
        if (changed) requestTileUpdate()
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != WearProtocol.PATH_RESULT) return
        WearStateStore.writeResult(this, String(messageEvent.data, Charsets.UTF_8))
        requestTileUpdate()
    }

    private fun requestTileUpdate() {
        TileService.getUpdater(this).requestUpdate(SoundKitWearTileService::class.java)
    }
}
