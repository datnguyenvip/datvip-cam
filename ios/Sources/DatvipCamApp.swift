import SwiftUI

@main
@MainActor
struct DatvipCamApp: App {
    @StateObject private var language = LanguageSelection()
    @StateObject private var store = AdFreeStore()
    @StateObject private var consent = CameraAdConsent()
    var body: some Scene {
        WindowGroup {
            ContentView().preferredColorScheme(.dark)
                .environmentObject(language)
                .environmentObject(store)
                .environmentObject(consent)
                .environment(\.locale, AppLanguage.locale)
        }
    }
}
