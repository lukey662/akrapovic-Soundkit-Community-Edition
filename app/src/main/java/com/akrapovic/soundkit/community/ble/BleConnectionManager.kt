package com.akrapovic.soundkit.community.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.akrapovic.soundkit.community.data.DiagnosticsRepository
import com.akrapovic.soundkit.community.domain.CommandResult
import com.akrapovic.soundkit.community.domain.ConnectionState
import com.akrapovic.soundkit.community.domain.SoundKitDevice
import com.akrapovic.soundkit.community.domain.ValveCommand
import com.akrapovic.soundkit.community.domain.ValveState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

interface BleConnectionGateway {
    val connectionState: StateFlow<ConnectionState>
    val valveState: StateFlow<ValveState>
    val receiverStatusMessage: StateFlow<String?>

    fun markReconnecting(device: SoundKitDevice, attempt: Int, nextDelayMs: Long)
    fun markReconnectGaveUp(message: String)
    suspend fun connect(device: SoundKitDevice): Result<Unit>
    suspend fun disconnect()
    suspend fun writeCommand(command: ValveCommand): CommandResult
}

class BleConnectionManager @Inject constructor(
    @ApplicationContext
    private val context: Context,
    bluetoothManager: BluetoothManager,
    private val diagnosticsRepository: DiagnosticsRepository,
) : BleConnectionGateway {
    private val bluetoothAdapter = bluetoothManager.adapter
    private val operationMutex = Mutex()

    private var gatt: BluetoothGatt? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null
    private var connectedDevice: SoundKitDevice? = null
    private var pendingWrite: CompletableDeferred<CommandResult>? = null
    private var pendingCommand: ValveCommand? = null
    private var bondReceiverRegistered = false

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _valveState = MutableStateFlow(ValveState.Unknown)
    override val valveState: StateFlow<ValveState> = _valveState

    private val _receiverStatusMessage = MutableStateFlow<String?>(null)
    override val receiverStatusMessage: StateFlow<String?> = _receiverStatusMessage

    override fun markReconnecting(device: SoundKitDevice, attempt: Int, nextDelayMs: Long) {
        _connectionState.value = ConnectionState.Reconnecting(device, attempt, nextDelayMs)
    }

    override fun markReconnectGaveUp(message: String) {
        _connectionState.value = ConnectionState.Error(message, recoverable = false)
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect(device: SoundKitDevice): Result<Unit> = operationMutex.withLock {
        if (!hasConnectPermission()) {
            return@withLock Result.failure(SecurityException("Missing Bluetooth connect permission"))
        }
        val adapter = bluetoothAdapter ?: return@withLock Result.failure(IllegalStateException("Bluetooth is unavailable"))
        val remoteDevice = runCatching { adapter.getRemoteDevice(device.address) }
            .getOrElse { return@withLock Result.failure(it) }

        disconnectLocked()
        connectedDevice = device
        _connectionState.value = ConnectionState.Connecting(device)
        diagnosticsRepository.info("Connecting to BLE device ${device.name} ${device.address}")

        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            remoteDevice.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            remoteDevice.connectGatt(context, false, gattCallback)
        }
        Result.success(Unit)
    }

    override suspend fun disconnect() = operationMutex.withLock {
        disconnectLocked()
    }

    @SuppressLint("MissingPermission")
    override suspend fun writeCommand(command: ValveCommand): CommandResult = operationMutex.withLock {
        val protocolReady = SoundKitProtocol.requireVerified()
        if (protocolReady.isFailure) {
            val message = protocolReady.exceptionOrNull()?.message.orEmpty()
            diagnosticsRepository.warning(message)
            return@withLock CommandResult.Failure(message, recoverable = false)
        }
        if (!hasConnectPermission()) {
            return@withLock CommandResult.Failure("Missing Bluetooth connect permission", recoverable = true)
        }
        val activeGatt = gatt ?: return@withLock CommandResult.Failure("No active BLE connection", recoverable = true)
        val characteristic = commandCharacteristic
            ?: return@withLock CommandResult.Failure("Command characteristic was not discovered", recoverable = true)
        val requestedState = command.toValveState()
        val payload = SoundKitProtocol.commandPayload(command, _valveState.value).getOrElse { error ->
            return@withLock CommandResult.Failure(error.message.orEmpty(), recoverable = false)
        }
        if (payload == null) {
            diagnosticsRepository.info("${command.name} request already matches receiver state")
            return@withLock CommandResult.Success(requestedState)
        }
        val writeType = SoundKitProtocol.writeType

        characteristic.writeType = writeType
        val deferred = CompletableDeferred<CommandResult>()
        pendingWrite = deferred
        pendingCommand = command
        diagnosticsRepository.debug("BLE write ${command.name}: ${payload.toHexString()} writeType=$writeType")

        val started = writeCharacteristic(activeGatt, characteristic, payload, writeType)
        if (!started) {
            pendingWrite = null
            pendingCommand = null
            return@withLock CommandResult.Failure("Bluetooth stack rejected the characteristic write", recoverable = true)
        }

        val result = withTimeoutOrNull(WRITE_TIMEOUT_MS) { deferred.await() }
            ?: CommandResult.Failure("Timed out waiting for BLE write confirmation", recoverable = true)
        if (pendingWrite == deferred) {
            pendingWrite = null
            pendingCommand = null
        }
        result
    }

    @SuppressLint("MissingPermission")
    private fun disconnectLocked() {
        pendingWrite?.complete(CommandResult.Failure("Disconnected before write completed", recoverable = true))
        pendingWrite = null
        pendingCommand = null
        commandCharacteristic = null
        unregisterBondReceiver()
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        connectedDevice = null
        _valveState.value = ValveState.Unknown
        _receiverStatusMessage.value = null
        _connectionState.value = ConnectionState.Disconnected
        diagnosticsRepository.info("BLE connection closed")
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val device = connectedDevice ?: SoundKitDevice(
                name = safeDeviceName(gatt.device),
                address = gatt.device.address,
            )
            diagnosticsRepository.debug("GATT connection state status=$status newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = ConnectionState.Connecting(device)
                    diagnosticsRepository.info("Connected at link layer; checking receiver pairing")
                    continueAfterBond(gatt)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    commandCharacteristic = null
                    _valveState.value = ValveState.Unknown
                    unregisterBondReceiver()
                    _connectionState.value = if (status == BluetoothGatt.GATT_SUCCESS) {
                        ConnectionState.Disconnected
                    } else {
                        ConnectionState.Error("GATT disconnected with status $status", recoverable = true)
                    }
                    pendingWrite?.complete(CommandResult.Failure("Disconnected", recoverable = true))
                    pendingWrite = null
                    pendingCommand = null
                    gatt.close()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                diagnosticsRepository.error("GATT service discovery failed with status $status")
                _connectionState.value = ConnectionState.Error("Service discovery failed: $status", recoverable = true)
                return
            }

            gatt.services.forEach { service ->
                diagnosticsRepository.debug("Discovered service ${service.uuid}")
                service.characteristics.forEach { characteristic ->
                    diagnosticsRepository.debug(
                        "Discovered characteristic ${characteristic.uuid} properties=${characteristic.properties}",
                    )
                }
            }
            diagnosticsRepository.info(buildGattProfileReport(gatt))

            commandCharacteristic = findCharacteristic(gatt, SoundKitProtocol.commandCharacteristicUuid)
            if (commandCharacteristic != null) {
                maybeEnableNotifications(gatt, commandCharacteristic)
            } else {
                diagnosticsRepository.error("Sound Kit command characteristic was not found")
            }

            val device = connectedDevice ?: SoundKitDevice(
                name = safeDeviceName(gatt.device),
                address = gatt.device.address,
            )
            _connectionState.value = ConnectionState.Connected(device)
            diagnosticsRepository.info("GATT services discovered for ${device.name}")
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            diagnosticsRepository.debug("BLE write completed characteristic=${characteristic.uuid} status=$status")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                pendingWrite?.complete(CommandResult.Failure("BLE write failed with status $status", recoverable = true))
                pendingWrite = null
                pendingCommand = null
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            diagnosticsRepository.debug("BLE notify ${characteristic.uuid}: ${value.toHexString()}")
            handleStatusNotification(value)
        }

        @Deprecated("Required by Android BluetoothGattCallback before API 33.")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: byteArrayOf()
            diagnosticsRepository.debug("BLE notify ${characteristic.uuid}: ${value.toHexString()}")
            handleStatusNotification(value)
        }
    }

    @SuppressLint("MissingPermission")
    private fun maybeEnableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic?) {
        if (characteristic == null) return
        val enabled = gatt.setCharacteristicNotification(characteristic, true)
        diagnosticsRepository.debug("Notification registration ${characteristic.uuid} enabled=$enabled")

        val cccd = characteristic.getDescriptor(CCCD_UUID) ?: return
        val value = if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(cccd, value)
        } else {
            @Suppress("DEPRECATION")
            cccd.value = value
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(cccd)
        }
    }

    private fun findCharacteristic(gatt: BluetoothGatt, uuid: UUID): BluetoothGattCharacteristic? {
        return gatt.services
            .asSequence()
            .flatMap { it.characteristics.asSequence() }
            .firstOrNull { it.uuid == uuid }
    }

    private fun handleStatusNotification(value: ByteArray) {
        val hasPendingCommand = pendingCommand != null || pendingWrite != null
        SoundKitProtocol.statusByteToValveState(value)
            .onSuccess { state ->
                _receiverStatusMessage.value = null
                if (state != ValveState.Unknown) {
                    _valveState.value = state
                    diagnosticsRepository.info("Receiver valve state is ${state.name.lowercase()}")
                } else {
                    diagnosticsRepository.debug("Receiver status did not include valve state")
                }
                completePendingCommandIfMatched(state)
            }
            .onFailure { error ->
                val message = error.message.orEmpty()
                val isNotReady = error is ReceiverStatusException && error.isNotReady
                if (isNotReady && !hasPendingCommand) {
                    diagnosticsRepository.warning(message)
                    _valveState.value = ValveState.Unknown
                    _receiverStatusMessage.value = message
                    return
                }
                if (hasPendingCommand) {
                    diagnosticsRepository.warning(message)
                    pendingWrite?.complete(CommandResult.Failure(message, recoverable = false))
                    pendingWrite = null
                    pendingCommand = null
                    return
                }
                diagnosticsRepository.error(message)
                _connectionState.value = ConnectionState.Error(message, recoverable = true)
            }
    }

    private fun completePendingCommandIfMatched(state: ValveState) {
        val command = pendingCommand ?: return
        val expectedState = command.toValveState()
        if (state == expectedState) {
            pendingWrite?.complete(CommandResult.Success(state))
            pendingWrite = null
            pendingCommand = null
        }
    }

    @SuppressLint("MissingPermission")
    private fun continueAfterBond(gatt: BluetoothGatt) {
        when (gatt.device.bondState) {
            BluetoothDevice.BOND_BONDED -> {
                diagnosticsRepository.info("Receiver paired; discovering GATT services")
                gatt.discoverServices()
            }
            BluetoothDevice.BOND_BONDING -> {
                diagnosticsRepository.info("Waiting for receiver pairing to finish")
                registerBondReceiver()
            }
            else -> {
                registerBondReceiver()
                val started = gatt.device.createBond()
                diagnosticsRepository.info("Starting Android pairing for receiver: $started")
                if (!started) {
                    _connectionState.value = ConnectionState.Error("Could not start receiver pairing", recoverable = true)
                }
            }
        }
    }

    private fun registerBondReceiver() {
        if (bondReceiverRegistered) return
        ContextCompat.registerReceiver(
            context,
            bondReceiver,
            IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        bondReceiverRegistered = true
    }

    private fun unregisterBondReceiver() {
        if (!bondReceiverRegistered) return
        runCatching { context.unregisterReceiver(bondReceiver) }
        bondReceiverRegistered = false
    }

    private val bondReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
            val activeGatt = gatt ?: return
            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            } ?: return
            if (device.address != activeGatt.device.address) return

            when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)) {
                BluetoothDevice.BOND_BONDED -> {
                    diagnosticsRepository.info("Receiver pairing complete; discovering GATT services")
                    unregisterBondReceiver()
                    activeGatt.discoverServices()
                }
                BluetoothDevice.BOND_NONE -> {
                    diagnosticsRepository.warning("Receiver pairing was cancelled or failed")
                    _connectionState.value = ConnectionState.Error("Receiver pairing was cancelled", recoverable = true)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeCharacteristic(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        payload: ByteArray,
        writeType: Int,
    ): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(characteristic, payload, writeType) == BluetoothStatusCodes.SUCCESS
        } else {
            // Android exposes the value-setting write API only before API 33. The call is deprecated
            // on newer SDKs, so it is isolated here and never used on Android 13+.
            @Suppress("DEPRECATION")
            characteristic.value = payload
            @Suppress("DEPRECATION")
            characteristic.writeType = writeType
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }
    }

    private fun hasConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    private fun buildGattProfileReport(gatt: BluetoothGatt): String {
        return buildString {
            appendLine("GATT PROFILE START")
            appendLine("services=${gatt.services.size}")
            gatt.services.forEachIndexed { serviceIndex, service ->
                appendLine("service[$serviceIndex]=${service.uuid}")
                appendLine("  type=${if (service.type == android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY) "PRIMARY" else "SECONDARY"}")
                service.characteristics.forEachIndexed { characteristicIndex, characteristic ->
                    appendLine("  characteristic[$characteristicIndex]=${characteristic.uuid}")
                    appendLine("    properties=${characteristic.properties.toCharacteristicPropertiesText()}")
                    appendLine("    permissions=${characteristic.permissions.toPermissionsText()}")
                    if (characteristic.descriptors.isEmpty()) {
                        appendLine("    descriptors=none")
                    } else {
                        characteristic.descriptors.forEachIndexed { descriptorIndex, descriptor ->
                            appendLine("    descriptor[$descriptorIndex]=${descriptor.uuid}")
                            appendLine("      permissions=${descriptor.permissions.toPermissionsText()}")
                            if (descriptor.uuid == CCCD_UUID) {
                                appendLine("      known=CCCD")
                            }
                        }
                    }
                }
            }
            append("GATT PROFILE END")
        }
    }

    private fun Int.toCharacteristicPropertiesText(): String {
        val values = buildList {
            if (this@toCharacteristicPropertiesText and BluetoothGattCharacteristic.PROPERTY_BROADCAST != 0) add("BROADCAST")
            if (this@toCharacteristicPropertiesText and BluetoothGattCharacteristic.PROPERTY_READ != 0) add("READ")
            if (this@toCharacteristicPropertiesText and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) add("WRITE_NO_RESPONSE")
            if (this@toCharacteristicPropertiesText and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) add("WRITE")
            if (this@toCharacteristicPropertiesText and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) add("NOTIFY")
            if (this@toCharacteristicPropertiesText and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) add("INDICATE")
            if (this@toCharacteristicPropertiesText and BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE != 0) add("SIGNED_WRITE")
            if (this@toCharacteristicPropertiesText and BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS != 0) add("EXTENDED_PROPS")
        }
        return values.joinToString(separator = "|").ifBlank { "NONE" } + " ($this)"
    }

    private fun Int.toPermissionsText(): String {
        val values = buildList {
            if (this@toPermissionsText and BluetoothGattCharacteristic.PERMISSION_READ != 0) add("READ")
            if (this@toPermissionsText and BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED != 0) add("READ_ENCRYPTED")
            if (this@toPermissionsText and BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED_MITM != 0) add("READ_ENCRYPTED_MITM")
            if (this@toPermissionsText and BluetoothGattCharacteristic.PERMISSION_WRITE != 0) add("WRITE")
            if (this@toPermissionsText and BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED != 0) add("WRITE_ENCRYPTED")
            if (this@toPermissionsText and BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED_MITM != 0) add("WRITE_ENCRYPTED_MITM")
            if (this@toPermissionsText and BluetoothGattCharacteristic.PERMISSION_WRITE_SIGNED != 0) add("WRITE_SIGNED")
            if (this@toPermissionsText and BluetoothGattCharacteristic.PERMISSION_WRITE_SIGNED_MITM != 0) add("WRITE_SIGNED_MITM")
        }
        return values.joinToString(separator = "|").ifBlank { "NONE" } + " ($this)"
    }

    @SuppressLint("MissingPermission")
    private fun safeDeviceName(device: BluetoothDevice): String {
        return runCatching { device.name }.getOrNull().orEmpty().ifBlank { "Unknown BLE device" }
    }

    private fun ByteArray.toHexString(): String = joinToString(separator = " ") { "%02X".format(it) }

    private fun ValveCommand.toValveState(): ValveState {
        return when (this) {
            ValveCommand.Open -> ValveState.Open
            ValveCommand.Close -> ValveState.Closed
        }
    }

    private companion object {
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        const val WRITE_TIMEOUT_MS = 5_000L
    }
}

