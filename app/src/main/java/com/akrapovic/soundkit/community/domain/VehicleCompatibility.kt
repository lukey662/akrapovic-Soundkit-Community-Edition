package com.akrapovic.soundkit.community.domain

enum class VehicleSupportTier {
    Supported,
    Beta,
    Unsupported,
}

data class VehicleCompatibilityEntry(
    val id: String,
    val make: String,
    val model: String,
    val tier: VehicleSupportTier,
    val suggestedGarageThemeId: String? = null,
    val defaultNickname: String? = null,
) {
    val displayName: String get() = "$make $model"

    val tierLabel: String
        get() = when (tier) {
            VehicleSupportTier.Supported -> "Supported"
            VehicleSupportTier.Beta -> "Beta"
            VehicleSupportTier.Unsupported -> "Not compatible"
        }

    val tierDescription: String
        get() = when (tier) {
            VehicleSupportTier.Supported ->
                "Supported — same receiver protocol as our reference testing."
            VehicleSupportTier.Beta ->
                "Beta — likely works on the same BLE protocol. If you hit issues, export diagnostics and email support@appsforgood.net."
            VehicleSupportTier.Unsupported ->
                "This app needs an Akrapovič Car Sound Kit BLE receiver. Install the kit first, then return to set up."
        }
}

object VehicleCompatibilityCatalog {
    const val OTHER_SOUND_KIT_ID = "other-soundkit-beta"
    const val NO_SOUND_KIT_ID = "no-soundkit"

    val entries: List<VehicleCompatibilityEntry> = listOf(
        VehicleCompatibilityEntry(
            id = "audi-rs3",
            make = "Audi",
            model = "RS3",
            tier = VehicleSupportTier.Supported,
            suggestedGarageThemeId = "audi-rs-dark",
            defaultNickname = "Audi RS3",
        ),
        VehicleCompatibilityEntry(
            id = "audi-rs-other",
            make = "Audi",
            model = "RS (other)",
            tier = VehicleSupportTier.Beta,
            suggestedGarageThemeId = "audi-rs-dark",
        ),
        VehicleCompatibilityEntry(
            id = "bmw-m3-m4-f8x",
            make = "BMW",
            model = "M3 / M4 (F80/F82/F83)",
            tier = VehicleSupportTier.Beta,
            suggestedGarageThemeId = "bmw-m-dark",
        ),
        VehicleCompatibilityEntry(
            id = "bmw-x3m-x4m-f97",
            make = "BMW",
            model = "X3 M / X4 M (F97/F98)",
            tier = VehicleSupportTier.Beta,
            suggestedGarageThemeId = "bmw-m-dark",
        ),
        VehicleCompatibilityEntry(
            id = "porsche-soundkit",
            make = "Porsche",
            model = "Akrapovič Sound Kit",
            tier = VehicleSupportTier.Beta,
            suggestedGarageThemeId = "porsche-dark",
        ),
        VehicleCompatibilityEntry(
            id = "amg-soundkit",
            make = "Mercedes-AMG",
            model = "Akrapovič Sound Kit",
            tier = VehicleSupportTier.Beta,
            suggestedGarageThemeId = "mercedes-amg-dark",
        ),
        VehicleCompatibilityEntry(
            id = OTHER_SOUND_KIT_ID,
            make = "Other",
            model = "Car with Sound Kit",
            tier = VehicleSupportTier.Beta,
        ),
        VehicleCompatibilityEntry(
            id = NO_SOUND_KIT_ID,
            make = "Other",
            model = "No Sound Kit yet",
            tier = VehicleSupportTier.Unsupported,
        ),
    )

    fun findById(id: String?): VehicleCompatibilityEntry? =
        entries.firstOrNull { it.id == id }

    fun makes(): List<String> = entries
        .filter { it.tier != VehicleSupportTier.Unsupported }
        .map { it.make }
        .distinct()
        .sorted()

    fun modelsForMake(make: String): List<VehicleCompatibilityEntry> =
        entries.filter { it.make == make && it.id != NO_SOUND_KIT_ID }

    fun unsupportedEntry(): VehicleCompatibilityEntry =
        entries.first { it.id == NO_SOUND_KIT_ID }
}
