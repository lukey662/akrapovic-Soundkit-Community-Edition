package com.akrapovic.soundkit.community

import android.app.Application
import com.akrapovic.soundkit.community.diagnostics.CrashReporter
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import timber.log.Timber

@HiltAndroidApp
class SoundKitApplication : Application() {
    @Inject lateinit var crashReporter: CrashReporter

    override fun onCreate() {
        super.onCreate()
        crashReporter.install()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
