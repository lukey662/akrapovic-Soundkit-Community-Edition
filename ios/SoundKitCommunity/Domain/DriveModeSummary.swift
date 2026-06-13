import Foundation

enum DriveModeSummary {
    static func headline(_ settings: SoundKitSettings) -> String {
        if !settings.driveModeEnabled { return "Off — tap to set up" }
        let preferred = settings.preferredValveMode.rawValue.lowercased()
        if settings.automationPaused { return "Paused · prefers \(preferred)" }
        if QuietWindowEvaluator.isActive(settings.quietStart) {
            return "Prefers \(preferred) · quiet hours"
        }
        return "Prefers \(preferred) on connect"
    }

    static func formatMinute(_ minuteOfDay: Int) -> String {
        String(format: "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60)
    }

    static func formatEndMinute(_ quiet: QuietStartSettings) -> String {
        let formatted = formatMinute(quiet.windowEndMinute)
        return QuietWindowEvaluator.isOvernight(quiet) ? "\(formatted) (next day)" : formatted
    }
}
