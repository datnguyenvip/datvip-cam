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
        app.launchArguments = ["-interfaceLanguage", language,
            "-addr", language == "vi" ? "Địa điểm mẫu" : "Sample location",
            "-proj", language == "vi" ? "Khảo sát công trình" : "Site inspection",
            "-f1l", language == "vi" ? "Khu vực" : "Area",
            "-f1v", language == "vi" ? "Lối vào phía Bắc" : "North entrance",
            "-logoOn", "NO"]
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
    func testVietnameseSettingsAndStampControls() { capture("vi") }
    func testEnglishSettingsAndStampControls() { capture("en") }
}
