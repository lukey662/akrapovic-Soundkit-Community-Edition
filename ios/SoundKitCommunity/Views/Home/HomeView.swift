import SwiftUI

struct HomeView: View {
    @EnvironmentObject private var viewModel: SoundKitViewModel
    @EnvironmentObject private var bleManager: BLEManager
    @EnvironmentObject private var settingsStore: SettingsStore
    @Environment(\.garageTheme) private var theme

    var body: some View {
        NavigationStack {
            Group {
                if bleManager.isConnected {
                    ConnectedDeviceView()
                } else {
                    ScanView()
                }
            }
            .themedScreen()
            .navigationTitle("Home")
            .toolbar {
                if !bleManager.isConnected {
                    ToolbarItem(placement: .topBarTrailing) {
                        if case .scanning = bleManager.connectionPhase {
                            Button("Stop") { bleManager.stopScan() }
                        } else {
                            Button("Scan") { bleManager.startScan() }
                        }
                    }
                }
            }
        }
    }
}

struct ConnectedDeviceView: View {
    @EnvironmentObject private var viewModel: SoundKitViewModel
    @EnvironmentObject private var bleManager: BLEManager
    @EnvironmentObject private var settingsStore: SettingsStore
    @Environment(\.garageTheme) private var theme

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                statusLine
                valveHero
                primaryAction
                driveModeRow
                disconnectRow
            }
            .padding(24)
        }
        .onAppear { viewModel.rememberConnectedDevice() }
    }

    private var statusLine: some View {
        HStack(spacing: 8) {
            Circle().fill(Color.green).frame(width: 8, height: 8)
            Text(connectionTitle)
                .font(.subheadline)
                .foregroundStyle(theme.muted)
            Spacer()
        }
    }

    private var valveHero: some View {
        VStack(spacing: 12) {
            Circle()
                .stroke(theme.accent.opacity(0.5), lineWidth: 3)
                .background(Circle().fill(theme.surface))
                .frame(width: 160, height: 160)
            Text(valveLabel)
                .font(.largeTitle.bold())
            Text(valveSubtitle)
                .font(.subheadline)
                .foregroundStyle(theme.muted)
                .multilineTextAlignment(.center)
            if let message = bleManager.statusMessage {
                Text(message)
                    .font(.footnote)
                    .foregroundStyle(.orange)
                    .multilineTextAlignment(.center)
            }
        }
    }

    private var primaryAction: some View {
        Button(primaryActionTitle) {
            viewModel.onValveCommand()
            if bleManager.valveState == .open {
                bleManager.closeValves()
            } else {
                bleManager.openValves()
            }
        }
        .buttonStyle(PrimaryButtonStyle())
        .disabled(!bleManager.canControlValves)
    }

    private var driveModeRow: some View {
        Button {
            viewModel.showDriveMode = true
        } label: {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Drive mode")
                        .font(.headline)
                    Text(DriveModeSummary.headline(settingsStore.settings))
                        .font(.caption)
                        .foregroundStyle(theme.muted)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .foregroundStyle(theme.muted)
            }
            .padding(16)
            .background(theme.surface)
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    private var disconnectRow: some View {
        Button("Disconnect", role: .destructive) {
            bleManager.disconnect()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var connectionTitle: String {
        if case .connected(let device) = bleManager.connectionPhase {
            return "\(device.name) · Connected"
        }
        return "Connected"
    }

    private var valveLabel: String {
        switch bleManager.valveState {
        case .open: return "Open"
        case .closed: return "Closed"
        case .unknown: return "Waiting"
        }
    }

    private var valveSubtitle: String {
        switch bleManager.valveState {
        case .open: return "Sport mode — valves are open."
        case .closed: return "Quiet mode — valves are closed."
        case .unknown: return "Waiting for receiver status…"
        }
    }

    private var primaryActionTitle: String {
        bleManager.valveState == .open ? "Close valves" : "Open valves"
    }
}

struct ScanView: View {
    @EnvironmentObject private var bleManager: BLEManager
    @Environment(\.garageTheme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Connect")
                .font(.largeTitle.bold())
            Text("Bluetooth only — nothing leaves your phone.")
                .foregroundStyle(theme.muted)
            Button("Scan nearby") { bleManager.startScan() }
                .buttonStyle(PrimaryButtonStyle())

            if bleManager.discoveredDevices.isEmpty {
                Text("No receivers yet")
                    .foregroundStyle(theme.muted)
                    .padding(.top, 8)
            } else {
                ForEach(bleManager.discoveredDevices.sorted { $0.rssi > $1.rssi }) { device in
                    Button {
                        bleManager.connect(to: device)
                    } label: {
                        HStack {
                            VStack(alignment: .leading) {
                                Text(device.name)
                                Text(device.isLikelySoundKit ? "Likely Sound Kit" : "BLE device")
                                    .font(.caption)
                                    .foregroundStyle(theme.muted)
                            }
                            Spacer()
                            Text("\(device.rssi) dBm")
                                .font(.caption.monospacedDigit())
                                .foregroundStyle(theme.muted)
                        }
                        .padding(12)
                        .background(theme.surface)
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .disabled(isConnecting)
                }
            }
            Spacer()
        }
        .padding(24)
    }

    private var isConnecting: Bool {
        switch bleManager.connectionPhase {
        case .connecting, .reconnecting: return true
        default: return false
        }
    }
}
