import Combine
import Foundation
import SwiftUI

@MainActor
final class SoundKitViewModel: ObservableObject {
    let settingsStore: SettingsStore
    let diagnosticsStore: DiagnosticsStore
    let bleManager: BLEManager
    let driveModeEngine: DriveModeEngine

    @Published var selectedTab: AppTab = .home
    @Published var showDriveMode = false
    @Published var showSettings = false
    @Published var showAppearance = false
    @Published var showAdvanced = false
    @Published var showDiagnostics = false

    private var cancellables = Set<AnyCancellable>()

    init(
        settingsStore: SettingsStore? = nil,
        diagnosticsStore: DiagnosticsStore? = nil,
        bleManager: BLEManager? = nil,
        driveModeEngine: DriveModeEngine? = nil
    ) {
        self.settingsStore = settingsStore ?? SettingsStore()
        self.diagnosticsStore = diagnosticsStore ?? DiagnosticsStore()
        self.driveModeEngine = driveModeEngine ?? DriveModeEngine()
        let engine = self.driveModeEngine
        let ble = bleManager ?? BLEManager(sessionProvider: { [weak engine] in engine?.nextSessionId() ?? 0 })
        self.bleManager = ble

        self.driveModeEngine.configure(ble: ble, settings: self.settingsStore, diagnostics: self.diagnosticsStore)
        ble.onDiagnostics = { [weak diagnosticsStore = self.diagnosticsStore] message in
            Task { @MainActor in diagnosticsStore?.debug(message) }
        }
        ble.onConnectReady = { [weak engine = self.driveModeEngine] sessionId in
            Task { @MainActor in engine?.onConnectReady(sessionId: sessionId) }
        }
        ble.onDisconnectEvent = { [weak engine = self.driveModeEngine] in
            Task { @MainActor in engine?.onDisconnect() }
        }

        self.settingsStore.$settings
            .receive(on: RunLoop.main)
            .sink { [weak self] _ in self?.objectWillChange.send() }
            .store(in: &cancellables)
    }

    var activeTheme: GarageTheme {
        GarageThemePresets.find(id: settingsStore.settings.garageThemeId)
    }

    func onAppear() {
        guard settingsStore.settings.onboardingCompleted else { return }
        tryConnectOnLaunch()
    }

    func tryConnectOnLaunch() {
        guard settingsStore.settings.connectOnLaunch,
              let receiver = settingsStore.settings.defaultReceiver else { return }
        bleManager.connectToRemembered(id: receiver.address, name: receiver.displayName())
    }

    func completeOnboarding(vehicleId: String?) {
        settingsStore.completeOnboarding(selectedVehicleId: vehicleId)
    }

    func onValveCommand() {
        driveModeEngine.onUserValveAdjustment()
    }

    func rememberConnectedDevice() {
        guard case .connected(let device) = bleManager.connectionPhase else { return }
        let nickname = VehicleCompatibilityCatalog.findById(settingsStore.settings.selectedVehicleId)?.defaultNickname
        settingsStore.rememberDevice(
            id: device.id,
            name: device.name,
            nickname: nickname,
            setDefault: true
        )
        diagnosticsStore.info("Saved receiver \(device.name)")
    }

    func applyProfile(_ profile: DriveModeProfile) {
        settingsStore.update { $0 = profile.apply(to: $0) }
    }
}

enum AppTab: String, CaseIterable, Identifiable {
    case home = "Home"
    case more = "More"

    var id: String { rawValue }
}
