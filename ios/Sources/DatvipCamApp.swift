import SwiftUI

@main
struct DatvipCamApp: App {
    @StateObject private var language = LanguageSelection()
    var body: some Scene {
        WindowGroup {
            ContentView().preferredColorScheme(.dark)
                .environmentObject(language)
                .environment(\.locale, AppLanguage.locale)
        }
    }
}
