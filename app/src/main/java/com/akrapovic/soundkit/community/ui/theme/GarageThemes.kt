package com.akrapovic.soundkit.community.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.akrapovic.soundkit.community.R

/**
 * Provides the active [GarageTheme] down the Composition tree.
 * Read via `LocalAkraTheme.current` in any Composable.
 * Set via `CompositionLocalProvider` inside [SoundKitTheme].
 */
val LocalAkraTheme = compositionLocalOf { GarageThemePresets.first() }

/**
 * One flat palette. Surfaces are solid; brand identity lives in [accent], [glow], and [button].
 * Gradient fields are kept for older callers and always equal their flat colors.
 */
data class GarageTheme(
    val id: String,
    val name: String,
    val subtitle: String,
    @DrawableRes val brandMark: Int = 0,
    val base: Color,
    val surface: Color,
    /** Text, icon, and link accent. */
    val accent: Color,
    val secondaryAccent: Color = accent,
    val highlight: Color = accent,
    val isDark: Boolean = true,
    val gradientStart: Color = base,
    val gradientEnd: Color = base,
    val cardGradientStart: Color = surface,
    val cardGradientEnd: Color = surface,
    val onBase: Color = if (isDark) DarkText else LightText,
    val onSurface: Color = if (isDark) DarkText else LightText,
    val muted: Color = if (isDark) DarkMuted else LightMuted,
    /** Heat and ember color in the valve art. */
    val glow: Color = accent,
    /** Filled primary action. */
    val button: Color = accent,
    val onButton: Color = Color.White,
    /** Brand stripe strip, for example the three BMW M colors. */
    val stripes: List<Color> = emptyList(),
    /** Akrapovič-inspired themes show heat-tinted titanium on the valve lip. */
    val heatTintedLip: Boolean = false,
)

data class GarageThemeFamily(
    val id: String,
    val name: String,
    val subtitle: String,
    @DrawableRes val brandMark: Int = 0,
    val dark: GarageTheme,
    val light: GarageTheme,
) {
    fun contains(themeId: String): Boolean = dark.id == themeId || light.id == themeId
}

private val DarkText = Color(0xFFFFFFFF)
private val DarkMuted = Color(0xFF8E8E96)
private val LightText = Color(0xFF0A0A0B)
private val LightMuted = Color(0xFF5A5A62)
private val DarkBase = Color(0xFF000000)
private val DarkSurface = Color(0xFF0E0E10)
private val LightBase = Color(0xFFF4F5F7)
private val LightSurface = Color(0xFFFFFFFF)
private val Ink = Color(0xFF0A0A0A)

/**
 * Builds a family from one brand palette.
 * Dark variants sit on true black; light variants on cool white. Both are flat.
 */
private fun brandFamily(
    id: String,
    name: String,
    subtitle: String,
    @DrawableRes brandMark: Int,
    darkSubtitle: String,
    lightSubtitle: String,
    accent: Color,
    glow: Color,
    button: Color,
    onButton: Color,
    lightAccent: Color,
    darkBase: Color = DarkBase,
    darkSurface: Color = DarkSurface,
    stripes: List<Color> = emptyList(),
    heatTintedLip: Boolean = false,
): GarageThemeFamily = GarageThemeFamily(
    id = id,
    name = name,
    subtitle = subtitle,
    brandMark = brandMark,
    dark = GarageTheme(
        id = "$id-dark",
        name = "$name Dark",
        subtitle = darkSubtitle,
        brandMark = brandMark,
        base = darkBase,
        surface = darkSurface,
        accent = accent,
        secondaryAccent = glow,
        highlight = accent,
        isDark = true,
        gradientStart = darkBase,
        gradientEnd = darkBase,
        cardGradientStart = darkSurface,
        cardGradientEnd = darkSurface,
        glow = glow,
        button = button,
        onButton = onButton,
        stripes = stripes,
        heatTintedLip = heatTintedLip,
    ),
    light = GarageTheme(
        id = "$id-light",
        name = "$name Light",
        subtitle = lightSubtitle,
        brandMark = brandMark,
        base = LightBase,
        surface = LightSurface,
        accent = lightAccent,
        secondaryAccent = glow,
        highlight = lightAccent,
        isDark = false,
        gradientStart = LightBase,
        gradientEnd = LightBase,
        cardGradientStart = LightSurface,
        cardGradientEnd = LightSurface,
        glow = glow,
        button = lightAccent,
        onButton = Color.White,
        stripes = stripes,
        heatTintedLip = heatTintedLip,
    ),
)

