import SwiftUI
import UIKit

struct DiagnosticsView: View {
    @EnvironmentObject private var diagnosticsStore: DiagnosticsStore
    @EnvironmentObject private var settingsStore: SettingsStore
    @Environment(\.garageTheme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Export your report, then email it to \(DiagnosticsSupport.email). Nothing is uploaded automatically.")
                .font(.footnote)
                .foregroundStyle(theme.muted)

            HStack(spacing: 8) {
                Button("Copy") {
                    UIPasteboard.general.string = diagnosticsStore.exportText(settings: settingsStore.settings)
                }
                .buttonStyle(.borderedProminent)
                .tint(theme.accent)
                ShareLink(item: exportURL, preview: SharePreview("Diagnostics")) {
                    Text("Share")
                }
                .buttonStyle(.bordered)
            }

            if diagnosticsStore.entries.isEmpty {
                Text("No diagnostics yet — connect from Home first.")
                    .foregroundStyle(theme.muted)
            } else {
                List(diagnosticsStore.entries.reversed()) { entry in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(entry.message)
                        Text(entry.level.rawValue.uppercased())
                            .font(.caption2)
                            .foregroundStyle(theme.muted)
                    }
                    .listRowBackground(theme.surface)
                }
                .scrollContentBackground(.hidden)
            }
            Spacer()
        }
        .padding(24)
        .themedScreen()
        .navigationTitle("Diagnostics")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                if let url = mailtoURL {
                    Link("Email support", destination: url)
                }
            }
        }
    }

    private var exportURL: URL {
        let text = diagnosticsStore.exportText(settings: settingsStore.settings)
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("soundkit-diagnostics.txt")
        try? text.write(to: url, atomically: true, encoding: .utf8)
        return url
    }

    private var mailtoURL: URL? {
        let body = DiagnosticsSupport.buildTriageBody(
            appVersion: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "dev",
            vehicleLine: settingsStore.settings.selectedVehicleId,
            connectionLine: "platform=iOS"
        )
        var components = URLComponents()
        components.scheme = "mailto"
        components.path = DiagnosticsSupport.email
        components.queryItems = [
            URLQueryItem(name: "subject", value: "Sound Kit Community — Diagnostics (iOS)"),
            URLQueryItem(name: "body", value: body),
        ]
        return components.url
    }
}
