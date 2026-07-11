import SwiftUI
import UniformTypeIdentifiers

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
    @State private var editingReceiver: SavedReceiver?
    @State private var receiverToForget: SavedReceiver?
    @State private var showImporter = false
    @State private var importMessage: String?
    @State private var showImportMessage = false

    var body: some View {
        Form {
            Section("Connection") {
                Toggle("Connect on launch", isOn: binding(\.connectOnLaunch))
                Toggle("Connect in CarPlay", isOn: binding(\.connectInCar))
                Toggle("Auto reconnect", isOn: binding(\.autoReconnect))
                Toggle("Head unit priority", isOn: binding(\.headUnitPriorityEnabled))
                Text("CarPlay connection is independent of phone launch. Head unit priority reduces connection contention with other phones.")
                    .font(.footnote)
                    .foregroundStyle(theme.muted)
            }
            Section("Saved receivers") {
                if settingsStore.settings.savedReceivers.isEmpty {
                    Text("Connect from Home to save a receiver.")
                        .foregroundStyle(theme.muted)
                } else {
                    ForEach(settingsStore.settings.savedReceivers) { receiver in
                        Button {
                            editingReceiver = receiver
                        } label: {
                            HStack {
                                VStack(alignment: .leading) {
                                    Text(receiver.displayName())
                                    Text(receiver.address).font(.caption).foregroundStyle(theme.muted)
                                    if receiver.isDefault {
                                        Text("Default").font(.caption2).foregroundStyle(theme.accent)
                                    }
                                }
                                Spacer()
                                Image(systemName: "chevron.right").foregroundStyle(theme.muted)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
                Text("\(settingsStore.settings.savedReceivers.count) of \(SoundKitSettings.maximumSavedReceivers) saved receivers. Disconnect or forget a receiver before adding another when full.")
                    .font(.footnote)
                    .foregroundStyle(theme.muted)
            }
            Section("Backup") {
                if let backupURL {
                    ShareLink(item: backupURL, preview: SharePreview("Sound Kit settings backup")) {
                        Label("Export settings", systemImage: "square.and.arrow.up")
                    }
                }
                Button {
                    showImporter = true
                } label: {
                    Label("Import settings", systemImage: "square.and.arrow.down")
                }
                Text("Preferences transfer between platforms. Saved receivers are device-specific: Android MAC addresses are discarded on iPhone and require a new scan.")
                    .font(.footnote)
                    .foregroundStyle(theme.muted)
            }
            Section("Developer") {
                Toggle("Detailed local BLE logging", isOn: binding(\.debugLoggingEnabled))
                Text("Logs stay on this device and are included only when you export diagnostics.")
                    .font(.footnote)
                    .foregroundStyle(theme.muted)
            }
        }
        .scrollContentBackground(.hidden)
        .themedScreen()
        .navigationTitle("Settings")
        .sheet(item: $editingReceiver) { receiver in
            ReceiverEditorSheet(
                receiver: receiver,
                onRename: { nickname in settingsStore.renameReceiver(id: receiver.address, nickname: nickname) },
                onSetDefault: { settingsStore.setDefaultReceiver(id: receiver.address) },
                onForget: {
                    editingReceiver = nil
                    receiverToForget = receiver
                }
            )
            .presentationDetents([.medium])
        }
        .alert("Forget \(receiverToForget?.displayName() ?? "receiver")?", isPresented: Binding(
            get: { receiverToForget != nil },
            set: { if !$0 { receiverToForget = nil } }
        )) {
            Button("Forget", role: .destructive) {
                if let receiverToForget { settingsStore.forgetReceiver(id: receiverToForget.address) }
                receiverToForget = nil
            }
            Button("Cancel", role: .cancel) { receiverToForget = nil }
        } message: {
            Text("This removes it from this iPhone. You can scan and connect again later.")
        }
        .fileImporter(isPresented: $showImporter, allowedContentTypes: [.json]) { result in
            switch result {
            case .success(let url):
                guard url.startAccessingSecurityScopedResource() else {
                    importMessage = "Couldn't access that backup file."
                    showImportMessage = true
                    return
                }
                defer { url.stopAccessingSecurityScopedResource() }
                do {
                    let outcome = try settingsStore.importBackup(try Data(contentsOf: url)).get()
                    importMessage = outcome.discardedPlatformBoundReceivers
                        ? "Settings imported. This backup's receiver identifiers were discarded for safety; scan again to connect."
                        : "Settings imported."
                } catch {
                    importMessage = "Settings were not changed: \(error.localizedDescription)"
                }
                showImportMessage = true
            case .failure(let error):
                importMessage = "Couldn't choose a backup: \(error.localizedDescription)"
                showImportMessage = true
            }
        }
        .alert("Settings import", isPresented: $showImportMessage) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(importMessage ?? "")
        }
    }

    private func binding(_ keyPath: WritableKeyPath<SoundKitSettings, Bool>) -> Binding<Bool> {
        Binding(
            get: { settingsStore.settings[keyPath: keyPath] },
            set: { newValue in settingsStore.update { $0[keyPath: keyPath] = newValue } }
        )
    }

    private var backupURL: URL? {
        do {
            let url = FileManager.default.temporaryDirectory.appendingPathComponent("soundkit-settings.json")
            try settingsStore.exportBackup().write(to: url, options: .atomic)
            return url
        } catch {
            return nil
        }
    }
}

private struct ReceiverEditorSheet: View {
    let receiver: SavedReceiver
    let onRename: (String?) -> Void
    let onSetDefault: () -> Void
    let onForget: () -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var nickname: String

    init(receiver: SavedReceiver, onRename: @escaping (String?) -> Void, onSetDefault: @escaping () -> Void, onForget: @escaping () -> Void) {
        self.receiver = receiver
        self.onRename = onRename
        self.onSetDefault = onSetDefault
        self.onForget = onForget
        _nickname = State(initialValue: receiver.nickname ?? "")
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Receiver") {
                    Text(receiver.name)
                    Text(receiver.address).font(.caption).textSelection(.enabled)
                }
                Section("Name") {
                    TextField("Custom name", text: $nickname)
                        .textInputAutocapitalization(.words)
                    Button("Save name") {
                        onRename(nickname)
                        dismiss()
                    }
                }
                Section {
                    if !receiver.isDefault {
                        Button("Set as default", action: onSetDefault)
                    }
                    Button("Forget receiver", role: .destructive, action: onForget)
                }
            }
            .navigationTitle("Receiver")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Done") { dismiss() } }
            }
        }
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