val GarageThemeFamilies = listOf(
    brandFamily(
        id = "studio",
        name = "Studio",
        subtitle = "Default look: true black, white type, and amber actions.",
        brandMark = R.drawable.brand_studio,
        darkSubtitle = "True black with amber actions and glow.",
        lightSubtitle = "Cool white with deep amber actions.",
        accent = Color(0xFFD6BD73),
        glow = Color(0xFFD6BD73),
        button = Color(0xFFD6BD73),
        onButton = Ink,
        lightAccent = Color(0xFF8A6A12),
    ),
    brandFamily(
        id = "akrapovic",
        name = "Akrapovič-inspired",
        subtitle = "Carbon black, scorpion red, and heat-tinted titanium.",
        brandMark = R.drawable.brand_akra,
        darkSubtitle = "Carbon black with red actions and heat-tinted titanium.",
        lightSubtitle = "Cool white with red actions and heat-tinted titanium.",
        accent = Color(0xFFE1173D),
        glow = Color(0xFFE1173D),
        button = Color(0xFFE1173D),
        onButton = Color.White,
        lightAccent = Color(0xFFC0122F),
        darkBase = Color(0xFF0B0B0C),
        darkSurface = Color(0xFF111113),
        heatTintedLip = true,
    ),
    brandFamily(
        id = "audi-rs",
        name = "Audi RS",
        subtitle = "Black and white with Audi progressive red.",
        brandMark = R.drawable.brand_audi_rs,
        darkSubtitle = "True black with progressive red actions.",
        lightSubtitle = "Cool white with progressive red actions.",
        accent = Color(0xFFF50537),
        glow = Color(0xFFF50537),
        // Progressive red is 4.2:1 behind white; the button is a step deeper for 4.5:1.
        button = Color(0xFFE0042F),
        onButton = Color.White,
        lightAccent = Color(0xFFD4032E),
    ),
    brandFamily(
        id = "bmw-m",
        name = "BMW M",
        subtitle = "M light blue, dark blue, and red stripes on black.",
        brandMark = R.drawable.brand_bmw_m,
        darkSubtitle = "True black with M blue actions and the M stripes.",
        lightSubtitle = "Cool white with M dark blue actions and the M stripes.",
        accent = Color(0xFF00AEEF),
        glow = Color(0xFF00AEEF),
        button = Color(0xFF0033A0),
        onButton = Color.White,
        lightAccent = Color(0xFF0033A0),
        stripes = listOf(Color(0xFF00AEEF), Color(0xFF0033A0), Color(0xFFE4002B)),
    ),
    brandFamily(
        id = "mercedes-amg",
        name = "Mercedes-AMG",
        subtitle = "Black and silver with a Petronas teal glow.",
        brandMark = R.drawable.brand_mercedes_amg,
        darkSubtitle = "True black with silver actions and a teal glow.",
        lightSubtitle = "Cool white with black actions and a teal glow.",
        accent = Color(0xFFC8CCCE),
        glow = Color(0xFF00A19B),
        button = Color(0xFFC8CCCE),
        onButton = Ink,
        lightAccent = Color(0xFF0A0A0B),
    ),
    brandFamily(
        id = "porsche",
        name = "Porsche",
        subtitle = "Black with Porsche red and crest gold.",
        brandMark = R.drawable.brand_porsche,
        darkSubtitle = "True black with Porsche red actions and crest gold detail.",
        lightSubtitle = "Cool white with Porsche red actions.",
        accent = Color(0xFFC69632),
        glow = Color(0xFFB12B28),
        button = Color(0xFFB12B28),
        onButton = Color.White,
        lightAccent = Color(0xFFB12B28),
    ),
    brandFamily(
        id = "volkswagen-r",
        name = "Volkswagen R",
        subtitle = "Black with Lapiz blue and R silver.",
        brandMark = R.drawable.brand_vw_r,
        darkSubtitle = "True black with Lapiz blue actions.",
        lightSubtitle = "Cool white with Lapiz blue actions.",
        accent = Color(0xFF4C86E8),
        glow = Color(0xFF4C86E8),
        button = Color(0xFF2A5DB8),
        onButton = Color.White,
        lightAccent = Color(0xFF2A5DB8),
    ),
    brandFamily(
        id = "lamborghini",
        name = "Lamborghini",
        subtitle = "Black and Lamborghini gold.",
        brandMark = R.drawable.brand_lamborghini,
        darkSubtitle = "True black with gold actions and glow.",
        lightSubtitle = "Cool white with dark gold actions.",
        accent = Color(0xFFFFC000),
        glow = Color(0xFFFFC000),
        button = Color(0xFFFFC000),
        onButton = Ink,
        lightAccent = Color(0xFF917300),
    ),
)

val GarageThemePresets: List<GarageTheme> = GarageThemeFamilies.flatMap { family ->
    listOf(family.dark, family.light)
}

/** The car display, widget, and watch are always dark, so they use the family's dark variant. */
fun darkVariantFor(themeId: String?): GarageTheme {
    val family = GarageThemeFamilies.firstOrNull { it.contains(themeId.orEmpty()) }
    return family?.dark ?: GarageThemeFamilies.first().dark
}

fun garageThemeFor(themeId: String?): GarageTheme {
    return GarageThemePresets.firstOrNull { it.id == themeId } ?: GarageThemePresets.first()
}
