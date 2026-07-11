import Combine
import CoreBluetooth
import Foundation

struct DiscoveredDevice: Identifiable, Hashable {
    let id: String
    let name: String
    let rssi: Int
    let isLikelySoundKit: Bool
}

typealias BLEConnectionPhase = ConnectionPhase

@MainActor
final class BLEManager: NSObject, ObservableObject {
    static let maxReconnectAttempts = 8

    @Published private(set) var connectionPhase: BLEConnectionPhase = .disconnected
    @Published private(set) var valveState: ValveState = .unknown
    @Published private(set) var discoveredDevices: [DiscoveredDevice] = []
    @Published private(set) var statusMessage: String?
    @Published private(set) var receiverNotReady = false
    @Published private(set) var connectionYieldState: ConnectionYieldState = .none
    @Published private(set) var commandInFlight = false
    @Published private(set) var commandPhase: CommandPhase = .idle

    static let yieldMessage =
        "Another phone may be controlling the receiver. Tap Take control if you need this phone."

    var settingsProvider: () -> SoundKitSettings = { SoundKitSettings() }
    var carSessionProvider: () -> Bool = { false }

    var onDiagnostics: ((String) -> Void)?

    private var userRequestedControl = false
    private let contentionDetector = BleContentionDetector()

    private var centralManager: CBCentralManager!
    private var peripheralById: [String: CBPeripheral] = [:]
    private var connectedPeripheral: CBPeripheral?
    private var selectedDevice: DiscoveredDevice?
    private var commandCharacteristic: CBCharacteristic?
    private var pendingCommand: ValveCommand?
    private var commandConfirmationTask: Task<Void, Never>?
    private var reconnectAttempts = 0
    private var reconnectTask: Task<Void, Never>?
    private var scanTimeoutTask: Task<Void, Never>?
    private var pendingReconnectId: String?
    private var wasConnectReady = false
    var onConnectReady: ((Int) -> Void)?
    var onDisconnectEvent: (() -> Void)?
    private let sessionProvider: () -> Int

