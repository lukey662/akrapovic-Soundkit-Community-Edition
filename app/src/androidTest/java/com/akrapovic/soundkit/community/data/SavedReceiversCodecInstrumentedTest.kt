package com.akrapovic.soundkit.community.data

import com.akrapovic.soundkit.community.domain.SavedReceiver
import org.junit.Assert.assertEquals
import org.junit.Test

class SavedReceiversCodecInstrumentedTest {
    @Test
    fun decodeParsesSavedReceiversJson() {
        val json =
            """[{"address":"aa:bb","name":"Kit A","nickname":"Garage","isDefault":true},""" +
                """{"address":"cc:dd","name":"Kit B","isDefault":false}]"""
        val decoded = SavedReceiversCodec.decode(json)

        assertEquals(2, decoded.size)
        assertEquals("Garage", decoded.first { it.address == "aa:bb" }.nickname)
    }

    @Test
    fun encodeDecodeRoundTrip() {
        val receivers = listOf(
            SavedReceiver("aa:bb", "Kit A", nickname = "Garage", isDefault = true),
            SavedReceiver("cc:dd", "Kit B", isDefault = false),
        )
        val json = SavedReceiversCodec.encode(receivers)
        val decoded = SavedReceiversCodec.decode(json)

        assertEquals(2, decoded.size)
        assertEquals("Garage", decoded.first { it.address == "aa:bb" }.nickname)
    }
}
