import Foundation

enum VehicleSupportTier: String, CaseIterable {
    case supported = "Supported"
    case beta = "Beta"
    case unsupported = "Not compatible"

    var description: String {
        switch self {
        case .supported:
            return "Supported — same receiver protocol as our reference testing."
        case .beta:
            return "Beta — likely works on the same BLE protocol. If you hit issues, export diagnostics and email support@appsforgood.net."
        case .unsupported:
            return "This app needs an Akrapovič Car Sound Kit BLE receiver. Install the kit first, then return to set up."
        }
    }
}

struct VehicleCompatibilityEntry: Identifiable, Hashable {
    let id: String
    let make: String
    let model: String
    let tier: VehicleSupportTier
    let suggestedGarageThemeId: String?
    let defaultNickname: String?

    var displayName: String { "\(make) \(model)" }

    var tierLabel: String { tier.rawValue }
}

enum VehicleCompatibilityCatalog {
    static let otherSoundKitId = "other-soundkit-beta"
    static let noSoundKitId = "no-soundkit"

    static let entries: [VehicleCompatibilityEntry] = [
        VehicleCompatibilityEntry(
            id: "audi-rs3",
            make: "Audi",
            model: "RS3",
            tier: .supported,
            suggestedGarageThemeId: "audi-rs-dark",
            defaultNickname: "Audi RS3"
        ),
        VehicleCompatibilityEntry(
            id: "audi-rs-other",
            make: "Audi",
            model: "RS (other)",
            tier: .beta,
            suggestedGarageThemeId: "audi-rs-dark",
            defaultNickname: nil
        ),
        VehicleCompatibilityEntry(
            id: "bmw-m3-m4-f8x",
            make: "BMW",
            model: "M3 / M4 (F80/F82/F83)",
            tier: .beta,
            suggestedGarageThemeId: "bmw-m-dark",
            defaultNickname: nil
        ),
        VehicleCompatibilityEntry(
            id: "bmw-x3m-x4m-f97",
            make: "BMW",
            model: "X3 M / X4 M (F97/F98)",
            tier: .beta,
            suggestedGarageThemeId: "bmw-m-dark",
            defaultNickname: nil
        ),
        VehicleCompatibilityEntry(
            id: "porsche-soundkit",
            make: "Porsche",
            model: "Akrapovič Sound Kit",
            tier: .beta,
            suggestedGarageThemeId: "porsche-dark",
            defaultNickname: nil
        ),
        VehicleCompatibilityEntry(
            id: "amg-soundkit",
            make: "Mercedes-AMG",
            model: "Akrapovič Sound Kit",
            tier: .beta,
            suggestedGarageThemeId: "mercedes-amg-dark",
            defaultNickname: nil
        ),
        VehicleCompatibilityEntry(
            id: otherSoundKitId,
            make: "Other",
            model: "Car with Sound Kit",
            tier: .beta,
            suggestedGarageThemeId: nil,
            defaultNickname: nil
        ),
        VehicleCompatibilityEntry(
            id: noSoundKitId,
            make: "Other",
            model: "No Sound Kit yet",
            tier: .unsupported,
            suggestedGarageThemeId: nil,
            defaultNickname: nil
        ),
    ]

    static func findById(_ id: String?) -> VehicleCompatibilityEntry? {
        guard let id else { return nil }
        return entries.first { $0.id == id }
    }

    static var makes: [String] {
        entries
            .filter { $0.tier != .unsupported }
            .map(\.make)
            .uniqued()
            .sorted()
    }

    static func modelsForMake(_ make: String) -> [VehicleCompatibilityEntry] {
        entries.filter { $0.make == make && $0.id != noSoundKitId }
    }

    static func unsupportedEntry() -> VehicleCompatibilityEntry {
        entries.first { $0.id == noSoundKitId }!
    }
}

private extension Array where Element: Hashable {
    func uniqued() -> [Element] {
        var seen = Set<Element>()
        return filter { seen.insert($0).inserted }
    }
}
