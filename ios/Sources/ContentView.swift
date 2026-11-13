import SwiftUI
import AVFoundation
import PhotosUI

final class PreviewUIView: UIView {
    override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
    var previewLayer: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
}
struct CameraPreview: UIViewRepresentable {
    let session: AVCaptureSession
    func makeUIView(context: Context) -> PreviewUIView {
        let v = PreviewUIView()
        v.previewLayer.session = session
        v.previewLayer.videoGravity = .resizeAspectFill
        #if DEBUG
        if ProcessInfo.processInfo.environment["CAMERA_HT_UI_TESTING"] == "1" {
            v.isAccessibilityElement = true
            v.accessibilityIdentifier = "camera-live-preview"
            v.accessibilityValue = UUID().uuidString
        }
        #endif
        return v
    }
    func updateUIView(_ v: PreviewUIView, context: Context) {}
}
struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController { UIActivityViewController(activityItems: items, applicationActivities: nil) }
    func updateUIViewController(_ v: UIActivityViewController, context: Context) {}
}
struct KeyField: View {
    @AppStorage private var text: String
    private let title: String
    init(_ key: String, _ title: String) { _text = AppStorage(wrappedValue: "", key); self.title = title }
    var body: some View { TextField(title, text: $text) }
}
struct CustomFields: View {
    var body: some View {
        ForEach(1...4, id: \.self) { i in
            HStack { KeyField("f\(i)l", AppLanguage.text("Nhãn {0} (VD: Tên)", String(describing: (i)))); Divider(); KeyField("f\(i)v", AppLanguage.text("Nội dung (VD: Đạt)")) }
        }
    }
}

struct ContentView: View {
    @EnvironmentObject private var language: LanguageSelection
    @StateObject private var m = CameraModel()
    @StateObject private var motion = CameraMotion()
    @Environment(\.scenePhase) private var scenePhase
    @AppStorage("compositionGuides") private var guides = false
    @AppStorage("levelCrosshair") private var levelEnabled = false
    @State private var levelWarning = false
    @State private var showSettings = false
    @State private var showNote = false
    @State private var showGallery = false
    private let orange = Color(red: 1, green: 0.42, blue: 0.10)
    private let yellow = Color(red: 1, green: 0.82, blue: 0.25)

    private var angle: Double { motion.angle }
    private var land: Bool { abs(angle) == 90 }

    var body: some View {
        GeometryReader { g in
            cameraBody
                .frame(width: land ? g.size.height : g.size.width,
                       height: land ? g.size.width : g.size.height)
                .rotationEffect(.degrees(angle))
                .frame(width: g.size.width, height: g.size.height)
        }
        .overlay(alignment: .bottomTrailing) {
            #if DEBUG
            if ProcessInfo.processInfo.environment["CAMERA_HT_TEST_ROTATION_SEQUENCE"] == "1" {
                Button("Rotate test sensor") { motion.advanceTestOrientation() }
                    .accessibilityIdentifier("camera-test-rotate")
                    .accessibilityValue(String(Int(motion.angle)))
                    .font(.caption).frame(width: 60, height: 44)
            }
            #endif
        }
        .onAppear { motion.start(); m.previewAspect = 3.0 / 4.0; m.start() }
        .onDisappear { motion.stop() }
        .alert(AppLanguage.text("Thước cân bằng"), isPresented: $levelWarning) {
            Button("OK", role: .cancel) {}
        } message: { Text(AppLanguage.text("Chưa xác định được phương chuẩn")) }
        .onChange(of: motion.angle) { _ in m.devOrient = motion.orientation }
        .onChange(of: scenePhase) { phase in
            if phase == .active { motion.start() } else { motion.stop() }
        }
    }

    private var cameraBody: some View {
        // Keep the camera at the same structural identity when the axis changes.
        let layout = land ? AnyLayout(HStackLayout(spacing: 0)) : AnyLayout(VStackLayout(spacing: 0))
        return layout {
            cameraStage
            cameraControls
        }
        .background(Color.black.ignoresSafeArea())
        .sheet(isPresented: $showSettings) { SettingsView(languageLocked: m.isRecording) }
        .sheet(isPresented: $showNote) { NoteView() }
        .fullScreenCover(isPresented: $showGallery) { GalleryView(model: m) }
    }

    private var cameraControls: some View {
        Group {
            if land {
                landscapeControls
            } else {
                VStack(spacing: 0) {
                    modeBar
                    HStack {
                        galleryButton
                        captureButton.frame(maxWidth: .infinity)
                        flipButton
                        settingsButton
                    }
                    .padding(.vertical, 14).background(controlBackground)
                }
            }
        }
    }

