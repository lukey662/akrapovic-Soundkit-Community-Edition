package com.akrapovic.soundkit.community.domain

import java.util.Locale

/**
 * Maps a car's Bluetooth or make name to a garage theme family, for an optional suggestion.
 * Only distinctive names count; "MB Bluetooth" or "My Car" stay unmatched.
 */
object CarBrandThemeMatcher {
    fun familyIdFor(name: String?): String? {
        val text = name?.lowercase(Locale.ROOT)?.trim().orEmpty()
        if (text.isEmpty()) return null
        return when {
            "audi" in text || Regex("\\bmmi\\b").containsMatchIn(text) -> "audi-rs"
            Regex("\\bbmw\\b").containsMatchIn(text) -> "bmw-m"
            "mercedes" in text || "mbux" in text || Regex("\\bamg\\b").containsMatchIn(text) ||
                Regex("\\bcomand\\b").containsMatchIn(text) -> "mercedes-amg"
            "porsche" in text || Regex("\\bpcm\\b").containsMatchIn(text) -> "porsche"
            "volkswagen" in text || Regex("^vw\\b").containsMatchIn(text) || Regex("\\bvw\\b").containsMatchIn(text) ||
                "discover pro" in text -> "volkswagen-r"
            "lamborghini" in text -> "lamborghini"
            else -> null
        }
    }
}
