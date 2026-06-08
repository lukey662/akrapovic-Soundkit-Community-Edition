package com.akrapovic.soundkit.community.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryPolicyTest {
    @Test
    fun delayUsesExponentialBackoffAndCapsAtMaximum() {
        val policy = RetryPolicy(initialDelayMs = 1_000, maxDelayMs = 5_000)

        assertEquals(1_000, policy.delayForAttempt(1))
        assertEquals(2_000, policy.delayForAttempt(2))
        assertEquals(4_000, policy.delayForAttempt(3))
        assertEquals(5_000, policy.delayForAttempt(4))
    }

    @Test
    fun attemptMustBePositive() {
        assertThrows(IllegalArgumentException::class.java) {
            RetryPolicy().delayForAttempt(0)
        }
    }

    @Test
    fun hasMoreAttemptsRespectsMaximum() {
        val policy = RetryPolicy(maxAttempts = 3)

        assertTrue(policy.hasMoreAttempts(1))
        assertTrue(policy.hasMoreAttempts(2))
        assertFalse(policy.hasMoreAttempts(3))
    }
}
