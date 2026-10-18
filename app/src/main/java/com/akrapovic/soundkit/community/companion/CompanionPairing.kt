package com.akrapovic.soundkit.community.companion

import android.bluetooth.le.ScanFilter
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.companion.ObservingDevicePresenceRequest
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import java.util.Locale

/**
 * Android's companion device pairing for the default receiver (Android 12+).
 * Once associated, the system wakes [SoundKitCompanionService] when the receiver is nearby and
 * lets the app start its foreground service from the background.
 */
object CompanionPairing {
    /** Association id used on Android 12, where the system does not return one. */
    const val LEGACY_ASSOCIATION_ID = -1

    fun isSupported(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)
    }

    /**
     * Shows the system chooser for [address]. [onChooser] must launch the IntentSender for result.
     * On Android 13+ [onCreated] reports the association id; on Android 12 the caller treats a
     * successful chooser result as created with [LEGACY_ASSOCIATION_ID].
     */
    fun request(
        context: Context,
        address: String,
        onChooser: (IntentSender) -> Unit,
        onCreated: (Int) -> Unit,
        onFailure: (String) -> Unit,
    ) {
        if (!isSupported(context)) {
            onFailure("Companion pairing needs Android 12 or newer")
            return
        }
        val manager = context.getSystemService(CompanionDeviceManager::class.java) ?: run {
            onFailure("Companion pairing is unavailable on this phone")
            return
        }
        val filter = BluetoothLeDeviceFilter.Builder()
            .setScanFilter(ScanFilter.Builder().setDeviceAddress(address.uppercase(Locale.ROOT)).build())
            .build()
        val request = AssociationRequest.Builder()
            .addDeviceFilter(filter)
            .setSingleDevice(true)
            .build()
        val callback = object : CompanionDeviceManager.Callback() {
            override fun onAssociationPending(intentSender: IntentSender) {
                onChooser(intentSender)
            }

            @Deprecated("Android 12 delivers the chooser here.")
            override fun onDeviceFound(intentSender: IntentSender) {
                onChooser(intentSender)
            }

            @RequiresApi(Build.VERSION_CODES.TIRAMISU)
            override fun onAssociationCreated(associationInfo: AssociationInfo) {
                onCreated(associationInfo.id)
            }

            override fun onFailure(error: CharSequence?) {
                onFailure(error?.toString() ?: "Pairing failed")
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.associate(request, context.mainExecutor, callback)
        } else {
            @Suppress("DEPRECATION")
            manager.associate(request, callback, Handler(Looper.getMainLooper()))
        }
    }

    fun startObserving(context: Context, associationId: Int, address: String): Boolean {
        if (!isSupported(context)) return false
        val manager = context.getSystemService(CompanionDeviceManager::class.java) ?: return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= 36 && associationId != LEGACY_ASSOCIATION_ID) {
                manager.startObservingDevicePresence(
                    ObservingDevicePresenceRequest.Builder().setAssociationId(associationId).build(),
                )
            } else {
                @Suppress("DEPRECATION")
                manager.startObservingDevicePresence(address.uppercase(Locale.ROOT))
            }
        }.isSuccess
    }

    fun forget(context: Context, associationId: Int, address: String) {
        if (!isSupported(context)) return
        val manager = context.getSystemService(CompanionDeviceManager::class.java) ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= 36 && associationId != LEGACY_ASSOCIATION_ID) {
                manager.stopObservingDevicePresence(
                    ObservingDevicePresenceRequest.Builder().setAssociationId(associationId).build(),
                )
            } else {
                @Suppress("DEPRECATION")
                manager.stopObservingDevicePresence(address.uppercase(Locale.ROOT))
            }
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && associationId != LEGACY_ASSOCIATION_ID) {
                manager.disassociate(associationId)
            } else {
                @Suppress("DEPRECATION")
                manager.disassociate(address.uppercase(Locale.ROOT))
            }
        }
    }

    /** Address for an association id, used when Android 16 reports presence by id only. */
    fun addressFor(context: Context, associationId: Int): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val manager = context.getSystemService(CompanionDeviceManager::class.java) ?: return null
        return runCatching {
            manager.myAssociations.firstOrNull { it.id == associationId }?.deviceMacAddress?.toString()
        }.getOrNull()
    }
}
