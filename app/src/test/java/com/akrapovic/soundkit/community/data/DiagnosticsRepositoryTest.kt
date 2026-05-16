package com.akrapovic.soundkit.community.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticsRepositoryTest {

    @Test
    fun entriesReceiveUniqueIdsForLazyListKeys() {
        val repository = DiagnosticsRepository()
        repeat(5) { repository.debug("event-$it") }
        val ids = repository.entries.value.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }
}
