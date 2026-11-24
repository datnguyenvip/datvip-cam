import XCTest
import UIKit

final class CameraToolsTests: XCTestCase {
    func testCompassWrapAndTwentyFourMountains() {
        XCTAssertEqual(CompassMath.mountain(359.9),"Tý")
        XCTAssertEqual(CompassMath.mountain(-1),"Tý")
        XCTAssertEqual(CompassMath.mountain(7.5),"Quý")
        XCTAssertEqual(CompassMath.mountain(180),"Ngọ")
        XCTAssertEqual(CompassMath.normalize(-180),180)
    }
    func testStyleClampsAndIsFrozenByValue() {
        let captured = LevelStyle(color:0xFFFF0000,length:0.3)
        var current=captured;current.color=0xFF00FF00;current.length=1
        XCTAssertEqual(captured.color,0xFFFF0000)
        XCTAssertEqual(captured.fraction,0.3)
        XCTAssertEqual(LevelStyle(length:-1).fraction,0.15)
        XCTAssertEqual(LevelStyle(length:.nan).fraction,1)
    }
    func testCustomLineColorAndLengthRender() {
        let format=UIGraphicsImageRendererFormat();format.scale=1
        let image=UIGraphicsImageRenderer(size:CGSize(width:360,height:480),format:format).image { context in
            LevelCrosshairRenderer.draw(context.cgContext,size:CGSize(width:360,height:480),tilt:0,style:LevelStyle(color:0xFF00FFFF,length:0.4))
        }
        let attachment=XCTAttachment(image:image);attachment.name="custom-cyan-crosshair";attachment.lifetime = .keepAlways;add(attachment)
        XCTAssertNotNil(image.cgImage)
        XCTAssertEqual(image.size.width,360)
    }
}
