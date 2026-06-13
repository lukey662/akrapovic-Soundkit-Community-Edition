package com.akrapovic.soundkit.community.ui

import com.akrapovic.soundkit.community.domain.PreferredValveMode
import com.akrapovic.soundkit.community.domain.QuietStartSettings
import com.akrapovic.soundkit.community.domain.SavedReceiver
import com.akrapovic.soundkit.community.domain.SoundKitSettings
import com.akrapovic.soundkit.community.ui.theme.GarageTheme
import com.akrapovic.soundkit.community.ui.theme.GarageThemePresets

val AudiRsDarkTheme: GarageTheme =
    GarageThemePresets.first { it.id == "audi-rs-dark" }

fun demoAudiSettings(
    driveModeEnabled: Boolean = true,
    preferredValveMode: PreferredValveMode = PreferredValveMode.Open,
    quietStart: QuietStartSettings = QuietStartSettings(),
    automationPaused: Boolean = false,
) = SoundKitSettings(
    garageThemeId = AudiRsDarkTheme.id,
    selectedVehicleId = "audi-rs3",
    onboardingCompletedAt = 1L,
    riskNoticeAcceptedAt = 1L,
    driveModeEnabled = driveModeEnabled,
    preferredValveMode = preferredValveMode,
    quietStart = quietStart,
    automationPaused = automationPaused,
    savedReceivers = listOf(
        SavedReceiver(
            address = "00:11:22:33:44:55",
            name = "Akrapovic SoundKit",
            nickname = "Audi RS3",
            isDefault = true,
        ),
    ),
)

/** Weekday overnight window — typical quiet-neighbours setup. */
val demoQuietNeighboursSettings = QuietStartSettings(
    enabled = true,
    daysOfWeek = setOf(0, 1, 2, 3, 4),
    windowStartMinute = 22 * 60,
    windowEndMinute = 7 * 60,
    holdClosedMinutes = 5,
)
