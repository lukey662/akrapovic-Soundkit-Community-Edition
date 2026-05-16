package com.akrapovic.soundkit.community.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Product palette shared by dark and light themes.
 */
object AkraColors {
    val Ink = androidx.compose.ui.graphics.Color(0xFF000000)
    val Carbon = androidx.compose.ui.graphics.Color(0xFF111111)
    val Graphite = androidx.compose.ui.graphics.Color(0xFF1A1A1A)
    val Titanium = androidx.compose.ui.graphics.Color(0xFF2C2C2E)
    val Steel = androidx.compose.ui.graphics.Color(0xFF3A3A3C)
    val Silver = androidx.compose.ui.graphics.Color(0xFFD6D6D8)
    val Pearl = androidx.compose.ui.graphics.Color(0xFFFFFFFF)
    val Mist = androidx.compose.ui.graphics.Color(0xFF8E8E93)
    val Amber = androidx.compose.ui.graphics.Color(0xFFBFA35A)
    val AmberHi = androidx.compose.ui.graphics.Color(0xFFD6BD73)
    val AmberDim = androidx.compose.ui.graphics.Color(0x44BFA35A)
    val Signal = androidx.compose.ui.graphics.Color(0xFF34C759)
    val Danger = androidx.compose.ui.graphics.Color(0xFFFF453A)
    val LightBackground = androidx.compose.ui.graphics.Color(0xFFF5F5F7)
    val LightSurface = androidx.compose.ui.graphics.Color(0xFFFFFFFF)
    val LightText = androidx.compose.ui.graphics.Color(0xFF111111)
    val LightMuted = androidx.compose.ui.graphics.Color(0xFF6E6E73)
}

/**
 * App-wide theme.
 *
 * Accepts the active [GarageTheme] and maps it to Material color roles:
 *   primary          → garageTheme.accent   (amber, blue, green, etc.)
 *   secondary        → garageTheme.highlight
 *   tertiary         → garageTheme.secondaryAccent
 *   background       → garageTheme.base
 *   surface          → garageTheme.surface
 *
 * Themes can be light or dark. Shared components read color roles so text
 * remains readable across both modes.
 *
 * The active theme is also provided as [LocalAkraTheme] so composables
 * that need the raw GarageTheme can read it without depending on the
 * MaterialTheme color role names.
 */
@Composable
fun SoundKitTheme(
    garageTheme: GarageTheme = GarageThemePresets.first(),
    content: @Composable () -> Unit,
) {
    val scheme = if (garageTheme.isDark) {
        darkColorScheme(
            primary = garageTheme.accent,
            onPrimary = garageTheme.base,
            secondary = garageTheme.highlight,
            onSecondary = garageTheme.base,
            tertiary = garageTheme.secondaryAccent,
            background = garageTheme.base,
            onBackground = garageTheme.onBase,
            surface = garageTheme.surface,
            onSurface = garageTheme.onSurface,
            surfaceVariant = garageTheme.cardGradientEnd,
            onSurfaceVariant = garageTheme.muted,
            outline = AkraColors.Titanium,
            outlineVariant = AkraColors.Steel,
            error = AkraColors.Danger,
            onError = AkraColors.Pearl,
        )
    } else {
        lightColorScheme(
            primary = garageTheme.accent,
            onPrimary = AkraColors.LightSurface,
            secondary = garageTheme.highlight,
            onSecondary = garageTheme.accent,
            tertiary = garageTheme.secondaryAccent,
            background = garageTheme.base,
            onBackground = garageTheme.onBase,
            surface = garageTheme.surface,
            onSurface = garageTheme.onSurface,
            surfaceVariant = garageTheme.cardGradientEnd,
            onSurfaceVariant = garageTheme.muted,
            outline = androidx.compose.ui.graphics.Color(0xFFD8D8DE),
            outlineVariant = androidx.compose.ui.graphics.Color(0xFFE7E7EC),
            error = AkraColors.Danger,
            onError = AkraColors.LightSurface,
        )
    }
    CompositionLocalProvider(LocalAkraTheme provides garageTheme) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AkraTypography,
            content = content,
        )
    }
}
