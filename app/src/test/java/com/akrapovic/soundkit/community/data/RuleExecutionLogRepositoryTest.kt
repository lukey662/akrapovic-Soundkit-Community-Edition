package com.akrapovic.soundkit.community.data

import com.akrapovic.soundkit.community.domain.RuleExecutionEntry
import com.akrapovic.soundkit.community.domain.RuleExecutionOutcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RuleExecutionLogRepositoryTest {
    @Test
    fun lastExecutionHydratesFromPersistedEntries() = runTest {
        val log = InMemoryRuleExecutionLog()

        log.append(
            RuleExecutionEntry(
                timestampMillis = 1L,
                ruleName = "First",
                action = "Open",
                reason = "schedule",
                outcome = RuleExecutionOutcome.Success,
            ),
        )
        log.append(
            RuleExecutionEntry(
                timestampMillis = 2L,
                ruleName = "Second",
                action = "Close",
                reason = "schedule",
                outcome = RuleExecutionOutcome.Success,
            ),
        )

        assertEquals("Second", log.lastExecution.first()?.ruleName)
    }

    private class InMemoryRuleExecutionLog : RuleExecutionLogStore {
        private val _entries = kotlinx.coroutines.flow.MutableStateFlow<List<RuleExecutionEntry>>(emptyList())
        private val _last = kotlinx.coroutines.flow.MutableStateFlow<RuleExecutionEntry?>(null)

        override val entries = _entries
        override val lastExecution = _last

        override suspend fun append(entry: RuleExecutionEntry) {
            _entries.value = (_entries.value + entry).takeLast(RuleExecutionLogRepository.MAX_ENTRIES)
            _last.value = entry
        }

        override suspend fun clear() {
            _entries.value = emptyList()
            _last.value = null
        }
    }
}
