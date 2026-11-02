import XCTest
import UIKit
import Vision

final class StampTests: XCTestCase {
    private var saved: [String: Any] = [:]
    private let keys = NotePreset.keys + ["addr", "time", "gps", "alt", "hdg", "logoOn", "wmOn", "wm",
                                          StampPreferences.backgroundKey, StampPreferences.opacityKey]

    override func setUp() {
        super.setUp()
        for key in keys {
            saved[key] = UserDefaults.standard.object(forKey: key)
            UserDefaults.standard.removeObject(forKey: key)
        }
        UserDefaults.standard.set(false, forKey: "wmOn")
    }

    override func tearDown() {
        for key in keys {
            if let value = saved[key] { UserDefaults.standard.set(value, forKey: key) }
            else { UserDefaults.standard.removeObject(forKey: key) }
        }
        super.tearDown()
    }

    private func sample() -> StampLines {
        StampLines(head: "22:58:52", date: "01/11/2026 (GMT+7)", project: "TASK ALPHA",
                   address: "GPS DELTA", custom: ["ANCS: TEAM ECHO", "Địa điểm: PLACE BRAVO"], rows: [])
    }

    func testVietnameseLabelsAndEveryCustomSlot() {
        let model = CameraModel()
        model.autoAddr = "GPS unchanged"
        for slot in 1...4 {
            for label in ["Địa điểm", "ĐỊA ĐIỂM", "Dia diem", "đia diem", "  Địa   điểm \n",
                          "Địa điểm".decomposedStringWithCanonicalMapping] {
                for i in 1...4 {
                    UserDefaults.standard.set("ANCS", forKey: "f\(i)l")
                    UserDefaults.standard.set("Team \(i)", forKey: "f\(i)v")
                }
                UserDefaults.standard.set(label, forKey: "f\(slot)l")
                UserDefaults.standard.set("Place: value stays intact", forKey: "f\(slot)v")
                let content = model.lines(Date(), nil)
                XCTAssertEqual(content.orderedBody.first?.role, .place)
                XCTAssertEqual(content.orderedBody.filter { $0.role == .place }.count, 1)
                XCTAssertEqual(content.orderedBody.first?.text, "\(label): Place: value stays intact")
                XCTAssertEqual(content.address, "GPS unchanged")
                XCTAssertEqual(content.orderedBody.filter { $0.role == .custom }.count, 3)
            }
        }
        XCTAssertFalse(StampLines.isPlace("Ghi chú: Địa điểm: elsewhere"))
        XCTAssertFalse(StampLines.isPlace("Địa điểm"))
    }

    func testOrderAndOptionalFields() {
        var content = sample()
        XCTAssertEqual(content.orderedBody.map(\.role), [.place, .time, .date, .address, .custom])
        XCTAssertEqual(content.orderedBody.filter { $0.text.contains("PLACE BRAVO") }.count, 1)
        content.custom = ["ANCS: TEAM ECHO"]
        XCTAssertEqual(content.orderedBody.map(\.role), [.time, .date, .address, .custom])
        content.head = nil; content.date = nil
        XCTAssertEqual(content.orderedBody.map(\.role), [.address, .custom])
        content.address = nil
        XCTAssertEqual(content.orderedBody.map(\.role), [.custom])
    }

    func testPresetSwitchAndEditsPreserveGPSAndStoredPresets() {
        let model = CameraModel()
        model.autoAddr = "175, Đường Số 6, P. Linh Xuân, Thành phố Hồ Chí Minh"
        let stored = UserDefaults.standard.object(forKey: "noteTemplates") as? String
        NotePreset.builtIn[0].apply()
        XCTAssertEqual(model.lines(Date(), nil).orderedBody.first?.role, .place)
        UserDefaults.standard.set("Địa điểm mới", forKey: "f1v")
        XCTAssertEqual(model.lines(Date(), nil).orderedBody.first?.text, "Địa điểm: Địa điểm mới")
        NotePreset.builtIn[1].apply()
        XCTAssertFalse(model.lines(Date(), nil).orderedBody.contains { $0.role == .place })
        NotePreset.builtIn[0].apply()
        XCTAssertEqual(model.lines(Date(), nil).address, model.autoAddr)
        XCTAssertEqual(UserDefaults.standard.object(forKey: "noteTemplates") as? String, stored)
    }