    private var controlBackground: Color { Color(red: 0.04, green: 0.08, blue: 0.10) }
    private var galleryButton: some View {
        chip("photo.on.rectangle") { if !m.isRecording { showGallery = true } }
    }
    private var flipButton: some View {
        chip("arrow.triangle.2.circlepath.camera") { if !m.isRecording { m.flip() } }
    }
    private var settingsButton: some View { chip("gearshape") { showSettings = true } }
    private var captureButton: some View {
        Button { if m.videoMode { m.toggleRecord() } else {
            if levelEnabled && motion.captureTilt == nil { levelWarning = true }
            else { m.shoot(level: levelEnabled ? motion.captureTilt : nil) }
        } } label: { shutterLabel }
            .accessibilityIdentifier("camera-shutter")
    }
    private var landscapeControls: some View {
        VStack(spacing: 10) {
            HStack(spacing: 0) { galleryButton; settingsButton }
            Spacer(minLength: 0)
            captureButton
            Spacer(minLength: 0)
            flipButton.frame(height: 48)
            modeBar
        }
        .padding(.horizontal, 8).padding(.vertical, 10)
        .frame(width: 120)
        .background(controlBackground)
    }

    private var cameraStage: some View {
            GeometryReader { g in
                let aspect: CGFloat = land ? 4.0 / 3.0 : 3.0 / 4.0
                let fw = min(g.size.width, g.size.height * aspect)
                let fh = fw / aspect
                ZStack {
                    Color.black
                    ZStack {
                        CameraPreview(session: m.session)
                            .frame(width: land ? fh : fw, height: land ? fw : fh)
                            .rotationEffect(.degrees(-angle))
                            .frame(width: fw, height: fh)
                        if guides { CompositionGuides(tilt: levelEnabled ? nil : motion.tilt) }
                        if levelEnabled {
                            LevelCrosshairView(tilt: motion.tilt).allowsHitTesting(false)
                            if motion.tilt == nil {
                                Text(AppLanguage.text("Chưa xác định được phương chuẩn"))
                                    .font(.caption).foregroundColor(.yellow).padding(6).background(Color.black.opacity(0.8))
                            }
                        }
                        TimelineView(.periodic(from: .now, by: 1)) { tl in overlay(tl.date, fw, fh) }
                    }
                    .frame(width: fw, height: fh).clipped()
                    .accessibilityElement(children: .contain)
                    .accessibilityIdentifier("camera-viewport")
                }
                .frame(width: g.size.width, height: g.size.height)
                .overlay(alignment: .topLeading) {
                    Text(m.loc != nil ? "● GPS ±\(Int(m.loc!.horizontalAccuracy))m" : AppLanguage.text("● Đang định vị…"))
                        .font(.system(size: 13, weight: .bold)).padding(.horizontal, 12).padding(.vertical, 6)
                        .background(m.loc != nil ? Color.green.opacity(0.85) : Color.red.opacity(0.85)).clipShape(Capsule()).padding(10)
                }
                .overlay(alignment: .top) {
                    if m.isRecording {
                        HStack(spacing: 6) {
                            Circle().fill(Color.red).frame(width: 9, height: 9)
                            Text(String(format: "%d:%02d", m.recSeconds / 60, m.recSeconds % 60)).font(.system(size: 17, weight: .bold, design: .monospaced))
                        }.padding(.horizontal, 12).padding(.vertical, 5).background(Color.black.opacity(0.55)).clipShape(Capsule()).padding(.top, 10)
                    }
                }
                .overlay(alignment: .bottomTrailing) {
                    VStack(spacing: 10) {
                        mini("scope") { levelEnabled.toggle() }
                            .overlay(Circle().stroke(levelEnabled ? Color.yellow : Color.clear, lineWidth: 2))
                            .accessibilityLabel(AppLanguage.text("Thước cân bằng"))
                            .accessibilityValue(levelEnabled ? AppLanguage.text("Bật") : AppLanguage.text("Tắt"))
                            .accessibilityIdentifier("level-crosshair")
                        mini("pencil") { showNote = true }
                        mini("mappin.and.ellipse") { if let l = m.loc, let u = URL(string: m.dirLink(l)) { UIApplication.shared.open(u) } }
                    }.padding(10)
                }
            }
    }

