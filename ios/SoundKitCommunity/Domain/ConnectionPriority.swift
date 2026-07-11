import Foundation

enum ConnectionYieldReason: Equatable {
    case headUnitMayBeActive
}

enum ConnectionYieldState: Equatable {
    case none
    case yielded(ConnectionYieldReason)
}

enum ConnectionPriorityPolicy {
    static func isPrimaryController(carSessionActive: Bool) -> Bool {
        carSessionActive
    }

    static func shouldAutoConnectOnLaunch(
        settings: SoundKitSettings,
        carSessionActive: Bool
    ) -> Bool {
        guard settings.connectOnLaunch else { return false }
        guard settings.defaultReceiver != nil else { return false }
        if settings.headUnitPriorityEnabled && !isPrimaryController(carSessionActive: carSessionActive) {
            return false
        }
        return true
    }

    static func shouldAutoConnectInCar(settings: SoundKitSettings) -> Bool {
        settings.connectInCar && settings.defaultReceiver != nil
    }

    static func shouldAutoReconnect(
        settings: SoundKitSettings,
        carSessionActive: Bool,
        userRequestedControl: Bool,
        yieldState: ConnectionYieldState
    ) -> Bool {
        guard settings.autoReconnect else { return false }
        if case .yielded = yieldState { return false }
        if !settings.headUnitPriorityEnabled { return true }
        if isPrimaryController(carSessionActive: carSessionActive) { return true }
        return userRequestedControl
    }

    static func shouldEnterYieldOnContention(
        settings: SoundKitSettings,
        carSessionActive: Bool
    ) -> Bool {
        guard settings.headUnitPriorityEnabled else { return false }
        return !isPrimaryController(carSessionActive: carSessionActive)
    }
}

enum BleContentionSignal {
    case quickDrop
    case connectStorm
}

final class BleContentionDetector {
    static let quickDropWindowMs: Int64 = 3_000
    static let stormWindowMs: Int64 = 30_000
    static let stormEventThreshold = 3

    private var connectedAtMs: Int64?
    private var recentEvents: [Int64] = []
    private let clock: () -> Int64

    init(clock: @escaping () -> Int64 = {
        Int64(Date().timeIntervalSince1970 * 1_000)
    }) {
        self.clock = clock
    }

    func onConnected() {
        connectedAtMs = clock()
    }

    func onDisconnected(userInitiated: Bool) -> BleContentionSignal? {
        let connectedAt = connectedAtMs
        connectedAtMs = nil
        if userInitiated {
            recordEvent(clock())
            return nil
        }
        let now = clock()
        recordEvent(now)
        if let connectedAt, now - connectedAt <= Self.quickDropWindowMs {
            return .quickDrop
        }
        if countEventsWithin(now: now, windowMs: Self.stormWindowMs) >= Self.stormEventThreshold {
            return .connectStorm
        }
        return nil
    }

    func onConnectFailed() -> BleContentionSignal? {
        let now = clock()
        recordEvent(now)
        if countEventsWithin(now: now, windowMs: Self.stormWindowMs) >= Self.stormEventThreshold {
            return .connectStorm
        }
        return nil
    }

    func reset() {
        connectedAtMs = nil
        recentEvents.removeAll()
    }

    private func recordEvent(_ timestampMs: Int64) {
        recentEvents.append(timestampMs)
        trimEvents(now: timestampMs)
    }

    private func trimEvents(now: Int64) {
        recentEvents.removeAll { now - $0 > Self.stormWindowMs }
    }

    private func countEventsWithin(now: Int64, windowMs: Int64) -> Int {
        trimEvents(now: now)
        return recentEvents.filter { now - $0 <= windowMs }.count
    }
}

/// Placeholder for a future CarPlay session hook; Android uses Android Auto Car App lifecycle.
@MainActor
final class CarSessionTracker: ObservableObject {
    static let shared = CarSessionTracker()

    @Published private(set) var isCarSessionActive = false
    private var sessionCount = 0

    private init() {}

    func beginSession() {
        sessionCount += 1
        isCarSessionActive = sessionCount > 0
    }

    func endSession() {
        sessionCount = max(0, sessionCount - 1)
        isCarSessionActive = sessionCount > 0
    }
}
