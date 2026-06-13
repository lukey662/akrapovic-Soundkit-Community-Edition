import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var bleManager: BLEManager

    var body: some View {
        NavigationStack {
            List {
                connectionSection
                valveSection
                devicesSection
                supportSection
            }
            .navigationTitle("Sound Kit Community")
            .toolbar {
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

    private var connectionSection: some View {
        Section("Connection") {
            LabeledContent("Status", value: connectionLabel)
            if let message = bleManager.statusMessage {
                Text(message)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            LabeledContent("Valve state", value: bleManager.valveState.rawValue.capitalized)

            if case .connected = bleManager.connectionPhase {
                Button("Disconnect", role: .destructive) {
                    bleManager.disconnect()
                }
            }
        }
    }

    private var valveSection: some View {
        Section("Valves") {
            Button("Open") { bleManager.openValves() }
                .disabled(!canControlValves)
            Button("Close") { bleManager.closeValves() }
                .disabled(!canControlValves)
            Text("OPEN/CLOSE are state-gated: toggle `0x01` is sent only when receiver status is known and differs from the request.")
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
    }

    private var devicesSection: some View {
        Section("Nearby devices") {
            if bleManager.discoveredDevices.isEmpty {
                Text("Tap Scan to find BLE receivers.")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(bleManager.discoveredDevices) { device in
                    Button {
                        bleManager.connect(to: device)
                    } label: {
                        HStack {
                            VStack(alignment: .leading) {
                                Text(device.name)
                                Text(device.isLikelySoundKit ? "Likely Sound Kit" : "BLE device")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            Text("\(device.rssi) dBm")
                                .font(.caption.monospacedDigit())
                                .foregroundStyle(.secondary)
                        }
                    }
                    .disabled(isConnecting)
                }
            }
        }
    }

    private var supportSection: some View {
        Section("Compatibility") {
            ForEach(VehicleCompatibilityCatalog.entries.filter { $0.tier != .unsupported }) { entry in
                VStack(alignment: .leading, spacing: 4) {
                    Text(entry.displayName)
                    Text(entry.tierLabel)
                        .font(.caption)
                        .foregroundStyle(entry.tier == .supported ? .green : .orange)
                }
            }
            Text("Protocol: see BLE_PROTOCOL.md in the repo root.")
                .font(.footnote)
                .foregroundStyle(.secondary)
            Text("Support: \(DiagnosticsSupport.email)")
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
    }

    private var connectionLabel: String {
        switch bleManager.connectionPhase {
        case .disconnected: return "Disconnected"
        case .scanning: return "Scanning"
        case .connecting(let device): return "Connecting — \(device.name)"
        case .connected(let device): return "Connected — \(device.name)"
        case .error(let message): return "Error — \(message)"
        }
    }

    private var canControlValves: Bool {
        if case .connected = bleManager.connectionPhase { return true }
        return false
    }

    private var isConnecting: Bool {
        if case .connecting = bleManager.connectionPhase { return true }
        return false
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
            .environmentObject(BLEManager())
    }
}