    private var modeBar: some View {
        HStack(spacing: land ? 12 : 28) {
            Button(AppLanguage.text("Ảnh")) { m.setVideoMode(false) }.foregroundColor(m.videoMode ? .gray : yellow)
            Button("Video") { m.setVideoMode(true) }.foregroundColor(m.videoMode ? yellow : .gray)
        }
        .font(.system(size: 15, weight: .bold)).frame(maxWidth: .infinity).padding(.top, 8)
        .background(Color(red: 0.04, green: 0.08, blue: 0.10)).disabled(m.isRecording)
    }
    private var shutterLabel: some View {
        ZStack {
            Circle().stroke(Color.white, lineWidth: 5).frame(width: 80, height: 80)
            if m.videoMode {
                if m.isRecording { RoundedRectangle(cornerRadius: 6).fill(Color.red).frame(width: 34, height: 34) }
                else { Circle().fill(Color.red).frame(width: 66, height: 66) }
            } else { Circle().fill(orange).frame(width: 68, height: 68) }
        }
    }
    private func chip(_ sys: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: sys).font(.system(size: 20)).foregroundColor(.white).frame(width: 48, height: 48)
                .background(Color(red: 0.106, green: 0.169, blue: 0.212)).clipShape(Circle())
        }.frame(maxWidth: .infinity).accessibilityIdentifier(sys)
    }
    private func mini(_ sys: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: sys).font(.system(size: 16)).foregroundColor(.white).frame(width: 40, height: 40)
                .background(Color.black.opacity(0.6)).clipShape(Circle())
        }
    }

    /// Use the photo/video renderer for preview too, including wrapping and height.
    private func overlay(_ d: Date, _ fw: CGFloat, _ fh: CGFloat) -> some View {
        let width = fw
        let height = fh
        let density = UIScreen.main.scale
        let image = m.overlayImage(CGSize(width: width * density, height: height * density),
                                   m.previewLines(d))
        return Image(uiImage: image).resizable()
            .frame(width: width, height: height)
            .frame(width: fw, height: fh)
            .allowsHitTesting(false)
    }

}

struct NoteView: View {
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            Form {
                NotePresetsSection()
                Section(AppLanguage.text("Ghi chú")) { KeyField("proj", AppLanguage.text("Nhiệm vụ / công việc")); KeyField("note", AppLanguage.text("Ghi chú")) }
                Section(AppLanguage.text("Trường tuỳ biến (Nhãn | Nội dung)")) { CustomFields() }
            }
            .navigationTitle(AppLanguage.text("Ghi chú nhanh"))
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button(AppLanguage.text("Xong")) { dismiss() } } }
        }
        .presentationDetents([.medium, .large])
    }
}

struct Thumb: View {
    let file: String
    var video = false
    @State private var img: UIImage?
    var body: some View {
        ZStack {
            Color(white: 0.15)
            if let img = img { Image(uiImage: img).resizable().scaledToFill() }
        }
        .clipped()
        .task {
            let url = CameraModel.photosDir.appendingPathComponent(file)
            img = await Task.detached(priority: .utility) { () -> UIImage? in
                if video {
                    let g = AVAssetImageGenerator(asset: AVURLAsset(url: url))
                    g.appliesPreferredTrackTransform = true; g.maximumSize = CGSize(width: 300, height: 300)
                    return (try? g.copyCGImage(at: .zero, actualTime: nil)).map { UIImage(cgImage: $0) }
                }
                guard let src = CGImageSourceCreateWithURL(url as CFURL, nil) else { return nil }
                let o: [CFString: Any] = [kCGImageSourceCreateThumbnailFromImageIfAbsent: true, kCGImageSourceCreateThumbnailWithTransform: true, kCGImageSourceThumbnailMaxPixelSize: 300]
                return CGImageSourceCreateThumbnailAtIndex(src, 0, o as CFDictionary).map { UIImage(cgImage: $0) }
            }.value
        }
    }
}

struct SettingsView: View {
    @EnvironmentObject private var language: LanguageSelection
    var languageLocked = false
    @Environment(\.dismiss) private var dismiss
    @AppStorage("time") private var time = true
    @AppStorage("gps") private var gps = true
    @AppStorage("alt") private var alt = true
    @AppStorage("hdg") private var hdg = true
    @AppStorage("wmOn") private var wmOn = true
    @AppStorage("logoOn") private var logoOn = false
    @AppStorage("shutterSound") private var shutterSound = true
    @AppStorage("compositionGuides") private var guides = false
    @State private var pick: PhotosPickerItem?
    @State private var logoRevision = 0
    @AppStorage(StampPreferences.logoOpacityKey) private var logoOpacity = 1.0
    @AppStorage(StampPreferences.removeLogoBackgroundKey) private var removeWhiteLogoBackground = false

