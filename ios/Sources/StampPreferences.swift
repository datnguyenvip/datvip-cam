import SwiftUI
import AudioToolbox

enum StampPreferences {
    static let backgroundKey = "stampBackgroundHex"
    static let opacityKey = "stampBackgroundOpacity"
    static let defaultHex = "0E1A24"
    static let defaultOpacity = 0.60

    static var background: UIColor {
        let defaults = UserDefaults.standard
        let hex = defaults.string(forKey: backgroundKey) ?? defaultHex
        let value = defaults.object(forKey: opacityKey) as? Double ?? defaultOpacity
        return UIColor(stampHex: hex).withAlphaComponent(CGFloat(min(1, max(0.4, value))))
    }
}

extension UIColor {
    convenience init(stampHex: String) {
        let hex = stampHex.trimmingCharacters(in: CharacterSet(charactersIn: "#"))
        let value = hex.count == 6 ? UInt32(hex, radix: 16) ?? 0x0E1A24 : 0x0E1A24
        self.init(red: CGFloat((value >> 16) & 255) / 255,
                  green: CGFloat((value >> 8) & 255) / 255,
                  blue: CGFloat(value & 255) / 255, alpha: 1)
    }

    var stampHex: String {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        getRed(&r, green: &g, blue: &b, alpha: &a)
        return String(format: "%02X%02X%02X", Int((r * 255).rounded()), Int((g * 255).rounded()), Int((b * 255).rounded()))
    }
}

final class ShutterFeedback {
    private var sound: SystemSoundID = 0
    init() {
        if let url = Bundle.main.url(forResource: "shutter", withExtension: "wav") {
            AudioServicesCreateSystemSoundID(url as CFURL, &sound)
        }
    }
    func play() {
        if UserDefaults.standard.object(forKey: "shutterSound") as? Bool ?? true, sound != 0 {
            AudioServicesPlaySystemSound(sound)
        }
    }
    deinit { if sound != 0 { AudioServicesDisposeSystemSoundID(sound) } }
}

struct NotePreset: Codable, Identifiable {
    var id = UUID().uuidString
    var name: String
    var values: [String: String]

    static let keys = ["proj", "note"] + (1...4).flatMap { ["f\($0)l", "f\($0)v"] }
    static func currentValues() -> [String: String] {
        Dictionary(uniqueKeysWithValues: keys.map { ($0, UserDefaults.standard.string(forKey: $0) ?? "") })
    }
    func apply() { values.filter { Self.keys.contains($0.key) }.forEach { UserDefaults.standard.set($0.value, forKey: $0.key) } }
}

struct NotePresetsSection: View {
    @AppStorage("noteTemplates") private var stored = "[]"
    @State private var name = ""
    @State private var feedback = ""

    private var presets: [NotePreset] {
        (try? JSONDecoder().decode([NotePreset].self, from: Data(stored.utf8))) ?? []
    }
    private func save(_ items: [NotePreset]) {
        if let data = try? JSONEncoder().encode(items), let text = String(data: data, encoding: .utf8) { stored = text }
    }
    var body: some View {
        Section {
            Text("Lưu cả nhiệm vụ, ghi chú và 4 trường tuỳ biến. Vị trí GPS vẫn lấy mới.").font(.caption).foregroundColor(.secondary)
            TextField("Tên mẫu ghi chú", text: $name)
            Button("Lưu nội dung hiện tại thành mẫu") {
                let title = String(name.trimmingCharacters(in: .whitespacesAndNewlines).prefix(60))
                guard !title.isEmpty else { feedback = "Hãy nhập tên mẫu."; return }
                let values = NotePreset.currentValues()
                guard values.values.contains(where: { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }) else {
                    feedback = "Hãy nhập nội dung trước khi lưu mẫu."; return
                }
                guard presets.count < 10 else { feedback = "Đã đủ 10 mẫu. Hãy cập nhật hoặc xoá một mẫu."; return }
                guard !presets.contains(where: { $0.name.caseInsensitiveCompare(title) == .orderedSame }) else {
                    feedback = "Tên mẫu đã có. Bạn có thể cập nhật mẫu đó."; return
                }
                save(presets + [NotePreset(name: title, values: values)]); name = ""; feedback = "Đã lưu mẫu."
            }
            ForEach(presets) { preset in
                HStack {
                    Button(preset.name) { preset.apply(); feedback = "Đang dùng mẫu: " + preset.name }.buttonStyle(.borderless)
                    Spacer()
                    Menu {
                        Button("Dùng mẫu") { preset.apply(); feedback = "Đang dùng mẫu: " + preset.name }
                        Button("Cập nhật từ nội dung hiện tại") {
                            var items = presets
                            if let i = items.firstIndex(where: { $0.id == preset.id }) { items[i].values = NotePreset.currentValues(); save(items); feedback = "Đã cập nhật mẫu." }
                        }
                        Button("Đổi tên theo ô tên mẫu") {
                            let title = String(name.trimmingCharacters(in: .whitespacesAndNewlines).prefix(60))
                            guard !title.isEmpty else { feedback = "Nhập tên mới vào ô tên mẫu trước."; return }
                            guard !presets.contains(where: { $0.id != preset.id && $0.name.caseInsensitiveCompare(title) == .orderedSame }) else { feedback = "Tên mẫu đã có."; return }
                            var items = presets
                            if let i = items.firstIndex(where: { $0.id == preset.id }) { items[i].name = title; save(items); name = "" }
                        }
                        Button("Xoá mẫu", role: .destructive) { save(presets.filter { $0.id != preset.id }) }
                    } label: { Image(systemName: "ellipsis.circle") }
                }
            }
            if !feedback.isEmpty { Text(feedback).font(.caption).foregroundColor(.secondary) }
        } header: { Text("Mẫu ghi chú (\(presets.count)/10)") }
    }
}

struct StampBackgroundSection: View {
    @AppStorage(StampPreferences.backgroundKey) private var hex = StampPreferences.defaultHex
    @AppStorage(StampPreferences.opacityKey) private var opacity = StampPreferences.defaultOpacity
    private var selectedColor: Binding<Color> {
        Binding(get: { Color(uiColor: UIColor(stampHex: hex)) }, set: { hex = UIColor($0).stampHex })
    }
    var body: some View {
        Section("Nền khung thông tin") {
            ColorPicker("Màu nền", selection: selectedColor, supportsOpacity: false)
            Text("Độ đậm nền: \(Int(min(1, max(0.4, opacity)) * 100))%")
            Slider(value: $opacity, in: 0.4...1, step: 0.05)
            Text("Nền tối mờ mặc định. Tối thiểu 40% để chữ dễ đọc.").font(.caption).foregroundColor(.secondary)
            Text("12:34:56 · Nhiệm vụ mẫu").font(.headline).foregroundColor(.white)
                .shadow(color: .black, radius: 1, x: 0, y: 1).padding()
                .frame(maxWidth: .infinity).background(Color(uiColor: StampPreferences.background)).cornerRadius(10)
            Button("Khôi phục nền mặc định") { hex = StampPreferences.defaultHex; opacity = StampPreferences.defaultOpacity }
        }
    }
}
