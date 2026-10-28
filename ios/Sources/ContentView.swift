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
    @State private var showShare = false
    @State private var showNote = false
    @State private var showGallery = false
    @State private var showTemplates = false
    @AppStorage("logoOn") private var logoOn = true
    @State private var logoImg = UIImage(contentsOfFile: CameraModel.logoURL.path)
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
                        mini("doc.text") { showTemplates = true }
                        mini("pencil") { showNote = true }
                        mini("mappin.and.ellipse") { if let l = m.loc, let u = URL(string: m.dirLink(l)) { UIApplication.shared.open(u) } }
                    }.padding(10)
                }
                .overlay(alignment: .trailing) {
                    if m.showShareHint {
                        Button { UIPasteboard.general.string = m.lastText; showShare = true } label: {
                            Text("📤 Chia sẻ + vị trí").font(.system(size: 14, weight: .bold)).foregroundColor(.white)
                                .padding(.horizontal, 14).padding(.vertical, 10).background(orange).clipShape(Capsule())
                        }.padding(10)
                    }
                }
                .onAppear { m.previewAspect = fw / fh; m.start() }
            }
            modeBar
            HStack {
                chip("photo.on.rectangle") { showGallery = true }
                Button { if m.videoMode { m.toggleRecord() } else { m.shoot() } } label: { shutterLabel }.frame(maxWidth: .infinity)
                chip("arrow.triangle.2.circlepath.camera") { if !m.isRecording { m.flip() } }
                chip("gearshape") { showSettings = true }
            }
            .padding(.vertical, 14).background(Color(red: 0.04, green: 0.08, blue: 0.10))
        }
        .background(Color.black.ignoresSafeArea())
        .sheet(isPresented: $showSettings, onDismiss: { logoImg = UIImage(contentsOfFile: CameraModel.logoURL.path) }) { SettingsView() }
        .sheet(isPresented: $showNote) { NoteView() }
        .sheet(isPresented: $showTemplates) { TemplatesView() }
        .sheet(isPresented: $showGallery) { GalleryView(model: m) }
        .sheet(isPresented: $showShare) { ShareSheet(items: m.shareItems) }
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
                        Image(uiImage: lg).resizable().scaledToFit().frame(maxWidth: 24 * u, maxHeight: 9 * u)
                            .padding(0.8 * u).background(Color.white.opacity(0.92)).clipShape(RoundedRectangle(cornerRadius: 0.8 * u))
                        VStack(alignment: .leading, spacing: 1) {
                            if let p = L.project { Text(p).font(.system(size: 3.8 * u, weight: .bold)).foregroundColor(softOrange) }
                            if let a = L.place { Text(a).font(.system(size: 3.4 * u, weight: .bold)) }
                        }
                    }
                } else {
                    if let p = L.project { Text(p).font(.system(size: 3.8 * u, weight: .bold)).foregroundColor(softOrange) }
                    if let a = L.place { Text(a).font(.system(size: 3.4 * u, weight: .bold)) }
                }
                if let h = L.head {
                    Text(h).font(.system(size: 7.5 * u, weight: .bold)).foregroundColor(yellow)
                    Text(L.date ?? "").font(.system(size: 3.4 * u))
                }
                if let a = L.address { Text(a).font(.system(size: 3.3 * u)).foregroundColor(Color(white: 0.92)) }
                ForEach(Array(L.custom.enumerated()), id: \.offset) { _, r in
                    Text(r).font(.system(size: 3.3 * u)).foregroundColor(Color(white: 0.92))
                }
                ForEach(Array(L.rows.enumerated()), id: \.offset) { _, r in
                    Text(r).font(.system(size: 3.2 * u)).foregroundColor(Color(white: 0.95))
                }
            }.foregroundColor(.white).padding(2.8 * u)
        }
        .background(Color(red: 0.055, green: 0.10, blue: 0.14).opacity(0.85))
        .clipShape(RoundedRectangle(cornerRadius: 1.6 * u))
        .fixedSize(horizontal: false, vertical: true)
    }
}

struct NoteView: View {
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            Form {
                Section("Địa điểm làm nhiệm vụ") { KeyField("place", "VD: Tại ngã ba Hoàng Cầm - số 6") }
                Section("Ghi chú") { KeyField("note", "Ghi chú") }
                Section("Trường tuỳ biến (Nhãn | Nội dung)") { CustomFields() }
            }
            .navigationTitle("Ghi chú nhanh")
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Xong") { dismiss() } } }
        }
        .presentationDetents([.medium, .large])
    }
}

struct GalleryView: View {
    @Environment(\.dismiss) private var dismiss
    let model: CameraModel
    @State private var recs = CameraModel.loadRecs()
    @State private var sel = Set<String>()
    @State private var last: PhotoRec?
    @State private var items: [Any] = []
    @State private var showShare = false

