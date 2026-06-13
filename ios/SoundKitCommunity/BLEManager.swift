import Combine
import CoreBluetooth
import Foundation

struct DiscoveredDevice: Identifiable, Hashable {
    let id: String
    let name: String
    let rssi: Int
    let isLikelySoundKit: Bool
}

enum BLEConnectionPhase: Equatable {
    case disconnected
    case scanning
    case connecting(DiscoveredDevice)
    case connected(DiscoveredDevice)
    case error(String)
}

/// CoreBluetooth transport stub. Valve writes are intentionally not sent until
/// GATT discovery, notifications, and state-gated safety checks are implemented.
final class BLEManager: NSObject, ObservableObject {
    @Published private(set) var connectionPhase: BLEConnectionPhase = .disconnected
    @Published private(set) var valveState: ValveState = .unknown
    @Published private(set) var discoveredDevices: [DiscoveredDevice] = []
    @Published private(set) var statusMessage: String?

    private var centralManager: CBCentralManager!
    private var connectedPeripheral: CBPeripheral?
    private var selectedDevice: DiscoveredDevice?

    override init() {
        super.init()
        centralManager = CBCentralManager(delegate: self, queue: .main)
    }

    func startScan() {
        guard centralManager.state == .poweredOn else {
            statusMessage = "Bluetooth is not ready."
            return
        }
        discoveredDevices = []
        connectionPhase = .scanning
        statusMessage = "Scanning for Sound Kit receivers…"
        // Unfiltered scan matching original APK; filter in didDiscover using signature 103 + name hints.
        centralManager.scanForPeripherals(withServices: nil, options: [CBCentralManagerScanOptionAllowDuplicatesKey: false])
    }

    func stopScan() {
        centralManager.stopScan()
        if case .scanning = connectionPhase {
            connectionPhase = .disconnected
        }
    }

    func connect(to device: DiscoveredDevice) {
        stopScan()
        selectedDevice = device
        connectionPhase = .connecting(device)
        statusMessage = "Connecting to \(device.name)…"
        // TODO: retrieve peripheral by identifier and call connect(_:options:)
        // TODO: after connect, bond if needed (system PIN UI), discover services, locate fff4.
        statusMessage = "Connect not implemented — add GATT flow."
        connectionPhase = .error("Connect stub")
    }

    func disconnect() {
        if let peripheral = connectedPeripheral {
            centralManager.cancelPeripheralConnection(peripheral)
        }
        resetConnectionState()
    }

    func openValves() {
        sendValveCommand(.open)
    }

    func closeValves() {
        sendValveCommand(.close)
    }

    // MARK: - State-gated toggle (mirrors Android BleConnectionManager.writeCommand)

    private func sendValveCommand(_ command: ValveCommand) {
        statusMessage = nil

        // 1. Fail closed if protocol constants are not verified.
        guard case .success = SoundKitProtocol.requireVerified() else {
            statusMessage = ProtocolError.protocolNotVerified.errorDescription
            return
        }

        // 2. Require an active GATT connection and discovered command characteristic.
        guard connectedPeripheral != nil else {
            statusMessage = "No active BLE connection"
            return
        }
        // guard commandCharacteristic != nil else { statusMessage = "Command characteristic was not discovered"; return }

        // 3. Do not send toggle when valve state is Unknown — wait for status notification first.
        //    Android: SoundKitProtocol.commandPayload fails with "Waiting for receiver status…"
        switch SoundKitProtocol.commandPayload(command: command, currentState: valveState) {
        case .failure(let error):
            statusMessage = error.errorDescription
            return
        case .success(nil):
            // Already in requested state — no-op success.
            statusMessage = "\(command.rawValue.capitalized) already matches receiver state."
            return
        case .success(let payload?):
            // 4. Send verified toggle payload 0x01 only when current state is the opposite of requested.
            //    Do not infer valve state from write success alone; wait for notification bytes 02/03/06/07.
            // 5. Status byte 0x04 = receiver not ready: show actionable copy, keep link up (no reconnect loop).
            _ = payload // TODO: peripheral.writeValue(payload, for: commandCharacteristic, type: .withResponse)
            statusMessage = "Write stub — would send toggle \(payload.map { String(format: "%02X", $0) }.joined())"
        }
    }

    private func resetConnectionState() {
        connectedPeripheral = nil
        selectedDevice = nil
        valveState = .unknown
        connectionPhase = .disconnected
        statusMessage = "Disconnected"
    }

    // MARK: - Notification handling (stub)

    private func handleStatusNotification(_ data: Data) {
        switch SoundKitProtocol.statusByteToValveState(data) {
        case .success(let state) where state != .unknown:
            valveState = state
            statusMessage = nil
        case .success:
            break
        case .failure(.receiverNotReady):
            // Keep connection; surface copy from BLE_PROTOCOL.md (Android stays connected on 0x04).
            statusMessage = SoundKitProtocol.receiverNotReadyMessage
            valveState = .unknown
        case .failure(let error):
            statusMessage = error.errorDescription
        }
    }
}

extension BLEManager: CBCentralManagerDelegate {
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        switch central.state {
        case .poweredOn:
            statusMessage = nil
        case .unauthorized:
            statusMessage = "Bluetooth permission denied. Enable in Settings."
        case .poweredOff:
            statusMessage = "Turn on Bluetooth to scan for Sound Kit."
            resetConnectionState()
        default:
            statusMessage = "Bluetooth unavailable."
        }
    }

    func centralManager(
        _ central: CBCentralManager,
        didDiscover peripheral: CBPeripheral,
        advertisementData: [String: Any],
        rssi RSSI: NSNumber
    ) {
        let name = peripheral.name ?? advertisementData[CBAdvertisementDataLocalNameKey] as? String ?? "Unknown BLE device"
        let manufacturerData = advertisementData[CBAdvertisementDataManufacturerDataKey] as? Data
        let serviceDataValues = (advertisementData[CBAdvertisementDataServiceDataKey] as? [CBUUID: Data])?.values
        let adPayload = manufacturerData ?? serviceDataValues?.first
        let signatureMatch = SoundKitProtocol.hasAdvertisingSignature(in: adPayload)
        let nameMatch = SoundKitProtocol.isLikelySoundKitDevice(name: name)
        let isLikely = signatureMatch || nameMatch

        let device = DiscoveredDevice(
            id: peripheral.identifier.uuidString,
            name: name,
            rssi: RSSI.intValue,
            isLikelySoundKit: isLikely
        )
        if let index = discoveredDevices.firstIndex(where: { $0.id == device.id }) {
            discoveredDevices[index] = device
        } else {
            discoveredDevices.append(device)
        }
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        connectedPeripheral = peripheral
        if let device = selectedDevice {
            connectionPhase = .connected(device)
        }
        valveState = .unknown
        // TODO: peripheral.delegate = self; discoverServices(nil); enable notify on fff4 + CCCD 2902.
    }

    func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        connectionPhase = .error(error?.localizedDescription ?? "Connection failed")
    }

    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        resetConnectionState()
    }
}
