package com.akrapovic.soundkit.community.ble

import com.akrapovic.soundkit.community.domain.ConnectionErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingOutcomeTest {
    @Test
    fun wrongOrRejectedPinIsAFailedPairing() {
        assertEquals(ConnectionErrorKind.PairingFailed, PairingOutcome.classifyBondEnded(1))
        assertEquals(ConnectionErrorKind.PairingFailed, PairingOutcome.classifyBondEnded(2))
    }

    @Test
    fun cancelledOnEitherSideIsACancelledPairing() {
        assertEquals(ConnectionErrorKind.PairingCancelled, PairingOutcome.classifyBondEnded(3))
        assertEquals(ConnectionErrorKind.PairingCancelled, PairingOutcome.classifyBondEnded(8))
    }

    @Test
    fun timeoutAndRemovedBondsHaveTheirOwnKinds() {
        assertEquals(ConnectionErrorKind.PairingTimedOut, PairingOutcome.classifyBondEnded(6))
        assertEquals(ConnectionErrorKind.StalePairing, PairingOutcome.classifyBondEnded(9))
    }

    @Test
    fun missingOrUnknownReasonFallsBackToFailed() {
        assertEquals(ConnectionErrorKind.PairingFailed, PairingOutcome.classifyBondEnded(PairingOutcome.REASON_UNKNOWN))
        assertEquals(ConnectionErrorKind.PairingFailed, PairingOutcome.classifyBondEnded(4))
        assertEquals(ConnectionErrorKind.PairingFailed, PairingOutcome.classifyBondEnded(42))
    }

    @Test
    fun everyBondOutcomeIsAPairingProblem() {
        listOf(-1, 1, 2, 3, 4, 6, 8, 9, 42).forEach { reason ->
            assertTrue("reason=$reason", PairingOutcome.classifyBondEnded(reason).isPairing)
        }
    }

    @Test
    fun insufficientAuthOrEncryptionGattStatusesMeanStalePairing() {
        assertTrue(PairingOutcome.isStalePairingGattStatus(5))
        assertTrue(PairingOutcome.isStalePairingGattStatus(15))
        assertTrue(PairingOutcome.isStalePairingGattStatus(137))
        assertFalse(PairingOutcome.isStalePairingGattStatus(0))
        assertFalse(PairingOutcome.isStalePairingGattStatus(133))
        assertFalse(PairingOutcome.isStalePairingGattStatus(129))
    }

    @Test
    fun onlyAuthenticationDisconnectReasonsMeanStalePairing() {
        assertTrue(PairingOutcome.isStalePairingDisconnectReason(5))
        assertTrue(PairingOutcome.isStalePairingDisconnectReason(6))
        // Ordinary link loss (timeout, remote/local terminate, GATT error) must keep auto-reconnecting.
        listOf(0, 8, 19, 22, 62, 133, 147).forEach { status ->
            assertFalse("status=$status", PairingOutcome.isStalePairingDisconnectReason(status))
        }
    }

    @Test
    fun diagnosticsKeepTheRawReason() {
        assertEquals(
            "Receiver pairing was cancelled (reason=3)",
            PairingOutcome.diagnosticsMessage(ConnectionErrorKind.PairingCancelled, 3),
        )
        assertEquals(
            "Receiver pairing failed (reason=1)",
            PairingOutcome.diagnosticsMessage(ConnectionErrorKind.PairingFailed, 1),
        )
    }
}