    var body: some View {
        NavigationStack {
            Form {
                Section(AppLanguage.text("Ngôn ngữ")) {
                    Picker(AppLanguage.text("Ngôn ngữ"), selection: $language.choice) {
                        Text(AppLanguage.text("Theo điện thoại")).tag("system")
                        Text("Tiếng Việt").tag("vi")
                        Text("English").tag("en")
                    }
                    .disabled(languageLocked)
                }
                Section {
                    Link(AppLanguage.text("Chính sách quyền riêng tư"), destination: URL(string: "https://datnguyenvip.github.io/datvip-cam/privacy-1.1-\(AppLanguage.locale.languageCode == "vi" ? "vi" : "en").html")!)
                }
                Section(AppLanguage.text("Nội dung dấu")) {
                    KeyField("proj", AppLanguage.text("Tên nhiệm vụ / công việc"))
                    KeyField("addr", AppLanguage.text("Địa chỉ (để trống = tự lấy theo GPS)"))
                    KeyField("note", AppLanguage.text("Ghi chú"))
                    KeyField("wm", AppLanguage.text("Watermark chìm (tên công ty…)"))
                }
                Section(AppLanguage.text("Trường tuỳ biến (Nhãn | Nội dung)")) { CustomFields() }
                NotePresetsSection()
                StampBackgroundSection(logoRevision: logoRevision)
                Section(AppLanguage.text("Chụp ảnh")) {
                    Toggle(AppLanguage.text("Tiếng chụp ảnh"), isOn: $shutterSound)
                    Toggle(AppLanguage.text("Căn chỉnh khung hình"), isOn: $guides)
                        .accessibilityIdentifier("composition-guides")
                    Text(AppLanguage.text("Bật lưới 3×3 và cân bằng. Chỉ hiển thị trên khung ngắm, không lưu vào ảnh/video."))
                        .font(.caption).foregroundColor(.secondary)
                }
                Section(AppLanguage.text("Hiển thị trên ảnh")) {
                    Toggle(AppLanguage.text("Ngày giờ"), isOn: $time); Toggle(AppLanguage.text("Toạ độ GPS"), isOn: $gps); Toggle(AppLanguage.text("Độ cao"), isOn: $alt)
                    Toggle(AppLanguage.text("Hướng la bàn"), isOn: $hdg); Toggle(AppLanguage.text("Watermark chìm"), isOn: $wmOn)
                }
                Section("Logo") {
                    PhotosPicker(selection: $pick, matching: .images) { Text(AppLanguage.text("Chọn logo từ thư viện")) }
                    Toggle(AppLanguage.text("Hiện logo lên ảnh"), isOn: $logoOn)
                    Text(AppLanguage.text("Độ đậm logo: {0}%", String(describing: (Int(min(1, max(0, logoOpacity)) * 100)))))
                    Slider(value: $logoOpacity, in: 0...1, step: 0.05) { Text(AppLanguage.text("Độ đậm logo")) }
                    Toggle(AppLanguage.text("Ẩn nền trắng của logo"), isOn: $removeWhiteLogoBackground)
                    Text(AppLanguage.text("Ẩn vùng trắng bên ngoài logo, giữ nguyên tệp gốc. Tắt tùy chọn để khôi phục."))
                        .font(.caption).foregroundColor(.secondary)
                    Button(AppLanguage.text("Xoá logo"), role: .destructive) {
                        try? FileManager.default.removeItem(at: CameraModel.logoURL)
                        logoRevision += 1
                    }
                }
                Section(AppLanguage.text("Ứng dụng")) {
                    NavigationLink(AppLanguage.text("Thông tin")) { AboutView() }
                }
            }
            .navigationTitle(AppLanguage.text("Cài đặt dấu ảnh"))
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button(AppLanguage.text("Xong")) { dismiss() } } }
            .onChange(of: pick) { item in
                Task {
                    guard let d = try? await item?.loadTransferable(type: Data.self), let img = UIImage(data: d) else { return }
                    let s = min(1, 1024 / max(img.size.width, img.size.height))
                    let size = CGSize(width: img.size.width * s, height: img.size.height * s)
                    let f = UIGraphicsImageRendererFormat(); f.scale = 1; f.opaque = false
                    let small = UIGraphicsImageRenderer(size: size, format: f).image { _ in img.draw(in: CGRect(origin: .zero, size: size)) }
                    try? small.trimmingTransparentMargins().pngData()?.write(to: CameraModel.logoURL)
                    logoRevision += 1
                }
            }
        }
    }
}

struct AboutView: View {
    private var version: String { Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0" }
    var body: some View {
        Form {
            Section {
                Text(AppLanguage.name).font(.title2.bold())
                Text(AppLanguage.text("Phiên bản: {0}", String(describing: (version))))
                Text(AppLanguage.text("app chụp hình hiện trường thời gian thực"))
            }
            Section(AppLanguage.text("Phát triển")) {
                Text("Nguyễn Đắc Đạt")
                Link("Email: chobanchotoi@gmail.com", destination: URL(string: "mailto:chobanchotoi@gmail.com")!)
            }
            Section { Text("© 2026 Nguyễn Đắc Đạt").foregroundColor(.secondary) }
        }
        .navigationTitle(AppLanguage.text("Thông tin")).navigationBarTitleDisplayMode(.inline)
    }
}

