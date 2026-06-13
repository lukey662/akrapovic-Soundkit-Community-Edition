import Foundation

enum DiagnosticsSupport {
    static let email = "support@appsforgood.net"

    static func buildTriageBody(
        appVersion: String,
        vehicleLine: String?,
        connectionLine: String
    ) -> String {
        var lines = [
            "Sound Kit Community support request",
            "appVersion=\(appVersion)",
        ]
        if let vehicleLine {
            lines.append(vehicleLine)
        }
        lines.append(connectionLine)
        lines.append("")
        lines.append("Please attach your exported diagnostics .txt file (Share or Save from Diagnostics).")
        return lines.joined(separator: "\n")
    }
}
