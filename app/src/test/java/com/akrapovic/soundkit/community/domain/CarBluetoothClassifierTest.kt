package com.akrapovic.soundkit.community.domain

import android.bluetooth.BluetoothClass
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarBluetoothClassifierTest {
    @Test
    fun carAudioAndHandsfreeAreSuggestions() {
        assertTrue(CarBluetoothClassifier.isLikelyCar(BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO))
        assertTrue(CarBluetoothClassifier.isLikelyCar(BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE))
    }

    @Test
    fun headphonesAndMissingClassAreNotSuggestions() {
        assertFalse(CarBluetoothClassifier.isLikelyCar(BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES))
        assertFalse(CarBluetoothClassifier.isLikelyCar(BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET))
        assertFalse(CarBluetoothClassifier.isLikelyCar(null))
    }
}
