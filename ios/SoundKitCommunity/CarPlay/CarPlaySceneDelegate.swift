import CarPlay
import Combine
import UIKit

/// The low-distraction CarPlay surface. It never performs setup or writes BLE directly.
@MainActor
final class CarPlaySceneDelegate: NSObject, CPTemplateApplicationSceneDelegate {
    private weak var interfaceController: CPInterfaceController?
    private var environmentCancellable: AnyCancellable?
    private var coordinatorCancellable: AnyCancellable?

    func templateApplicationScene(
        _ templateApplicationScene: CPTemplateApplicationScene,
        didConnect interfaceController: CPInterfaceController
    ) {
        self.interfaceController = interfaceController
        CarSessionTracker.shared.beginSession()
        AppCommandEnvironment.shared.connectInCarIfEnabled()

        bind(to: AppCommandEnvironment.shared.currentCoordinator)
        environmentCancellable = AppCommandEnvironment.shared.coordinatorPublisher
            .sink { [weak self] coordinator in
                self?.bind(to: coordinator)
            }
    }

    func templateApplicationScene(
        _ templateApplicationScene: CPTemplateApplicationScene,
        didDisconnectInterfaceController interfaceController: CPInterfaceController
    ) {
        environmentCancellable = nil
        coordinatorCancellable = nil
        self.interfaceController = nil
        CarSessionTracker.shared.endSession()
    }

    private func bind(to coordinator: ValveControlCoordinator?) {
        coordinatorCancellable = nil
        guard let coordinator else {
            refresh(using: nil)
            return
        }
        coordinatorCancellable = coordinator.objectWillChange
            .receive(on: RunLoop.main)
            .sink { [weak self, weak coordinator] _ in
                self?.refresh(using: coordinator)
            }
        refresh(using: coordinator)
    }

    private func refresh(using coordinator: ValveControlCoordinator?) {
        guard let interfaceController else { return }
        let buttons = makeButtons(using: coordinator)
        let template = CPGridTemplate(title: "Sound Kit", gridButtons: buttons)
        interfaceController.setRootTemplate(template, animated: false) { _, _ in }
    }

    private func makeButtons(using coordinator: ValveControlCoordinator?) -> [CPGridButton] {
        guard let coordinator else {
            return [statusButton(
                title: "Finish setup on phone",
                imageName: "iphone",
                isEnabled: false
            )]
        }

        let stateTitle: String
        switch coordinator.currentStatus {
        case .open:
            stateTitle = "Valves open"
        case .closed:
            stateTitle = "Valves closed"
        case .unknown:
            stateTitle = "Status unavailable"
        }
        let status = statusButton(title: stateTitle, imageName: "info.circle", isEnabled: false)
        let open = commandButton(
            title: "Open",
            imageName: "arrow.up.circle",
            isEnabled: coordinator.canOpen,
            command: .open,
            coordinator: coordinator
        )
        let close = commandButton(
            title: "Close",
            imageName: "arrow.down.circle",
            isEnabled: coordinator.canClose,
            command: .close,
            coordinator: coordinator
        )
        return [open, close, status]
    }

    private func statusButton(title: String, imageName: String, isEnabled: Bool) -> CPGridButton {
        let button = CPGridButton(
            titleVariants: [title],
            image: UIImage(systemName: imageName) ?? UIImage()
        ) { _ in }
        button.isEnabled = isEnabled
        return button
    }

    private func commandButton(
        title: String,
        imageName: String,
        isEnabled: Bool,
        command: ValveCommand,
        coordinator: ValveControlCoordinator
    ) -> CPGridButton {
        let button = CPGridButton(
            titleVariants: [title],
            image: UIImage(systemName: imageName) ?? UIImage()
        ) { [weak self, weak coordinator] _ in
            guard let self, let coordinator else { return }
            Task { @MainActor in
                _ = await coordinator.execute(command)
                self.refresh(using: coordinator)
            }
        }
        button.isEnabled = isEnabled
        return button
    }
}
