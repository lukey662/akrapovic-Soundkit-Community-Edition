import SwiftUI

struct RootView: View {
    @StateObject private var viewModel = SoundKitViewModel()

    var body: some View {
        Group {
            if viewModel.settingsStore.settings.onboardingCompleted {
                MainTabView()
            } else {
                OnboardingFlowView()
            }
        }
        .environmentObject(viewModel)
        .environmentObject(viewModel.settingsStore)
        .environmentObject(viewModel.bleManager)
        .environmentObject(viewModel.diagnosticsStore)
        .environment(\.garageTheme, viewModel.activeTheme)
        .preferredColorScheme(viewModel.activeTheme.isDark ? .dark : .light)
        .onAppear { viewModel.onAppear() }
    }
}

struct MainTabView: View {
    @EnvironmentObject private var viewModel: SoundKitViewModel

    var body: some View {
        TabView(selection: $viewModel.selectedTab) {
            HomeView()
                .tabItem { Label("Home", systemImage: "house.fill") }
                .tag(AppTab.home)
            MoreView()
                .tabItem { Label("More", systemImage: "ellipsis.circle.fill") }
                .tag(AppTab.more)
        }
        .tint(viewModel.activeTheme.accent)
        .sheet(isPresented: $viewModel.showDriveMode) {
            NavigationStack { DriveModeView() }
        }
        .sheet(isPresented: $viewModel.showSettings) {
            NavigationStack { SettingsView() }
        }
        .sheet(isPresented: $viewModel.showAppearance) {
            NavigationStack { AppearanceView() }
        }
        .sheet(isPresented: $viewModel.showAdvanced) {
            NavigationStack { AdvancedView() }
        }
        .sheet(isPresented: $viewModel.showDiagnostics) {
            NavigationStack { DiagnosticsView() }
        }
    }
}
