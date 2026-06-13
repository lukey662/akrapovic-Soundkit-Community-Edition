import Foundation

@MainActor
final class SettingsStore: ObservableObject {
    @Published private(set) var settings: SoundKitSettings

    private let defaults: UserDefaults
    private let settingsKey = "soundkit_settings_v1"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        if let data = defaults.data(forKey: settingsKey),
           let decoded = try? JSONDecoder().decode(SoundKitSettings.self, from: data) {
            settings = decoded
        } else {
            settings = SoundKitSettings()
        }
    }

    func update(_ transform: (inout SoundKitSettings) -> Void) {
        var copy = settings
        transform(&copy)
        settings = copy
        persist()
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

    func rememberDevice(id: String, name: String, nickname: String?, setDefault: Bool) {
        update { s in
            var receivers = s.savedReceivers.filter { $0.address != id }
            if setDefault {
                receivers = receivers.map { var r = $0; r.isDefault = false; return r }
            }
            let receiver = SavedReceiver(
                address: id,
                name: name,
                nickname: nickname,
                isDefault: setDefault || receivers.isEmpty
            )
            receivers.insert(receiver, at: 0)
            s.savedReceivers = Array(receivers.prefix(8))
        }
    }

    private func persist() {
        guard let data = try? JSONEncoder().encode(settings) else { return }
        defaults.set(data, forKey: settingsKey)
    }
}
