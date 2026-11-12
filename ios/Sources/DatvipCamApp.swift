import SwiftUI

@main
@MainActor
struct DatvipCamApp: App {
    @StateObject private var language = LanguageSelection()
    init() {
        #if DEBUG
        if ProcessInfo.processInfo.environment["CAMERA_HT_UI_TESTING"] == "1" {
            for key in ["logoOn", "gps", "alt", "hdg"] { UserDefaults.standard.set(false, forKey: key) }
        }
        #endif
    }
    var body: some Scene {
        WindowGroup {
            ContentView().preferredColorScheme(.dark)
                .environmentObject(language)
                .environment(\.locale, AppLanguage.locale)
        }
    }
}
