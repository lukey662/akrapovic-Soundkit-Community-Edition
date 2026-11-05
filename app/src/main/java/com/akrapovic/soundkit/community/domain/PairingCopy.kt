package com.akrapovic.soundkit.community.domain

/**
 * Customer copy for Bluetooth pairing. The receiver's six-digit PIN is the only thing that stops
 * another phone controlling it, so the app explains it whenever Android asks for it.
 */
object PairingCopy {
    const val PIN_HINT = "The first time you connect, Android asks for the 6-digit PIN printed on your receiver and in the manual."
    const val IN_PROGRESS_TITLE = "Pairing"
    const val IN_PROGRESS_BODY = "Enter the 6-digit PIN printed on your receiver in Android's pairing prompt."
    const val ERROR_TITLE = "Pairing didn't finish"
    const val STILL_PAIRED_NOTE = "Your phone stays paired with SoundKit. To pair again from scratch, forget SoundKit in Bluetooth settings."
    const val OPEN_BLUETOOTH_SETTINGS = "Open Bluetooth settings"
    const val TRY_AGAIN = "Try again"

    /** Short status line for the shade, Home dot labels and Android Auto. */
    fun shortText(kind: ConnectionErrorKind): String? = when (kind) {
        ConnectionErrorKind.PairingCancelled -> "Pairing was cancelled"
        ConnectionErrorKind.PairingFailed -> "Pairing failed — check the PIN"
        ConnectionErrorKind.PairingTimedOut -> "Pairing timed out"
        ConnectionErrorKind.PairingNotStarted -> "Couldn't start pairing"
        ConnectionErrorKind.StalePairing -> "Phone and receiver pairing is out of date"
        ConnectionErrorKind.Generic -> null
    }

    /** One helper line under [ERROR_TITLE] on Home. */
    fun body(kind: ConnectionErrorKind): String? = when (kind) {
        ConnectionErrorKind.PairingCancelled ->
            "Try again and enter the 6-digit PIN printed on your receiver and in the manual."
        ConnectionErrorKind.PairingFailed ->
            "Check the 6-digit PIN printed on your receiver and in the manual, then try again."
        ConnectionErrorKind.PairingTimedOut ->
            "Android's pairing prompt closed before a PIN was entered. Try again and have the receiver's PIN ready."
        ConnectionErrorKind.PairingNotStarted ->
            "Android couldn't start pairing. Turn Bluetooth off and on, then try again."
        ConnectionErrorKind.StalePairing ->
            "The receiver no longer recognises this phone. Forget SoundKit in Bluetooth settings, then try again with the PIN."
        ConnectionErrorKind.Generic -> null
    }

    /** Bluetooth settings only helps when the stored pairing itself is wrong or stuck. */
    fun offersBluetoothSettings(kind: ConnectionErrorKind): Boolean = when (kind) {
        ConnectionErrorKind.PairingFailed,
        ConnectionErrorKind.StalePairing,
        ConnectionErrorKind.PairingNotStarted,
        -> true
        ConnectionErrorKind.PairingCancelled,
        ConnectionErrorKind.PairingTimedOut,
        ConnectionErrorKind.Generic,
        -> false
    }
}
