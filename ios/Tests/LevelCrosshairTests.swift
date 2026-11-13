import XCTest
import UIKit

final class LevelCrosshairTests: XCTestCase {
    func testProjectedGravityAcrossAllOrientations() {
        for orientation in [0.0, 90.0, -90.0, 180.0] {
            for residual in [-20.0, 0, 20.0] {
                var filter = CameraOrientationFilter()
                let radians = (orientation + residual) * .pi / 180
                for index in 0..<60 {
                    filter.update(x: -sin(radians), y: -cos(radians), time: Double(index) * 0.02)
                }
                XCTAssertEqual(filter.angle, orientation)
                XCTAssertEqual(filter.tilt ?? 999, residual, accuracy: 0.01)
            }
        }
    }
    func testFlatPhoneInvalidatesImmediatelyAndRecovers() {
        var filter = CameraOrientationFilter()
        filter.update(x: 0, y: -1, time: 0)
        filter.update(x: 0.01, y: 0.01, time: 0.02)
        XCTAssertNil(filter.tilt)
        filter.update(x: 0, y: -1, time: 0.04)
        XCTAssertEqual(filter.tilt, 0)
        filter.update(x: .nan, y: -1, time: 0.06)
        XCTAssertNil(filter.tilt)
    }
    func testTimeBasedFilterAndFastTilt() {
        func response(_ dt: Double) -> Double {
            var filter = CameraOrientationFilter()
            filter.update(x: 0, y: -1, time: 0)
            for index in 1...Int((0.2 / dt).rounded()) {
                filter.update(x: -sin(0.2), y: -cos(0.2), time: Double(index) * dt)
            }
            return filter.tilt ?? 999
        }
        XCTAssertEqual(response(0.01), response(0.05), accuracy: 0.05)
        XCTAssertGreaterThan(response(0.02), 10)
    }
    func testRenderingAtPreviewAndExportResolutions() {
        for size in [CGSize(width: 360, height: 480), CGSize(width: 480, height: 360),
                     CGSize(width: 1080, height: 1440), CGSize(width: 1440, height: 1080)] {
            let format = UIGraphicsImageRendererFormat(); format.scale = 1
            let blank = UIGraphicsImageRenderer(size: size, format: format).image { _ in }
            let result = LevelCrosshairRenderer.render(blank, tilt: 20)
            XCTAssertEqual(result.size, size)
            let data = result.cgImage!.dataProvider!.data!
            let bytes = CFDataGetBytePtr(data)!
            let stride = result.cgImage!.bytesPerRow
            let pixelBytes = result.cgImage!.bitsPerPixel / 8
            let unit = min(size.width, size.height) / 360
            for degrees in [20.0, 110, 200, 290] {
                let radians = degrees * .pi / 180
                let x = Int((size.width / 2 + cos(radians) * 80 * unit).rounded())
                let y = Int((size.height / 2 + sin(radians) * 80 * unit).rounded())
                let offset = y * stride + x * pixelBytes
                XCTAssertTrue((0..<pixelBytes).contains { bytes[offset + $0] != 0 })
            }
            let attachment = XCTAttachment(image: result)
            attachment.name = "Level-20deg-\(Int(size.width))x\(Int(size.height))"
            attachment.lifetime = .keepAlways; add(attachment)
        }
    }
}
