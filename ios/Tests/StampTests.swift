import XCTest
import UIKit
import Vision
import CoreLocation

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
                    let boxes = try markers.map { marker in
                        // A valid compact layout may wrap a marker across two lines.
                        var bounds: CGRect?
                        for word in marker.split(separator: " ") {
                            let match = try XCTUnwrap(observations.first { $0.0.contains(String(word)) }, "Missing \(word): \(observations)")
                            bounds = bounds.map { $0.union(match.1) } ?? match.1
                        }
                        return try XCTUnwrap(bounds)
                    }
                    XCTAssertGreaterThan(boxes[0].midY, boxes[1].midY)
                    for clockIndex in [2, 3] {
                        XCTAssertGreaterThan(boxes[1].midY, boxes[clockIndex].midY)
                        XCTAssertGreaterThan(boxes[clockIndex].midY, boxes[4].midY)
                    }
                    XCTAssertGreaterThan(boxes[4].midY, boxes[5].midY)

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
        // Hostless XCTest does not create an app's Documents directory for us.
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
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
        XCTAssertTrue(FileManager.default.fileExists(atPath: url.path))
        XCTAssertEqual(LogoImage.load()?.size, custom.size)
        try FileManager.default.removeItem(at: url)
        XCTAssertNotNil(LogoImage.load())
    }

    func testVietnameseTrafficFixture() {
        let model = CameraModel()
        NotePreset.builtIn[0].apply()
        model.autoAddr = "175, Đường Số 6, P. Linh Xuân, Thành phố Hồ Chí Minh"
        var content = model.lines(Date(timeIntervalSince1970: 1793548732), nil)
        content.head = "22:58:52"
        content.date = "Chủ Nhật, 01/11/2026 (GMT+7)"
        content.rows = ["Mã xác thực: TEST2026"]
        let image = model.overlayImage(CGSize(width: 1200, height: 1600), content)
        let attachment = XCTAttachment(image: image)
        attachment.name = "Giao-thong-Vietnamese"
        attachment.lifetime = .keepAlways
        add(attachment)
        XCTAssertEqual(content.orderedBody.map(\.role), [.place, .time, .date, .address, .custom, .detail])
    }

    func testAdaptiveCornerAndFooterGeometry() throws {
        for size in [CGSize(width: 1200, height: 1600), CGSize(width: 1600, height: 1200)] {
            for logoSize in [nil, CGSize(width: 240, height: 185), CGSize(width: 900, height: 80), CGSize(width: 80, height: 900)] as [CGSize?] {
                var content = sample()
                content.rows = ["Mã xác thực: 12345678"]
                let small = StampLayout.make(size: size, content: content, logoSize: logoSize)
                XCTAssertEqual(small.mode, .corner)
                XCTAssertLessThanOrEqual(small.rect.width, size.width / 2)
                XCTAssertLessThanOrEqual(small.rect.height, size.height / 2)
                XCTAssertEqual(small.rect.maxY, size.height - min(size.width, size.height) * 0.03, accuracy: 0.1)
                XCTAssertGreaterThan(small.rect.minX, 0)
                XCTAssertTrue(small.texts.allSatisfy { small.rect.insetBy(dx: -0.1, dy: -0.1).contains($0.rect) })
                for i in small.texts.indices {
                    for j in small.texts.indices where j > i {
                        XCTAssertFalse(small.texts[i].rect.intersects(small.texts[j].rect), "Text blocks overlap")
                    }
                }
                if let logo = small.logoRect {
                    XCTAssertTrue(small.texts.allSatisfy { !logo.intersects($0.rect) })
                }
                content.custom[1] += String(repeating: " nhiều nội dung địa điểm", count: 30)
                content.address! += String(repeating: " địa chỉ đường rất dài", count: 30)
                content.rows += (1...8).map { "Thông tin \($0): " + String(repeating: "chi tiết bổ sung ", count: 4) }
                let large = StampLayout.make(size: size, content: content, logoSize: logoSize)
                XCTAssertEqual(large.mode, .footer)
                XCTAssertGreaterThan(large.rect.width, size.width * 0.9)
                XCTAssertGreaterThanOrEqual(large.rect.minY, 0)
                XCTAssertTrue(large.texts.allSatisfy { large.rect.insetBy(dx: -0.1, dy: -0.1).contains($0.rect) })
                XCTAssertEqual(large.texts.count, content.orderedBody.count + 1, "Do not drop content to fit")
            }
        }
    }

    func testTelemetryTogglesRecalculateLayout() {
        let model = CameraModel()
        model.autoAddr = "175, Đường Số 6, P. Linh Xuân, Thành phố Hồ Chí Minh"
        NotePreset.builtIn[0].apply()
        UserDefaults.standard.set(String(repeating: "Thông tin nhiệm vụ bổ sung ", count: 10), forKey: "note")
        let location = CLLocation(coordinate: CLLocationCoordinate2D(latitude: 10.879036, longitude: 106.72466),
                                  altitude: 27, horizontalAccuracy: 6, verticalAccuracy: 3, timestamp: Date())
        model.heading = 190
        let size = CGSize(width: 1200, height: 1600)
        let logo = LogoImage.load()
        let enabled = model.lines(Date(), location)
        XCTAssertTrue(enabled.rows.contains { $0.hasPrefix("📍") })
        XCTAssertTrue(enabled.rows.contains { $0.hasPrefix("Độ cao") })
        XCTAssertTrue(enabled.rows.contains { $0.hasPrefix("Hướng") })
        let before = StampLayout.make(size: size, content: enabled, logoSize: logo?.size)
        for key in ["gps", "alt", "hdg"] { UserDefaults.standard.set(false, forKey: key) }
        let disabled = model.lines(Date(), location)
        XCTAssertFalse(disabled.rows.contains { $0.hasPrefix("📍") || $0.hasPrefix("Độ cao") || $0.hasPrefix("Hướng") })
        XCTAssertEqual(disabled.address, enabled.address, "GPS address is independent of coordinates toggle")
        let after = StampLayout.make(size: size, content: disabled, logoSize: logo?.size)
        if before.mode == after.mode { XCTAssertLessThan(after.rect.height, before.rect.height) }
        else { XCTAssertEqual(after.mode, .corner) }
    }

    func testAdaptiveEvidenceAndPreviewScale() {
        let model = CameraModel()
        NotePreset.builtIn[0].apply()
        model.autoAddr = "175, Đường Số 6, P. Linh Xuân, Thành phố Hồ Chí Minh"
        var content = model.lines(Date(), nil)
        content.head = "09:32:54"; content.date = "Thứ Hai, 02/11/2026 (GMT+7)"
        content.rows = ["Mã xác thực: 6E4D10FE"]
        for dense in [false, true] {
            var fixture = content
            if dense {
                fixture.custom += ["Nội dung: " + String(repeating: "Kiểm tra bảo vệ hiện trường. ", count: 12)]
                fixture.rows += ["📍 Vĩ độ 10.879036, Kinh độ 106.72466 (±6m)", "Độ cao 27 m", "Hướng 190° N"]
            }
            for size in [CGSize(width: 1200, height: 1600), CGSize(width: 1600, height: 1200)] {
                let image = model.overlayImage(size, fixture)
                let attachment = XCTAttachment(image: image)
                attachment.name = "adaptive-dense-\(dense)-width-\(Int(size.width))"
                attachment.lifetime = .keepAlways; add(attachment)
                let logo = LogoImage.load()?.size
                let exported = StampLayout.make(size: size, content: fixture, logoSize: logo)
                let preview = StampLayout.make(size: CGSize(width: size.width / 3, height: size.height / 3), content: fixture, logoSize: logo)
                XCTAssertEqual(exported.mode, preview.mode)
                XCTAssertEqual(exported.rect.width / size.width, preview.rect.width / (size.width / 3), accuracy: 0.01)
                XCTAssertEqual(exported.rect.height / size.height, preview.rect.height / (size.height / 3), accuracy: 0.02)
            }
        }
    }
}
