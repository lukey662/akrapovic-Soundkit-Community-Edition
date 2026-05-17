package com.akrapovic.soundkit.community.data

import com.akrapovic.soundkit.community.domain.GeofenceZone
import org.json.JSONArray
import org.json.JSONObject

object GeofenceZonesCodec {
    const val MAX_ZONES = 4

    fun encode(zones: List<GeofenceZone>): String {
        val array = JSONArray()
        zones.forEach { zone ->
            array.put(
                JSONObject()
                    .put("id", zone.id)
                    .put("name", zone.name)
                    .put("latitude", zone.latitude)
                    .put("longitude", zone.longitude)
                    .put("radiusMeters", zone.radiusMeters.toDouble()),
            )
        }
        return array.toString()
    }

    fun decode(json: String?): List<GeofenceZone> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        GeofenceZone(
                            id = item.getString("id"),
                            name = item.getString("name"),
                            latitude = item.getDouble("latitude"),
                            longitude = item.getDouble("longitude"),
                            radiusMeters = item.getDouble("radiusMeters").toFloat(),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun normalize(zones: List<GeofenceZone>): List<GeofenceZone> =
        zones.distinctBy { it.id }.take(MAX_ZONES)
}
