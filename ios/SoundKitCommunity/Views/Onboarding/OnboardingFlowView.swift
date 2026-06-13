import SwiftUI

struct OnboardingFlowView: View {
    @EnvironmentObject private var viewModel: SoundKitViewModel
    @EnvironmentObject private var settingsStore: SettingsStore
    @Environment(\.garageTheme) private var theme

    @State private var acceptedRisk = false
    @State private var selectedVehicleId: String?

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
            ForEach(VehicleCompatibilityCatalog.makes, id: \.self) { make in
                Text(make)
                    .font(.subheadline.bold())
                ForEach(VehicleCompatibilityCatalog.modelsForMake(make)) { entry in
                    Button {
                        selectedVehicleId = entry.id
                    } label: {
                        HStack {
                            VStack(alignment: .leading) {
                                Text(entry.displayName)
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
            }
        }
        .padding(16)
        .background(theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}
