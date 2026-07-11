package com.akrapovic.soundkit.community.domain

import com.akrapovic.soundkit.community.data.BleRepository
import com.akrapovic.soundkit.community.data.SettingsStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Narrow admission boundary for launcher shortcuts and future verified Assistant fulfillment. */
@Singleton
class VoiceValveActionRouter @Inject constructor(
    private val bleRepository: BleRepository,
    private val settingsStore: SettingsStore,
    private val coordinator: ValveCommandCoordinator,
) {
    suspend fun execute(action: VoiceValveAction): VoiceValveActionResult {
        val settings = settingsStore.settings.first()
        if (!settings.onboardingCompleted) {
            return VoiceValveActionResult.Failure("Finish Sound Kit setup on your phone before using this shortcut.")
        }
        val receiver = RememberedDeviceConnector.defaultDevice(settings)
            ?: return VoiceValveActionResult.Failure("Choose a default receiver in Sound Kit before using this shortcut.")
        val connectionFailure = connectDefaultWithinDeadline(receiver)
        if (connectionFailure != null) return VoiceValveActionResult.Failure(connectionFailure)

        return when (action) {
            VoiceValveAction.Open -> coordinator.open().toVoiceResult("Valves are open.")
            VoiceValveAction.Close -> coordinator.close().toVoiceResult("Valves are closed.")
            VoiceValveAction.Status -> when (val state = coordinator.currentStatus()) {
                ValveState.Open -> VoiceValveActionResult.Success("Valves are open.")
                ValveState.Closed -> VoiceValveActionResult.Success("Valves are closed.")
                ValveState.Unknown -> VoiceValveActionResult.Failure(
                    "Valve status is unavailable because the receiver has not reported its status.",
                )
            }
        }
    }

    private suspend fun connectDefaultWithinDeadline(receiver: SoundKitDevice): String? {
        when (val current = bleRepository.connectionState.value) {
            is ConnectionState.Connected ->
                if (current.device.address == receiver.address) return null
                else return "The saved default receiver is not connected."
            is ConnectionState.Connecting ->
                if (current.device.address != receiver.address) return "The saved default receiver is not connected."
            is ConnectionState.Reconnecting ->
                if (current.device.address != receiver.address) return "The saved default receiver is not connected."
            ConnectionState.Disconnected,
            is ConnectionState.Error,
            ConnectionState.Scanning,
            -> bleRepository.connect(receiver, userInitiated = false)
        }

        val connected = withTimeoutOrNull(CONNECTION_DEADLINE_MS) {
            bleRepository.connectionState
                .filter { it is ConnectionState.Connected && it.device.address == receiver.address }
                .first()
        }
        return if (connected == null) {
            "Couldn't reconnect to the saved default receiver. Open Sound Kit and try again."
        } else {
            null
        }
    }

    private fun CommandResult.toVoiceResult(successMessage: String): VoiceValveActionResult {
        return when (this) {
            is CommandResult.Success -> VoiceValveActionResult.Success(successMessage)
            is CommandResult.Failure -> VoiceValveActionResult.Failure(message)
        }
    }

    companion object {
        const val CONNECTION_DEADLINE_MS = 8_000L
    }
}

enum class VoiceValveAction { Open, Close, Status }

sealed interface VoiceValveActionResult {
    data class Success(val message: String) : VoiceValveActionResult
    data class Failure(val message: String) : VoiceValveActionResult
}
