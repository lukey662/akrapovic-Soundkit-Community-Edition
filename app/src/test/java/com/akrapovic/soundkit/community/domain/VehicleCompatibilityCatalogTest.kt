package com.akrapovic.soundkit.community.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class VehicleCompatibilityCatalogTest {
    @Test
    fun rs3IsSupportedTier() {
        val entry = VehicleCompatibilityCatalog.findById("audi-rs3")
        assertNotNull(entry)
        assertEquals(VehicleSupportTier.Supported, entry!!.tier)
        assertEquals("Audi RS3", entry.displayName)
    }

    @Test
    fun otherSoundKitIsBeta() {
        val entry = VehicleCompatibilityCatalog.findById(VehicleCompatibilityCatalog.OTHER_SOUND_KIT_ID)
        assertEquals(VehicleSupportTier.Beta, entry!!.tier)
    }

    @Test
    fun noSoundKitIsUnsupported() {
        val entry = VehicleCompatibilityCatalog.findById(VehicleCompatibilityCatalog.NO_SOUND_KIT_ID)
        assertEquals(VehicleSupportTier.Unsupported, entry!!.tier)
    }
}
