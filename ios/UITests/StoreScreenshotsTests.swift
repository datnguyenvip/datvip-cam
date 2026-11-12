import XCTest

final class StoreScreenshotsTests: XCTestCase {
    override func setUp() { continueAfterFailure = false }

    private func capture(_ language: String) {
        addUIInterruptionMonitor(withDescription: "Optional system permissions") { alert in
            let deny = alert.buttons["Don't Allow"]
            if deny.exists { deny.tap(); return true }
            return false
        }
        let app = XCUIApplication()
        app.launchEnvironment["CAMERA_HT_UI_TESTING"] = "1"
        app.launchArguments = ["-interfaceLanguage", language,
            "-addr", language == "vi" ? "Địa điểm mẫu" : "Sample location",
            "-proj", language == "vi" ? "Khảo sát công trình" : "Site inspection",
            "-f1l", language == "vi" ? "Khu vực" : "Area",
            "-f1v", language == "vi" ? "Lối vào phía Bắc" : "North entrance"]
        app.launch()
        let settings = app.buttons["gearshape"]
        XCTAssertTrue(settings.waitForExistence(timeout: 30))
        settings.tap()
        let done = app.buttons[language == "vi" ? "Xong" : "Done"]
        XCTAssertTrue(done.waitForExistence(timeout: 20))
        func snapshot(_ name: String) {
            let image = XCTAttachment(screenshot: app.screenshot())
            image.name = "app-store-\(language)-\(name)"; image.lifetime = .keepAlways
            add(image)
        }
        snapshot("settings")
        let size = app.staticTexts["stamp-size-label"]
        for _ in 0..<20 {
            if size.isHittable { break }
            app.swipeUp()
        }
        XCTAssertTrue(size.isHittable)
        snapshot("frame-background")
        done.tap()
        app.terminate()
    }
    func testCameraControlsFitBothSensorLandscapeLayouts() {
        for angle in ["90", "-90"] {
            let app = XCUIApplication()
            app.launchEnvironment["CAMERA_HT_UI_TESTING"] = "1"
            app.launchEnvironment["CAMERA_HT_TEST_ANGLE"] = angle
            app.launchArguments = ["-interfaceLanguage", "en", "-compositionGuides", "YES"]
            app.launch()
            let system = XCUIApplication(bundleIdentifier: "com.apple.springboard")
            for _ in 0..<4 {
                let alert = system.alerts.firstMatch
                if !alert.waitForExistence(timeout: 2) { break }
                let labels = ["Allow While Using App", "Allow Once", "OK", "Don’t Allow", "Don't Allow"]
                if let label = labels.first(where: { alert.buttons[$0].exists }) {
                    alert.buttons[label].tap()
                } else {
                    XCTFail("Unexpected permission alert: \(alert.label)")
                    return
                }
            }
            XCTAssertFalse(system.alerts.firstMatch.exists)
            let settings = app.buttons["gearshape"]
            XCTAssertTrue(settings.waitForExistence(timeout: 30))
            let bounds = app.frame
            for name in ["gearshape", "photo.on.rectangle", "arrow.triangle.2.circlepath.camera"] {
                let button = app.buttons[name]
                XCTAssertTrue(button.isHittable)
                XCTAssertTrue(bounds.contains(button.frame), "Control clipped at sensor angle \(angle): \(name)")
            }
            let image = XCTAttachment(screenshot: app.screenshot())
            image.name = "camera-sensor-layout-\(angle)"; image.lifetime = .keepAlways
            add(image)
            app.terminate()
        }
    }
    func testVietnameseSettingsAndStampControls() { capture("vi") }
    func testEnglishSettingsAndStampControls() { capture("en") }
}
