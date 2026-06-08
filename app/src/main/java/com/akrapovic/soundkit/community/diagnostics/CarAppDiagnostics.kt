package com.akrapovic.soundkit.community.diagnostics

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.car.app.CarAppService
import com.akrapovic.soundkit.community.ble.SoundKitProtocol
import com.akrapovic.soundkit.community.car.SoundKitCarAppService

object CarAppDiagnostics {
    fun format(context: Context, hasDefaultReceiver: Boolean): String {
        return buildString {
            appendLine("CAR APP READINESS")
            appendLine("carAppServiceRegistered=${isCarAppServiceRegistered(context)}")
            appendLine("protocolVerified=${SoundKitProtocol.VERIFIED}")
            appendLine("defaultReceiverSaved=$hasDefaultReceiver")
            appendLine("androidAutoNote=Sideload requires AA Developer mode, Unknown sources, and Customize launcher")
        }
    }

    fun isCarAppServiceRegistered(context: Context): Boolean {
        val intent = Intent(CarAppService.SERVICE_INTERFACE).apply {
            setPackage(context.packageName)
            addCategory("androidx.car.app.category.IOT")
        }
        val services = context.packageManager.queryIntentServices(
            intent,
            PackageManager.GET_RESOLVED_FILTER,
        )
        return services.any { it.serviceInfo.name == SoundKitCarAppService::class.java.name }
    }
}
