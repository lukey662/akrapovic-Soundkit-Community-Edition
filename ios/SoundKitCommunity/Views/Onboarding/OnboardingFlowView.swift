import SwiftUI

struct OnboardingFlowView: View {
    @EnvironmentObject private var viewModel: SoundKitViewModel
    @EnvironmentObject private var settingsStore: SettingsStore
    @Environment(\.garageTheme) private var theme

    @State private var acceptedRisk = false
    @State private var selectedVehicleId: String?
    @State private var expandedMake: String?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Text("First launch")
                        .font(.caption)
                        .foregroundStyle(theme.accent)
                    Text("Set up Sound Kit")
                        .font(.largeTitle.bold())
                    Text("Grant Bluetooth access, pick your car, then connect to your receiver.")
                        .foregroundStyle(theme.muted)

                    riskCard
                    vehicleCard
                }
                .padding(24)
            }
            .themedScreen()
            .safeAreaInset(edge: .bottom) {
                VStack(spacing: 12) {
                    Text("Accept the disclaimer to continue.")
                        .font(.footnote)
                        .foregroundStyle(theme.muted)
                    Button("Get started") {
                        viewModel.completeOnboarding(vehicleId: selectedVehicleId)
                    }
                    .buttonStyle(PrimaryButtonStyle())
                    .disabled(!acceptedRisk || selectedVehicleId == nil)
                }
                .padding(24)
                .background(theme.surface)
            }
            .navigationBarHidden(true)
        }
    }

    private var riskCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Use at your own risk")
                .font(.headline)
            Text("Independent open-source project — not affiliated with Akrapovič. Reverse-engineered protocol. Use only when parked.")
                .font(.subheadline)
                .foregroundStyle(theme.muted)
            Toggle("I understand and accept", isOn: $acceptedRisk)
                .tint(theme.accent)
                .onChange(of: acceptedRisk) { _, on in
                    if on { settingsStore.acceptRisk() }
                }
        }
        .padding(16)
        .background(theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private var vehicleCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Your car")
                .font(.headline)
            Text("Pick the car with your Sound Kit. Used for support and theme hints — not to limit connections.")
                .font(.subheadline)
                .foregroundStyle(theme.muted)

            if let selected = VehicleCompatibilityCatalog.findById(selectedVehicleId),
               selected.tier != .unsupported {
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
        .padding(16)
        .background(theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}
