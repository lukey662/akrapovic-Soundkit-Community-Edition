package com.akrapovic.soundkit.community.domain.rules

import com.akrapovic.soundkit.community.automation.ActiveGeofenceState
import com.akrapovic.soundkit.community.data.DiagnosticsRepository
import com.akrapovic.soundkit.community.data.RuleExecutionLogRepository
import com.akrapovic.soundkit.community.domain.CommandResult
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.RuleExecutionOutcome
import com.akrapovic.soundkit.community.domain.SoundKitSettings
import com.akrapovic.soundkit.community.domain.ValveState
import com.akrapovic.soundkit.community.test.FakeBleRepository
import com.akrapovic.soundkit.community.test.FakeSettingsStore
import com.akrapovic.soundkit.community.test.testDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleExecutionEngineTest {
    @Test
    fun skipsWhenAutomationPaused() = runTest {
        val settings = FakeSettingsStore(SoundKitSettings(automationPaused = true))
        val rules = FakeRulesStore(
            listOf(
                Rule(
                    id = "1",
                    name = "Test",
                    trigger = RuleTrigger.Schedule(setOf(0), 0, 24 * 60),
                    action = RuleAction.Open,
                ),
            ),
        )
        val log = InMemoryLog()
        val engine = engine(settings, rules, FakeBleRepository(), log)

        engine.evaluateNow()

        assertTrue(log.entries.first().isEmpty())
    }

    @Test
    fun executesOpenWhenConnectedAndScheduleMatches() = runTest {
        val device = testDevice()
        val ble = FakeBleRepository()
        ble.connectionState.value = ConnectionState.Connected(device)
        ble.valveState.value = ValveState.Closed
        ble.openResult = CompletableDeferred(CommandResult.Success(ValveState.Open))
        val settings = FakeSettingsStore()
        val rules = FakeRulesStore(
            listOf(
                Rule(
                    id = "1",
                    name = "Morning",
                    trigger = currentScheduleTrigger(),
                    action = RuleAction.Open,
                    priority = 1,
                ),
            ),
        )
        val log = InMemoryLog()
        val engine = engine(settings, rules, ble, log)

        engine.evaluateNow()

        val entries = log.entries.first()
        assertTrue(entries.isNotEmpty())
        assertEquals(RuleExecutionOutcome.Success, entries.last().outcome)
    }

    private fun currentScheduleTrigger(): RuleTrigger.Schedule {
        val calendar = java.util.Calendar.getInstance()
        val day = (calendar.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7
        val minute = calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calendar.get(java.util.Calendar.MINUTE)
        return RuleTrigger.Schedule(
            daysOfWeek = setOf(day),
            startMinuteOfDay = (minute - 30).coerceAtLeast(0),
            endMinuteOfDay = (minute + 30).coerceAtMost(24 * 60 - 1),
        )
    }

    private fun engine(
        settings: FakeSettingsStore,
        rules: FakeRulesStore,
        ble: FakeBleRepository,
        log: InMemoryLog = InMemoryLog(),
    ): RuleExecutionEngine {
        return RuleExecutionEngine(
            bleRepository = ble,
            rulesStore = rules,
            settingsStore = settings,
            executionLog = log,
            activeGeofenceState = ActiveGeofenceState(),
            diagnosticsRepository = DiagnosticsRepository(),
        )
    }

    private class FakeRulesStore(initial: List<Rule>) : com.akrapovic.soundkit.community.data.RulesStore {
        override val rules = MutableStateFlow(initial)
        var upsertCount = 0

        override suspend fun upsertRule(rule: Rule) {
            upsertCount++
            rules.value = rules.value.filterNot { it.id == rule.id } + rule
        }

        override suspend fun deleteRule(id: String) {
            rules.value = rules.value.filterNot { it.id == id }
        }

        override suspend fun setRuleEnabled(id: String, enabled: Boolean) {
            rules.value = rules.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
        }
    }

    private class InMemoryLog : com.akrapovic.soundkit.community.data.RuleExecutionLogStore {
        private val _entries = MutableStateFlow<List<com.akrapovic.soundkit.community.domain.RuleExecutionEntry>>(emptyList())
        private val _last = MutableStateFlow<com.akrapovic.soundkit.community.domain.RuleExecutionEntry?>(null)

        override val entries = _entries
        override val lastExecution = _last

        override suspend fun append(entry: com.akrapovic.soundkit.community.domain.RuleExecutionEntry) {
            _entries.value = (_entries.value + entry).takeLast(30)
            _last.value = entry
        }

        override suspend fun clear() {
            _entries.value = emptyList()
            _last.value = null
        }
    }
}
