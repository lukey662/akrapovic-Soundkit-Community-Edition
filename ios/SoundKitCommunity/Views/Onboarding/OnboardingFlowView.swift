import SwiftUI

struct OnboardingFlowView: View {
    @EnvironmentObject private var viewModel: SoundKitViewModel
    @EnvironmentObject private var settingsStore: SettingsStore
    @Environment(\.garageTheme) private var theme

    @State private var acceptedRisk = false
    @State private var selectedVehicleId: String?
    @State private var expandedMake: String?
    @State private var editingVehicle = false
    @State private var reviewingRisk = false

    private var vehicleComplete: Bool {
        guard let selected = VehicleCompatibilityCatalog.findById(selectedVehicleId) else { return false }
        return selected.tier != .unsupported
    }

    private var canFinish: Bool {
        acceptedRisk && vehicleComplete
    }

    var body: some View {
        NavigationStack {
            ScrollViewReader { proxy in
                ScrollView {
                    VStack(alignment: .leading, spacing: 20) {
                        Text("First launch")
                            .font(.caption)
                            .foregroundStyle(theme.accent)
                        Text("Set up Sound Kit")
                            .font(.largeTitle.bold())
                        Text("Finish one step at a time — each section unlocks the next.")
                            .foregroundStyle(theme.muted)

                        riskCard
                            .id("risk")

                        if acceptedRisk {
                            vehicleCard
                                .id("vehicle")
                        }

                        if vehicleComplete {
                            nextStepHint
                                .id("next")
                        }
                    }
                    .padding(24)
                }
                .themedScreen()
                .onChange(of: acceptedRisk) { _, on in
                    if on {
                        reviewingRisk = false
                        withAnimation {
                            proxy.scrollTo("vehicle", anchor: .top)
                        }
                    }
                }
                .onChange(of: selectedVehicleId) { _, _ in
                    if vehicleComplete {
                        editingVehicle = false
                        expandedMake = nil
                        withAnimation {
                            proxy.scrollTo("next", anchor: .top)
                        }
                    }
                }
            }
            .safeAreaInset(edge: .bottom) {
                VStack(spacing: 12) {
                    Text(footerHint)
                        .font(.footnote)
                        .foregroundStyle(theme.muted)
                    Button("Get started") {
                        viewModel.completeOnboarding(vehicleId: selectedVehicleId)
                    }
                    .buttonStyle(PrimaryButtonStyle())
                    .disabled(!canFinish)
                }
                .padding(24)
                .background(theme.surface)
            }
            .navigationBarHidden(true)
        }
    }

    private var footerHint: String {
        if !acceptedRisk {
            return "Accept the disclaimer to continue."
        }
        if !vehicleComplete {
            return "Select your car to continue."
        }
        return "Ready — tap Get started, then connect your receiver on Home."
    }

    private var riskCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("Use at your own risk")
                    .font(.headline)
                Spacer()
                if acceptedRisk {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundStyle(theme.accent)
                }
            }

            if acceptedRisk && !reviewingRisk {
                Text("Accepted")
                    .font(.subheadline)
                    .foregroundStyle(theme.muted)
                Button("Review") {
                    reviewingRisk = true
                }
                .foregroundStyle(theme.accent)
            } else {
                Text("Independent open-source project — not affiliated with Akrapovič. Reverse-engineered protocol. Use only when parked.")
                    .font(.subheadline)
                    .foregroundStyle(theme.muted)
                Toggle("I understand and accept", isOn: $acceptedRisk)
                    .tint(theme.accent)
                    .onChange(of: acceptedRisk) { _, on in
                        if on { settingsStore.acceptRisk() }
                    }
                if acceptedRisk && reviewingRisk {
                    Button("Done") {
                        reviewingRisk = false
                    }
                    .foregroundStyle(theme.accent)
                }
            }
        }
        .padding(16)
        .background(theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private var vehicleCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("Your car")
                    .font(.headline)
                Spacer()
                if vehicleComplete {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundStyle(theme.accent)
                }
            }

            if vehicleComplete && !editingVehicle,
               let selected = VehicleCompatibilityCatalog.findById(selectedVehicleId) {
                selectedSummary(selected)
                Button("Change car") {
                    editingVehicle = true
                }
                .foregroundStyle(theme.accent)
            } else {
                Text("Pick the car with your Sound Kit. Used for support and theme hints — not to limit connections.")
                    .font(.subheadline)
                    .foregroundStyle(theme.muted)

                if vehicleComplete,
                   let selected = VehicleCompatibilityCatalog.findById(selectedVehicleId) {
                    selectedSummary(selected)
                    Button("Done") {
                        editingVehicle = false
                        expandedMake = nil
                    }
                    .foregroundStyle(theme.accent)
                }

                ForEach(VehicleCompatibilityCatalog.makes, id: \.self) { make in
                    let models = VehicleCompatibilityCatalog.modelsForMake(make)
                    let selectedInMake = models.first { $0.id == selectedVehicleId }
                    DisclosureGroup(
                        isExpanded: Binding(
                            get: { expandedMake == make },
                            set: { expandedMake = $0 ? make : nil }
                        )
                    ) {
                        ForEach(models) { entry in
                            Button {
                                selectedVehicleId = entry.id
                                expandedMake = nil
                                editingVehicle = false
                            } label: {
                                HStack {
                                    VStack(alignment: .leading) {
                                        Text(entry.model)
                                        Text(entry.tierLabel)
                                            .font(.caption)
                                            .foregroundStyle(entry.tier == .supported ? .green : .orange)
                                    }
                                    Spacer()
                                    if selectedVehicleId == entry.id {
                                        Image(systemName: "checkmark.circle.fill")
                                            .foregroundStyle(theme.accent)
                                    }
                                }
                            }
                            .buttonStyle(.plain)
                            .padding(.vertical, 6)
                        }
                    } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(make)
                                .font(.subheadline.bold())
                            if let selectedInMake, expandedMake != make {
                                Text("\(selectedInMake.model) · \(selectedInMake.tierLabel)")
                                    .font(.caption)
                                    .foregroundStyle(theme.accent)
                            }
                        }
                    }
                }
            }
        }
        .padding(16)
        .background(theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private var nextStepHint: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Next")
                .font(.headline)
            Text("After Get started, grant Bluetooth if asked, then scan for your Sound Kit receiver on Home.")
                .font(.subheadline)
                .foregroundStyle(theme.muted)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    @ViewBuilder
    private func selectedSummary(_ selected: VehicleCompatibilityEntry) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Selected")
                .font(.caption)
                .foregroundStyle(theme.muted)
            Text(selected.displayName)
                .font(.subheadline.bold())
            Text(selected.tier.description)
                .font(.caption)
                .foregroundStyle(theme.muted)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(theme.surface.opacity(0.7))
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}
