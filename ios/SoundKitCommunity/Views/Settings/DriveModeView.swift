import SwiftUI

struct DriveModeView: View {
    @EnvironmentObject private var viewModel: SoundKitViewModel
    @EnvironmentObject private var settingsStore: SettingsStore
    @Environment(\.garageTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    private let dayLabels = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Quick profiles")
                    .font(.subheadline)
                    .foregroundStyle(theme.muted)
                profilePicker

                VStack(spacing: 0) {
                    toggleRow(
                        title: "Drive mode",
                        subtitle: "Apply preferred valves when connected",
                        isOn: binding(\.driveModeEnabled)
                    )
                    if settingsStore.settings.driveModeEnabled {
                        preferredValves
                        toggleRow(
                            title: "Pause drive mode",
                            subtitle: "Manual control until you resume",
                            isOn: binding(\.automationPaused)
                        )
                        quietNeighboursSection
                    }
                }
                .background(theme.surface)
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
            .padding(24)
        }
        .themedScreen()
        .navigationTitle("Drive mode")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Done") { dismiss() }
            }
        }
    }

    private var profilePicker: some View {
        HStack {
            ForEach(DriveModeProfile.allCases) { profile in
                let selected = DriveModeProfile.matching(settingsStore.settings) == profile
                Button(profile.label) {
                    viewModel.applyProfile(profile)
                }
                .font(.caption.bold())
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(selected ? theme.accent : theme.surface)
                .foregroundStyle(selected ? .white : theme.muted)
                .clipShape(Capsule())
            }
        }
    }

    private var preferredValves: some View {
        VStack(alignment: .leading, spacing: 10) {
            Divider().overlay(theme.muted.opacity(0.3))
            Text("Preferred valves")
                .padding(.horizontal, 16)
            HStack(spacing: 8) {
                ForEach(PreferredValveMode.allCases, id: \.self) { mode in
                    let selected = settingsStore.settings.preferredValveMode == mode
                    Button(mode.rawValue) {
                        settingsStore.update { $0.preferredValveMode = mode }
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .background(selected ? theme.accent : theme.base)
                    .foregroundStyle(selected ? .white : theme.muted)
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 12)
        }
    }

    private var quietNeighboursSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Divider().overlay(theme.muted.opacity(0.3))
            toggleRow(
                title: "Quiet neighbours",
                subtitle: "Hold closed briefly when you connect during the window",
                isOn: quietEnabledBinding
            )
            if settingsStore.settings.quietStart.enabled {
                Text("Days")
                    .font(.caption)
                    .foregroundStyle(theme.muted)
                    .padding(.horizontal, 16)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack {
                        ForEach(Array(dayLabels.enumerated()), id: \.offset) { index, label in
                            let selected = settingsStore.settings.quietStart.daysOfWeek.contains(index)
                            Button(label) {
                                settingsStore.update { settings in
                                    var days = settings.quietStart.daysOfWeek
                                    if selected { days.remove(index) } else { days.insert(index) }
                                    settings.quietStart.daysOfWeek = days
                                }
                            }
                            .font(.caption.bold())
                            .foregroundStyle(selected ? theme.accent : theme.muted)
                        }
                    }
                    .padding(.horizontal, 16)
                }
                quietTimeRow(title: "Start", minute: settingsStore.settings.quietStart.windowStartMinute) { minute in
                    settingsStore.update { $0.quietStart.windowStartMinute = minute }
                }
                quietTimeRow(
                    title: "End",
                    minute: settingsStore.settings.quietStart.windowEndMinute,
                    trailing: DriveModeSummary.formatEndMinute(settingsStore.settings.quietStart)
                ) { minute in
                    settingsStore.update { $0.quietStart.windowEndMinute = minute }
                }
                if QuietWindowEvaluator.isOvernight(settingsStore.settings.quietStart) {
                    Text("Runs overnight (ends next morning)")
                        .font(.caption)
                        .foregroundStyle(theme.muted)
                        .padding(.horizontal, 16)
                }
                VStack(alignment: .leading, spacing: 8) {
                    Text("Hold closed: \(settingsStore.settings.quietStart.holdClosedMinutes) min")
                        .font(.caption)
                        .foregroundStyle(theme.muted)
                    Slider(
                        value: holdBinding,
                        in: 1...15,
                        step: 1
                    )
                    .tint(theme.accent)
                    .padding(.horizontal, 16)
                }
                .padding(.bottom, 12)
            }
        }
    }

    private func toggleRow(title: String, subtitle: String, isOn: Binding<Bool>) -> some View {
        Toggle(isOn: isOn) {
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                Text(subtitle).font(.caption).foregroundStyle(theme.muted)
            }
        }
        .tint(theme.accent)
        .padding(16)
    }

    private func quietTimeRow(
        title: String,
        minute: Int,
        trailing: String? = nil,
        onChange: @escaping (Int) -> Void
    ) -> some View {
        HStack {
            Text(title)
            Spacer()
            Menu(trailing ?? DriveModeSummary.formatMinute(minute)) {
                ForEach([22, 23, 0, 6, 7, 8, 9, 10, 22].unique(), id: \.self) { hour in
                    let m = hour * 60
                    Button(DriveModeSummary.formatMinute(m)) { onChange(m) }
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
    }

    private func binding(_ keyPath: WritableKeyPath<SoundKitSettings, Bool>) -> Binding<Bool> {
        Binding(
            get: { settingsStore.settings[keyPath: keyPath] },
            set: { newValue in settingsStore.update { $0[keyPath: keyPath] = newValue } }
        )
    }

    private var quietEnabledBinding: Binding<Bool> {
        Binding(
            get: { settingsStore.settings.quietStart.enabled },
            set: { enabled in settingsStore.update { $0.quietStart.enabled = enabled } }
        )
    }

    private var holdBinding: Binding<Double> {
        Binding(
            get: { Double(settingsStore.settings.quietStart.holdClosedMinutes) },
            set: { value in settingsStore.update { $0.quietStart.holdClosedMinutes = Int(value) } }
        )
    }
}

private extension Array where Element: Hashable {
    func unique() -> [Element] {
        var seen = Set<Element>()
        return filter { seen.insert($0).inserted }
    }
}
