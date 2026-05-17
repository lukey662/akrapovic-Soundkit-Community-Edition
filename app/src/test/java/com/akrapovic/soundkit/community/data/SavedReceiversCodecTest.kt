package com.akrapovic.soundkit.community.data

import com.akrapovic.soundkit.community.domain.SavedReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedReceiversCodecTest {
    @Test
    fun normalizeEnforcesSingleDefaultAndCap() {
        val receivers = (1..10).map { index ->
            SavedReceiver(
                address = "addr-$index",
                name = "Kit $index",
                isDefault = index == 3,
            )
        }
        val normalized = SavedReceiversCodec.normalize(receivers)

        assertEquals(SavedReceiversCodec.MAX_SAVED_RECEIVERS, normalized.size)
        assertEquals(1, normalized.count { it.isDefault })
        assertTrue(normalized.first().isDefault)
    }

    @Test
    fun migrateLegacyCreatesDefaultEntry() {
        val migrated = SavedReceiversCodec.migrateLegacy("Legacy", "11:22:33:44:55:66")

        assertEquals(1, migrated.size)
        assertEquals("11:22:33:44:55:66", migrated.single().address)
        assertTrue(migrated.single().isDefault)
    }

    @Test
    fun migrateLegacyReturnsEmptyWhenAddressMissing() {
        assertTrue(SavedReceiversCodec.migrateLegacy("Name", null).isEmpty())
    }

    @Test
    fun normalizePicksFirstWhenNoDefaultFlag() {
        val normalized = SavedReceiversCodec.normalize(
            listOf(
                SavedReceiver("b", "B", isDefault = false),
                SavedReceiver("a", "A", isDefault = false),
            ),
        )

        assertEquals("b", normalized.first { it.isDefault }.address)
        assertFalse(normalized.any { it.isDefault && it.address != "b" })
    }
}
