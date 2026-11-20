
import SwiftUI
import AudioToolbox

enum StampPreferences {
    static let backgroundKey = "stampBackgroundHex"
    static let opacityKey = "stampBackgroundOpacity"
    static let sizeKey = "stampDisplaySize"
    static let logoOpacityKey = "stampLogoOpacity"
    static let removeLogoBackgroundKey = "stampLogoRemoveWhiteBackground"
    static let defaultSize = 0.75
    static let defaultHex = "576145"
    static let defaultOpacity = 0.75

    private static func number(_ key: String, fallback: Double, range: ClosedRange<Double>) -> Double {
        let value = UserDefaults.standard.object(forKey: key) as? Double ?? fallback
        return value.isFinite ? min(range.upperBound, max(range.lowerBound, value)) : fallback
    }
    static var displaySize: CGFloat { CGFloat(number(sizeKey, fallback: defaultSize, range: 0.5...1.2)) }
    static var backgroundOpacity: CGFloat { CGFloat(number(opacityKey, fallback: defaultOpacity, range: 0...1)) }
    static var logoOpacity: CGFloat { CGFloat(number(logoOpacityKey, fallback: 1, range: 0...1)) }

    static var background: UIColor {
        let defaults = UserDefaults.standard
        let hex = defaults.string(forKey: backgroundKey) ?? defaultHex
        return UIColor(stampHex: hex).withAlphaComponent(backgroundOpacity)
    }
}

extension UIColor {
    convenience init(stampHex: String) {
        let hex = stampHex.trimmingCharacters(
            in: CharacterSet(charactersIn: "#")
        )
        let value = hex.count == 6
            ? UInt32(hex, radix: 16) ?? 0x576145
            : 0x576145

        self.init(
            red: CGFloat((value >> 16) & 255) / 255,
            green: CGFloat((value >> 8) & 255) / 255,
            blue: CGFloat(value & 255) / 255,
            alpha: 1
        )
    }

    var stampHex: String {
        var r: CGFloat = 0
        var g: CGFloat = 0
        var b: CGFloat = 0
        var a: CGFloat = 0

        getRed(&r, green: &g, blue: &b, alpha: &a)

        return String(
            format: "%02X%02X%02X",
            Int((r * 255).rounded()),
            Int((g * 255).rounded()),
            Int((b * 255).rounded())
        )
    }
}

final class ShutterFeedback {
    private var sound: SystemSoundID = 0

    init() {
        if let url = Bundle.main.url(
            forResource: "shutter",
            withExtension: "wav"
        ) {
            AudioServicesCreateSystemSoundID(
                url as CFURL,
                &sound
            )
        }
    }

    func play() {
        if UserDefaults.standard.object(
            forKey: "shutterSound"
        ) as? Bool ?? true, sound != 0 {
            AudioServicesPlaySystemSound(sound)
        }
    }

    deinit {
        if sound != 0 {
            AudioServicesDisposeSystemSoundID(sound)
        }
    }
}

struct NotePreset: Codable, Identifiable {
    var id = UUID().uuidString
    var name: String
    var values: [String: String]

    static let keys = ["proj", "note"]
        + (1...4).flatMap { ["f\($0)l", "f\($0)v"] }

    static func currentValues() -> [String: String] {
        Dictionary(
            uniqueKeysWithValues: keys.map {
                ($0, UserDefaults.standard.string(forKey: $0) ?? "")
            }
        )
    }

    func apply() {
        // Xoa truong cu de khong lan thong tin tu mau truoc.
        for key in Self.keys {
            UserDefaults.standard.set(
                values[key] ?? "",
                forKey: key
            )
        }
    }

    static func make(
        _ name: String,
        _ project: String,
        _ location: String
    ) -> NotePreset {
        NotePreset(
            name: name,
            values: [
                "proj": project,
                "note": "",
                "f1l": "Địa điểm",
                "f1v": location,
                "f2l": "",
                "f2v": "",
                "f3l": "",
                "f3v": "",
                "f4l": "",
                "f4v": ""
            ]
        )
    }

    static let builtIn: [NotePreset] = [
        make("Khảo sát", "Khảo sát hiện trường", "Công trình A"),
        make("Giao nhận hàng", "Kiểm tra giao nhận hàng hóa", "Điểm giao nhận"),
        make("Công trình", "Kiểm tra tiến độ thi công", "Công trình A"),
        make("Du lịch", "Ghi lại hành trình", "Điểm tham quan")
    ]
}

struct NotePresetsSection: View {
    @AppStorage("noteTemplates") private var stored = "[]"
    @AppStorage("noteTemplatesSeedVersion") private var seedVersion = 0

