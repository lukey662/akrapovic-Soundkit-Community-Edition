import CoreBluetooth
import XCTest
@testable import SoundKitCommunity

final class PairingErrorClassifierTests: XCTestCase {
    func testInsufficientAuthenticationOrEncryptionMeansNotPaired() {
        XCTAssertEqual(PairingErrorClassifier.classify(CBATTError(.insufficientAuthentication)), .notPaired)
        XCTAssertEqual(PairingErrorClassifier.classify(CBATTError(.insufficientEncryption)), .notPaired)
        XCTAssertEqual(PairingErrorClassifier.classify(CBATTError(.insufficientEncryptionKeySize)), .notPaired)
        XCTAssertEqual(PairingErrorClassifier.classify(CBError(.encryptionTimedOut)), .notPaired)
    }

    func testPeerRemovedPairingMeansOutOfDate() {
        XCTAssertEqual(PairingErrorClassifier.classify(CBError(.peerRemovedPairingInformation)), .outOfDate)
    }

    func testOrdinaryLinkErrorsAreNotPairingProblems() {
        XCTAssertNil(PairingErrorClassifier.classify(nil))
        XCTAssertNil(PairingErrorClassifier.classify(CBError(.connectionTimeout)))
        XCTAssertNil(PairingErrorClassifier.classify(CBError(.peripheralDisconnected)))
        XCTAssertNil(PairingErrorClassifier.classify(CBATTError(.writeNotPermitted)))
        XCTAssertNil(PairingErrorClassifier.classify(NSError(domain: "other", code: 5)))
    }

    func testPairingProblemsBlockAutomaticRecovery() {
        XCTAssertTrue(PairingRecoveryPolicy.allowsAutomaticConnect(pairingProblem: nil))
        XCTAssertTrue(PairingRecoveryPolicy.shouldRecoverFromLinkLoss(pairingProblem: nil))
        for problem in [PairingProblem.notPaired, .outOfDate] {
            XCTAssertFalse(PairingRecoveryPolicy.allowsAutomaticConnect(pairingProblem: problem))
            XCTAssertFalse(PairingRecoveryPolicy.shouldRecoverFromLinkLoss(pairingProblem: problem))
        }
    }

    func testCopyExplainsThePinAndBluetoothSettings() {
        XCTAssertTrue(PairingCopy.pinHint.contains("6-digit PIN"))
        XCTAssertTrue(PairingCopy.stillPairedNote.contains("Forget This Device"))
        for problem in [PairingProblem.notPaired, .outOfDate] {
            XCTAssertTrue(PairingCopy.message(for: problem).contains("PIN"))
            XCTAssertFalse(PairingCopy.steps(for: problem).isEmpty)
        }
        XCTAssertTrue(PairingCopy.steps(for: .outOfDate)[0].contains("Forget This Device"))
    }
}
