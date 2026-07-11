import AppIntents
import Combine
import Foundation

@MainActor
final class AppCommandEnvironment {
    static let shared = AppCommandEnvironment()

    private weak var coordinator: ValveControlCoordinator?
    private let coordinatorSubject = CurrentValueSubject<ValveControlCoordinator?, Never>(nil)

    private init() {}

    func configure(coordinator: ValveControlCoordinator) {
        self.coordinator = coordinator
        coordinatorSubject.send(coordinator)
    }

    var currentCoordinator: ValveControlCoordinator? { coordinator }
    var coordinatorPublisher: AnyPublisher<ValveControlCoordinator?, Never> {
        coordinatorSubject.eraseToAnyPublisher()
    }

    func execute(_ command: ValveCommand) async -> ValveCommandExecutionOutcome {
        guard let coordinator else {
            return .rejected("Sound Kit is starting. Unlock your iPhone and open Sound Kit Community, then try again.")
        }
        return await coordinator.execute(command)
    }

    func valveState() -> ValveState {
        coordinator?.currentStatus ?? .unknown
    }
}

enum ValveIntentDialogMapper {
    static func commandDialog(for command: ValveCommand, outcome: ValveCommandExecutionOutcome) -> String {
        switch outcome {
        case .confirmed(.open):
            return "Valves are open."
        case .confirmed(.closed):
            return "Valves are closed."
        case .confirmed:
            return "The receiver confirmed the valve change."
        case .rejected(let message):
            return message
        }
    }

    static func statusDialog(for state: ValveState) -> String {
        switch state {
        case .open:
            return "The valves are open."
        case .closed:
            return "The valves are closed."
        case .unknown:
            return "Valve status is unavailable. Unlock your iPhone and open Sound Kit Community to reconnect."
        }
    }
}

struct OpenValvesIntent: AppIntent {
    static let title: LocalizedStringResource = "Open Valves"
    static let description = IntentDescription("Opens the Sound Kit valves after the receiver confirms the change.")
    static let openAppWhenRun = false

    func perform() async throws -> some IntentResult & ProvidesDialog {
        let outcome = await AppCommandEnvironment.shared.execute(.open)
        return .result(dialog: IntentDialog(stringLiteral: ValveIntentDialogMapper.commandDialog(for: .open, outcome: outcome)))
    }
}

struct CloseValvesIntent: AppIntent {
    static let title: LocalizedStringResource = "Close Valves"
    static let description = IntentDescription("Closes the Sound Kit valves after the receiver confirms the change.")
    static let openAppWhenRun = false

    func perform() async throws -> some IntentResult & ProvidesDialog {
        let outcome = await AppCommandEnvironment.shared.execute(.close)
        return .result(dialog: IntentDialog(stringLiteral: ValveIntentDialogMapper.commandDialog(for: .close, outcome: outcome)))
    }
}

struct GetValveStatusIntent: AppIntent {
    static let title: LocalizedStringResource = "Get Valve Status"
    static let description = IntentDescription("Reports the last confirmed Sound Kit valve status.")
    static let openAppWhenRun = false

    func perform() async throws -> some IntentResult & ProvidesDialog {
        let state = await AppCommandEnvironment.shared.valveState()
        return .result(dialog: IntentDialog(stringLiteral: ValveIntentDialogMapper.statusDialog(for: state)))
    }
}

struct SoundKitAppShortcuts: AppShortcutsProvider {
    static var shortcutTileColor: ShortcutTileColor = .orange

    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: OpenValvesIntent(),
            phrases: [
                "Open valves in \(.applicationName)",
                "Open Sound Kit valves in \(.applicationName)",
            ],
            shortTitle: "Open valves",
            systemImageName: "arrow.up.circle"
        )
        AppShortcut(
            intent: CloseValvesIntent(),
            phrases: [
                "Close valves in \(.applicationName)",
                "Close Sound Kit valves in \(.applicationName)",
            ],
            shortTitle: "Close valves",
            systemImageName: "arrow.down.circle"
        )
        AppShortcut(
            intent: GetValveStatusIntent(),
            phrases: [
                "Get valve status in \(.applicationName)",
                "What is the valve status in \(.applicationName)",
            ],
            shortTitle: "Valve status",
            systemImageName: "info.circle"
        )
    }
}
