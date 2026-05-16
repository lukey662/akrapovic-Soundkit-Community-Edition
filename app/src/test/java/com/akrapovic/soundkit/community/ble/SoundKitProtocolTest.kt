package com.akrapovic.soundkit.community.ble

import com.akrapovic.soundkit.community.domain.ValveCommand
import com.akrapovic.soundkit.community.domain.ValveState
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundKitProtocolTest {
    @Test
    fun protocolUsesVerifiedApkConstants() {
        assertTrue(SoundKitProtocol.VERIFIED)
        assertTrue(SoundKitProtocol.requireVerified().isSuccess)
        assertNull(SoundKitProtocol.serviceUuid)
        assertEquals("0000fff4-0000-1000-8000-00805f9b34fb", SoundKitProtocol.commandCharacteristicUuid.toString())
        assertEquals(SoundKitProtocol.commandCharacteristicUuid, SoundKitProtocol.notificationCharacteristicUuid)
    }

    @Test
    fun likelyDeviceNameMatchesKnownHints() {
        assertTrue(SoundKitProtocol.isLikelySoundKitDevice("Akrapovic SoundKit"))
        assertTrue(SoundKitProtocol.isLikelySoundKitDevice("Akrapovič Receiver"))
        assertFalse(SoundKitProtocol.isLikelySoundKitDevice("Kitchen Light"))
    }

    @Test
    fun advertisingSignatureMatchesOriginalApkScanFilter() {
        val record = byteArrayOf(0x02, 0x01, 0x06, -1, -1, -1, 0x31, 0x30, 0x33, 0x00)

        assertEquals("103", SoundKitProtocol.advertisingSignature(record))
        assertTrue(SoundKitProtocol.hasAdvertisingSignature(record))
    }

    @Test
    fun openCloseUseToggleOnlyWhenStateMustChange() {
        assertArrayEquals(
            byteArrayOf(0x01),
            SoundKitProtocol.commandPayload(ValveCommand.Open, ValveState.Closed).getOrThrow(),
        )
        assertArrayEquals(
            byteArrayOf(0x01),
            SoundKitProtocol.commandPayload(ValveCommand.Close, ValveState.Open).getOrThrow(),
        )
        assertNull(SoundKitProtocol.commandPayload(ValveCommand.Open, ValveState.Open).getOrThrow())
        assertNull(SoundKitProtocol.commandPayload(ValveCommand.Close, ValveState.Closed).getOrThrow())
        assertTrue(SoundKitProtocol.commandPayload(ValveCommand.Open, ValveState.Unknown).isFailure)
    }

    @Test
    fun statusBytesMapToValveState() {
        assertEquals(ValveState.Closed, SoundKitProtocol.statusByteToValveState(byteArrayOf(0x02)).getOrThrow())
        assertEquals(ValveState.Open, SoundKitProtocol.statusByteToValveState(byteArrayOf(0x03)).getOrThrow())
        assertEquals(ValveState.Open, SoundKitProtocol.statusByteToValveState(byteArrayOf(0x06)).getOrThrow())
        assertEquals(ValveState.Closed, SoundKitProtocol.statusByteToValveState(byteArrayOf(0x07)).getOrThrow())
        assertTrue(SoundKitProtocol.statusByteToValveState(byteArrayOf(0x04)).isFailure)
    }
}

