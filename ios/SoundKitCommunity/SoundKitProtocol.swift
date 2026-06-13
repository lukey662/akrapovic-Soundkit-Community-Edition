import CoreBluetooth
import Foundation

/// Verified Akrapovič Car Sound Kit BLE protocol constants.
/// Source of truth: repository `BLE_PROTOCOL.md` and Android `SoundKitProtocol.kt`.
enum SoundKitProtocol {
    static let verified = true

    static let deviceNameHints: Set<String> = [
        "akrapovic",
        "akrapovič",
        "soundkit",
        "sound kit",
    ]

    /// Advertising signature decoded after the last `FFFFFF` marker in scan data.
    static let advertisingSignature = "103"

    /// Original APK does not filter by service UUID; it searches all services for fff4.
    static let serviceUUID: CBUUID? = nil

    static let commandCharacteristicUUID = CBUUID(string: "0000FFF4-0000-1000-8000-00805F9B34FB")
    static let notificationCharacteristicUUID = commandCharacteristicUUID
    static let cccdDescriptorUUID = CBUUID(string: "00002902-0000-1000-8000-00805F9B34FB")

    /// Verified toggle payload — not distinct OPEN/CLOSE commands.
    static let togglePayload = Data([0x01])

    static let receiverNotReadyMessage =
        "Receiver isn't ready to change valves. Use while parked with ignition on (if required by your kit). Check the official app once, then try again."

    // MARK: - Status bytes (notification payload, first byte)

    static let statusClosedValues: Set<UInt8> = [0x02, 0x07]
    static let statusOpenValues: Set<UInt8> = [0x03, 0x06]
    static let statusNotReady: UInt8 = 0x04

    static func isReceiverNotReadyStatus(_ value: Data) -> Bool {
        value.first.map { $0 == statusNotReady } ?? false
    }

    static func isLikelySoundKitDevice(name: String?) -> Bool {
        let normalized = (name ?? "").lowercased()
        return deviceNameHints.contains { normalized.contains($0) }
    }

    /// Parses the `FFFFFF` + ASCII signature from raw advertisement bytes (hex pipeline matching Android).
    static func advertisingSignature(from advertisementData: Data?) -> String? {
        guard let advertisementData else { return nil }
        let hex = advertisementData.map { String(format: "%02X", $0) }.joined()
        guard let markerRange = hex.range(of: "FFFFFF", options: .backwards) else { return nil }

        let signatureHex = String(hex[markerRange.upperBound...])
        let chars = stride(from: 0, to: signatureHex.count, by: 2).compactMap { start -> Character? in
            let startIndex = signatureHex.index(signatureHex.startIndex, offsetBy: start)
            let endIndex = signatureHex.index(startIndex, offsetBy: 2, limitedBy: signatureHex.endIndex) ?? signatureHex.endIndex
            let pair = String(signatureHex[startIndex..<endIndex])
            guard pair != "00", let byte = UInt8(pair, radix: 16) else { return nil }
            return Character(UnicodeScalar(byte))
        }
        let signature = String(chars)
        return signature.isEmpty ? nil : signature
    }

    static func hasAdvertisingSignature(in advertisementData: Data?) -> Bool {
        advertisingSignature(from: advertisementData) == advertisingSignature
    }

    /// State-gated command resolution — fail closed when valve state is unknown.
    static func commandPayload(command: ValveCommand, currentState: ValveState) -> Result<Data?, ProtocolError> {
        switch (command, currentState) {
        case (_, .unknown):
            return .failure(.waitingForStatus)
        case (.open, .open), (.close, .closed):
            return .success(nil)
        default:
            return .success(togglePayload)
        }
    }

    static func statusByteToValveState(_ value: Data) -> Result<ValveState, ProtocolError> {
        guard let status = value.first else {
            return .failure(.emptyStatus)
        }
        if statusClosedValues.contains(status) {
            return .success(.closed)
        }
        if statusOpenValues.contains(status) {
            return .success(.open)
        }
        if status == statusNotReady {
            return .failure(.receiverNotReady)
        }
        return .success(.unknown)
    }

    static func requireVerified() -> Result<Void, ProtocolError> {
        verified ? .success(()) : .failure(.protocolNotVerified)
    }
}

enum ValveCommand: String, CaseIterable {
    case open
    case close

    var targetState: ValveState {
        switch self {
        case .open: return .open
        case .close: return .closed
        }
    }
}

enum ValveState: String {
    case unknown
    case open
    case closed
}

enum ProtocolError: LocalizedError {
    case protocolNotVerified
    case waitingForStatus
    case emptyStatus
    case receiverNotReady

    var errorDescription: String? {
        switch self {
        case .protocolNotVerified:
            return "Sound Kit BLE protocol is not verified. Analyze the original APK or HCI log before enabling valve writes."
        case .waitingForStatus:
            return "Waiting for receiver status before changing the valves."
        case .emptyStatus:
            return "Receiver sent an empty status update."
        case .receiverNotReady:
            return SoundKitProtocol.receiverNotReadyMessage
        }
    }
}
