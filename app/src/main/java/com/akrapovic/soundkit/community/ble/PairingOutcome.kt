package com.akrapovic.soundkit.community.ble

import com.akrapovic.soundkit.community.domain.ConnectionErrorKind

/**
 * Turns Android bond and GATT signals into a [ConnectionErrorKind].
 *
 * Pure Kotlin so the mapping is unit-testable without a Bluetooth stack. The bond reason is a
 * hidden framework extra, so every unknown or missing value falls back to [ConnectionErrorKind.PairingFailed].
 */
object PairingOutcome {
    /** `BluetoothDevice.EXTRA_REASON` is hidden API; the string key is stable across releases. */
    const val EXTRA_BOND_REASON = "android.bluetooth.device.extra.REASON"
    const val REASON_UNKNOWN = -1

    // Hidden BluetoothDevice.UNBOND_REASON_* values.
    private const val UNBOND_REASON_AUTH_FAILED = 1
    private const val UNBOND_REASON_AUTH_REJECTED = 2
    private const val UNBOND_REASON_AUTH_CANCELED = 3
    private const val UNBOND_REASON_AUTH_TIMEOUT = 6
    private const val UNBOND_REASON_REMOTE_AUTH_CANCELED = 8
    private const val UNBOND_REASON_REMOVED = 9

    // GATT operation statuses that mean the link needs (re-)pairing.
    private const val GATT_INSUFFICIENT_AUTHENTICATION = 5
    private const val GATT_INSUFFICIENT_ENCRYPTION = 15
    private const val GATT_AUTH_FAIL = 137

    // HCI disconnect reasons reported in onConnectionStateChange.
    private const val HCI_AUTHENTICATION_FAILURE = 5
    private const val HCI_PIN_OR_KEY_MISSING = 6

    /** Kind for a bond that ended in `BOND_NONE` while this app was waiting for it. */
    fun classifyBondEnded(reason: Int): ConnectionErrorKind = when (reason) {
        UNBOND_REASON_AUTH_CANCELED,
        UNBOND_REASON_REMOTE_AUTH_CANCELED,
        -> ConnectionErrorKind.PairingCancelled
        UNBOND_REASON_AUTH_TIMEOUT -> ConnectionErrorKind.PairingTimedOut
        UNBOND_REASON_REMOVED -> ConnectionErrorKind.StalePairing
        UNBOND_REASON_AUTH_FAILED,
        UNBOND_REASON_AUTH_REJECTED,
        -> ConnectionErrorKind.PairingFailed
        else -> ConnectionErrorKind.PairingFailed
    }

    /** True when a GATT operation status (e.g. the CCCD write) means the pairing keys are not valid. */
    fun isStalePairingGattStatus(status: Int): Boolean = when (status) {
        GATT_INSUFFICIENT_AUTHENTICATION,
        GATT_INSUFFICIENT_ENCRYPTION,
        GATT_AUTH_FAIL,
        -> true
        else -> false
    }

    /**
     * True when a link-loss reason means the phone and receiver no longer share valid pairing keys,
     * for example after the receiver was reset or the phone's pairing was removed on one side.
     */
    fun isStalePairingDisconnectReason(status: Int): Boolean = when (status) {
        HCI_AUTHENTICATION_FAILURE,
        HCI_PIN_OR_KEY_MISSING,
        -> true
        else -> false
    }

    /** Diagnostics text for an ended bond; raw reason codes stay out of customer copy. */
    fun diagnosticsMessage(kind: ConnectionErrorKind, reason: Int): String = when (kind) {
        ConnectionErrorKind.PairingCancelled -> "Receiver pairing was cancelled (reason=$reason)"
        ConnectionErrorKind.PairingTimedOut -> "Receiver pairing timed out (reason=$reason)"
        ConnectionErrorKind.StalePairing -> "Receiver pairing was removed (reason=$reason)"
        else -> "Receiver pairing failed (reason=$reason)"
    }
}