    @State private var name = ""
    @State private var feedback = ""

    private let maxPresets = 20

    private var presets: [NotePreset] {
        (try? JSONDecoder().decode(
            [NotePreset].self,
            from: Data(stored.utf8)
        )) ?? []
    }

    @discardableResult
    private func save(_ items: [NotePreset]) -> Bool {
        guard let data = try? JSONEncoder().encode(items),
              let text = String(data: data, encoding: .utf8) else {
            return false
        }

        UserDefaults.standard.set(text, forKey: "noteTemplates")
        stored = text

        guard let saved = UserDefaults.standard.string(
            forKey: "noteTemplates"
        ),
        let decoded = try? JSONDecoder().decode(
            [NotePreset].self,
            from: Data(saved.utf8)
        ) else {
            return false
        }

        return decoded.count == items.count &&
            Set(decoded.map(\.id)) == Set(items.map(\.id))
    }

    private func installDefaultsIfNeeded() {
        guard seedVersion < 1 else { return }

        var items = presets

        for preset in NotePreset.builtIn {
            if !items.contains(where: {
                $0.name.caseInsensitiveCompare(preset.name)
                    == .orderedSame
            }) {
                items.append(preset)
            }
        }

        save(items)
        seedVersion = 1
    }

    private func use(_ preset: NotePreset) {
        preset.apply()
        feedback = AppLanguage.text("Đang dùng mẫu: ") + preset.name
    }

    var body: some View {
        Section {
            Text(
                AppLanguage.text("Chọn mẫu có sẵn hoặc lưu nhiệm vụ, ghi chú và 4 trường tuỳ biến. GPS luôn lấy mới.")
            )
            .font(.caption)
            .foregroundColor(.secondary)

            TextField(AppLanguage.text("Tên mẫu ghi chú"), text: $name)

            Button(AppLanguage.text("Lưu nội dung hiện tại thành mẫu")) {
                let title = String(
                    name.trimmingCharacters(
                        in: .whitespacesAndNewlines
                    ).prefix(60)
                )

                guard !title.isEmpty else {
                    feedback = AppLanguage.text("Hãy nhập tên mẫu.")
                    return
                }

                let values = NotePreset.currentValues()

                guard values.values.contains(where: {
                    !$0.trimmingCharacters(
                        in: .whitespacesAndNewlines
                    ).isEmpty
                }) else {
                    feedback = AppLanguage.text("Hãy nhập nội dung trước khi lưu mẫu.")
                    return
                }

                guard presets.count < maxPresets else {
                    feedback = AppLanguage.text("Đã đủ 20 mẫu.")
                    return
                }

                guard !presets.contains(where: {
                    $0.name.caseInsensitiveCompare(title)
                        == .orderedSame
                }) else {
                    feedback = AppLanguage.text("Tên mẫu đã tồn tại.")
                    return
                }

                let newPreset = NotePreset(name: title, values: values)
                let saved = save(presets + [newPreset])

                if saved {
                    name = ""
                    feedback = AppLanguage.text("Đã lưu mẫu.")
                } else {
                    feedback = AppLanguage.text("Không lưu được mẫu. Vui lòng thử lại.")
                }
            }

            ForEach(presets) { preset in
                HStack {
                    Button(preset.name) {
                        use(preset)
                    }
                    .buttonStyle(.borderless)

                    Spacer()

                    Menu {
                        Button(AppLanguage.text("Dùng mẫu")) {
                            use(preset)
                        }

                        Button(AppLanguage.text("Cập nhật từ nội dung hiện tại")) {
                            var items = presets
                            if let i = items.firstIndex(where: {
                                $0.id == preset.id
                            }) {
                                items[i].values =
                                    NotePreset.currentValues()
                                save(items)
                                feedback = AppLanguage.text("Đã cập nhật mẫu.")
                            }
                        }

                        Button(AppLanguage.text("Đổi tên theo ô tên mẫu")) {
                            let title = String(
                                name.trimmingCharacters(
                                    in: .whitespacesAndNewlines
                                ).prefix(60)
                            )

                            guard !title.isEmpty else {
                                feedback = AppLanguage.text("Nhập tên mới trước.")
                                return
                            }

                            guard !presets.contains(where: {
                                $0.id != preset.id &&
                                $0.name.caseInsensitiveCompare(title)
                                    == .orderedSame
                            }) else {
                                feedback = AppLanguage.text("Tên mẫu đã tồn tại.")
                                return
                            }

                            var items = presets
                            if let i = items.firstIndex(where: {
                                $0.id == preset.id
                            }) {
                                items[i].name = title
                                save(items)
                                name = ""
                                feedback = AppLanguage.text("Đã đổi tên.")
                            }
                        }

                        Button(
                            AppLanguage.text("Xoá mẫu"),
                            role: .destructive
                        ) {
                            save(presets.filter {
                                $0.id != preset.id
                            })
                        }
                    } label: {
                        Image(systemName: "ellipsis.circle")
                    }
                }
            }

            if !feedback.isEmpty {
                Text(feedback)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        } header: {
            Text(AppLanguage.text("Mẫu ghi chú ({0}/{1})", String(describing: (presets.count)), String(describing: (maxPresets))))
        }
        .onAppear {
            installDefaultsIfNeeded()
        }
    }
}

struct StampBackgroundSection: View {
    var logoRevision = 0
    @StateObject private var model = CameraModel()
    @AppStorage(StampPreferences.sizeKey) private var displaySize = StampPreferences.defaultSize
    @AppStorage(StampPreferences.logoOpacityKey) private var logoOpacity = 1.0
    @AppStorage(StampPreferences.removeLogoBackgroundKey) private var removeWhite = false
    @AppStorage("logoOn") private var logoOn = false
    @AppStorage(StampPreferences.backgroundKey)
    private var hex = StampPreferences.defaultHex

