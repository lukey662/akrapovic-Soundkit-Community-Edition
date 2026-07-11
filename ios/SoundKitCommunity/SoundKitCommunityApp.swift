import SwiftUI

@main
struct SoundKitCommunityApp: App {
    @StateObject private var viewModel: SoundKitViewModel

    init() {
        let viewModel = SoundKitViewModel()
        _viewModel = StateObject(wrappedValue: viewModel)
        AppCommandEnvironment.shared.configure(coordinator: viewModel.valveControl)
    }

    var body: some Scene {
        WindowGroup {
            RootView(viewModel: viewModel)
        }
    }
}
