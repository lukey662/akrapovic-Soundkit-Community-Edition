import CoreBluetooth
import Foundation

/// Why the receiver refused this iPhone. The receiver's six-digit PIN, entered in the iOS pairing
/// prompt, is its only access control, so these are never retried automatically.
enum PairingProblem: Equatable {
    /// The receiver asked for pairing and it did not complete (cancelled or wrong PIN).
    case notPaired
    /// The receiver no longer recognises this iPhone's stored pairing.
    case outOfDate
}

enum PairingErrorClassifier {
    static func classify(_ error: Error?) -> PairingProblem? {
        guard let error else { return nil }
        let nsError = error as NSError
        if nsError.domain == CBATTErrorDomain {
            switch CBATTError.Code(rawValue: nsError.code) {
            case .insufficientAuthentication, .insufficientEncryption, .insufficientEncryptionKeySize:
                return .notPaired
            default:
                return nil
            }
        }
        if nsError.domain == CBErrorDomain {
            switch CBError.Code(rawValue: nsError.code) {
            case .peerRemovedPairingInformation:
                return .outOfDate
            case .encryptionTimedOut:
                return .notPaired
            default:
                return nil
            }
        }
        return nil
    }
}

/// Background paths (auto-reconnect, away scan, restore) must not reopen the PIN prompt on their own.
enum PairingRecoveryPolicy {
    static func allowsAutomaticConnect(pairingProblem: PairingProblem?) -> Bool {
        pairingProblem == nil
    }

    static func shouldRecoverFromLinkLoss(pairingProblem: PairingProblem?) -> Bool {
        pairingProblem == nil
    }
}

enum PairingCopy {
    static let title = "Pairing didn't finish"
    static let pinHint = "The first time you connect, iOS may ask for the 6-digit PIN printed on your receiver and in the manual."
    static let stillPairedNote = "This iPhone stays paired with SoundKit. To pair again from scratch, open Settings › Bluetooth, tap (i) next to SoundKit, then Forget This Device."

    static func message(for problem: PairingProblem) -> String {
        switch problem {
        case .notPaired:
            return "Check the 6-digit PIN printed on your receiver and in the manual, then try again."
        case .outOfDate:
            return "The receiver no longer recognises this iPhone. Forget SoundKit in Settings › Bluetooth, then try again with the PIN."
        }
    }

    static func steps(for problem: PairingProblem) -> [String] {
        var steps = [
            "Have the 6-digit PIN printed on your receiver and in the Akrapovič manual ready.",
            "Tap Retry connection and enter the PIN when iOS asks.",
        ]
        if problem == .outOfDate {
            steps.insert(
                "Open Settings › Bluetooth, tap (i) next to SoundKit, then Forget This Device.",
                at: 0
            )
        } else {
            steps.append("If iOS does not ask for a PIN, forget SoundKit in Settings › Bluetooth and try again.")
        }
        return steps
    }
}
