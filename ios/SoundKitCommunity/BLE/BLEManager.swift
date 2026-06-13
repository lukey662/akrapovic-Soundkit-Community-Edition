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
    case reconnecting(DiscoveredDevice, attempt: Int)
    case error(String)
}

@MainActor
final class BLEManager: NSObject, ObservableObject {
    static let maxReconnectAttempts = 8

    @Published private(set) var connectionPhase: BLEConnectionPhase = .disconnected
    @Published private(set) var valveState: ValveState = .unknown
    @Published private(set) var discoveredDevices: [DiscoveredDevice] = []
    @Published private(set) var statusMessage: String?
    @Published private(set) var receiverNotReady = false
    @Published private(set) var commandInFlight = false

    var onDiagnostics: ((String) -> Void)?

    private var centralManager: CBCentralManager!
    private var peripheralById: [String: CBPeripheral] = [:]
    private var connectedPeripheral: CBPeripheral?
    private var selectedDevice: DiscoveredDevice?
    private var commandCharacteristic: CBCharacteristic?
    private var reconnectAttempts = 0
    private var reconnectTask: Task<Void, Never>?
    private var pendingReconnectId: String?
    private var wasConnectReady = false
    var onConnectReady: ((Int) -> Void)?
    var onDisconnectEvent: (() -> Void)?
    private let sessionProvider: () -> Int

    init(sessionProvider: @escaping () -> Int = { 0 }) {
        self.sessionProvider = sessionProvider
        super.init()
        centralManager = CBCentralManager(delegate: self, queue: .main)
    }

    var isConnected: Bool {
        if case .connected = connectionPhase { return true }
        return false
    }

    var isReadyForAutomation: Bool {
        isConnected && valveState != .unknown && !receiverNotReady && !commandInFlight
    }

    var canControlValves: Bool {
        isReadyForAutomation
    }

    func startScan() {
        guard centralManager.state == .poweredOn else {
            statusMessage = "Bluetooth is not ready."
            return
        }
        discoveredDevices = []
        connectionPhase = .scanning
        statusMessage = "Scanning for Sound Kit receivers…"
        log("BLE scan started")
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
        reconnectTask?.cancel()
        reconnectAttempts = 0
        selectedDevice = device
        connectionPhase = .connecting(device)
        statusMessage = "Connecting to \(device.name)…"
        guard let peripheral = peripheralById[device.id] else {
            connectionPhase = .error("Receiver not found — scan again")
            return
        }
        peripheral.delegate = self
        connectedPeripheral = peripheral
        centralManager.connect(peripheral, options: nil)
        log("BLE connect \(device.name)")
    }

    func connectToRemembered(id: String, name: String) {
        if let peripheral = peripheralById[id] {
            connect(to: DiscoveredDevice(id: id, name: name, rssi: 0, isLikelySoundKit: true))
            return
        }
        let uuid = UUID(uuidString: id)
        let retrieved = uuid.map { centralManager.retrievePeripherals(withIdentifiers: [$0]) } ?? []
        if let peripheral = retrieved.first {
            peripheralById[id] = peripheral
            connect(to: DiscoveredDevice(id: id, name: name, rssi: 0, isLikelySoundKit: true))
        } else {
            statusMessage = "Couldn't reach receiver — tap to scan"
            log("Remembered receiver not found: \(id)")
        }
    }

    func disconnect() {
        reconnectTask?.cancel()
        pendingReconnectId = nil
        if let peripheral = connectedPeripheral {
            centralManager.cancelPeripheralConnection(peripheral)
        }
        resetConnectionState(userInitiated: true)
    }

    func openValves() {
        sendValveCommand(.open)
    }

    func closeValves() {
        sendValveCommand(.close)
    }

    func retryConnection() {
        reconnectAttempts = 0
        if let device = selectedDevice {
            connect(to: device)
        } else if let id = pendingReconnectId,
                  let name = discoveredDevices.first(where: { $0.id == id })?.name {
            connectToRemembered(id: id, name: name)
        }
    }