    @AppStorage(StampPreferences.opacityKey)
    private var opacity = StampPreferences.defaultOpacity

    private var selectedColor: Binding<Color> {
        Binding(
            get: {
                Color(uiColor: UIColor(stampHex: hex))
            },
            set: {
                hex = UIColor($0).stampHex
            }
        )
    }

    var body: some View {
        Section(AppLanguage.text("Kích thước và nền khung dấu")) {
            Text(AppLanguage.text("Cỡ dấu: {0}%", String(describing: (Int(min(1.2, max(0.5, displaySize)) * 100)))))
                .accessibilityIdentifier("stamp-size-label")
            HStack {
                Button(AppLanguage.text("Rất nhỏ")) { displaySize = 0.5 }
                Spacer()
                Button(AppLanguage.text("Gọn")) { displaySize = StampPreferences.defaultSize }
                Spacer()
                Button(AppLanguage.text("Lớn")) { displaySize = 1.0 }
            }.buttonStyle(.borderless)
            Slider(value: $displaySize, in: 0.5...1.2, step: 0.05) {
                Text(AppLanguage.text("Kích thước dấu"))
            } minimumValueLabel: { Text(AppLanguage.text("Nhỏ")) } maximumValueLabel: { Text(AppLanguage.text("Lớn")) }
            Text(AppLanguage.text("Chỉnh trực tiếp theo hình xem thử. Khung tự mở ngang khi có nhiều nội dung."))
                .font(.caption).foregroundColor(.secondary)
            ColorPicker(
                AppLanguage.text("Màu nền"),
                selection: selectedColor,
                supportsOpacity: false
            )

            Text(
                AppLanguage.text("Độ đậm nền: {0}%", String(describing: (Int(min(1, max(0, opacity)) * 100))))
            )

            Slider(
                value: $opacity,
                in: 0...1,
                step: 0.05
            )

            Text(AppLanguage.text("0%: nền trong suốt hoàn toàn. 100%: nền đậm hoàn toàn."))
                .font(.caption)
                .foregroundColor(.secondary)

            let sample = model.previewLines(Date())
            Image(uiImage: model.overlayImage(CGSize(width: 900, height: 1200), sample))
                .resizable().scaledToFit()
                .background(LinearGradient(colors: [.gray, .blue.opacity(0.35), .black], startPoint: .topLeading, endPoint: .bottomTrailing))
                .frame(height: 260).frame(maxWidth: .infinity)
                .accessibilityLabel(AppLanguage.text("Xem thử khung dấu"))
            Text(logoOn ? AppLanguage.text("Logo: {0}% độ đậm{1}", String(describing: (Int(min(1, max(0, logoOpacity)) * 100))), String(describing: (removeWhite ? AppLanguage.text(" · Đã ẩn nền trắng") : ""))) : AppLanguage.text("Logo đang tắt"))
                .font(.caption).foregroundColor(.secondary)

            Button(AppLanguage.text("Khôi phục nền mặc định")) {
                hex = StampPreferences.defaultHex
                opacity = StampPreferences.defaultOpacity
            }
            Button(AppLanguage.text("Khôi phục cỡ dấu gọn")) { displaySize = StampPreferences.defaultSize }
        }
    }
}
