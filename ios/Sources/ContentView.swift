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
        let v = PreviewUIView(); v.previewLayer.session = session; v.previewLayer.videoGravity = .resizeAspectFill; return v
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
            HStack { KeyField("f\(i)l", "Nhãn \(i) (VD: Tên)"); Divider(); KeyField("f\(i)v", "Nội dung (VD: Đạt)") }
        }
    }
}

struct ContentView: View {
    @StateObject private var m = CameraModel()
    @State private var showSettings = false
    @State private var showNote = false
    @State private var showGallery = false
    @AppStorage("logoOn") private var logoOn = true
    @State private var logoImg = LogoImage.load()
    private let orange = Color(red: 1, green: 0.42, blue: 0.10)
    private let softOrange = Color(red: 1, green: 0.64, blue: 0.36)
    private let yellow = Color(red: 1, green: 0.82, blue: 0.25)

    private var angle: Double {
        switch m.uiOrient { case .landscapeLeft: return 90; case .landscapeRight: return -90; case .portraitUpsideDown: return 180; default: return 0 }
    }
    private var land: Bool { m.uiOrient == .landscapeLeft || m.uiOrient == .landscapeRight }

    var body: some View {
        VStack(spacing: 0) {
            GeometryReader { g in
                let fw = min(g.size.width, g.size.height * 3 / 4)
                let fh = fw * 4 / 3
                ZStack {
                    Color.black
                    ZStack {
                        CameraPreview(session: m.session)
                        TimelineView(.periodic(from: .now, by: 1)) { tl in overlay(tl.date, fw, fh) }
                    }
                    .frame(width: fw, height: fh).clipped()
                }
                .frame(width: g.size.width, height: g.size.height)
                .overlay(alignment: .topLeading) {
                    Text(m.loc != nil ? "● GPS ±\(Int(m.loc!.horizontalAccuracy))m" : "● Đang định vị…")
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
                        mini("pencil") { showNote = true }
                        mini("mappin.and.ellipse") { if let l = m.loc, let u = URL(string: m.dirLink(l)) { UIApplication.shared.open(u) } }
                    }.padding(10)
                }
                .onAppear { m.previewAspect = fw / fh; m.start() }
            }
            modeBar
            HStack {
                chip("photo.on.rectangle") { if !m.isRecording { showGallery = true } }
                Button { if m.videoMode { m.toggleRecord() } else { m.shoot() } } label: { shutterLabel }.frame(maxWidth: .infinity)
                chip("arrow.triangle.2.circlepath.camera") { if !m.isRecording { m.flip() } }
                chip("gearshape") { showSettings = true }
            }
            .padding(.vertical, 14).background(Color(red: 0.04, green: 0.08, blue: 0.10))
        }
        .background(Color.black.ignoresSafeArea())
        .sheet(isPresented: $showSettings, onDismiss: { logoImg = LogoImage.load() }) { SettingsView() }
        .sheet(isPresented: $showNote) { NoteView() }
        .fullScreenCover(isPresented: $showGallery) { GalleryView(model: m) }
    }

    private var modeBar: some View {
        HStack(spacing: 28) {
            Button("Ảnh") { m.setVideoMode(false) }.foregroundColor(m.videoMode ? .gray : yellow)
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
        }.frame(maxWidth: .infinity)
    }
    private func mini(_ sys: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: sys).font(.system(size: 16)).foregroundColor(.white).frame(width: 40, height: 40)
                .background(Color.black.opacity(0.6)).clipShape(Circle())
        }
    }

    /// Khung thông tin: luôn nằm góc dưới của ảnh, xoay theo hướng cầm điện thoại
    private func overlay(_ d: Date, _ fw: CGFloat, _ fh: CGFloat) -> some View {
        let u = min(fw, fh) / 100
        return ZStack(alignment: .bottomLeading) { Color.clear; panel(d, u).padding(3 * u) }
            .frame(width: land ? fh : fw, height: land ? fw : fh)
            .rotationEffect(.degrees(angle))
            .animation(.easeInOut(duration: 0.15), value: angle)
            .frame(width: fw, height: fh)
    }
    private func panel(_ d: Date, _ u: CGFloat) -> some View {
        let L = m.lines(d, m.loc)
        let lg: UIImage? = logoOn ? logoImg : nil
        return HStack(spacing: 0) {
            Rectangle().fill(orange).frame(width: 1.4 * u)
            VStack(alignment: .leading, spacing: 2) {
                if let lg = lg {
                    HStack(alignment: .top, spacing: 1.8 * u) {
                        Image(uiImage: lg).resizable().scaledToFit()
                            .frame(width: LogoImage.displaySize(lg, unit: u).width, height: LogoImage.displaySize(lg, unit: u).height)
                        VStack(alignment: .leading, spacing: 1) {
                            if let p = L.project { Text(p).font(.system(size: 3.8 * u, weight: .bold)).foregroundColor(softOrange) }
                            if let a = L.address { Text(a).font(.system(size: 3.4 * u, weight: .bold)) }
                        }
                    }
                }
                if let h = L.head {
                    Text(h).font(.system(size: 7.5 * u, weight: .bold)).foregroundColor(yellow)
                    Text(L.date ?? "").font(.system(size: 3.4 * u))
                }
                if lg == nil {
                    if let p = L.project { Text(p).font(.system(size: 3.8 * u, weight: .bold)).foregroundColor(softOrange) }
                    if let a = L.address { Text(a).font(.system(size: 3.4 * u, weight: .bold)) }
                }
                ForEach(Array(L.custom.enumerated()), id: \.offset) { _, r in
                    Text(r).font(.system(size: 3.3 * u)).foregroundColor(Color(white: 0.92))
                }
                ForEach(Array(L.rows.enumerated()), id: \.offset) { _, r in
                    Text(r).font(.system(size: 3.2 * u)).foregroundColor(Color(white: 0.95))
                }
            }.foregroundColor(.white).shadow(color: .black.opacity(0.8), radius: 1, x: 0, y: 1).padding(2.8 * u)
        }
        .background(Color(uiColor: StampPreferences.background))
        .clipShape(RoundedRectangle(cornerRadius: 1.6 * u))
        .fixedSize(horizontal: false, vertical: true)
    }
}

