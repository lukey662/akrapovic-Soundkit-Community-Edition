package com.akrapovic.soundkit.community.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/**
 * Product palette shared by dark and light themes.
 */
object AkraColors {
    val Ink = Color(0xFF000000)
    val Carbon = Color(0xFF0E0E10)
    val Graphite = Color(0xFF151518)
    val Titanium = Color(0xFF2A2A2E)
    val Steel = Color(0xFF3A3A3F)
    val Silver = Color(0xFFC9C9CF)
    val Pearl = Color(0xFFFFFFFF)
    val Mist = Color(0xFF8E8E96)
    val Amber = Color(0xFFBFA35A)
    val AmberHi = Color(0xFFD6BD73)
    val AmberDim = Color(0x44BFA35A)
    val Signal = Color(0xFF34C759)
    val Danger = Color(0xFFFF453A)
    val LightBackground = Color(0xFFF4F5F7)
    val LightSurface = Color(0xFFFFFFFF)
    val LightText = Color(0xFF0A0A0B)
    val LightMuted = Color(0xFF5A5A62)
}

/**
 * App-wide theme.
 *
 * Maps the active [GarageTheme] to Material roles:
 *   primary    → garageTheme.button   (filled actions)
 *   onPrimary  → garageTheme.onButton
 *   secondary  → garageTheme.accent   (text, icons, links)
 *   tertiary   → garageTheme.glow     (valve heat)
 *   background → garageTheme.base
 *   surface    → garageTheme.surface
 *
 * Surfaces are flat. Depth comes from one tonal step and a hairline, never a shadow.
 * The active theme is also provided as [LocalAkraTheme].
 */
@Composable
fun SoundKitTheme(
    garageTheme: GarageTheme = GarageThemePresets.first(),
    content: @Composable () -> Unit,
) {
    val scheme = if (garageTheme.isDark) {
        darkColorScheme(
            primary = garageTheme.button,
            onPrimary = garageTheme.onButton,
            secondary = garageTheme.accent,
            onSecondary = AkraColors.Ink,
            tertiary = garageTheme.glow,
            background = garageTheme.base,
            onBackground = garageTheme.onBase,
            surface = garageTheme.surface,
            onSurface = garageTheme.onSurface,
            surfaceVariant = AkraColors.Graphite,
            onSurfaceVariant = garageTheme.muted,
            outline = Color.White.copy(alpha = 0.12f),
            outlineVariant = Color.White.copy(alpha = 0.07f),
            error = AkraColors.Danger,
            onError = AkraColors.Pearl,
        )
    } else {
        lightColorScheme(
            primary = garageTheme.button,
            onPrimary = garageTheme.onButton,
            secondary = garageTheme.accent,
            onSecondary = AkraColors.Pearl,
            tertiary = garageTheme.glow,
            background = garageTheme.base,
            onBackground = garageTheme.onBase,
            surface = garageTheme.surface,
            onSurface = garageTheme.onSurface,
            surfaceVariant = Color(0xFFEEF0F3),
            onSurfaceVariant = garageTheme.muted,
            outline = Color.Black.copy(alpha = 0.12f),
            outlineVariant = Color.Black.copy(alpha = 0.08f),
            error = Color(0xFFC62828),
            onError = AkraColors.Pearl,
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
