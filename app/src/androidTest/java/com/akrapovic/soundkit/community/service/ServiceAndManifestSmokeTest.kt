package com.akrapovic.soundkit.community.service

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import com.akrapovic.soundkit.community.R
import androidx.car.app.CarAppService
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akrapovic.soundkit.community.car.SoundKitCarAppService
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.ValveState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServiceAndManifestSmokeTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun notificationFactoryBuildsDisconnectedNotification() {
        val factory = SoundKitNotificationFactory(context)

        factory.ensureChannel()
        val notification = factory.build(ConnectionState.Disconnected, ValveState.Unknown)

        assertEquals("Sound Kit Community", notification.extras.getString("android.title"))
        assertTrue(notification.actions.any { it.title.toString() == "Open" })
        assertTrue(notification.actions.any { it.title.toString() == "Close" })
    }

    @Test
    fun manifestDoesNotRequestInternetPermission() {
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )

        assertFalse(packageInfo.requestedPermissions.orEmpty().contains(Manifest.permission.INTERNET))
    }

    @Test
    fun manifestDeclaresNonExportedDiagnosticsFileProvider() {
        val provider = context.packageManager.resolveContentProvider(
            "${context.packageName}.fileprovider",
            PackageManager.GET_META_DATA,
        )

        assertTrue(provider != null)
        assertEquals("androidx.core.content.FileProvider", provider?.name)
        assertFalse(provider?.exported ?: true)
        assertTrue(provider?.grantUriPermissions ?: false)
    }

    @Test
    fun manifestDeclaresAutomotiveAppDescription() {
        val appInfo = context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA,
        )
        val metaData = appInfo.metaData
        assertTrue(metaData.containsKey("com.google.android.gms.car.application"))
        assertEquals(
            R.xml.automotive_app_desc,
            metaData.getInt("com.google.android.gms.car.application"),
        )
    }

    @Test
    fun androidAutoServiceDeclaresIotCategory() {
        val intent = Intent(CarAppService.SERVICE_INTERFACE).apply {
            setPackage(context.packageName)
            addCategory("androidx.car.app.category.IOT")
        }
        val matches = context.packageManager.queryIntentServices(intent, PackageManager.GET_META_DATA)

        assertTrue(matches.any { it.serviceInfo.name == SoundKitCarAppService::class.java.name })
    }
}

