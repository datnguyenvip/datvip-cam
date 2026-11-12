import XCTest
import CoreLocation

final class LanguageTests: XCTestCase {
    func testLanguageChangesLabelsWithoutChangingSavedUserData() {
        let defaults = UserDefaults.standard
        let old = [AppLanguage.key, "proj", "f1l", "f1v"].map { ($0, defaults.object(forKey: $0)) }
        defer { for (key, value) in old { if let value = value { defaults.set(value, forKey: key) } else { defaults.removeObject(forKey: key) } } }
        defaults.set("en", forKey: AppLanguage.key)
        defaults.set("Địa điểm {1}: Linh Xuân", forKey: "proj")
        defaults.set("Địa điểm", forKey: "f1l")
        defaults.set("Ngã ba đường Hoàng Cầm - đường số 5", forKey: "f1v")
        XCTAssertEqual(AppLanguage.name, "Field Camera – GPS Stamp")
        XCTAssertEqual(AppLanguage.text("Chụp ảnh"), "Take Photo")
        XCTAssertEqual(AppLanguage.text("Đang dùng mẫu: {0}", "Gốc {1}"), "Using template: Gốc {1}")
        XCTAssertEqual(AppLanguage.direction(180), "S")
        let model = CameraModel()
        let location = CLLocation(latitude: 10.879036, longitude: 106.772466)
        let stamp = model.lines(Date(timeIntervalSince1970: 1793581200), location)
        XCTAssertEqual(stamp.project, "Task: Địa điểm {1}: Linh Xuân")
        XCTAssertTrue(stamp.rows.contains { $0.contains("10.879036") && $0.contains("106.772466") })
        defaults.set("vi", forKey: AppLanguage.key)
        XCTAssertEqual(AppLanguage.text("Chụp ảnh"), "Chụp ảnh")
        XCTAssertEqual(AppLanguage.direction(180), "N")
        XCTAssertEqual(defaults.string(forKey: "f1v"), "Ngã ba đường Hoàng Cầm - đường số 5")
    }
}
