import SwiftUI

struct GarageTheme: Equatable {
    let id: String
    let name: String
    let isDark: Bool
    let base: Color
    let surface: Color
    let accent: Color
    let muted: Color
    let onBase: Color
    let onSurface: Color
}

enum GarageThemePresets {
    static let studioDark = GarageTheme(
        id: "studio-dark",
        name: "Studio Blue",
        isDark: true,
        base: Color(red: 0.02, green: 0.04, blue: 0.08),
        surface: Color(red: 0.06, green: 0.10, blue: 0.18),
        accent: Color(red: 0.31, green: 0.64, blue: 1.0),
        muted: Color(red: 0.56, green: 0.56, blue: 0.58),
        onBase: .white,
        onSurface: .white
    )

    static let audiRsDark = GarageTheme(
        id: "audi-rs-dark",
        name: "Audi RS Dark",
        isDark: true,
        base: Color(red: 0.04, green: 0.04, blue: 0.04),
        surface: Color(red: 0.08, green: 0.08, blue: 0.08),
        accent: Color(red: 0.8, green: 0.0, blue: 0.0),
        muted: Color(red: 0.56, green: 0.56, blue: 0.58),
        onBase: .white,
        onSurface: .white
    )

    static let all: [GarageTheme] = [studioDark, audiRsDark]

    static func find(id: String) -> GarageTheme {
        all.first { $0.id == id } ?? studioDark
    }
}

private struct GarageThemeKey: EnvironmentKey {
    static let defaultValue = GarageThemePresets.studioDark
}

extension EnvironmentValues {
    var garageTheme: GarageTheme {
        get { self[GarageThemeKey.self] }
        set { self[GarageThemeKey.self] = newValue }
    }
}

struct ThemedBackground: ViewModifier {
    @Environment(\.garageTheme) private var theme

    func body(content: Content) -> some View {
        content
            .background(theme.base.ignoresSafeArea())
            .foregroundStyle(theme.onBase)
    }
}

extension View {
    func themedScreen() -> some View {
        modifier(ThemedBackground())
    }
}

struct PrimaryButtonStyle: ButtonStyle {
    @Environment(\.garageTheme) private var theme

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(theme.accent.opacity(configuration.isPressed ? 0.75 : 1))
            .foregroundStyle(.white)
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}
