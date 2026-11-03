import SwiftUI

@main
@MainActor
struct DatvipCamApp: App {
    @StateObject private var language = LanguageSelection()
    @StateObject private var store = AdFreeStore()
    var body: some Scene {
        WindowGroup {
            ContentView().preferredColorScheme(.dark)
                .environmentObject(language)
                .environmentObject(store)
                .environment(\.locale, AppLanguage.locale)
        }
    }
}
