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

    enum CodingKeys: String, CodingKey { case address, name, nickname, isDefault }

    init(address: String, name: String, nickname: String? = nil, isDefault: Bool = false) {
        self.address = address
        self.name = name
        self.nickname = nickname
        self.isDefault = isDefault
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        address = try container.decode(String.self, forKey: .address)
        name = try container.decode(String.self, forKey: .name)
        nickname = try container.decodeIfPresent(String.self, forKey: .nickname)
        isDefault = try container.decodeIfPresent(Bool.self, forKey: .isDefault) ?? false
    }
}

struct SoundKitSettings: Codable, Equatable {
    var savedReceivers: [SavedReceiver] = []
    var connectOnLaunch: Bool = true
    /// CarPlay connection is a separate, explicit preference from phone launch connection.
    var connectInCar: Bool = true
    var headUnitPriorityEnabled: Bool = true
    var autoReconnect: Bool = true
    var debugLoggingEnabled: Bool = false
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

    enum CodingKeys: String, CodingKey {
        case savedReceivers, connectOnLaunch, connectInCar, headUnitPriorityEnabled, autoReconnect
        case debugLoggingEnabled, garageThemeId, riskNoticeAcceptedAt, onboardingCompletedAt
        case selectedVehicleId, automationPaused, driveModeEnabled, preferredValveMode, quietStart
    }

    init() {}

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        savedReceivers = try container.decodeIfPresent([SavedReceiver].self, forKey: .savedReceivers) ?? []
        connectOnLaunch = try container.decodeIfPresent(Bool.self, forKey: .connectOnLaunch) ?? true
        connectInCar = try container.decodeIfPresent(Bool.self, forKey: .connectInCar) ?? true
        headUnitPriorityEnabled = try container.decodeIfPresent(Bool.self, forKey: .headUnitPriorityEnabled) ?? true
        autoReconnect = try container.decodeIfPresent(Bool.self, forKey: .autoReconnect) ?? true
        debugLoggingEnabled = try container.decodeIfPresent(Bool.self, forKey: .debugLoggingEnabled) ?? false
        garageThemeId = try container.decodeIfPresent(String.self, forKey: .garageThemeId) ?? "studio-dark"
        riskNoticeAcceptedAt = try container.decodeIfPresent(TimeInterval.self, forKey: .riskNoticeAcceptedAt) ?? 0
        onboardingCompletedAt = try container.decodeIfPresent(TimeInterval.self, forKey: .onboardingCompletedAt) ?? 0
        selectedVehicleId = try container.decodeIfPresent(String.self, forKey: .selectedVehicleId)
        automationPaused = try container.decodeIfPresent(Bool.self, forKey: .automationPaused) ?? false
        driveModeEnabled = try container.decodeIfPresent(Bool.self, forKey: .driveModeEnabled) ?? true
        preferredValveMode = try container.decodeIfPresent(PreferredValveMode.self, forKey: .preferredValveMode) ?? .open
        quietStart = try container.decodeIfPresent(QuietStartSettings.self, forKey: .quietStart) ?? QuietStartSettings()
    }
}

enum SettingsValidationError: LocalizedError, Equatable {
    case unsupportedBackupVersion
    case invalidReceiverIdentifier
    case invalidReceiverName
    case duplicateReceiver
    case tooManyReceivers
    case multipleDefaults
    case invalidQuietStart
    case invalidBackup

    var errorDescription: String? {
        switch self {
        case .unsupportedBackupVersion: return "This settings backup uses an unsupported version."
        case .invalidReceiverIdentifier: return "A saved receiver has an invalid iOS identifier."
        case .invalidReceiverName: return "A saved receiver has an invalid name."
        case .duplicateReceiver: return "A settings backup contains duplicate receivers."
        case .tooManyReceivers: return "A settings backup contains more than 8 receivers."
        case .multipleDefaults: return "A settings backup contains more than one default receiver."
        case .invalidQuietStart: return "A settings backup contains invalid quiet-start settings."
        case .invalidBackup: return "This settings backup has an invalid format."
        }
    }
}

extension SoundKitSettings {
    static let maximumSavedReceivers = 8

    func validated(validateReceiverIdentifiers: Bool = true) throws -> SoundKitSettings {
        guard savedReceivers.count <= Self.maximumSavedReceivers else {
            throw SettingsValidationError.tooManyReceivers
        }
        guard quietStart.windowStartMinute >= 0, quietStart.windowStartMinute < 24 * 60,
              quietStart.windowEndMinute >= 0, quietStart.windowEndMinute < 24 * 60,
              quietStart.holdClosedMinutes >= 0, quietStart.holdClosedMinutes <= 60 else {
            throw SettingsValidationError.invalidQuietStart
        }

        var identifiers = Set<String>()
        var defaultCount = 0
        for receiver in savedReceivers {
            let trimmedName = receiver.name.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !trimmedName.isEmpty, trimmedName.count <= 80 else {
                throw SettingsValidationError.invalidReceiverName
            }
            if validateReceiverIdentifiers {
                guard UUID(uuidString: receiver.address) != nil else {
                    throw SettingsValidationError.invalidReceiverIdentifier
                }
            }
            guard identifiers.insert(receiver.address).inserted else {
                throw SettingsValidationError.duplicateReceiver
            }
            if receiver.isDefault { defaultCount += 1 }
        }
        guard defaultCount <= 1 else { throw SettingsValidationError.multipleDefaults }
        return self
    }
}

struct SettingsBackup: Codable, Equatable {
    static let currentVersion = 1
    static let iOSPlatform = "ios"

    let version: Int
    let platform: String
    let settings: SoundKitSettings

    init(settings: SoundKitSettings) {
        version = Self.currentVersion
        platform = Self.iOSPlatform
        self.settings = settings
    }

    init(version: Int, platform: String, settings: SoundKitSettings) {
        self.version = version
        self.platform = platform
        self.settings = settings
    }
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
