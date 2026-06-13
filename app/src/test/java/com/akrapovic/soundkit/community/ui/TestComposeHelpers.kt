package com.akrapovic.soundkit.community.ui

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.app.ActivityOptionsCompat

/** Paparazzi / unit tests lack an Activity; stub activity-result launchers. */
@Composable
fun WithStubActivityResultRegistry(content: @Composable () -> Unit) {
    val owner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I : Any?, O : Any?> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) = Unit
        }
    }
    CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
        content()
    }
}
