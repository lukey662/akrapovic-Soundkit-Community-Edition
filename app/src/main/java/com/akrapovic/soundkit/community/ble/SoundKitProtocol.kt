package com.akrapovic.soundkit.community.ble

import android.bluetooth.BluetoothGattCharacteristic
import com.akrapovic.soundkit.community.domain.ValveCommand
import com.akrapovic.soundkit.community.domain.ValveState
import java.util.Locale
import java.util.UUID

object SoundKitProtocol {
    const val VERIFIED: Boolean = true

    val deviceNameHints: Set<String> = setOf(
        "akrapovic",
        "akrapovič",
        "soundkit",
        "sound kit",
    )

    val advertisingSignature: String = "103"

    val serviceUuid: UUID? = null
    val commandCharacteristicUuid: UUID = UUID.fromString("0000fff4-0000-1000-8000-00805f9b34fb")
    val notificationCharacteristicUuid: UUID = commandCharacteristicUuid

    val writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

    private val togglePayload = byteArrayOf(0x01)

    fun isLikelySoundKitDevice(name: String?): Boolean {
        val normalized = name.orEmpty().lowercase(Locale.ROOT)
        return deviceNameHints.any(normalized::contains)
    }

    fun advertisingSignature(scanRecord: ByteArray?): String? {
        val hex = scanRecord?.joinToString(separator = "") { "%02X".format(it) }.orEmpty()
        val markerIndex = hex.lastIndexOf("FFFFFF")
        if (markerIndex < 0) return null

        val signatureHex = hex.substring(markerIndex + 6)
        return signatureHex.chunked(2)
            .filterNot { it == "00" }
            .mapNotNull { runCatching { it.toInt(16).toChar() }.getOrNull() }
            .joinToString(separator = "")
            .ifBlank { null }
    }

    fun hasAdvertisingSignature(scanRecord: ByteArray?): Boolean {
        return advertisingSignature(scanRecord) == advertisingSignature
    }

    fun commandPayload(command: ValveCommand, currentState: ValveState): Result<ByteArray?> {
        return when {
            currentState == ValveState.Unknown -> Result.failure(
                ProtocolNotVerifiedException("Waiting for receiver status before changing the valves."),
            )
            command == ValveCommand.Open && currentState == ValveState.Open -> Result.success(null)
            command == ValveCommand.Close && currentState == ValveState.Closed -> Result.success(null)
            else -> Result.success(togglePayload.copyOf())
        }
    }

    fun statusByteToValveState(value: ByteArray): Result<ValveState> {
        val status = value.firstOrNull()
            ?: return Result.failure(ReceiverStatusException("Receiver sent an empty status update."))
        return when (status.toInt() and 0xFF) {
            0x02, 0x07 -> Result.success(ValveState.Closed)
            0x03, 0x06 -> Result.success(ValveState.Open)
            0x04 -> Result.failure(ReceiverStatusException("Receiver reported a valve control error."))
            else -> Result.success(ValveState.Unknown)
        }
    }

    fun requireVerified(): Result<Unit> {
        if (VERIFIED) {
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
class ReceiverStatusException(message: String) : IllegalStateException(message)

