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

    @Test
    fun primaryTextMeetsWcagAaContrastAgainstThemeSurfaces() {
        GarageThemePresets.forEach { theme ->
            assertTrue(
                "${theme.id} onBase contrast is too low",
                contrastRatio(theme.onBase, theme.base) >= 4.5,
            )
            assertTrue(
                "${theme.id} onSurface contrast is too low",
                contrastRatio(theme.onSurface, theme.surface) >= 4.5,
            )
        }
    }

    private fun contrastRatio(
        foreground: androidx.compose.ui.graphics.Color,
        background: androidx.compose.ui.graphics.Color,
    ): Double {
        val lighter = relativeLuminance(foreground).coerceAtLeast(relativeLuminance(background))
        val darker = relativeLuminance(foreground).coerceAtMost(relativeLuminance(background))
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: androidx.compose.ui.graphics.Color): Double {
        fun linear(channel: Float): Double {
            return if (channel <= 0.04045f) {
                (channel / 12.92f).toDouble()
            } else {
                Math.pow(((channel + 0.055f) / 1.055f).toDouble(), 2.4)
            }
        }
        return 0.2126 * linear(color.red) +
            0.7152 * linear(color.green) +
            0.0722 * linear(color.blue)
    }
}
