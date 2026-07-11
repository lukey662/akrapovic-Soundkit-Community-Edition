import XCTest
@testable import SoundKitCommunity

final class SoundKitProtocolTests: XCTestCase {
    func testTogglePayloadWhenStateKnownAndDiffers() {
        let openResult = SoundKitProtocol.commandPayload(command: .open, currentState: .closed)
        guard case .success(let payload?) = openResult else {
            return XCTFail("Expected toggle payload")
        }
        XCTAssertEqual(payload, Data([0x01]))
    }

    func testToggleBlockedWhenUnknown() {
        let result = SoundKitProtocol.commandPayload(command: .open, currentState: .unknown)
        guard case .failure(.waitingForStatus) = result else {
            return XCTFail("Expected waitingForStatus")
        }
    }

    func testStatusOpenByte() {
        let result = SoundKitProtocol.statusByteToValveState(Data([0x03]))
        XCTAssertEqual(try? result.get(), .open)
    }

    func testStatusNotReady() {
        let result = SoundKitProtocol.statusByteToValveState(Data([0x04]))
        guard case .failure(.receiverNotReady) = result else {
            return XCTFail("Expected receiverNotReady")
        }
    }
}

final class QuietWindowEvaluatorTests: XCTestCase {
    func testOvernightWindow() {
        let quiet = QuietStartSettings(
            enabled: true,
            daysOfWeek: [0],
            windowStartMinute: 22 * 60,
            windowEndMinute: 7 * 60
        )
        XCTAssertTrue(QuietWindowEvaluator.isOvernight(quiet))
    }

    func testActiveDuringOvernightStart() {
        var components = DateComponents()
        components.year = 2026
        components.month = 6
        components.day = 13
        components.hour = 23
        components.minute = 0
        let calendar = Calendar.current
        let date = calendar.date(from: components)!
        let quiet = QuietStartSettings(
            enabled: true,
            daysOfWeek: [(calendar.component(.weekday, from: date) + 5) % 7],
            windowStartMinute: 22 * 60,
            windowEndMinute: 7 * 60
        )
        XCTAssertTrue(QuietWindowEvaluator.isActive(quiet, now: date))
    }
}

final class DriveModeProfileTests: XCTestCase {
    func testQuietStreetEnablesQuietStart() {
        let settings = DriveModeProfile.quietStreet.apply(to: SoundKitSettings())
        XCTAssertEqual(settings.preferredValveMode, .closed)
        XCTAssertTrue(settings.quietStart.enabled)
    }
}

final class ConnectReadyObserverTests: XCTestCase {
    func testBecameReadyOnFirstKnownValve() {
        let t = ConnectReadyObserver.evaluate(
            isConnected: true,
            valve: .open,
            receiverNotReady: false,
            wasConnectReady: false
        )
        XCTAssertTrue(t.becameReady)
    }
}

final class ConnectionPriorityPolicyTests: XCTestCase {
    func testAutoConnectRequiresCarSessionWhenHeadUnitPriorityEnabled() {
        var settings = SoundKitSettings()
        settings.connectOnLaunch = true
        settings.headUnitPriorityEnabled = true
        settings.savedReceivers = [
            SavedReceiver(address: "aa", name: "SoundKit", isDefault: true),
        ]
        XCTAssertTrue(ConnectionPriorityPolicy.shouldAutoConnectOnLaunch(
            settings: settings,
            carSessionActive: true
        ))
        XCTAssertFalse(ConnectionPriorityPolicy.shouldAutoConnectOnLaunch(
            settings: settings,
            carSessionActive: false
        ))
    }
}

final class BleContentionDetectorTests: XCTestCase {
    func testQuickDropSignalsContention() {
        var now: Int64 = 0
        let detector = BleContentionDetector { now }
        detector.onConnected()
        now = 2_000
        XCTAssertEqual(detector.onDisconnected(userInitiated: false), .quickDrop)
    }
}

final class ValveCommandConfirmationTests: XCTestCase {
    func testMatchingNotificationConfirmsCommand() {
        XCTAssertEqual(
            ValveCommandConfirmation.outcome(pending: .open, status: .success(.open)),
            .idle
        )
    }

    func testOppositeNotificationFailsCommandWithoutSuccess() {
        XCTAssertEqual(
            ValveCommandConfirmation.outcome(pending: .open, status: .success(.closed)),
            .failed("Receiver reported the opposite valve state; command was not confirmed.")
        )
    }

    func testUnknownAndNotReadyNotificationsFailCommand() {
        XCTAssertEqual(
            ValveCommandConfirmation.outcome(pending: .close, status: .success(.unknown)),
            .failed("Receiver sent an unrecognised status; command was not confirmed.")
        )
        XCTAssertEqual(
            ValveCommandConfirmation.outcome(pending: .close, status: .failure(.receiverNotReady)),
            .failed(SoundKitProtocol.receiverNotReadyMessage)
        )
    }

    func testNoPendingCommandDoesNotConsumeStatus() {
        XCTAssertNil(ValveCommandConfirmation.outcome(pending: nil, status: .success(.open)))
    }

    func testTimeoutFailsPendingCommand() {
        XCTAssertEqual(
            ValveCommandConfirmation.timedOut(pending: .open),
            .failed("Receiver did not confirm the valve command.")
        )
    }
}

final class ValveIntentDialogMapperTests: XCTestCase {
    func testConfirmedOutcomeUsesConfirmedStateDialog() {
        XCTAssertEqual(
            ValveIntentDialogMapper.commandDialog(for: .open, outcome: .confirmed(.open)),
            "Valves are open."
        )
        XCTAssertEqual(
            ValveIntentDialogMapper.commandDialog(for: .close, outcome: .confirmed(.closed)),
            "Valves are closed."
        )
    }

    func testRejectedOutcomeNeverClaimsSuccess() {
        XCTAssertEqual(
            ValveIntentDialogMapper.commandDialog(
                for: .open,
                outcome: .rejected("Receiver did not confirm the valve command.")
            ),
            "Receiver did not confirm the valve command."
        )
    }

    func testUnknownStatusGuidesUserWithoutGuessing() {
        XCTAssertEqual(
            ValveIntentDialogMapper.statusDialog(for: .unknown),
            "Valve status is unavailable. Unlock your iPhone and open Sound Kit Community to reconnect."
        )
    }
}

final class ReconnectAttemptPolicyTests: XCTestCase {
    func testRetryAttemptsCapAtEightWithoutResetting() {
        var attempt = 0
        for expected in 1...BLEManager.maxReconnectAttempts {
            attempt = try! XCTUnwrap(ReconnectAttemptPolicy.nextAttempt(
                current: attempt,
                maximum: BLEManager.maxReconnectAttempts
            ))
            XCTAssertEqual(attempt, expected)
        }
        XCTAssertNil(ReconnectAttemptPolicy.nextAttempt(
            current: attempt,
            maximum: BLEManager.maxReconnectAttempts
        ))
    }
}
