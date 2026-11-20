import XCTest

final class CameraOrientationTests: XCTestCase {
    func testTurnsBothWaysAndReturnsToPortrait() {
        var filter = CameraOrientationFilter()
        var time = 0.0
        func feed(_ x: Double, _ y: Double) {
            for _ in 0..<40 { time += 0.05; filter.update(x: x, y: y, time: time) }
        }
        feed(0, -1); XCTAssertEqual(filter.angle, 0)
        feed(-1, 0); XCTAssertEqual(filter.angle, 90)
        XCTAssertEqual(filter.tilt ?? 999, 0, accuracy: 0.1)
        feed(1, 0); XCTAssertEqual(filter.angle, -90)
        feed(0, -1); XCTAssertEqual(filter.angle, 0)
    }
    func testBoundaryJitterAndFlatPhoneDoNotRotate() {
        var filter = CameraOrientationFilter()
        for i in 0..<100 {
            filter.update(x: -0.7, y: i % 2 == 0 ? -0.69 : -0.72, time: Double(i) * 0.05)
        }
        XCTAssertEqual(filter.angle, 0)
        for i in 100..<160 { filter.update(x: 0, y: 0, time: Double(i) * 0.05) }
        XCTAssertEqual(filter.angle, 0); XCTAssertNil(filter.tilt)
    }
    func testBriefTurnDoesNotCommitOrientation() {
        var filter = CameraOrientationFilter()
        filter.update(x: -1, y: 0, time: 10)
        filter.update(x: -1, y: 0, time: 10.1)
        XCTAssertEqual(filter.angle, 0)
        filter.update(x: -1, y: 0, time: 10.3)
        XCTAssertEqual(filter.angle, 90)
    }
}
