import Foundation

enum DiagnosticsLevel: String, Codable {
    case debug, info, warning, error
}

struct DiagnosticsEntry: Identifiable, Equatable {
    let id: UUID
    let timestamp: Date
    let level: DiagnosticsLevel
    let message: String

    init(level: DiagnosticsLevel, message: String) {
        self.id = UUID()
        self.timestamp = Date()
        self.level = level
        self.message = message
    }
}

@MainActor
final class DiagnosticsStore: ObservableObject {
    @Published private(set) var entries: [DiagnosticsEntry] = []
    private let maxEntries = 500

    func debug(_ message: String) { append(.debug, message) }
    func info(_ message: String) { append(.info, message) }
    func warning(_ message: String) { append(.warning, message) }
    func error(_ message: String) { append(.error, message) }

    func exportText(settings: SoundKitSettings) -> String {
        var lines = [
            "Sound Kit Community — Diagnostics (iOS)",
            "exportedAt=\(ISO8601DateFormatter().string(from: Date()))",
            "vehicleId=\(settings.selectedVehicleId ?? "none")",
            "themeId=\(settings.garageThemeId)",
            "",
        ]
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
        for entry in entries {
            lines.append("[\(entry.level.rawValue.uppercased())] \(formatter.string(from: entry.timestamp)) \(entry.message)")
        }
        return lines.joined(separator: "\n")
    }

    private func append(_ level: DiagnosticsLevel, _ message: String) {
        entries.append(DiagnosticsEntry(level: level, message: message))
        if entries.count > maxEntries {
            entries.removeFirst(entries.count - maxEntries)
        }
    }
}
