package com.akrapovic.soundkit.community.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * Ticks once a second while a quiet hold is running.
 * Callers should keep this value out of a semantics live region so TalkBack does not speak every tick.
 */
@Composable
fun rememberQuietHoldNow(endsAtMillis: Long?): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAtMillis) {
        if (endsAtMillis == null) return@LaunchedEffect
        while (now < endsAtMillis) {
            now = System.currentTimeMillis()
            if (now >= endsAtMillis) break
            delay(1_000)
        }
    }
    return now
}
