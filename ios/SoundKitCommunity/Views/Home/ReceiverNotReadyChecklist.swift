import SwiftUI

enum ReceiverNotReadyChecklist {
    static let steps = [
        "Switch on the vehicle (ignition or accessory) so the Sound Kit receiver has power.",
        "Start the engine if your installation requires it; some receivers remain on status 04 until ready.",
        "Move the phone within about 20 metres of the receiver.",
        "Check that the receiver green LED shows power. If the remote LED blinks white, consult the Akrapovič manual.",
        "Stay parked, wait a few seconds, then retry Open or Close.",
        "If it remains unavailable, export Diagnostics and email support@appsforgood.net.",
    ]
}

struct ReceiverNotReadyChecklistView: View {
    @Environment(\.garageTheme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label("Receiver not ready", systemImage: "exclamationmark.triangle.fill")
                .font(.headline)
                .foregroundStyle(.orange)
            Text("The receiver is connected but is not accepting valve changes yet. Try these steps:")
                .font(.subheadline)
                .foregroundStyle(theme.muted)
            ForEach(Array(ReceiverNotReadyChecklist.steps.enumerated()), id: \.offset) { _, step in
                Text("• \(step)")
                    .font(.footnote)
                    .foregroundStyle(theme.muted)
            }
        }
        .padding(16)
        .background(theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .accessibilityElement(children: .combine)
    }
}

struct PairingChecklistView: View {
    @Environment(\.garageTheme) private var theme
    let problem: PairingProblem

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label(PairingCopy.title, systemImage: "lock.trianglebadge.exclamationmark")
                .font(.headline)
                .foregroundStyle(.orange)
            Text(PairingCopy.message(for: problem))
                .font(.subheadline)
                .foregroundStyle(theme.muted)
            ForEach(Array(PairingCopy.steps(for: problem).enumerated()), id: \.offset) { _, step in
                Text("• \(step)")
                    .font(.footnote)
                    .foregroundStyle(theme.muted)
            }
        }
        .padding(16)
        .background(theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .accessibilityElement(children: .combine)
    }
}
