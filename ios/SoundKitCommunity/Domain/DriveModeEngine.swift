import Foundation

@MainActor
final class DriveModeEngine {
    private weak var bleManager: BLEManager?
    private weak var valveControl: ValveControlCoordinator?
    private weak var settingsStore: SettingsStore?
    private weak var diagnostics: DiagnosticsStore?

    private var quietTask: Task<Void, Never>?
    private var activeSessionId: Int = 0
    private var currentSessionId: Int?
    private var userAdjustedSessionId: Int?

    func configure(
        ble: BLEManager,
        valveControl: ValveControlCoordinator,
        settings: SettingsStore,
        diagnostics: DiagnosticsStore
    ) {
        bleManager = ble
        self.valveControl = valveControl
        settingsStore = settings
        self.diagnostics = diagnostics
    }

    func onUserValveAdjustment() {
        userAdjustedSessionId = currentSessionId
        quietTask?.cancel()
        quietTask = nil
    }

    func onDisconnect() {
        quietTask?.cancel()
        quietTask = nil
        currentSessionId = nil
    }

    func onConnectReady(sessionId: Int) {
        guard let settingsStore, let bleManager else { return }
        if currentSessionId == sessionId { return }
        currentSessionId = sessionId
        userAdjustedSessionId = nil
        quietTask?.cancel()
        quietTask = nil

        let settings = settingsStore.settings
        guard settings.driveModeEnabled, !settings.automationPaused else {
            diagnostics?.debug("Drive mode skipped: disabled or paused")
            return
        }
        guard bleManager.isReadyForAutomation else {
            diagnostics?.debug("Drive mode skipped: not ready")
            return
        }

        let quiet = settings.quietStart
        if QuietWindowEvaluator.isActive(quiet) {
            applyClose(reason: "quiet start")
            let holdSeconds = quiet.holdClosedMinutes.clamped(to: 1...15) * 60
            quietTask = Task { [weak self] in
                try? await Task.sleep(nanoseconds: UInt64(holdSeconds) * 1_000_000_000)
                guard !Task.isCancelled else { return }
                await self?.applyPreferredAfterQuiet(sessionId: sessionId)
            }
        } else {
            applyPreferredMode(settings.preferredValveMode, reason: "connect")
        }
    }

    private func applyPreferredAfterQuiet(sessionId: Int) {
        guard currentSessionId == sessionId, userAdjustedSessionId != sessionId else { return }
        guard let settingsStore, let bleManager else { return }
        let settings = settingsStore.settings
        guard settings.driveModeEnabled, !settings.automationPaused, bleManager.isReadyForAutomation else { return }
        applyPreferredMode(settings.preferredValveMode, reason: "quiet end")
    }

    private func applyPreferredMode(_ mode: PreferredValveMode, reason: String) {
        guard userAdjustedSessionId != currentSessionId else { return }
        guard let bleManager, bleManager.isReadyForAutomation else { return }
        switch mode {
        case .open:
            if bleManager.valveState == .open {
                diagnostics?.debug("Drive mode skipped open (\(reason))")
                return
            }
            valveControl?.open()
        case .closed:
            if bleManager.valveState == .closed {
                diagnostics?.debug("Drive mode skipped closed (\(reason))")
                return
            }
            valveControl?.close()
        }
        diagnostics?.info("Drive mode \(mode.rawValue) (\(reason))")
    }

    private func applyClose(reason: String) {
        guard userAdjustedSessionId != currentSessionId else { return }
        guard let bleManager, bleManager.isReadyForAutomation else { return }
        if bleManager.valveState == .closed {
            diagnostics?.debug("Drive mode already closed (\(reason))")
            return
        }
        valveControl?.close()
        diagnostics?.info("Drive mode close (\(reason))")
    }

    func nextSessionId() -> Int {
        activeSessionId += 1
        return activeSessionId
    }
}

private extension Comparable {
    func clamped(to range: ClosedRange<Self>) -> Self {
        min(max(self, range.lowerBound), range.upperBound)
    }
}
