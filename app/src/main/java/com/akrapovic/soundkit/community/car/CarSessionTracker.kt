package com.akrapovic.soundkit.community.car

import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks active Android Auto / Automotive OS Car App sessions on this phone.
 * Reference-counted so nested screens do not flip primary status prematurely.
 */
@Singleton
class CarSessionTracker @Inject constructor() {
    private val sessionCount = AtomicInteger(0)

    private val _isCarSessionActive = MutableStateFlow(false)
    val isCarSessionActive: StateFlow<Boolean> = _isCarSessionActive.asStateFlow()

    fun beginSession() {
        val count = sessionCount.incrementAndGet()
        _isCarSessionActive.value = count > 0
    }

    fun endSession() {
        val count = sessionCount.updateAndGet { current -> maxOf(0, current - 1) }
        _isCarSessionActive.value = count > 0
    }
}
