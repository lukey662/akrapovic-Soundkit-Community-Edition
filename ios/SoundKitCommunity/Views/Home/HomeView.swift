import SwiftUI

struct HomeView: View {
    @EnvironmentObject private var viewModel: SoundKitViewModel
    @EnvironmentObject private var bleManager: BLEManager
    @EnvironmentObject private var settingsStore: SettingsStore
    @Environment(\.garageTheme) private var theme

    var body: some View {
        NavigationStack {
            Group {
                if showsConnectionStatus {
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

    private var showsConnectionStatus: Bool {
        switch bleManager.connectionPhase {
        case .connecting, .preparing, .connected, .reconnecting, .error:
            return true
        case .disconnected, .scanning:
            return false
        }
    }
}

struct ConnectedDeviceView: View {
    @EnvironmentObject private var viewModel: SoundKitViewModel
    @EnvironmentObject private var bleManager: BLEManager
    @EnvironmentObject private var settingsStore: SettingsStore
    @Environment(\.garageTheme) private var theme
    @State private var showDisconnectConfirm = false
    @State private var wasCommandInFlight = false

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                statusLine
                valveHero
                if bleManager.receiverNotReady {
                    ReceiverNotReadyChecklistView()
                }
                if case .error = bleManager.connectionPhase {
                    Button("Retry connection") { bleManager.retryConnection() }
                        .buttonStyle(PrimaryButtonStyle())
                        .accessibilityLabel("Retry receiver connection")
                }
                primaryAction
                driveModeRow
                disconnectRow
            }
            .padding(24)
        }
        .onAppear { viewModel.rememberConnectedDevice() }
        .onChange(of: bleManager.commandInFlight) { inFlight in
            if wasCommandInFlight && !inFlight {
                let feedback = UINotificationFeedbackGenerator()
                feedback.notificationOccurred(bleManager.commandPhase.isFailure ? .error : .success)
            }
            wasCommandInFlight = inFlight
        }
        .onChange(of: bleManager.valveState) { _ in
            UIAccessibility.post(
                notification: .announcement,
                argument: "\(valveLabel). \(valveSubtitle)"
            )
        }
        .onChange(of: bleManager.statusMessage) { message in
            guard let message, !message.isEmpty else { return }
            UIAccessibility.post(notification: .announcement, argument: message)
        }
        .alert("Disconnect from receiver?", isPresented: $showDisconnectConfirm) {
            Button("Disconnect", role: .destructive) { bleManager.disconnect() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("The valves will keep their current position. You can reconnect from Home.")
        }
    }

    private var statusLine: some View {
        HStack(spacing: 8) {
            Circle().fill(connectionColor).frame(width: 8, height: 8)
            if isConnectionInProgress {
                ProgressView().controlSize(.small)
            }
            Text(connectionTitle)
                .font(.subheadline)
                .foregroundStyle(theme.muted)
            Spacer()
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(connectionTitle)
    }

    private var valveHero: some View {
        VStack(spacing: 12) {
            ValveVisual(
                state: bleManager.valveState,
                commandInFlight: bleManager.commandInFlight,
                accent: theme.accent,
                surface: theme.surface
            )
            .accessibilityHidden(true)
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
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(valveLabel). \(valveSubtitle)")
        .accessibilityValue(bleManager.statusMessage ?? "")
        .accessibilityAddTraits(.updatesFrequently)
    }

    private var primaryAction: some View {
        Button(primaryActionTitle) {
            viewModel.toggleValves()
        }
        .buttonStyle(PrimaryButtonStyle())
        .disabled(!bleManager.canControlValves)
        .accessibilityHint(bleManager.canControlValves ? "Changes the receiver valve state." : "Unavailable until the receiver reports its current valve state.")
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
            showDisconnectConfirm = true
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var connectionTitle: String {
        switch bleManager.connectionPhase {
        case .connecting(let device):
            return "\(device.name) · Connecting"
        case .preparing(let device):
            return "\(device.name) · Preparing receiver"
        case .connected(let device):
            return "\(device.name) · Connected"
        case .reconnecting(let device, let attempt):
            return "\(device.name) · Reconnecting (attempt \(attempt))"
        case .error:
            if case .error(let message) = bleManager.connectionPhase {
                return "Connection error · \(message)"
            }
            return "Connection needs attention"
        case .disconnected, .scanning:
            return "Disconnected"
        }
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
        if bleManager.commandInFlight { return "Changing…" }
        if bleManager.valveState == .unknown { return "Waiting for status" }
        return bleManager.valveState == .open ? "Close valves" : "Open valves"
    }

    private var connectionColor: Color {
        switch bleManager.connectionPhase {
        case .connected: return .green
        case .connecting, .preparing, .reconnecting: return .orange
        case .error: return .red
        case .disconnected, .scanning: return theme.muted
        }
    }

    private var isConnectionInProgress: Bool {
        switch bleManager.connectionPhase {
        case .connecting, .preparing, .reconnecting: return true
        default: return false
        }
    }
}

private struct ValveVisual: View {
    let state: ValveState
    let commandInFlight: Bool
    let accent: Color
    let surface: Color
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        Group {
            if (state == .unknown || commandInFlight) && !reduceMotion {
                TimelineView(.animation(minimumInterval: 1.0 / 30.0)) { context in
                    visual(at: context.date)
                }
            } else {
                visual(at: .distantPast)
            }
        }
        .frame(width: 168, height: 168)
        .accessibilityHidden(true)
    }

    @ViewBuilder
    private func visual(at date: Date) -> some View {
        let phase = date == .distantPast ? 0.0 : (sin(date.timeIntervalSinceReferenceDate * 2.2) + 1) / 2
        let targetOpen: Double = {
            switch state {
            case .open: return 1
            case .closed: return 0
            case .unknown: return reduceMotion ? 0.28 : 0.18 + phase * 0.2
            }
        }()
        let discHeight = cos(targetOpen * .pi / 2) * 0.96 + 0.04
        let heat = targetOpen * 0.92
        let busyPulse = commandInFlight && !reduceMotion ? 0.55 + phase * 0.45 : 1.0

        ZStack {
            // Outer bloom
            Circle()
                .fill(
                    RadialGradient(
                        colors: [
                            accent.opacity(0.28 * heat * busyPulse),
                            accent.opacity(0.08 * heat),
                            .clear,
                        ],
                        center: .center,
                        startRadius: 10,
                        endRadius: 90
                    )
                )
                .scaleEffect(1.12)

            // Carbon sleeve
            Circle()
                .fill(
                    LinearGradient(
                        colors: [Color(white: 0.18), Color(white: 0.07), .black],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
            Circle()
                .stroke(Color.white.opacity(0.16 * busyPulse), lineWidth: 1.5)

            // Titanium lip
            Circle()
                .stroke(
                    LinearGradient(
                        colors: [Color(white: 0.9), Color(white: 0.55), Color(white: 0.28)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    ),
                    lineWidth: 10
                )
                .padding(14)

            // Bore
            Circle()
                .fill(Color.black.opacity(0.92))
                .padding(28)

            // Heat
            Circle()
                .fill(
                    RadialGradient(
                        colors: [
                            accent.opacity(0.75 * heat * busyPulse),
                            accent.opacity(0.35 * heat),
                            .clear,
                        ],
                        center: .init(x: 0.5, y: 0.62),
                        startRadius: 4,
                        endRadius: 58
                    )
                )
                .padding(30)

            // Hinged disc
            Ellipse()
                .fill(
                    LinearGradient(
                        colors: [Color(white: 0.32), Color(white: 0.12), Color(white: 0.05)],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )
                .frame(width: 104, height: 104 * discHeight)
                .overlay(
                    Ellipse()
                        .stroke(Color.white.opacity(0.2 * discHeight), lineWidth: 1)
                )

            // Pivot pins
            HStack {
                Circle().fill(Color(white: 0.78)).frame(width: 7, height: 7)
                Spacer()
                Circle().fill(Color(white: 0.78)).frame(width: 7, height: 7)
            }
            .padding(.horizontal, 34)

            if commandInFlight {
                Circle()
                    .stroke(accent.opacity(0.35 + phase * 0.4), lineWidth: 2)
            }
        }
        .opacity(state == .unknown ? 0.88 : 1)
    }
}

struct ScanView: View {
    @EnvironmentObject private var bleManager: BLEManager
    @EnvironmentObject private var viewModel: SoundKitViewModel
    @Environment(\.garageTheme) private var theme
    @State private var showTakeControlConfirm = false

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Connect")
                .font(.largeTitle.bold())
            Text("Bluetooth only — nothing leaves your phone.")
                .foregroundStyle(theme.muted)

            if case .yielded = bleManager.connectionYieldState {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Receiver in use")
                        .font(.headline)
                    Text(BLEManager.yieldMessage)
                        .font(.subheadline)
                        .foregroundStyle(theme.muted)
                    Button("Take control") { showTakeControlConfirm = true }
                        .buttonStyle(PrimaryButtonStyle())
                }
                .padding(12)
                .background(theme.surface)
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }

            Button("Scan nearby") { bleManager.startScan() }
                .buttonStyle(PrimaryButtonStyle())

            if bleManager.discoveredDevices.isEmpty {
                Text(bleManager.statusMessage ?? "No receivers yet")
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
                            VStack(alignment: .trailing, spacing: 2) {
                                Text(rssiHint(for: device.rssi))
                                    .font(.caption.weight(.medium))
                                Text("\(device.rssi) dBm")
                                    .font(.caption2.monospacedDigit())
                            }
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
        .confirmationDialog(
            "Take control?",
            isPresented: $showTakeControlConfirm,
            titleVisibility: .visible
        ) {
            Button("Take control", role: .destructive) { viewModel.takeControl() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This may disconnect the other phone that is using the Sound Kit receiver.")
        }
    }

    private var isConnecting: Bool {
        switch bleManager.connectionPhase {
        case .connecting, .reconnecting: return true
        default: return false
        }
    }

    private func rssiHint(for rssi: Int) -> String {
        switch rssi {
        case -55...0: return "Excellent signal"
        case -67 ... -56: return "Good signal"
        case -80 ... -68: return "Fair signal"
        case ..<(-80): return "Weak signal — move closer"
        default: return "Signal unknown"
        }
    }
}
