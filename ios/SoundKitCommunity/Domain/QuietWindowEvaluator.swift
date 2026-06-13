import Foundation

enum QuietWindowEvaluator {
    static func isOvernight(_ quiet: QuietStartSettings) -> Bool {
        quiet.windowEndMinute < quiet.windowStartMinute
    }

    static func isActive(_ quiet: QuietStartSettings, now: Date = Date()) -> Bool {
        guard quiet.enabled else { return false }
        var calendar = Calendar.current
        calendar.timeZone = .current
        let weekday = (calendar.component(.weekday, from: now) + 5) % 7
        guard quiet.daysOfWeek.contains(weekday) else { return false }
        let hour = calendar.component(.hour, from: now)
        let minute = calendar.component(.minute, from: now)
        let minuteOfDay = hour * 60 + minute
        if quiet.windowEndMinute >= quiet.windowStartMinute {
            return minuteOfDay >= quiet.windowStartMinute && minuteOfDay <= quiet.windowEndMinute
        }
        return minuteOfDay >= quiet.windowStartMinute || minuteOfDay <= quiet.windowEndMinute
    }
}
