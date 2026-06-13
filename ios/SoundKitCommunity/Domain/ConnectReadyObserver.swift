import Foundation

enum ConnectReadyObserver {
    struct Transition {
        let isConnectReady: Bool
        let becameReady: Bool
        let disconnected: Bool
    }

    static func evaluate(
        isConnected: Bool,
        valve: ValveState,
        receiverNotReady: Bool,
        wasConnectReady: Bool
    ) -> Transition {
        let disconnected = !isConnected
        let isConnectReady = isConnected && valve != .unknown && !receiverNotReady
        let becameReady = isConnectReady && !wasConnectReady
        return Transition(isConnectReady: isConnectReady, becameReady: becameReady, disconnected: disconnected)
    }
}
