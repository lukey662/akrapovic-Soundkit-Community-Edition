package com.akrapovic.soundkit.community.data

import com.akrapovic.soundkit.community.domain.SavedReceiver
import org.json.JSONArray
import org.json.JSONObject

object SavedReceiversCodec {
    const val MAX_SAVED_RECEIVERS = 8

    fun encode(receivers: List<SavedReceiver>): String {
        val array = JSONArray()
        receivers.forEach { receiver ->
            val item = JSONObject()
                .put("address", receiver.address)
                .put("name", receiver.name)
                .put("isDefault", receiver.isDefault)
            receiver.nickname?.let { item.put("nickname", it) }
            array.put(item)
        }
        return array.toString()
    }

    fun decode(json: String?): List<SavedReceiver> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        SavedReceiver(
                            address = item.getString("address"),
                            name = item.getString("name"),
                            nickname = item.optString("nickname").takeIf { it.isNotBlank() },
                            isDefault = item.optBoolean("isDefault", false),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun normalize(receivers: List<SavedReceiver>): List<SavedReceiver> {
        if (receivers.isEmpty()) return emptyList()
        val distinct = receivers.distinctBy { it.address }
        val defaultAddress = distinct.firstOrNull { it.isDefault }?.address ?: distinct.first().address
        return distinct
            .take(MAX_SAVED_RECEIVERS)
            .map { it.copy(isDefault = it.address == defaultAddress) }
            .sortedWith(compareByDescending<SavedReceiver> { it.isDefault }.thenBy { it.displayName() })
    }

    fun migrateLegacy(name: String?, address: String?): List<SavedReceiver> {
        if (address.isNullOrBlank()) return emptyList()
        return normalize(
            listOf(
                SavedReceiver(
                    address = address,
                    name = name ?: "Sound Kit",
                    isDefault = true,
                ),
            ),
        )
    }
}
