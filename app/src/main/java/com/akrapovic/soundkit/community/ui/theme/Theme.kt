package com.akrapovic.soundkit.community.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Akrapovič-inspired brand palette.
 *
 * Why fixed (not Material You dynamic color):
 *   - This is an aftermarket performance product. Brand integrity outranks
 *     device theming. The app should look the same on a Pixel, a Samsung,
 *     and an Audi MMI display in dark mode.
 *
 * Layer model, top → bottom:
 *   Ink        – page background, near-black, infinite depth
 *   Carbon     – panels, instrument-cluster surface
 *   Graphite   – raised cards (rare; only when stacking on Carbon)
 *   Titanium   – hairline dividers and borders
 *   Steel      – hover / focus borders
 *   Silver     – body text
 *   Pearl      – hi-emphasis numbers, titles, hero values
 *   Mist       – muted captions, units, metadata
 *
 * Accent:
 *   Amber      – the only saturated colour. Used like a signal, never as bulk.
 *
 * Supporting:
 *   Signal     – sparing positive status (rarely used)
 *   Danger     – error states
 */
object AkraColors {
    val Ink = Color(0xFF0A0B0D)
    val Carbon = Color(0xFF111316)
    val Graphite = Color(0xFF181B20)
    val Titanium = Color(0xFF2A2E36)
    val Steel = Color(0xFF3A3F49)
    val Silver = Color(0xFFC9CDD3)
    val Pearl = Color(0xFFEAECEF)
    val Mist = Color(0xFF6E727A)
    val Amber = Color(0xFFC9A24A)
    val AmberHi = Color(0xFFE6BD60)
    val AmberDim = Color(0x66C9A24A)
    val Signal = Color(0xFF7CE0AF)
    val Danger = Color(0xFFE3625B)
}

private val AkraDarkScheme = darkColorScheme(
    primary = AkraColors.Amber,
    onPrimary = AkraColors.Ink,
    secondary = AkraColors.Pearl,
    onSecondary = AkraColors.Ink,
    tertiary = AkraColors.Signal,
    background = AkraColors.Ink,
    onBackground = AkraColors.Silver,
    surface = AkraColors.Carbon,
    onSurface = AkraColors.Silver,
    surfaceVariant = AkraColors.Graphite,
    onSurfaceVariant = AkraColors.Mist,
    outline = AkraColors.Titanium,
    outlineVariant = AkraColors.Steel,
    error = AkraColors.Danger,
)

// Light scheme retained as a graceful fallback only. Brand is dark-first.
private val AkraLightScheme = lightColorScheme(
    primary = Color(0xFF7E5A0A),
    secondary = Color(0xFF1A1D22),
    background = Color(0xFFF7F7F8),
    surface = Color(0xFFFFFFFF),
)

@Composable
fun SoundKitTheme(
    // Dark-first by design. The opening screen and HUD aesthetic depend on it.
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) AkraDarkScheme else AkraLightScheme
    MaterialTheme(
        colorScheme = scheme,
        typography = AkraTypography,
        content = content,
    )
}
