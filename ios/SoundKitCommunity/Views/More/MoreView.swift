import SwiftUI

struct MoreView: View {
    @EnvironmentObject private var viewModel: SoundKitViewModel
    @Environment(\.garageTheme) private var theme

    var body: some View {
        NavigationStack {
            List {
                Section {
                    row("Settings", subtitle: "Receivers, connect on launch") {
                        viewModel.showSettings = true
                    }
                    row("Appearance", subtitle: "Garage themes") {
                        viewModel.showAppearance = true
                    }
                    row("Advanced", subtitle: "Diagnostics and troubleshooting") {
                        viewModel.showAdvanced = true
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .themedScreen()
            .navigationTitle("More")
        }
    }

    private func row(_ title: String, subtitle: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(title).font(.headline)
                    Text(subtitle).font(.caption).foregroundStyle(theme.muted)
                }
                Spacer()
                Image(systemName: "chevron.right").foregroundStyle(theme.muted)
            }
        }
        .buttonStyle(.plain)
        .listRowBackground(theme.surface)
    }
}

struct AdvancedView: View {
    @EnvironmentObject private var viewModel: SoundKitViewModel
    @Environment(\.garageTheme) private var theme

    var body: some View {
        List {
            Button("Diagnostics") { viewModel.showDiagnostics = true }
                .listRowBackground(theme.surface)
            Text("Vehicle compatibility details live in COMPATIBILITY.md in the repository.")
                .font(.footnote)
                .foregroundStyle(theme.muted)
                .listRowBackground(theme.surface)
        }
        .scrollContentBackground(.hidden)
        .themedScreen()
        .navigationTitle("Advanced")
    }
}

struct SettingsView: View {
    @EnvironmentObject private var settingsStore: SettingsStore
    @Environment(\.garageTheme) private var theme

    var body: some View {
        Form {
            Toggle("Connect on launch", isOn: binding(\.connectOnLaunch))
            Toggle("Auto reconnect", isOn: binding(\.autoReconnect))
            Section("Saved receivers") {
                if settingsStore.settings.savedReceivers.isEmpty {
                    Text("Connect from Home to save a receiver.")
                        .foregroundStyle(theme.muted)
                } else {
                    ForEach(settingsStore.settings.savedReceivers) { receiver in
                        VStack(alignment: .leading) {
                            Text(receiver.displayName())
                            Text(receiver.address).font(.caption).foregroundStyle(theme.muted)
                            if receiver.isDefault {
                                Text("Default").font(.caption2).foregroundStyle(theme.accent)
                            }
                        }
                    }
                }
            }
        }
        .scrollContentBackground(.hidden)
        .themedScreen()
        .navigationTitle("Settings")
    }

    private func binding(_ keyPath: WritableKeyPath<SoundKitSettings, Bool>) -> Binding<Bool> {
        Binding(
            get: { settingsStore.settings[keyPath: keyPath] },
            set: { newValue in settingsStore.update { $0[keyPath: keyPath] = newValue } }
        )
    }
}

struct AppearanceView: View {
    @EnvironmentObject private var settingsStore: SettingsStore
    @Environment(\.garageTheme) private var theme

    var body: some View {
        List {
            ForEach(GarageThemePresets.all, id: \.id) { preset in
                Button {
                    settingsStore.update { $0.garageThemeId = preset.id }
                } label: {
                    HStack {
                        VStack(alignment: .leading) {
                            Text(preset.name)
                            Text(preset.id).font(.caption).foregroundStyle(theme.muted)
                        }
                        Spacer()
                        if settingsStore.settings.garageThemeId == preset.id {
                            Image(systemName: "checkmark.circle.fill").foregroundStyle(theme.accent)
                        }
                    }
                }
                .buttonStyle(.plain)
                .listRowBackground(theme.surface)
            }
        }
        .scrollContentBackground(.hidden)
        .themedScreen()
        .navigationTitle("Appearance")
    }
}