    private func sendValveCommand(_ command: ValveCommand) {
        statusMessage = nil
        guard case .success = SoundKitProtocol.requireVerified() else {
            statusMessage = ProtocolError.protocolNotVerified.errorDescription
            return
        }
        guard connectedPeripheral != nil, commandCharacteristic != nil else {
            statusMessage = "No active BLE connection"
            return
        }
        switch SoundKitProtocol.commandPayload(command: command, currentState: valveState) {
        case .failure(let error):
            statusMessage = error.errorDescription
        case .success(nil):
            statusMessage = "\(command.rawValue.capitalized) already matches receiver state."
        case .success(let payload?):
            guard let peripheral = connectedPeripheral, let characteristic = commandCharacteristic else { return }
            commandInFlight = true
            peripheral.writeValue(payload, for: characteristic, type: .withResponse)
            log("BLE write toggle for \(command.rawValue)")
        }
    }

    private func resetConnectionState(userInitiated: Bool = false) {
        connectedPeripheral = nil
        commandCharacteristic = nil
        valveState = .unknown
        receiverNotReady = false
        commandInFlight = false
        wasConnectReady = false
        if userInitiated {
            selectedDevice = nil
            pendingReconnectId = nil
        }
        connectionPhase = .disconnected
        statusMessage = userInitiated ? "Disconnected" : statusMessage
        onDisconnectEvent?()
    }

    private func evaluateConnectReady() {
        let transition = ConnectReadyObserver.evaluate(
            isConnected: isConnected,
            valve: valveState,
            receiverNotReady: receiverNotReady,
            wasConnectReady: wasConnectReady
        )
        if transition.becameReady {
            onConnectReady?(sessionProvider())
        }
        wasConnectReady = transition.isConnectReady
    }

    private func handleStatusNotification(_ data: Data) {
        switch SoundKitProtocol.statusByteToValveState(data) {
        case .success(let state) where state != .unknown:
            valveState = state
            receiverNotReady = false
            statusMessage = nil
            commandInFlight = false
            evaluateConnectReady()
        case .success:
            break
        case .failure(.receiverNotReady):
            receiverNotReady = true
            valveState = .unknown
            statusMessage = SoundKitProtocol.receiverNotReadyMessage
            commandInFlight = false
            evaluateConnectReady()
        case .failure(let error):
            statusMessage = error.errorDescription
            commandInFlight = false
        }
    }

    private func scheduleReconnect() {
        guard reconnectAttempts < Self.maxReconnectAttempts, let device = selectedDevice else {
            connectionPhase = .error("Couldn't reach receiver — tap to retry")
            statusMessage = "Couldn't reach receiver — tap to retry"
            return
        }
        reconnectAttempts += 1
        pendingReconnectId = device.id
        connectionPhase = .reconnecting(device, attempt: reconnectAttempts)
        let delayNs = UInt64(min(reconnectAttempts, 5)) * 1_000_000_000
        reconnectTask?.cancel()
        reconnectTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: delayNs)
            guard let self, !Task.isCancelled else { return }
            self.connect(to: device)
        }
    }

    private func log(_ message: String) {
        onDiagnostics?(message)
    }

    // MARK: - GATT (called from peripheral delegate)

    fileprivate func beginServiceDiscovery() {
        connectedPeripheral?.discoverServices(nil)
    }

    fileprivate func processDiscoveredServices(_ services: [CBService]) {
        for service in services {
            connectedPeripheral?.discoverCharacteristics([SoundKitProtocol.commandCharacteristicUUID], for: service)
        }
    }

    fileprivate func processDiscoveredCharacteristics(_ characteristics: [CBCharacteristic], for service: CBService) {
        guard let characteristic = characteristics.first(where: {
            $0.uuid == SoundKitProtocol.commandCharacteristicUUID
        }) else { return }
        commandCharacteristic = characteristic
        connectedPeripheral?.setNotifyValue(true, for: characteristic)
        log("GATT fff4 located; notifications enabled")
    }

    fileprivate func processUpdatedValue(_ data: Data?, for characteristic: CBCharacteristic) {
        guard characteristic.uuid == SoundKitProtocol.commandCharacteristicUUID, let data else { return }
        handleStatusNotification(data)
    }

    fileprivate func processWriteComplete(for characteristic: CBCharacteristic, error: Error?) {
        guard characteristic.uuid == SoundKitProtocol.commandCharacteristicUUID else { return }
        if let error {
            commandInFlight = false
            statusMessage = error.localizedDescription
            log("BLE write error: \(error.localizedDescription)")
        }
    }

    fileprivate func finalizeConnection(device: DiscoveredDevice) {
        connectionPhase = .connected(device)
        valveState = .unknown
        receiverNotReady = false
        statusMessage = nil
        reconnectAttempts = 0
        log("BLE connected \(device.name)")
    }
}

