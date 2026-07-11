import Foundation

@MainActor
final class SettingsStore: ObservableObject {
    @Published private(set) var settings: SoundKitSettings
    @Published private(set) var persistenceError: String?

    private let defaults: UserDefaults
    private let settingsKey = "soundkit_settings_v1"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        persistenceError = nil
        guard let data = defaults.data(forKey: settingsKey) else {
            settings = SoundKitSettings()
            return
        }
        do {
            settings = try JSONDecoder().decode(SoundKitSettings.self, from: data).validated()
        } catch {
            settings = SoundKitSettings()
            persistenceError = "Couldn't restore saved settings: \(error.localizedDescription)"
        }
    }

    func update(_ transform: (inout SoundKitSettings) -> Void) {
        var copy = settings
        transform(&copy)
        commit(copy)
    }

    func completeOnboarding(selectedVehicleId: String?) {
        update { s in
            s.onboardingCompletedAt = Date().timeIntervalSince1970
            if s.riskNoticeAcceptedAt == 0 {
                s.riskNoticeAcceptedAt = s.onboardingCompletedAt
            }
            if let selectedVehicleId {
                s.selectedVehicleId = selectedVehicleId
                if let entry = VehicleCompatibilityCatalog.findById(selectedVehicleId),
                   let themeId = entry.suggestedGarageThemeId {
                    s.garageThemeId = themeId
                }
            }
        }
    }

    func acceptRisk() {
        update { $0.riskNoticeAcceptedAt = Date().timeIntervalSince1970 }
    }

    @discardableResult
    func rememberDevice(id: String, name: String, nickname: String?, setDefault: Bool) -> Result<Void, SettingsValidationError> {
        guard UUID(uuidString: id) != nil else { return .failure(.invalidReceiverIdentifier) }
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedName.isEmpty, trimmedName.count <= 80 else { return .failure(.invalidReceiverName) }

        var copy = settings
        let existing = copy.savedReceivers.first { $0.address == id }
        if existing == nil, copy.savedReceivers.count >= SoundKitSettings.maximumSavedReceivers {
            return .failure(.tooManyReceivers)
        }
        copy.savedReceivers.removeAll { $0.address == id }
        let shouldDefault = setDefault || copy.savedReceivers.isEmpty
        if shouldDefault {
            copy.savedReceivers = copy.savedReceivers.map { receiver in
                var receiver = receiver
                receiver.isDefault = false
                return receiver
            }
        }
        copy.savedReceivers.insert(
            SavedReceiver(address: id, name: trimmedName, nickname: nickname, isDefault: shouldDefault),
            at: 0
        )
        commit(copy)
        return .success(())
    }

    func renameReceiver(id: String, nickname: String?) {
        let normalizedNickname = nickname?.trimmingCharacters(in: .whitespacesAndNewlines)
        update { settings in
            guard let index = settings.savedReceivers.firstIndex(where: { $0.address == id }) else { return }
            settings.savedReceivers[index].nickname = normalizedNickname?.isEmpty == true ? nil : normalizedNickname
        }
    }

    func setDefaultReceiver(id: String) {
        update { settings in
            guard settings.savedReceivers.contains(where: { $0.address == id }) else { return }
            settings.savedReceivers = settings.savedReceivers.map { receiver in
                var receiver = receiver
                receiver.isDefault = receiver.address == id
                return receiver
            }
        }
    }

    func forgetReceiver(id: String) {
        update { settings in
            let removedDefault = settings.defaultReceiver?.address == id
            settings.savedReceivers.removeAll { $0.address == id }
            if removedDefault, !settings.savedReceivers.isEmpty {
                settings.savedReceivers[0].isDefault = true
            }
        }
    }

    func exportBackup() throws -> Data {
        try JSONEncoder().encode(SettingsBackup(settings: try settings.validated()))
    }

    /// Imports all preferences atomically. Receiver identifiers are platform-bound:
    /// Android MAC addresses and unknown platforms are intentionally discarded.
    func importBackup(_ data: Data) -> Result<SettingsImportOutcome, Error> {
        do {
            let backup = try decodeBackup(data)
            guard backup.version == SettingsBackup.currentVersion else {
                throw SettingsValidationError.unsupportedBackupVersion
            }
            var imported = backup.settings
            let isLocalBackup = backup.platform.lowercased() == SettingsBackup.iOSPlatform
            if !isLocalBackup {
                imported.savedReceivers = []
            }
            try imported.validated(validateReceiverIdentifiers: isLocalBackup)
            try commitAtomically(imported)
            return .success(SettingsImportOutcome(discardedPlatformBoundReceivers: !isLocalBackup && !backup.settings.savedReceivers.isEmpty))
        } catch {
            return .failure(error)
        }
    }

    private func decodeBackup(_ data: Data) throws -> SettingsBackup {
        guard data.count <= 64_000,
              let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let version = root["version"] as? Int else {
            throw SettingsValidationError.invalidBackup
        }
        if root["settings"] != nil {
            return try JSONDecoder().decode(SettingsBackup.self, from: data)
        }

        // Android's v1 backup is a flat JSON object. It deliberately has no
        // platform field, so parse only portable preferences and never import its MAC addresses.
        guard version == SettingsBackup.currentVersion else {
            throw SettingsValidationError.unsupportedBackupVersion
        }
        var settings = SoundKitSettings()
        settings.savedReceivers = []
        settings.connectOnLaunch = try boolean(root, key: "connectOnLaunch", defaultValue: settings.connectOnLaunch)
        settings.connectInCar = try boolean(root, key: "connectInCar", defaultValue: settings.connectInCar)
        settings.headUnitPriorityEnabled = try boolean(root, key: "headUnitPriorityEnabled", defaultValue: settings.headUnitPriorityEnabled)
        settings.autoReconnect = try boolean(root, key: "autoReconnect", defaultValue: settings.autoReconnect)
        settings.driveModeEnabled = try boolean(root, key: "driveModeEnabled", defaultValue: settings.driveModeEnabled)

        if let theme = root["garageThemeId"] {
            guard let theme = theme as? String, GarageThemePresets.all.contains(where: { $0.id == theme }) else {
                throw SettingsValidationError.invalidBackup
            }
            settings.garageThemeId = theme
        }
        if let vehicle = root["selectedVehicleId"] {
            guard let vehicle = vehicle as? String, !vehicle.isEmpty, vehicle.count <= 128 else {
                throw SettingsValidationError.invalidBackup
            }
            settings.selectedVehicleId = vehicle
        }
        if let mode = root["preferredValveMode"] {
            guard let value = mode as? String, let preferredMode = PreferredValveMode(rawValue: value) else {
                throw SettingsValidationError.invalidBackup
            }
            settings.preferredValveMode = preferredMode
        }
        if let quietJSON = root["quietStartJson"] {
            guard let quietJSON = quietJSON as? String, quietJSON.utf8.count <= 8_192,
                  let quietData = quietJSON.data(using: .utf8) else {
                throw SettingsValidationError.invalidBackup
            }
            settings.quietStart = try decodeAndroidQuietStart(quietData)
        }
        try settings.validated()
        return SettingsBackup(version: version, platform: "android", settings: settings)
    }

    private func boolean(_ root: [String: Any], key: String, defaultValue: Bool) throws -> Bool {
        guard let value = root[key] else { return defaultValue }
        guard let value = value as? Bool else { throw SettingsValidationError.invalidBackup }
        return value
    }

    private func decodeAndroidQuietStart(_ data: Data) throws -> QuietStartSettings {
        guard let object = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw SettingsValidationError.invalidBackup
        }
        guard let enabled = object["enabled"] as? Bool,
              let start = object["windowStartMinute"] as? Int,
              let end = object["windowEndMinute"] as? Int,
              let hold = object["holdClosedMinutes"] as? Int else {
            throw SettingsValidationError.invalidBackup
        }
        let days: Set<Int>
        if let values = object["daysOfWeek"] as? String {
            let parsed = values.split(separator: ",").compactMap { Int($0.trimmingCharacters(in: .whitespaces)) }
            guard !parsed.isEmpty, parsed.allSatisfy({ (0...6).contains($0) }) else {
                throw SettingsValidationError.invalidBackup
            }
            days = Set(parsed)
        } else {
            throw SettingsValidationError.invalidBackup
        }
        return QuietStartSettings(
            enabled: enabled,
            daysOfWeek: days,
            windowStartMinute: start,
            windowEndMinute: end,
            holdClosedMinutes: hold
        )
    }

    private func commit(_ newSettings: SoundKitSettings) {
        do {
            try commitAtomically(newSettings)
        } catch {
            persistenceError = "Couldn't save settings: \(error.localizedDescription)"
        }
    }

    private func commitAtomically(_ newSettings: SoundKitSettings) throws {
        let validated = try newSettings.validated()
        let data = try JSONEncoder().encode(validated)
        defaults.set(data, forKey: settingsKey)
        settings = validated
        persistenceError = nil
    }
}

struct SettingsImportOutcome: Equatable {
    let discardedPlatformBoundReceivers: Bool
}