struct NoteView: View {
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            Form {
                NotePresetsSection()
                Section("Ghi chú") { KeyField("proj", "Nhiệm vụ / công việc"); KeyField("note", "Ghi chú") }
                Section("Trường tuỳ biến (Nhãn | Nội dung)") { CustomFields() }
            }
            .navigationTitle("Ghi chú nhanh")
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Xong") { dismiss() } } }
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
    @Environment(\.dismiss) private var dismiss
    @AppStorage("time") private var time = true
    @AppStorage("gps") private var gps = true
    @AppStorage("alt") private var alt = true
    @AppStorage("hdg") private var hdg = true
    @AppStorage("wmOn") private var wmOn = true
    @AppStorage("logoOn") private var logoOn = true
    @AppStorage("shutterSound") private var shutterSound = true
    @State private var pick: PhotosPickerItem?

    var body: some View {
        NavigationStack {
            Form {
                Section("Nội dung dấu") {
                    KeyField("proj", "Tên nhiệm vụ / công việc")
                    KeyField("addr", "Địa chỉ (để trống = tự lấy theo GPS)")
                    KeyField("note", "Ghi chú")
                    KeyField("wm", "Watermark chìm (tên công ty…)")
                }
                Section("Trường tuỳ biến (Nhãn | Nội dung)") { CustomFields() }
                NotePresetsSection()
                StampBackgroundSection()
                Section("Chụp ảnh") { Toggle("Tiếng chụp ảnh", isOn: $shutterSound) }
                Section("Hiển thị trên ảnh") {
                    Toggle("Ngày giờ", isOn: $time); Toggle("Toạ độ GPS", isOn: $gps); Toggle("Độ cao", isOn: $alt)
                    Toggle("Hướng la bàn", isOn: $hdg); Toggle("Watermark chìm", isOn: $wmOn)
                }
                Section("Logo") {
                    PhotosPicker(selection: $pick, matching: .images) { Text("Chọn logo từ thư viện") }
                    Toggle("Hiện logo lên ảnh", isOn: $logoOn)
                    Button("Xoá logo", role: .destructive) { try? FileManager.default.removeItem(at: CameraModel.logoURL) }
                }
                Section("Ứng dụng") {
                    NavigationLink("Thông tin") { AboutView() }
                }
            }
            .navigationTitle("Cài đặt dấu ảnh")
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Xong") { dismiss() } } }
            .onChange(of: pick) { item in
                Task {
                    guard let d = try? await item?.loadTransferable(type: Data.self), let img = UIImage(data: d) else { return }
                    let s = min(1, 1024 / max(img.size.width, img.size.height))
                    let size = CGSize(width: img.size.width * s, height: img.size.height * s)
                    let f = UIGraphicsImageRendererFormat(); f.scale = 1; f.opaque = false
                    let small = UIGraphicsImageRenderer(size: size, format: f).image { _ in img.draw(in: CGRect(origin: .zero, size: size)) }
                    try? small.trimmingTransparentMargins().pngData()?.write(to: CameraModel.logoURL)
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
                Text("Datvip Cam").font(.title2.bold())
                Text("Phiên bản: \(version)")
                Text("app chụp hình hiện trường thời gian thực")
            }
            Section("Phát triển") {
                Text("Nguyễn Đắc Đạt")
                Link("Email: chobanchotoi@gmail.com", destination: URL(string: "mailto:chobanchotoi@gmail.com")!)
            }
            Section { Text("© 2026 Nguyễn Đắc Đạt").foregroundColor(.secondary) }
        }
        .navigationTitle("Thông tin").navigationBarTitleDisplayMode(.inline)
    }
}
