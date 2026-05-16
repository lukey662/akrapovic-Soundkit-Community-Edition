package com.akrapovic.soundkit.community.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GarageThemeFamiliesTest {
    @Test
    fun everyFamilyHasLightAndDarkVariants() {
        GarageThemeFamilies.forEach { family ->
            assertTrue(family.dark.id.endsWith("-dark"))
            assertTrue(family.light.id.endsWith("-light"))
            assertTrue(family.dark.isDark)
            assertTrue(!family.light.isDark)
            assertTrue(family.contains(family.dark.id))
            assertTrue(family.contains(family.light.id))
        }
    }

    @Test
    fun flattenedPresetIdsAreUnique() {
        val ids = GarageThemePresets.map { it.id }

        assertEquals(ids.size, ids.toSet().size)
        assertEquals(GarageThemeFamilies.size * 2, GarageThemePresets.size)
    }
}
