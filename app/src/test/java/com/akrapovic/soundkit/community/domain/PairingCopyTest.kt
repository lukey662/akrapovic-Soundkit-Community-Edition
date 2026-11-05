package com.akrapovic.soundkit.community.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingCopyTest {
    private val pairingKinds = ConnectionErrorKind.entries.filter { it.isPairing }

    @Test
    fun onlyGenericIsNotAPairingKind() {
        assertEquals(listOf(ConnectionErrorKind.Generic), ConnectionErrorKind.entries.filterNot { it.isPairing })
    }

    @Test
    fun everyPairingKindHasShortTextAndBody() {
        pairingKinds.forEach { kind ->
            assertNotNull("$kind short text", PairingCopy.shortText(kind))
            assertNotNull("$kind body", PairingCopy.body(kind))
        }
        assertNull(PairingCopy.shortText(ConnectionErrorKind.Generic))
        assertNull(PairingCopy.body(ConnectionErrorKind.Generic))
    }

    @Test
    fun pinBodiesPointAtTheReceiver() {
        listOf(
            ConnectionErrorKind.PairingCancelled,
            ConnectionErrorKind.PairingFailed,
            ConnectionErrorKind.PairingTimedOut,
            ConnectionErrorKind.StalePairing,
        ).forEach { kind ->
            val body = PairingCopy.body(kind).orEmpty()
            assertTrue("$kind mentions the PIN", "PIN" in body)
        }
        assertTrue("6-digit" in PairingCopy.PIN_HINT)
        assertTrue("receiver" in PairingCopy.PIN_HINT)
        assertTrue("PIN" in PairingCopy.IN_PROGRESS_BODY)
    }

    @Test
    fun copyHasNoRawCodes() {
        val texts = pairingKinds.flatMap { listOfNotNull(PairingCopy.shortText(it), PairingCopy.body(it)) } +
            listOf(PairingCopy.PIN_HINT, PairingCopy.IN_PROGRESS_BODY, PairingCopy.STILL_PAIRED_NOTE)
        texts.forEach { text ->
            assertFalse(text, Regex("\\d{2,}").containsMatchIn(text.replace("6-digit", "")))
            assertFalse(text, "GATT" in text || "bond" in text.lowercase() || "reason" in text)
        }
    }

    @Test
    fun bluetoothSettingsIsOfferedOnlyWhenStoredPairingIsTheProblem() {
        assertTrue(PairingCopy.offersBluetoothSettings(ConnectionErrorKind.StalePairing))
        assertTrue(PairingCopy.offersBluetoothSettings(ConnectionErrorKind.PairingFailed))
        assertTrue(PairingCopy.offersBluetoothSettings(ConnectionErrorKind.PairingNotStarted))
        assertFalse(PairingCopy.offersBluetoothSettings(ConnectionErrorKind.PairingCancelled))
        assertFalse(PairingCopy.offersBluetoothSettings(ConnectionErrorKind.PairingTimedOut))
        assertFalse(PairingCopy.offersBluetoothSettings(ConnectionErrorKind.Generic))
    }

    @Test
    fun stillPairedNotePointsToBluetoothSettings() {
        assertTrue("Bluetooth settings" in PairingCopy.STILL_PAIRED_NOTE)
    }

    @Test
    fun pairingErrorsNeverAllowAutomaticConnect() {
        val device = SoundKitDevice(name = "SoundKit", address = "00:11:22:33:44:55")
        pairingKinds.forEach { kind ->
            val error = ConnectionState.Error("x", recoverable = false, kind = kind)
            assertTrue(error.isPairingProblem)
            assertFalse("$kind", error.allowsAutomaticConnect())
        }
        assertTrue(ConnectionState.Error("x", recoverable = true).allowsAutomaticConnect())
        assertTrue(ConnectionState.Disconnected.allowsAutomaticConnect())
        assertTrue(ConnectionState.Away(1L, AwayReason.LeftCar).allowsAutomaticConnect())
        assertFalse(ConnectionState.Connecting(device, pairing = true).allowsAutomaticConnect())
        assertFalse(ConnectionState.Connected(device).allowsAutomaticConnect())
    }
}
