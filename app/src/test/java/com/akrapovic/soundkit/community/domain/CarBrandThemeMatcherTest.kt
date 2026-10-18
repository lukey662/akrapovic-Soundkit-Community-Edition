package com.akrapovic.soundkit.community.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CarBrandThemeMatcherTest {
    @Test
    fun matchesCommonHeadUnitNames() {
        assertEquals("audi-rs", CarBrandThemeMatcher.familyIdFor("Audi MMI 1234"))
        assertEquals("audi-rs", CarBrandThemeMatcher.familyIdFor("MMI 4471"))
        assertEquals("bmw-m", CarBrandThemeMatcher.familyIdFor("BMW 52187"))
        assertEquals("mercedes-amg", CarBrandThemeMatcher.familyIdFor("MBUX"))
        assertEquals("mercedes-amg", CarBrandThemeMatcher.familyIdFor("Mercedes-Benz 2210"))
        assertEquals("porsche", CarBrandThemeMatcher.familyIdFor("PCM 4.0"))
        assertEquals("volkswagen-r", CarBrandThemeMatcher.familyIdFor("VW BT 5432"))
        assertEquals("volkswagen-r", CarBrandThemeMatcher.familyIdFor("Discover Pro"))
        assertEquals("lamborghini", CarBrandThemeMatcher.familyIdFor("Lamborghini Urus"))
    }

    @Test
    fun ignoresAmbiguousNames() {
        assertNull(CarBrandThemeMatcher.familyIdFor("MB Bluetooth"))
        assertNull(CarBrandThemeMatcher.familyIdFor("My Car"))
        assertNull(CarBrandThemeMatcher.familyIdFor("Commander headset"))
        assertNull(CarBrandThemeMatcher.familyIdFor(null))
        assertNull(CarBrandThemeMatcher.familyIdFor(""))
    }
}
