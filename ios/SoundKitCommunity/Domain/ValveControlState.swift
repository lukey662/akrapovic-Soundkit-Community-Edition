import Foundation

enum ConnectionPhase: Equatable {
    case disconnected
    case scanning
    case connecting(DiscoveredDevice)
    case preparing(DiscoveredDevice)
    case connected(DiscoveredDevice)
    case reconnecting(DiscoveredDevice, attempt: Int)
    case error(String)

    var isConnectingOrConnected: Bool {
        switch self {
        case .connecting, .preparing, .connected, .reconnecting:
            return true
        default:
            return false
        }
    }
}

enum CommandPhase: Equatable {
    case idle
    case writing(ValveCommand)
    case awaitingConfirmation(ValveCommand)
    case failed(String)

    var isFailure: Bool {
        if case .failed = self { return true }
        return false
    }
}

struct ValveControlState: Equatable {
    var connection: ConnectionPhase = .disconnected
    var command: CommandPhase = .idle
    var valve: ValveState = .unknown
    var receiverNotReady = false

    var isReady: Bool {
        if case .connected = connection {
            return valve != .unknown && !receiverNotReady && command == .idle
        }
        return false
    }
}

/// Pure state transition rules for notifications received while a command awaits confirmation.
enum ValveCommandConfirmation {
    static func outcome(
        pending: ValveCommand?,
        status: Result<ValveState, ProtocolError>
    ) -> CommandPhase? {
        guard let pending else { return nil }
        switch status {
        case .success(let state) where state == pending.targetState:
            return .idle
        case .success(let state) where state == .unknown:
            return .failed("Receiver sent an unrecognised status; command was not confirmed.")
        case .success:
            return .failed("Receiver reported the opposite valve state; command was not confirmed.")
        case .failure(.receiverNotReady):
            return .failed(SoundKitProtocol.receiverNotReadyMessage)
        case .failure(let error):
            return .failed(error.errorDescription ?? "Receiver status could not confirm the command.")
        }
    }

    static func timedOut(pending: ValveCommand?) -> CommandPhase? {
        pending.map { _ in .failed("Receiver did not confirm the valve command.") }
    }
}

enum ReconnectAttemptPolicy {
    static func nextAttempt(current: Int, maximum: Int) -> Int? {
        guard current < maximum else { return nil }
        return current + 1
    }
}
