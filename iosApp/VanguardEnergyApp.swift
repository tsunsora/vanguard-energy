import SwiftUI
import EnergyShared

@main
struct VanguardEnergyApp: App {
    @Environment(\.scenePhase) private var scenePhase
    @State private var tracker = IosApp()

    var body: some Scene {
        WindowGroup {
            TrackerView(tracker: tracker)
                .ignoresSafeArea()
                .onAppear { tracker.setActive(active: true) }
                .onChange(of: scenePhase) { phase in
                    tracker.setActive(active: phase == .active)
                }
        }
    }
}

private struct TrackerView: UIViewControllerRepresentable {
    let tracker: IosApp
    func makeUIViewController(context: Context) -> UIViewController { tracker.viewController() }
    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}