    private func recognize(_ image: UIImage) throws -> [(String, CGRect)] {
        // Composite on black: Vision must see white text rather than transparent pixels.
        let format = UIGraphicsImageRendererFormat(); format.scale = 1
        let opaque = UIGraphicsImageRenderer(size: image.size, format: format).image { _ in
            UIColor.black.setFill(); UIRectFill(CGRect(origin: .zero, size: image.size))
            image.draw(at: .zero)
        }
        let request = VNRecognizeTextRequest()
        request.recognitionLevel = .accurate
        request.recognitionLanguages = ["en-US"]
        request.usesLanguageCorrection = false
        try VNImageRequestHandler(cgImage: XCTUnwrap(opaque.cgImage), options: [:]).perform([request])
        return (request.results ?? []).compactMap { observation in
            observation.topCandidates(1).first.map { ($0.string, observation.boundingBox) }
        }
    }

    func testRenderedOrderWithLogoOnOffAndLongLines() throws {
        let model = CameraModel()
        XCTAssertNotNil(LogoImage.load(), "Bundled default logo must be available on a fresh install")
        for logoOn in [true, false] {
            UserDefaults.standard.set(logoOn, forKey: "logoOn")
            for landscape in [false, true] {
                for long in [false, true] {
                    var content = sample()
                    if long {
                        content.custom[1] += String(repeating: " long location text", count: 12)
                        content.address! += String(repeating: " long GPS street address", count: 12)
                    }
                    let size = landscape ? CGSize(width: 1600, height: 1200) : CGSize(width: 1200, height: 1600)
                    let image = model.overlayImage(size, content)
                    let attachment = XCTAttachment(image: image)
                    attachment.name = "stamp-logo-\(logoOn)-landscape-\(landscape)-long-\(long)"
                    attachment.lifetime = .keepAlways
                    add(attachment)
                    let observations = try recognize(image)
                    let markers = ["TASK ALPHA", "PLACE BRAVO", "22:58:52", "01/11/2026", "GPS DELTA", "TEAM ECHO"]
                    var previousY: CGFloat = 2
                    for marker in markers {
                        let match = try XCTUnwrap(observations.first { $0.0.contains(marker) }, "Missing \(marker): \(observations)")
                        XCTAssertLessThan(match.1.midY, previousY, "Wrong visual order for \(marker)")
                        previousY = match.1.midY
                    }
                }
            }
        }
    }

    func testPhotoAndVideoOverlayMatchAndBackgroundChanges() throws {
        let model = CameraModel()
        let size = CGSize(width: 1200, height: 1600)
        let format = UIGraphicsImageRendererFormat(); format.scale = 1
        let clear = UIGraphicsImageRenderer(size: size, format: format).image { _ in }
        let overlay = model.overlayImage(size, sample())
        XCTAssertEqual(model.stamp(clear, sample()).pngData(), overlay.pngData())
        UserDefaults.standard.set("1234AB", forKey: StampPreferences.backgroundKey)
        UserDefaults.standard.set(0.4, forKey: StampPreferences.opacityKey)
        XCTAssertNotEqual(model.overlayImage(size, sample()).pngData(), overlay.pngData())
    }

    func testCustomLogoTakesPriorityAndDefaultReturns() throws {
        let url = CameraModel.logoURL
        let original = try? Data(contentsOf: url)
        defer {
            if let original = original { try? original.write(to: url) }
            else { try? FileManager.default.removeItem(at: url) }
        }
        try? FileManager.default.removeItem(at: url)
        XCTAssertNotNil(LogoImage.load())
        let format = UIGraphicsImageRendererFormat(); format.scale = 1
        let custom = UIGraphicsImageRenderer(size: CGSize(width: 80, height: 40), format: format).image { _ in
            UIColor.red.setFill(); UIRectFill(CGRect(x: 0, y: 0, width: 80, height: 40))
        }
        try XCTUnwrap(custom.pngData()).write(to: url)
        XCTAssertEqual(LogoImage.load()?.size, custom.size)
        try FileManager.default.removeItem(at: url)
        XCTAssertNotNil(LogoImage.load())
    }
}
