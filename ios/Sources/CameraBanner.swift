import SwiftUI
import GoogleMobileAds
import UserMessagingPlatform

@MainActor
final class CameraAdConsent: ObservableObject {
    @Published private(set) var allowed = false
    @Published private(set) var privacyRequired = false
    @Published private(set) var generation = 0
    private var requested = false
    private var initialized = false

    func prepare() async {
        guard !requested else { return }
        requested = true
        do {
            try await ConsentInformation.shared.requestConsentInfoUpdate(with: RequestParameters())
            try await ConsentForm.loadAndPresentIfRequired(from: nil)
        } catch { /* Previous consent may still allow contextual ads. */ }
        update()
    }
    private func update() {
        privacyRequired = ConsentInformation.shared.privacyOptionsRequirementStatus == .required
        allowed = ConsentInformation.shared.canRequestAds
        if allowed && !initialized {
            initialized = true
            MobileAds.shared.start()
        }
    }
    func privacyOptions() async {
        guard privacyRequired else { return }
        allowed = false
        do { try await ConsentForm.presentPrivacyOptionsForm(from: nil) } catch {}
        generation += 1
        update()
    }
}

private struct SmallBanner: UIViewRepresentable {
    @Binding var loaded: Bool
    func makeCoordinator() -> Coordinator { Coordinator(loaded: $loaded) }
    func makeUIView(context: Context) -> BannerView {
        let banner = BannerView(adSize: AdSizeBanner)
        #if DEBUG
        banner.adUnitID = "ca-app-pub-3940256099942544/2934735716"
        #else
        banner.adUnitID = "ca-app-pub-7989207995898060/5206413927"
        #endif
        banner.delegate = context.coordinator
        banner.load(Request())
        return banner
    }
    func updateUIView(_ uiView: BannerView, context: Context) {}
    static func dismantleUIView(_ uiView: BannerView, coordinator: Coordinator) {
        uiView.delegate = nil
    }
    final class Coordinator: NSObject, BannerViewDelegate {
        @Binding var loaded: Bool
        init(loaded: Binding<Bool>) { _loaded = loaded }
        func bannerViewDidReceiveAd(_ bannerView: BannerView) { loaded = true }
        func bannerView(_ bannerView: BannerView, didFailToReceiveAdWithError error: Error) { loaded = false }
    }
}

struct CameraBanner: View {
    @EnvironmentObject private var store: AdFreeStore
    @EnvironmentObject private var consent: CameraAdConsent
    @State private var loaded = false
    var body: some View {
        Group {
            if store.checked && !store.owned && consent.allowed {
                SmallBanner(loaded: $loaded)
                    .id(consent.generation)
                    .frame(width: 320, height: loaded ? 50 : 0)
                    .clipped()
                    .padding(.top, loaded ? 12 : 0)
            }
        }
        .task(id: store.checked && !store.owned) {
            if store.checked && !store.owned { await consent.prepare() }
        }
    }
}

struct AdPrivacySection: View {
    @EnvironmentObject private var consent: CameraAdConsent
    var body: some View {
        if consent.privacyRequired {
            Section {
                Button(AppLanguage.text("Tùy chọn quyền riêng tư quảng cáo")) {
                    Task { await consent.privacyOptions() }
                }
            }
        }
    }
}
