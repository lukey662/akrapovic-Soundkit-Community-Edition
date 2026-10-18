package com.akrapovic.soundkit.community.wear

import com.akrapovic.soundkit.community.data.BleRepository
import com.akrapovic.soundkit.community.domain.CommandResult
import com.akrapovic.soundkit.community.domain.CommandSource
import com.akrapovic.soundkit.community.domain.DriveModeEngine
import com.akrapovic.soundkit.community.domain.ValveCommandCoordinator
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Receives Open/Close from the watch. The Data Layer only delivers messages from the same app,
 * signed with the same key, on a paired watch. Commands still go through the coordinator.
 */
@AndroidEntryPoint
class WearCommandListenerService : WearableListenerService() {
    @Inject lateinit var bleRepository: BleRepository
    @Inject lateinit var coordinator: ValveCommandCoordinator
    @Inject lateinit var driveModeEngine: DriveModeEngine
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val open = when (messageEvent.path) {
            WearProtocol.PATH_OPEN -> true
            WearProtocol.PATH_CLOSE -> false
            else -> return
        }
        val nodeId = messageEvent.sourceNodeId
        appScope.launch {
            if (!WearCommandPolicy.allow(bleRepository.connectionState.value)) {
                reply(nodeId, "Open Car Sound Kit on your phone")
                return@launch
            }
            driveModeEngine.onUserValveAdjustment()
            val result = if (open) coordinator.open(CommandSource.Wear) else coordinator.close(CommandSource.Wear)
            reply(
                nodeId,
                when (result) {
                    is CommandResult.Success -> if (open) "Valves open" else "Valves closed"
                    is CommandResult.Failure -> result.message
                },
            )
        }
    }

    private suspend fun reply(nodeId: String, text: String) {
        runCatching {
            Wearable.getMessageClient(applicationContext)
                .sendMessage(nodeId, WearProtocol.PATH_RESULT, text.toByteArray(Charsets.UTF_8))
                .await()
        }
    }
}