    init(sessionProvider: @escaping () -> Int = { 0 }) {
        self.sessionProvider = sessionProvider
        super.init()
        centralManager = CBCentralManager(
            delegate: self,
            queue: .main,
            options: [CBCentralManagerOptionRestoreIdentifierKey: "com.akrapovic.soundkit.community.ble"]
        )
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
        scanTimeoutTask?.cancel()
        scanTimeoutTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 15_000_000_000)
            guard let self, !Task.isCancelled else { return }
            self.centralManager.stopScan()
            guard case .scanning = self.connectionPhase else { return }
            self.connectionPhase = .disconnected
            self.statusMessage = self.discoveredDevices.isEmpty
                ? "No receiver found. Turn the car on, move closer, then try again."
                : "Scan complete."
            self.log("BLE scan timed out after 15000ms")
        }
    }

    func stopScan() {
        scanTimeoutTask?.cancel()
        scanTimeoutTask = nil
        centralManager.stopScan()
        if case .scanning = connectionPhase {
            connectionPhase = .disconnected
        }
    }

    func connect(to device: DiscoveredDevice, userInitiated: Bool = true) {
        guard !userInitiated || !connectionPhase.isConnectingOrConnected else {
            log("BLE connect skipped; connection is already active")
            return
        }
        stopScan()
        reconnectTask?.cancel()
        if userInitiated {
            reconnectAttempts = 0
            userRequestedControl = true
            connectionYieldState = .none
            contentionDetector.reset()
        }
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

    func connectToRemembered(id: String, name: String, userInitiated: Bool = true) {
        let remembered = DiscoveredDevice(id: id, name: name, rssi: 0, isLikelySoundKit: true)
        selectedDevice = remembered
        if let peripheral = peripheralById[id] {
            connect(
                to: remembered,
                userInitiated: userInitiated
            )
            return
        }
        let uuid = UUID(uuidString: id)
        let retrieved = uuid.map { centralManager.retrievePeripherals(withIdentifiers: [$0]) } ?? []
        if let peripheral = retrieved.first {
            peripheralById[id] = peripheral
            connect(
                to: remembered,
                userInitiated: userInitiated
            )
        } else {
            connectionPhase = .error("Couldn't reach receiver — scan again")
            statusMessage = "Couldn't reach receiver — scan again"
            log("Remembered receiver not found: \(id)")
        }
    }

    func takeControl() {
        guard let receiver = settingsProvider().defaultReceiver else { return }
        connectToRemembered(id: receiver.address, name: receiver.displayName(), userInitiated: true)
    }

    func disconnect() {
        reconnectTask?.cancel()
        pendingReconnectId = nil
        userRequestedControl = false
        contentionDetector.reset()
        connectionYieldState = .none
        if let peripheral = connectedPeripheral {
            centralManager.cancelPeripheralConnection(peripheral)
        }
        resetConnectionState(userInitiated: true)
    }

    func retryConnection() {
        reconnectAttempts = 0
        userRequestedControl = true
        connectionYieldState = .none
        contentionDetector.reset()
        if let device = selectedDevice {
            connect(to: device, userInitiated: true)
        } else if let id = pendingReconnectId,
                  let name = discoveredDevices.first(where: { $0.id == id })?.name {
            connectToRemembered(id: id, name: name, userInitiated: true)
        }
    }

    private var currentSettings: SoundKitSettings { settingsProvider() }

    private func shouldAutoReconnectNow() -> Bool {
        ConnectionPriorityPolicy.shouldAutoReconnect(
            settings: currentSettings,
            carSessionActive: carSessionProvider(),
            userRequestedControl: userRequestedControl,
            yieldState: connectionYieldState
        )
    }

    private func maybeYieldOnContention(_ signal: BleContentionSignal?) {
        guard let signal else { return }
        if userRequestedControl, signal == .connectStorm { return }
        guard ConnectionPriorityPolicy.shouldEnterYieldOnContention(
            settings: currentSettings,
            carSessionActive: carSessionProvider()
        ) else { return }
        reconnectTask?.cancel()
        connectionYieldState = .yielded(.headUnitMayBeActive)
        log("BLE contention detected (\(signal)); yielding until user takes control")
    }

    private func handleLinkLoss(userInitiated: Bool, connectFailed: Bool) {
        let signal: BleContentionSignal?
        if connectFailed {
            signal = contentionDetector.onConnectFailed()
        } else {
            signal = contentionDetector.onDisconnected(userInitiated: userInitiated)
        }
        maybeYieldOnContention(signal)
        if shouldAutoReconnectNow() {
            scheduleReconnect()
        }
    }

    func requestValveCommand(_ command: ValveCommand) {
        statusMessage = nil
        guard !commandInFlight else {
            statusMessage = "Waiting for the previous valve command to be confirmed."
            return
        }
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
            pendingCommand = command
            commandPhase = .writing(command)
            peripheral.writeValue(payload, for: characteristic, type: .withResponse)
            log("BLE write toggle for \(command.rawValue)")
        }
    }

    private func resetConnectionState(userInitiated: Bool = false) {
        failPendingCommand("BLE connection ended before the valve command was confirmed.")
        connectedPeripheral = nil
        commandCharacteristic = nil
        valveState = .unknown
        receiverNotReady = false
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
        let parsedStatus = SoundKitProtocol.statusByteToValveState(data)
        let confirmation = ValveCommandConfirmation.outcome(pending: pendingCommand, status: parsedStatus)
        switch parsedStatus {
        case .success(let state) where state != .unknown:
            valveState = state
            receiverNotReady = false
            if let confirmation {
                finishPendingCommand(confirmation)
            } else {
                statusMessage = nil
            }
            evaluateConnectReady()
        case .success:
            if let confirmation {
                finishPendingCommand(confirmation)
            }
            break
        case .failure(.receiverNotReady):
            receiverNotReady = true
            valveState = .unknown
            statusMessage = SoundKitProtocol.receiverNotReadyMessage
            if let confirmation {
                finishPendingCommand(confirmation)
            }
            evaluateConnectReady()
        case .failure(let error):
            statusMessage = error.errorDescription
            if let confirmation {
                finishPendingCommand(confirmation)
            }
        }
    }

    private func finishPendingCommand(_ phase: CommandPhase) {
        commandConfirmationTask?.cancel()
        commandConfirmationTask = nil
        pendingCommand = nil
        commandInFlight = false
        commandPhase = phase
        if case .failed(let message) = phase {
            statusMessage = message
            log("BLE command confirmation failed: \(message)")
        }
    }

    private func failPendingCommand(_ message: String) {
        guard pendingCommand != nil || commandInFlight else { return }
        finishPendingCommand(.failed(message))
    }

    private func startCommandConfirmationTimeout(for command: ValveCommand) {
        commandConfirmationTask?.cancel()
        commandConfirmationTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 5_000_000_000)
            guard let self, !Task.isCancelled, self.pendingCommand == command else { return }
            if let phase = ValveCommandConfirmation.timedOut(pending: self.pendingCommand) {
                self.finishPendingCommand(phase)
            }
        }
    }

    private func scheduleReconnect() {
        guard shouldAutoReconnectNow() else {
            log("Auto-reconnect skipped by head-unit priority policy")
            return
        }
        guard let nextAttempt = ReconnectAttemptPolicy.nextAttempt(
            current: reconnectAttempts,
            maximum: Self.maxReconnectAttempts
        ), let device = selectedDevice else {
            connectionPhase = .error("Couldn't reach receiver — tap to retry")
            statusMessage = "Couldn't reach receiver — tap to retry"
            maybeYieldOnContention(contentionDetector.onConnectFailed())
            return
        }
        reconnectAttempts = nextAttempt
        pendingReconnectId = device.id
        connectionPhase = .reconnecting(device, attempt: reconnectAttempts)
        let delayNs = UInt64(min(reconnectAttempts, 5)) * 1_000_000_000
        reconnectTask?.cancel()
        reconnectTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: delayNs)
            guard let self, !Task.isCancelled else { return }
            guard self.shouldAutoReconnectNow() else { return }
            self.connect(to: device, userInitiated: false)
        }
    }

    private func log(_ message: String) {
        onDiagnostics?(message)
    }

    // MARK: - GATT (called from peripheral delegate)

    fileprivate func beginServiceDiscovery() {
        if let device = selectedDevice {
            connectionPhase = .preparing(device)
            statusMessage = "Preparing receiver…"
        }
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
            failPendingCommand(error.localizedDescription)
            log("BLE write error: \(error.localizedDescription)")
        } else if let pendingCommand {
            commandPhase = .awaitingConfirmation(pendingCommand)
            startCommandConfirmationTimeout(for: pendingCommand)
        }
    }

    fileprivate func finalizeConnection(device: DiscoveredDevice) {
        connectionPhase = .connected(device)
        valveState = .unknown
        receiverNotReady = false
        statusMessage = nil
        reconnectAttempts = 0
        contentionDetector.onConnected()
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
            beginServiceDiscovery()
        }
    }

    nonisolated func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        Task { @MainActor in
            log("BLE connect failed: \(error?.localizedDescription ?? "unknown")")
            handleLinkLoss(userInitiated: false, connectFailed: true)
        }
    }

    nonisolated func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        Task { @MainActor in
            log("BLE disconnected: \(error?.localizedDescription ?? "link lost")")
            let hadDevice = selectedDevice != nil
            resetConnectionState()
            if hadDevice {
                handleLinkLoss(userInitiated: false, connectFailed: false)
            }
        }
    }

    nonisolated func centralManager(_ central: CBCentralManager, willRestoreState dict: [String: Any]) {
        Task { @MainActor in
            let restored = dict[CBCentralManagerRestoredStatePeripheralsKey] as? [CBPeripheral] ?? []
            guard let peripheral = restored.first else { return }
            peripheral.delegate = self
            connectedPeripheral = peripheral
            let device = DiscoveredDevice(
                id: peripheral.identifier.uuidString,
                name: peripheral.name ?? "Sound Kit receiver",
                rssi: 0,
                isLikelySoundKit: true
            )
            peripheralById[device.id] = peripheral
            selectedDevice = device
            commandCharacteristic = peripheral.services?
                .flatMap { $0.characteristics ?? [] }
                .first(where: { $0.uuid == SoundKitProtocol.commandCharacteristicUUID })
            if let commandCharacteristic, commandCharacteristic.isNotifying {
                finalizeConnection(device: device)
            } else {
                connectionPhase = .preparing(device)
                statusMessage = "Restoring receiver connection…"
                beginServiceDiscovery()
            }
            log("BLE state restored without sending a valve command")
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

    nonisolated func peripheral(
        _ peripheral: CBPeripheral,
        didUpdateNotificationStateFor characteristic: CBCharacteristic,
        error: Error?
    ) {
        Task { @MainActor in
            guard characteristic.uuid == SoundKitProtocol.commandCharacteristicUUID else { return }
            guard error == nil, characteristic.isNotifying else {
                statusMessage = error?.localizedDescription ?? "Receiver did not accept status notifications."
                if let connectedPeripheral { centralManager.cancelPeripheralConnection(connectedPeripheral) }
                return
            }
            guard let device = selectedDevice else { return }
            finalizeConnection(device: device)
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
