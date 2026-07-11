import Combine
import Foundation

enum ValveCommandExecutionOutcome: Equatable {
    case confirmed(ValveState)
    case rejected(String)
}

/// The single application-level authority for user and automation valve intents.
@MainActor
final class ValveControlCoordinator: ObservableObject {
    private weak var bleManager: BLEManager?
    private var cancellables = Set<AnyCancellable>()

    init(bleManager: BLEManager) {
        self.bleManager = bleManager
        bleManager.objectWillChange
            .sink { [weak self] _ in self?.objectWillChange.send() }
            .store(in: &cancellables)
    }

    var currentStatus: ValveState {
        bleManager?.valveState ?? .unknown
    }

    var canOpen: Bool {
        bleManager?.canControlValves == true && currentStatus != .open
    }

    var canClose: Bool {
        bleManager?.canControlValves == true && currentStatus != .closed
    }

    func open() {
        bleManager?.requestValveCommand(.open)
    }

    func close() {
        bleManager?.requestValveCommand(.close)
    }

    /// Sends a state-gated toggle and returns success only after the receiver reports the target state.
    /// App Intents and CarPlay use this instead of issuing BLE writes directly.
    func execute(_ command: ValveCommand) async -> ValveCommandExecutionOutcome {
        guard let bleManager else {
            return .rejected("Sound Kit controls are unavailable. Unlock your iPhone and open Sound Kit Community.")
        }
        guard bleManager.isConnected else {
            return .rejected("Receiver is not connected. Unlock your iPhone and open Sound Kit Community to reconnect.")
        }
        guard !bleManager.commandInFlight else {
            return .rejected("A valve command is already waiting for receiver confirmation.")
        }
        guard case .success = SoundKitProtocol.requireVerified() else {
            return .rejected(ProtocolError.protocolNotVerified.errorDescription ?? "Protocol is not verified.")
        }
        guard bleManager.valveState != .unknown, !bleManager.receiverNotReady else {
            return .rejected(bleManager.statusMessage ?? "Waiting for receiver status before changing the valves.")
        }
        if bleManager.valveState == command.targetState {
            return .confirmed(command.targetState)
        }

        bleManager.requestValveCommand(command)
        var observedCommandInFlight = false
        for _ in 0..<60 {
            switch bleManager.commandPhase {
            case .writing, .awaitingConfirmation:
                observedCommandInFlight = true
            case .failed(let message):
                return .rejected(message)
            case .idle where observedCommandInFlight && bleManager.valveState == command.targetState:
                return .confirmed(command.targetState)
            case .idle:
                if let message = bleManager.statusMessage {
                    return .rejected(message)
                }
            }
            try? await Task.sleep(nanoseconds: 100_000_000)
        }
        return .rejected("Receiver did not confirm the valve command. Unlock your iPhone and try again.")
    }
}