extension BLEManager: CBCentralManagerDelegate {
    nonisolated func centralManagerDidUpdateState(_ central: CBCentralManager) {
        Task { @MainActor in
            switch central.state {
            case .poweredOn:
                statusMessage = nil
            case .unauthorized:
                statusMessage = "Bluetooth permission denied. Enable in Settings."
            case .poweredOff:
                statusMessage = "Turn on Bluetooth to scan for Sound Kit."
                resetConnectionState(userInitiated: true)
            default:
                statusMessage = "Bluetooth unavailable."
            }
        }
    }

    nonisolated func centralManager(
        _ central: CBCentralManager,
        didDiscover peripheral: CBPeripheral,
        advertisementData: [String: Any],
        rssi RSSI: NSNumber
    ) {
        Task { @MainActor in
            let id = peripheral.identifier.uuidString
            peripheralById[id] = peripheral
            let name = peripheral.name
                ?? advertisementData[CBAdvertisementDataLocalNameKey] as? String
                ?? "Unknown BLE device"
            let manufacturerData = advertisementData[CBAdvertisementDataManufacturerDataKey] as? Data
            let serviceDataValues = (advertisementData[CBAdvertisementDataServiceDataKey] as? [CBUUID: Data])?.values
            let adPayload = manufacturerData ?? serviceDataValues?.first
            let isLikely = SoundKitProtocol.hasAdvertisingSignature(in: adPayload)
                || SoundKitProtocol.isLikelySoundKitDevice(name: name)
            let device = DiscoveredDevice(id: id, name: name, rssi: RSSI.intValue, isLikelySoundKit: isLikely)
            if let index = discoveredDevices.firstIndex(where: { $0.id == device.id }) {
                discoveredDevices[index] = device
            } else {
                discoveredDevices.append(device)
            }
        }
    }

    nonisolated func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        Task { @MainActor in
            peripheral.delegate = self
            connectedPeripheral = peripheral
            if let device = selectedDevice {
                finalizeConnection(device: device)
            }
            beginServiceDiscovery()
        }
    }

    nonisolated func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        Task { @MainActor in
            log("BLE connect failed: \(error?.localizedDescription ?? "unknown")")
            scheduleReconnect()
        }
    }

    nonisolated func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        Task { @MainActor in
            log("BLE disconnected: \(error?.localizedDescription ?? "link lost")")
            if selectedDevice != nil, reconnectAttempts < Self.maxReconnectAttempts {
                scheduleReconnect()
            } else {
                resetConnectionState()
            }
        }
    }
}

extension BLEManager: CBPeripheralDelegate {
    nonisolated func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        Task { @MainActor in
            if let error {
                statusMessage = error.localizedDescription
                return
            }
            processDiscoveredServices(peripheral.services ?? [])
        }
    }

    nonisolated func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        Task { @MainActor in
            if let error {
                statusMessage = error.localizedDescription
                return
            }
            processDiscoveredCharacteristics(service.characteristics ?? [], for: service)
        }
    }

    nonisolated func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        Task { @MainActor in
            if let error {
                statusMessage = error.localizedDescription
                return
            }
            processUpdatedValue(characteristic.value, for: characteristic)
        }
    }

    nonisolated func peripheral(_ peripheral: CBPeripheral, didWriteValueFor characteristic: CBCharacteristic, error: Error?) {
        Task { @MainActor in
            processWriteComplete(for: characteristic, error: error)
        }
    }
}
