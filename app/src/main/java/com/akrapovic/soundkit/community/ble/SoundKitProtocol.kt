package com.akrapovic.soundkit.community.ble

import com.akrapovic.soundkit.community.domain.ValveCommand
import java.util.Locale
import java.util.UUID

object SoundKitProtocol {
    const val VERIFIED: Boolean = false

    val deviceNameHints: Set<String> = setOf(
        "akrapovic",
        "akrapovič",
        "soundkit",
        "sound kit",
    )

    val serviceUuid: UUID? = null
    val commandCharacteristicUuid: UUID? = null
    val notificationCharacteristicUuid: UUID? = null

    val writeType: Int? = null

    fun isLikelySoundKitDevice(name: String?): Boolean {
        val normalized = name.orEmpty().lowercase(Locale.ROOT)
        return deviceNameHints.any(normalized::contains)
    }

    fun commandPayload(command: ValveCommand): Result<ByteArray> {
        return Result.failure(
            ProtocolNotVerifiedException(
                "Cannot send ${command.name.uppercase(Locale.ROOT)} because BLE_PROTOCOL.md has no verified UUIDs or command bytes yet.",
            ),
        )
    }

    fun requireVerified(): Result<Unit> {
        if (VERIFIED && serviceUuid != null && commandCharacteristicUuid != null && writeType != null) {
            return Result.success(Unit)
        }
        return Result.failure(
            ProtocolNotVerifiedException(
                "Sound Kit BLE protocol is not verified. Analyze the original APK or HCI log before enabling valve writes.",
            ),
        )
    }
}

class ProtocolNotVerifiedException(message: String) : IllegalStateException(message)

