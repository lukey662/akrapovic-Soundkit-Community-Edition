package com.akrapovic.soundkit.community.car

import androidx.car.app.model.CarColor
import com.akrapovic.soundkit.community.ui.theme.GarageThemePresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CarButtonColorTest {
    @Test
    fun brandThemesUseTheNearestStandardCarColour() {
        assertEquals(CarColor.RED, carButtonColorFor("audi-rs-dark"))
        assertEquals(CarColor.RED, carButtonColorFor("akrapovic-light"))
        assertEquals(CarColor.BLUE, carButtonColorFor("bmw-m-dark"))
        assertEquals(CarColor.YELLOW, carButtonColorFor("lamborghini-dark"))
        assertEquals(CarColor.GREEN, carButtonColorFor("mercedes-amg-dark"))
    }

    @Test
    fun everyPresetGetsAStandardColourNotTheDefault() {
        GarageThemePresets.forEach { theme ->
            assertNotEquals(theme.id, CarColor.PRIMARY, carButtonColorFor(theme.id))
        }
    }
}
