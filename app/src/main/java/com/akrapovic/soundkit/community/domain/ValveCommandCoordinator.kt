package com.akrapovic.soundkit.community.domain

import com.akrapovic.soundkit.community.data.BleRepository
import com.akrapovic.soundkit.community.data.RuleExecutionLogStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single command authority for every Android surface (UI, notification, widget, QS, car, voice).
 * Surfaces must not write BLE characteristics directly.
 */
@Singleton
class ValveCommandCoordinator @Inject constructor(
    private val bleRepository: BleRepository,
    private val executionLog: RuleExecutionLogStore,
    private val clock: WallClock,
) {
    private val mutex = Mutex()

    private val _commandPhase = MutableStateFlow<CommandPhase>(CommandPhase.Idle)
    val commandPhase: StateFlow<CommandPhase> = _commandPhase.asStateFlow()

    val commandInFlight: Boolean
        get() = when (_commandPhase.value) {
            is CommandPhase.Writing,
            is CommandPhase.AwaitingConfirmation,
            -> true
            else -> false
        }

    suspend fun open(source: CommandSource): CommandResult = execute(ValveCommand.Open, source)

    suspend fun close(source: CommandSource): CommandResult = execute(ValveCommand.Close, source)

    fun currentStatus(): ValveState = bleRepository.valveState.value

    fun canOpen(): Boolean {
        return bleRepository.connectionState.value is ConnectionState.Connected &&
            bleRepository.valveState.value == ValveState.Closed &&
            bleRepository.receiverStatusMessage.value == null &&
            !commandInFlight
    }

    fun canClose(): Boolean {
        return bleRepository.connectionState.value is ConnectionState.Connected &&
            bleRepository.valveState.value == ValveState.Open &&
            bleRepository.receiverStatusMessage.value == null &&
            !commandInFlight
    }

    private suspend fun execute(command: ValveCommand, source: CommandSource): CommandResult {
        val target = command.target()
        val alreadyThere = bleRepository.connectionState.value is ConnectionState.Connected &&
            bleRepository.receiverStatusMessage.value == null &&
            bleRepository.valveState.value == target
        val result = executeLocked(command)
        record(command, source, result, noOp = alreadyThere && result is CommandResult.Success)
        return result
    }

    /** Every request, including refused ones, lands in Activity with its source. */
    private suspend fun record(command: ValveCommand, source: CommandSource, result: CommandResult, noOp: Boolean) {
        val outcome = when {
            noOp -> RuleExecutionOutcome.Skipped
            result is CommandResult.Success -> RuleExecutionOutcome.Success
            else -> RuleExecutionOutcome.Failed
        }
        runCatching {
            executionLog.append(
                RuleExecutionEntry(
                    timestampMillis = clock.nowMillis(),
                    ruleName = source.label,
                    action = if (command == ValveCommand.Open) "Open" else "Close",
                    reason = source.label,
                    outcome = outcome,
                    detail = (result as? CommandResult.Failure)?.message,
                ),
            )
        }
    }

    private fun ValveCommand.target(): ValveState = when (this) {
        ValveCommand.Open -> ValveState.Open
        ValveCommand.Close -> ValveState.Closed
    }

    private suspend fun executeLocked(command: ValveCommand): CommandResult = mutex.withLock {
        if (commandInFlight) {
            return CommandResult.Failure(
                message = "A valve command is already in progress.",
                recoverable = true,
            )
        }
        val target = when (command) {
            ValveCommand.Open -> ValveState.Open
            ValveCommand.Close -> ValveState.Closed
        }
        val blockedReason = blockedReason(target)
        if (blockedReason != null) {
            _commandPhase.value = CommandPhase.Failed(
                target = target,
                reason = blockedReason,
                recoverable = true,
            )
            return CommandResult.Failure(blockedReason, recoverable = true)
        }
        if (bleRepository.valveState.value == target) {
            _commandPhase.value = CommandPhase.Succeeded(target)
            return CommandResult.Success(target)
        }
        _commandPhase.value = CommandPhase.Writing(target)
        return try {
            val result = when (command) {
                ValveCommand.Open -> bleRepository.openValve()
                ValveCommand.Close -> bleRepository.closeValve()
            }
            _commandPhase.value = when (result) {
                is CommandResult.Success -> CommandPhase.Succeeded(result.valveState)
                is CommandResult.Failure -> CommandPhase.Failed(
                    target = target,
                    reason = result.message,
                    recoverable = result.recoverable,
                )
            }
            result
        } catch (error: Throwable) {
            val message = error.message ?: "Valve command failed"
            _commandPhase.value = CommandPhase.Failed(
                target = target,
                reason = message,
                recoverable = true,
            )
            CommandResult.Failure(message, recoverable = true)
        } finally {
            // Keep terminal Succeeded/Failed briefly visible to collectors, then idle.
            if (_commandPhase.value !is CommandPhase.Writing &&
                _commandPhase.value !is CommandPhase.AwaitingConfirmation
            ) {
                // Leave Succeeded/Failed until next command starts; UI can treat non-busy as idle.
            }
        }
    }

    private fun blockedReason(target: ValveState): String? {
        if (bleRepository.connectionState.value !is ConnectionState.Connected) {
            return "Receiver is not connected."
        }
        if (bleRepository.receiverStatusMessage.value != null) {
            return "Receiver is not ready."
        }
        return when (bleRepository.valveState.value) {
            ValveState.Unknown -> "Waiting for receiver status."
            target -> null
            else -> null
        }
    }

    fun clearTerminalPhase() {
        when (_commandPhase.value) {
            is CommandPhase.Succeeded,
            is CommandPhase.Failed,
            CommandPhase.Idle,
            -> _commandPhase.value = CommandPhase.Idle
            else -> Unit
        }
    }
}
