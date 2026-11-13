import XCTest

final class StoreScreenshotsTests: XCTestCase {
    override func setUp() { continueAfterFailure = false }

    func testLevelToggleInBothLanguagesAndAllLayouts() {
        for (language, angle) in [("vi", "0"), ("en", "90"), ("vi", "-90")] {
            let app = XCUIApplication()
            app.launchEnvironment["CAMERA_HT_UI_TESTING"] = "1"
            app.launchEnvironment["CAMERA_HT_TEST_ANGLE"] = angle
            // A launch argument would override UserDefaults even after tapping,
            // preventing AppStorage from exercising its real persisted value.
            app.launchArguments = ["-interfaceLanguage", language]
            app.launch()
            dismissPermissionAlerts()
            let button = app.buttons["level-crosshair"]
            XCTAssertTrue(button.waitForExistence(timeout: 20), app.debugDescription)
            XCTAssertTrue(button.isHittable)
            XCTAssertEqual(button.label, language == "vi" ? "Thước cân bằng" : "Level Crosshair")
            func waitForSelected(_ selected: Bool) {
                let condition = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in button.isSelected == selected }, object: button)
                XCTAssertEqual(XCTWaiter.wait(for: [condition], timeout: 10), .completed,
                    "Expected selected=\(selected); actual selected=\(button.isSelected), value=\(String(describing: button.value)); \(button.debugDescription)")
            }
            if button.isSelected { button.tap(); waitForSelected(false) }
            for _ in 0..<3 {
                button.tap()
                waitForSelected(true)
                button.tap()
                waitForSelected(false)
            }
            button.tap()
            let image = XCTAttachment(screenshot: app.screenshot())
            image.name = "level-enabled-\(language)-\(angle)"; image.lifetime = .keepAlways
            add(image)
            app.terminate()
            app.launchArguments = ["-interfaceLanguage", language]
            app.launch()
            XCTAssertTrue(button.waitForExistence(timeout: 20))
            waitForSelected(true)
            button.tap()
            waitForSelected(false)
            app.terminate()
        }
    }

    private func dismissPermissionAlerts() {
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
    }

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
            dismissPermissionAlerts()
            let settings = app.buttons["gearshape"]
            XCTAssertTrue(settings.waitForExistence(timeout: 30))
            let bounds = app.frame
            let viewport = app.otherElements["camera-viewport"]
            XCTAssertTrue(viewport.waitForExistence(timeout: 10))
            // In a portrait-locked host, landscape preview height maps to physical width.
            // This fails for the old bottom-toolbar layout that shrank the viewfinder.
            XCTAssertGreaterThanOrEqual(viewport.frame.width, bounds.width * 0.9)
            XCTAssertEqual(viewport.frame.height / viewport.frame.width, 4.0 / 3.0, accuracy: 0.03)
            for name in ["gearshape", "photo.on.rectangle", "arrow.triangle.2.circlepath.camera", "camera-shutter"] {
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
    func testPreviewSurvivesRepeatedSensorRotation() {
        let app = XCUIApplication()
        app.launchEnvironment["CAMERA_HT_UI_TESTING"] = "1"
        app.launchEnvironment["CAMERA_HT_TEST_ANGLE"] = "0"
        app.launchEnvironment["CAMERA_HT_TEST_ROTATION_SEQUENCE"] = "1"
        app.launchArguments = ["-interfaceLanguage", "en"]
        app.launch()
        dismissPermissionAlerts()
        let preview = app.descendants(matching: .any).matching(identifier: "camera-live-preview").firstMatch
        XCTAssertTrue(preview.waitForExistence(timeout: 30))
        let originalID = preview.value as? String
        XCTAssertNotNil(originalID)
        let rotate = app.buttons["camera-test-rotate"]
        for angle in [90, -90, 0, 90, -90, 0] {
            rotate.tap()
            expectation(for: NSPredicate(format: "value == %@", String(angle)), evaluatedWith: rotate)
            waitForExpectations(timeout: 10)
            let current = app.descendants(matching: .any).matching(identifier: "camera-live-preview").firstMatch
            XCTAssertTrue(current.exists)
            XCTAssertEqual(current.value as? String, originalID, "Preview recreated after turning to \(angle)")
            XCTAssertTrue(app.buttons["camera-shutter"].isHittable)
        }
        app.terminate()
    }
    func testVietnameseSettingsAndStampControls() { capture("vi") }
    func testEnglishSettingsAndStampControls() { capture("en") }
}
