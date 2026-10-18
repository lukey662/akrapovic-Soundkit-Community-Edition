package com.akrapovic.soundkit.community.domain

import com.akrapovic.soundkit.community.car.CarPresenceSource
import com.akrapovic.soundkit.community.data.BleRepository
import com.akrapovic.soundkit.community.data.DiagnosticsRepository
import com.akrapovic.soundkit.community.data.RuleExecutionLogStore
import com.akrapovic.soundkit.community.data.SettingsStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class DriveModeEngine @Inject constructor(
    private val bleRepository: BleRepository,
    private val settingsStore: SettingsStore,
    private val executionLog: RuleExecutionLogStore,
    private val diagnosticsRepository: DiagnosticsRepository,
    private val valveCommandCoordinator: ValveCommandCoordinator,
    private val clock: WallClock,
    carPresence: CarPresenceSource,
) {
    private val mutex = Mutex()
    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var quietJob: Job? = null
    private var activeSessionId: Long? = null
    private var userAdjustedSessionId: Long? = null
    private var holdCancelled = false

    // One drive spans BLE drops. Drive mode acts once per drive; a manual change holds until it ends.
    private var appliedThisDrive = false
    private var manualChangeThisDrive = false
    private var disconnectedAtMillis: Long? = null
    private var holdEndsAtDisconnect: Long? = null
    private var driveBoundarySinceDisconnect = false

    private val _quietHoldEndsAt = MutableStateFlow<Long?>(null)
    val quietHoldEndsAt: StateFlow<Long?> = _quietHoldEndsAt.asStateFlow()

    init {
        persistScope.launch {
            val endsAt = settingsStore.settings.first().quietHoldEndsAtMillis
            when {
                endsAt > clock.nowMillis() -> _quietHoldEndsAt.value = endsAt
                endsAt > 0L -> settingsStore.setQuietHoldEndsAt(null)
            }
        }
        persistScope.launch {
            // Getting in or out of the car starts a new drive, even if the receiver link survived.
            // Opening or closing the car screen is not a boundary, so the session flag is ignored.
            carPresence.presence
                .map { it.inCar }
                .distinctUntilChanged()
                .drop(1)
                .collect { endDrive() }
        }
    }

    private fun endDrive() {
        appliedThisDrive = false
        manualChangeThisDrive = false
        holdEndsAtDisconnect = null
        driveBoundarySinceDisconnect = true
    }

    fun onUserValveAdjustment() {
        manualChangeThisDrive = true
        holdCancelled = true
        userAdjustedSessionId = activeSessionId
        quietJob?.cancel()
        quietJob = null
        clearHoldAsync()
    }

    fun onDisconnect() {
        if (disconnectedAtMillis == null) {
            disconnectedAtMillis = clock.nowMillis()
            holdEndsAtDisconnect = _quietHoldEndsAt.value
            driveBoundarySinceDisconnect = false
        }
        holdCancelled = true
        quietJob?.cancel()
        quietJob = null
        activeSessionId = null
        userAdjustedSessionId = null
        clearHoldAsync()
    }

    suspend fun onConnectReady(sessionId: Long, scope: CoroutineScope) {
        val settings = settingsStore.settings.first()
        val now = clock.nowMillis()
        val persistedEnd = settings.quietHoldEndsAtMillis.takeIf { it > 0L }
        val resumeExisting = mutex.withLock {
            if (activeSessionId == sessionId &&
                (quietJob?.isActive == true || userAdjustedSessionId == sessionId)
            ) {
                return
            }
            val resume = !holdCancelled && persistedEnd != null && persistedEnd > now
            activeSessionId = sessionId
            if (userAdjustedSessionId != sessionId) {
                userAdjustedSessionId = null
            }
            quietJob?.cancel()
            quietJob = null
            holdCancelled = false
            resume
        }

        if (!settings.driveModeEnabled || settings.automationPaused) {
            diagnosticsRepository.debug("Drive mode skipped: disabled or paused")
            clearHold()
            return
        }
        if (!isReadyForAutomation()) {
            diagnosticsRepository.debug("Drive mode skipped: not ready")
            return
        }
        if (resumeAfterReconnect(sessionId, scope, now, settings)) return
        appliedThisDrive = true

        if (resumeExisting && persistedEnd != null) {
            armQuietHold(sessionId, scope, persistedEnd)
            return
        }
        if (persistedEnd != null && persistedEnd <= now) {
            clearHold()
            applyPreferredMode(settings.preferredValveMode, "quiet end")
            return
        }

        val quiet = settings.quietStart
        if (QuietWindowEvaluator.isActive(quiet)) {
            applyClose("quiet start")
            val holdMs = quiet.holdClosedMinutes.coerceIn(1, 15) * 60_000L
            val endsAt = now + holdMs
            publishHold(endsAt)
            armQuietHold(sessionId, scope, endsAt)
        } else {
            clearHold()
            applyPreferredMode(settings.preferredValveMode, "connect")
        }
    }

    /**
     * A short BLE drop in the same drive must not re-run drive mode. Without this, a link blip
     * after the driver closed the valves would reopen them with no tap.
     * @return true when this reconnect was fully handled.
     */
    private suspend fun resumeAfterReconnect(
        sessionId: Long,
        scope: CoroutineScope,
        now: Long,
        settings: SoundKitSettings,
    ): Boolean {
        val disconnectedAt = disconnectedAtMillis
        val holdEnd = holdEndsAtDisconnect
        disconnectedAtMillis = null
        holdEndsAtDisconnect = null
        val sameDrive = disconnectedAt != null &&
            !driveBoundarySinceDisconnect &&
            now - disconnectedAt < SAME_DRIVE_GAP_MS
        driveBoundarySinceDisconnect = false
        if (!sameDrive) return false
        when {
            manualChangeThisDrive -> {
                userAdjustedSessionId = sessionId
                diagnosticsRepository.info("Drive mode kept the manual valve change after a reconnect")
                logApply(settings.preferredValveMode, "reconnect", skipped = true)
            }
            holdEnd != null && holdEnd > now -> {
                diagnosticsRepository.info("Drive mode resumed the quiet hold after a reconnect")
                publishHold(holdEnd)
                armQuietHold(sessionId, scope, holdEnd)
            }
            holdEnd != null -> {
                clearHold()
                applyPreferredMode(settings.preferredValveMode, "quiet end")
            }
            appliedThisDrive -> diagnosticsRepository.debug("Drive mode already applied this drive")
            else -> return false
        }
        appliedThisDrive = true
        return true
    }

    private fun armQuietHold(sessionId: Long, scope: CoroutineScope, endsAt: Long) {
        _quietHoldEndsAt.value = endsAt
        quietJob = scope.launch {
            val remaining = (endsAt - clock.nowMillis()).coerceAtLeast(0L)
            delay(remaining)
            if (activeSessionId != sessionId || userAdjustedSessionId == sessionId) return@launch
            clearHold()
            val latest = settingsStore.settings.first()
            if (!latest.driveModeEnabled || latest.automationPaused) return@launch
            applyPreferredMode(latest.preferredValveMode, "quiet end")
        }
    }

    private suspend fun publishHold(endsAt: Long) {
        _quietHoldEndsAt.value = endsAt
        settingsStore.setQuietHoldEndsAt(endsAt)
    }

    private suspend fun clearHold() {
        _quietHoldEndsAt.value = null
        settingsStore.setQuietHoldEndsAt(null)
    }

    private fun clearHoldAsync() {
        _quietHoldEndsAt.value = null
        persistScope.launch { settingsStore.setQuietHoldEndsAt(null) }
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
                valveCommandCoordinator.open()
            }
            PreferredValveMode.Closed -> {
                if (valveState == ValveState.Closed) {
                    logApply(mode, reason, skipped = true)
                    return
                }
                valveCommandCoordinator.close()
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
        logApply(PreferredValveMode.Closed, reason, result = valveCommandCoordinator.close())
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
                timestampMillis = clock.nowMillis(),
                ruleName = "Drive mode",
                action = mode.name,
                reason = reason,
                outcome = outcome,
                detail = detail,
            ),
        )
    }

    companion object {
        /** A reconnect within this gap, with no car presence change, is the same drive. */
        const val SAME_DRIVE_GAP_MS = 10L * 60L * 1000L
    }

    private fun isReadyForAutomation(): Boolean {
        val connection = bleRepository.connectionState.value
        if (connection !is ConnectionState.Connected) return false
        if (bleRepository.valveState.value == ValveState.Unknown) return false
        if (bleRepository.receiverStatusMessage.value != null) return false
        return true
    }
}
