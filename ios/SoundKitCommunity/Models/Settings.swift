import Foundation

enum PreferredValveMode: String, Codable, CaseIterable {
    case open = "Open"
    case closed = "Closed"
}

struct QuietStartSettings: Codable, Equatable {
    var enabled: Bool = false
    var daysOfWeek: Set<Int> = Set(0...6)
    var windowStartMinute: Int = 6 * 60
    var windowEndMinute: Int = 9 * 60
    var holdClosedMinutes: Int = 3
}

struct SavedReceiver: Codable, Equatable, Identifiable {
    var id: String { address }
    let address: String
    let name: String
    var nickname: String?
    var isDefault: Bool = false

    func displayName() -> String {
        nickname.flatMap { $0.isEmpty ? nil : $0 } ?? name
    }
}

struct SoundKitSettings: Codable, Equatable {
    var savedReceivers: [SavedReceiver] = []
    var connectOnLaunch: Bool = true
    var headUnitPriorityEnabled: Bool = true
    var autoReconnect: Bool = true
    var garageThemeId: String = "studio-dark"
    var riskNoticeAcceptedAt: TimeInterval = 0
    var onboardingCompletedAt: TimeInterval = 0
    var selectedVehicleId: String?
    var automationPaused: Bool = false
    var driveModeEnabled: Bool = true
    var preferredValveMode: PreferredValveMode = .open
    var quietStart: QuietStartSettings = QuietStartSettings()

    var riskNoticeAccepted: Bool { riskNoticeAcceptedAt > 0 }
    var onboardingCompleted: Bool { onboardingCompletedAt > 0 }
    var defaultReceiver: SavedReceiver? { savedReceivers.first { $0.isDefault } }
}

enum DriveModeProfile: String, CaseIterable, Identifiable {
    case everyday = "Everyday"
    case quietStreet = "Quiet street"
    case track = "Track"

    var id: String { rawValue }

    var label: String { rawValue }

    var preferredMode: PreferredValveMode {
        switch self {
        case .everyday, .track: return .open
        case .quietStreet: return .closed
        }
    }

    var quietEnabled: Bool {
        switch self {
        case .quietStreet: return true
        case .everyday, .track: return false
        }
    }

    func apply(to settings: SoundKitSettings) -> SoundKitSettings {
        var copy = settings
        copy.driveModeEnabled = true
        copy.preferredValveMode = preferredMode
        copy.quietStart.enabled = quietEnabled
        return copy
    }

    static func matching(_ settings: SoundKitSettings) -> DriveModeProfile {
        DriveModeProfile.allCases.first {
            $0.preferredMode == settings.preferredValveMode &&
                $0.quietEnabled == settings.quietStart.enabled
        } ?? .everyday
    }
}
