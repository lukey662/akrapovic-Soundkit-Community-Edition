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
