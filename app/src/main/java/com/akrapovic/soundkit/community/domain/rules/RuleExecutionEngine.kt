package com.akrapovic.soundkit.community.domain.rules

import com.akrapovic.soundkit.community.automation.ActiveGeofenceState
import com.akrapovic.soundkit.community.data.BleRepository
import com.akrapovic.soundkit.community.data.DiagnosticsRepository
import com.akrapovic.soundkit.community.data.RuleExecutionLogStore
import com.akrapovic.soundkit.community.data.RulesStore
import com.akrapovic.soundkit.community.data.SettingsStore
import com.akrapovic.soundkit.community.domain.CommandResult
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.RuleExecutionEntry
import com.akrapovic.soundkit.community.domain.RuleExecutionOutcome
import com.akrapovic.soundkit.community.domain.ValveState
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class RuleExecutionEngine @Inject constructor(
    private val bleRepository: BleRepository,
    private val rulesStore: RulesStore,
    private val settingsStore: SettingsStore,
    private val executionLog: RuleExecutionLogStore,
    private val activeGeofenceState: ActiveGeofenceState,
    private val diagnosticsRepository: DiagnosticsRepository,
) {
    private val mutex = Mutex()
    private var lastFiredRuleId: String? = null
    private var lastFiredAction: RuleAction? = null
    private var lastFiredAtMillis: Long = 0L

    suspend fun evaluateNow(triggerReason: String = "tick") {
        mutex.withLock {
            runEvaluation(triggerReason)
        }
    }

    private suspend fun runEvaluation(triggerReason: String) {
        val settings = settingsStore.settings.first()
        if (settings.automationPaused) {
            diagnosticsRepository.debug("Automation skipped: paused")
            return
        }

        val connectionState = bleRepository.connectionState.value
        val connected = connectionState is ConnectionState.Connected
        val valveState = bleRepository.valveState.value
        val notReady = bleRepository.receiverStatusMessage.value != null

        if (!connected || valveState == ValveState.Unknown || notReady) {
            diagnosticsRepository.debug("Automation skipped: not ready (connected=$connected, valve=$valveState, notReady=$notReady)")
            return
        }

        val rules = rulesStore.rules.first()
        if (rules.none { it.enabled }) return

        val context = buildContext(settings.automationPaused)
        val decision = RuleEvaluator.evaluate(rules, context) ?: return

        if (shouldDebounce(decision)) {
            diagnosticsRepository.debug("Automation debounced for ${decision.rule.name}")
            return
        }

        val result = executeAction(decision.action, valveState)
        val outcome = when (result) {
            is CommandResult.Success -> RuleExecutionOutcome.Success
            is CommandResult.Failure -> RuleExecutionOutcome.Failed
            null -> RuleExecutionOutcome.Skipped
        }
        val detail = when (result) {
            is CommandResult.Success -> null
            is CommandResult.Failure -> result.message
            null -> "No write needed"
        }

        executionLog.append(
            RuleExecutionEntry(
                timestampMillis = System.currentTimeMillis(),
                ruleName = decision.rule.name,
                action = decision.action.label(),
                reason = "${decision.reason} via $triggerReason",
                outcome = outcome,
                detail = detail,
            ),
        )

        if (outcome == RuleExecutionOutcome.Success) {
            lastFiredRuleId = decision.rule.id
            lastFiredAction = decision.action
            lastFiredAtMillis = System.currentTimeMillis()
        }
    }

    private fun buildContext(manualPause: Boolean): RuleContext {
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        val dayOfWeek = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Mon=0
        val minuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val minuteOfWeek = dayOfWeek * 24 * 60 + minuteOfDay

        return RuleContext(
            nowEpochMinuteOfWeek = minuteOfWeek,
            activeGeofenceZoneIds = activeGeofenceState.activeZoneIds.value,
            connectionConnected = bleRepository.connectionState.value is ConnectionState.Connected,
            valveState = bleRepository.valveState.value,
            manualPause = manualPause,
        )
    }

    private fun shouldDebounce(decision: RuleDecision): Boolean {
        val now = System.currentTimeMillis()
        if (decision.rule.id == lastFiredRuleId &&
            decision.action == lastFiredAction &&
            now - lastFiredAtMillis < DEBOUNCE_MS
        ) {
            return true
        }
        return false
    }

    private suspend fun executeAction(action: RuleAction, currentValve: ValveState): CommandResult? {
        return when (action) {
            RuleAction.Open -> {
                if (currentValve == ValveState.Open) return null
                bleRepository.openValve()
            }
            RuleAction.Close -> {
                if (currentValve == ValveState.Closed) return null
                bleRepository.closeValve()
            }
            RuleAction.Toggle -> when (currentValve) {
                ValveState.Open -> bleRepository.closeValve()
                ValveState.Closed -> bleRepository.openValve()
                ValveState.Unknown -> null
            }
        }
    }

    companion object {
        const val DEBOUNCE_MS = 60_000L
    }
}
