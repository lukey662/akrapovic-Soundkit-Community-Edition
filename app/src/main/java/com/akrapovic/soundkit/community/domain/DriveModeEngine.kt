package com.akrapovic.soundkit.community.domain

import com.akrapovic.soundkit.community.data.BleRepository
import com.akrapovic.soundkit.community.data.DiagnosticsRepository
import com.akrapovic.soundkit.community.data.RuleExecutionLogStore
import com.akrapovic.soundkit.community.data.SettingsStore
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class DriveModeEngine @Inject constructor(
    private val bleRepository: BleRepository,
    private val settingsStore: SettingsStore,
    private val executionLog: RuleExecutionLogStore,
    private val diagnosticsRepository: DiagnosticsRepository,
) {
    private val mutex = Mutex()
    private var quietJob: Job? = null
    private var activeSessionId: Long? = null
    private var userAdjustedSessionId: Long? = null

    fun onUserValveAdjustment() {
        userAdjustedSessionId = activeSessionId
        quietJob?.cancel()
        quietJob = null
    }

    fun onDisconnect() {
        quietJob?.cancel()
        quietJob = null
        activeSessionId = null
    }

    suspend fun onConnectReady(sessionId: Long, scope: CoroutineScope) {
        val settings = settingsStore.settings.first()
        mutex.withLock {
            if (activeSessionId == sessionId) return
            activeSessionId = sessionId
            userAdjustedSessionId = null
            quietJob?.cancel()
            quietJob = null
        }

        if (!settings.driveModeEnabled || settings.automationPaused) {
            diagnosticsRepository.debug("Drive mode skipped: disabled or paused")
            return
        }
        if (!isReadyForAutomation()) {
            diagnosticsRepository.debug("Drive mode skipped: not ready")
            return
        }

        val quiet = settings.quietStart
        if (quiet.enabled && isQuietWindowActive(quiet)) {
            applyClose("quiet start")
            val holdMs = quiet.holdClosedMinutes.coerceIn(1, 15) * 60_000L
            quietJob = scope.launch {
                delay(holdMs)
                if (activeSessionId != sessionId || userAdjustedSessionId == sessionId) return@launch
                val latest = settingsStore.settings.first()
                if (!latest.driveModeEnabled || latest.automationPaused) return@launch
                applyPreferredMode(latest.preferredValveMode, "quiet end")
            }
        } else {
            applyPreferredMode(settings.preferredValveMode, "connect")
        }
    }

    private suspend fun applyPreferredMode(mode: PreferredValveMode, reason: String) {
        if (userAdjustedSessionId == activeSessionId) return
        if (!isReadyForAutomation()) return

        val valveState = bleRepository.valveState.value
        val result = when (mode) {
            PreferredValveMode.Open -> {
                if (valveState == ValveState.Open) {
                    logApply(mode, reason, skipped = true)
                    return
                }
                bleRepository.openValve()
            }
            PreferredValveMode.Closed -> {
                if (valveState == ValveState.Closed) {
                    logApply(mode, reason, skipped = true)
                    return
                }
                bleRepository.closeValve()
            }
        }
        logApply(mode, reason, result = result)
    }

    private suspend fun applyClose(reason: String) {
        if (userAdjustedSessionId == activeSessionId) return
        if (!isReadyForAutomation()) return

        val valveState = bleRepository.valveState.value
        if (valveState == ValveState.Closed) {
            logApply(PreferredValveMode.Closed, reason, skipped = true)
            return
        }
        logApply(PreferredValveMode.Closed, reason, result = bleRepository.closeValve())
    }

    private suspend fun logApply(
        mode: PreferredValveMode,
        reason: String,
        result: CommandResult? = null,
        skipped: Boolean = false,
    ) {
        val outcome = when {
            skipped -> RuleExecutionOutcome.Skipped
            result is CommandResult.Success -> RuleExecutionOutcome.Success
            result is CommandResult.Failure -> RuleExecutionOutcome.Failed
            else -> RuleExecutionOutcome.Skipped
        }
        val detail = when (result) {
            is CommandResult.Failure -> result.message
            else -> null
        }
        executionLog.append(
            RuleExecutionEntry(
                timestampMillis = System.currentTimeMillis(),
                ruleName = "Drive mode",
                action = mode.name,
                reason = reason,
                outcome = outcome,
                detail = detail,
            ),
        )
    }

    private fun isReadyForAutomation(): Boolean {
        val connection = bleRepository.connectionState.value
        if (connection !is ConnectionState.Connected) return false
        if (bleRepository.valveState.value == ValveState.Unknown) return false
        if (bleRepository.receiverStatusMessage.value != null) return false
        return true
    }

    private fun isQuietWindowActive(quiet: QuietStartSettings): Boolean {
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        val dayOfWeek = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
        if (dayOfWeek !in quiet.daysOfWeek) return false
        val minuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        return minuteOfDay in quiet.windowStartMinute..quiet.windowEndMinute
    }
}