    var body: some View {
        NavigationStack {
            ScrollView {
                if recs.isEmpty { Text("Chưa có ảnh nào. Hãy chụp trước.").foregroundColor(.secondary).padding(40) }
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 2), count: 3), spacing: 2) {
                    ForEach(recs) { r in
                        Thumb(file: r.file, video: r.video == true).aspectRatio(1, contentMode: .fit)
                            .overlay(alignment: .bottomLeading) {
                                if r.video == true { Image(systemName: "play.circle.fill").font(.system(size: 22)).foregroundColor(.white).shadow(radius: 2).padding(6) }
                            }
                            .overlay(alignment: .topTrailing) {
                                if sel.contains(r.id) {
                                    Image(systemName: "checkmark.circle.fill").font(.system(size: 24)).foregroundColor(Color(red: 1, green: 0.42, blue: 0.10))
                                        .background(Circle().fill(Color.white)).padding(6)
                                }
                            }
                            .onTapGesture { if sel.contains(r.id) { sel.remove(r.id) } else { sel.insert(r.id) }; last = r }
                    }
                }
            }
            .navigationTitle("Ảnh & video (\(sel.count)/\(recs.count))")
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Đóng") { dismiss() } } }
            .safeAreaInset(edge: .bottom) {
                HStack(spacing: 10) {
                    Button("Cùng vị trí") {
                        guard let ref = last else { return }
                        recs.filter { $0.id == ref.id || CameraModel.near(ref, $0) }.forEach { sel.insert($0.id) }
                    }
                    Button(sel.count == recs.count ? "Bỏ chọn" : "Tất cả") { sel = sel.count == recs.count ? [] : Set(recs.map { $0.id }) }
                    Button("📤 Chia sẻ (\(sel.count))") { share() }.disabled(sel.isEmpty).buttonStyle(.borderedProminent).tint(Color(red: 1, green: 0.42, blue: 0.10))
                }.buttonStyle(.bordered).padding().background(.bar)
            }
        }
        .sheet(isPresented: $showShare) { ShareSheet(items: items) }
    }
    private func share() {
        let chosen = recs.filter { sel.contains($0.id) }
        let text = model.batchText(chosen)
        UIPasteboard.general.string = text
        items = chosen.map { CameraModel.photosDir.appendingPathComponent($0.file) as Any } + [text as Any]
        showShare = true
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
    @AppStorage("addrOn") private var addrOn = true
    @AppStorage("logoOn") private var logoOn = true
    @State private var pick: PhotosPickerItem?

    var body: some View {
        NavigationStack {
            Form {
                Section("Thông tin") {
                    KeyField("proj", "Tên nhiệm vụ / công việc")
                    KeyField("place", "Địa điểm làm nhiệm vụ (VD: Tại ngã ba Hoàng Cầm - số 6)")
                    KeyField("note", "Ghi chú")
                    KeyField("wm", "Watermark chìm (tên công ty…)")
                }
                Section("Trường tuỳ biến (Nhãn | Nội dung)") { CustomFields() }
                Section("Hiển thị trên ảnh") {
                    Toggle("Ngày giờ", isOn: $time); Toggle("Toạ độ GPS", isOn: $gps); Toggle("Độ cao", isOn: $alt)
                    Toggle("Hướng la bàn", isOn: $hdg); Toggle("Watermark chìm", isOn: $wmOn); Toggle("Địa chỉ theo GPS (dưới giờ)", isOn: $addrOn)
                }
                Section("Logo") {
                    PhotosPicker(selection: $pick, matching: .images) { Text("Chọn logo từ thư viện") }
                    Toggle("Hiện logo lên ảnh", isOn: $logoOn)
                    Button("Xoá logo", role: .destructive) { try? FileManager.default.removeItem(at: CameraModel.logoURL) }
                }
            }
            .navigationTitle("Cài đặt dấu ảnh")
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Xong") { dismiss() } } }
            .onChange(of: pick) { item in
                Task {
                    guard let d = try? await item?.loadTransferable(type: Data.self), let img = UIImage(data: d) else { return }
                    let s = min(1, 1024 / max(img.size.width, img.size.height))
                    let size = CGSize(width: img.size.width * s, height: img.size.height * s)
                    let f = UIGraphicsImageRendererFormat(); f.scale = 1
                    let small = UIGraphicsImageRenderer(size: size, format: f).image { _ in img.draw(in: CGRect(origin: .zero, size: size)) }
                    try? small.pngData()?.write(to: CameraModel.logoURL)
                }
            }
        }
    }
}


