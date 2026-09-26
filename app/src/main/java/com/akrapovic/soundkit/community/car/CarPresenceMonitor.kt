package com.akrapovic.soundkit.community.car

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.car.app.connection.CarConnection
import androidx.core.content.ContextCompat
import com.akrapovic.soundkit.community.data.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Watches the phone's existing link to the car.
 * There is no Audi MMI API. Classic Bluetooth (calls/media) and Android Auto projection are the signals.
 */
@Singleton
class CarPresenceMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bluetoothManager: BluetoothManager,
    private val settingsStore: SettingsStore,
) : CarPresenceSource {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val connectedAddresses = linkedSetOf<String>()
    private var savedAddress: String? = null
    private var projectionConnected = false
    private var started = false

    private val _presence = MutableStateFlow(CarPresence())
    override val presence: StateFlow<CarPresence> = _presence.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    deviceAddress(intent)?.let { connectedAddresses += it }
                    publish()
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    deviceAddress(intent)?.let { connectedAddresses -= it }
                    publish()
                }
            }
        }
    }

    fun start() {
        if (started) return
        started = true
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        CarConnection(context).type.observeForever { type ->
            projectionConnected = type == CarConnection.CONNECTION_TYPE_PROJECTION ||
                type == CarConnection.CONNECTION_TYPE_NATIVE
            publish()
        }
        scope.launch {
            settingsStore.settings.collect { settings ->
                savedAddress = settings.carBluetoothAddress
                refreshConnectedProfiles()
                publish()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun refreshConnectedProfiles() {
        if (!hasConnectPermission()) return
        val adapter = bluetoothManager.adapter ?: return
        listOf(BluetoothProfile.HEADSET, BluetoothProfile.A2DP).forEach { profile ->
            adapter.getProfileProxy(
                context,
                object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                        proxy.connectedDevices.mapTo(connectedAddresses) { it.address }
                        publish()
                        adapter.closeProfileProxy(profile, proxy)
                    }

                    override fun onServiceDisconnected(profile: Int) = Unit
                },
                profile,
            )
        }
    }

    private fun publish() {
        val address = savedAddress
        val bluetoothConnected = !address.isNullOrBlank() &&
            connectedAddresses.any { it.equals(address, ignoreCase = true) }
        _presence.value = CarPresence(
            bluetoothConnected = bluetoothConnected,
            projectionConnected = projectionConnected,
        )
    }

    private fun deviceAddress(intent: Intent): String? {
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        return device?.address
    }

    private fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