// MARK: - Mẫu nhiệm vụ (ghi chú có sẵn, sửa được)
struct NoteTemplate: Codable, Identifiable {
    var id = UUID()
    var name: String
    var vals: [String: String]
    var summary: String { [vals["proj"], vals["place"]].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · ") }
}

enum TemplateStore {
    /// Các ô được ghi vào mẫu (ghi chú riêng từng ảnh không nằm trong mẫu)
    static let keys = ["proj", "place", "f1l", "f1v", "f2l", "f2v", "f3l", "f3v", "f4l", "f4v"]
    static func current() -> [String: String] {
        Dictionary(uniqueKeysWithValues: keys.map { ($0, UserDefaults.standard.string(forKey: $0) ?? "") })
    }
    static func apply(_ t: NoteTemplate) { for k in keys { UserDefaults.standard.set(t.vals[k] ?? "", forKey: k) } }
    static func save(_ list: [NoteTemplate]) {
        if let d = try? JSONEncoder().encode(list) { UserDefaults.standard.set(d, forKey: "templates") }
    }
    static func load() -> [NoteTemplate] {
        if let d = UserDefaults.standard.data(forKey: "templates"), let r = try? JSONDecoder().decode([NoteTemplate].self, from: d) { return r }
        save(defaults); return defaults
    }
    private static func make(_ name: String, _ proj: String, _ place: String) -> NoteTemplate {
        NoteTemplate(name: name, vals: ["proj": proj, "place": place, "f1l": "ANCS", "f1v": "DatVip – tổ 3"])
    }
    static let defaults: [NoteTemplate] = [
        make("Giao thông", "Trực chốt giao thông", "Ngã ba đường Hoàng Cầm - đường số 5"),
        make("Karaoke", "Mở nhạc/karaoke gây tiếng ồn", "Đã nhắc nhở"),
        make("Đưa đối tượng đi test", "Đưa đối tượng đi test/khám sức khỏe", "Bệnh viện/TT Y tế: Linh Xuân"),
        make("Bảo vệ hiện trường", "Bảo vệ hiện trường", "Vụ việc: tai nạn giao thông tại "),
        make("Giao phạm", "Đưa đối tượng đi trại", "Trại: Bố Lá"),
        make("Tuần tra", "Tuần tra/xử lý vi phạm", "Xử lý vi phạm"),
        make("Hỗ trợ UB", "Hỗ trợ Ủy ban", "Sắp xếp"),
    ]
}

struct TemplatesView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var items = TemplateStore.load()
    @State private var showNew = false
    @State private var newName = ""

    var body: some View {
        NavigationStack {
            List {
                ForEach(items) { t in
                    Button { TemplateStore.apply(t); dismiss() } label: {
                        VStack(alignment: .leading, spacing: 3) {
                            Text(t.name).font(.headline)
                            Text(t.summary).font(.caption).foregroundColor(.secondary).lineLimit(2)
                        }
                    }
                    .contextMenu {
                        Button("Cập nhật mẫu bằng nội dung hiện tại") { replace(t) }
                        Button("Xoá mẫu", role: .destructive) { items.removeAll { $0.id == t.id }; TemplateStore.save(items) }
                    }
                }
                .onDelete { idx in items.remove(atOffsets: idx); TemplateStore.save(items) }
                Section {
                    Text("Chạm vào mẫu để dùng. Sau đó bạn vẫn sửa được ở nút ✎ hoặc ⚙. Giữ lâu một mẫu để cập nhật hoặc xoá. Bấm + để lưu nội dung đang dùng thành mẫu mới.")
                        .font(.footnote).foregroundColor(.secondary)
                }
            }
            .navigationTitle("Mẫu nhiệm vụ")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Đóng") { dismiss() } }
                ToolbarItem(placement: .primaryAction) { Button { newName = ""; showNew = true } label: { Image(systemName: "plus") } }
            }
            .alert("Lưu nội dung hiện tại thành mẫu", isPresented: $showNew) {
                TextField("Tên mẫu", text: $newName)
                Button("Lưu") { saveNew() }
                Button("Huỷ", role: .cancel) {}
            }
        }
    }
    private func replace(_ t: NoteTemplate) {
        if let i = items.firstIndex(where: { $0.id == t.id }) { items[i].vals = TemplateStore.current(); TemplateStore.save(items) }
    }
    private func saveNew() {
        let n = newName.trimmingCharacters(in: .whitespaces)
        guard !n.isEmpty else { return }
        if let i = items.firstIndex(where: { $0.name == n }) { items[i].vals = TemplateStore.current() }
        else { items.append(NoteTemplate(name: n, vals: TemplateStore.current())) }
        TemplateStore.save(items)
    }
}
