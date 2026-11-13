import SwiftUI
import UIKit

enum LevelCrosshairRenderer {
    /// Coordinates are in the upright, cropped logical preview / image.
    static func draw(_ context: CGContext, size: CGSize, tilt: Double) {
        guard tilt.isFinite, size.width > 0, size.height > 0 else { return }
        let unit = min(size.width, size.height) / 360
        context.saveGState()
        context.clip(to: CGRect(origin: .zero, size: size))
        context.translateBy(x: size.width / 2, y: size.height / 2)
        context.saveGState()
        context.rotate(by: CGFloat(tilt * .pi / 180))
        let span = hypot(size.width, size.height)
        for outline in [true, false] {
            context.setStrokeColor((outline ? UIColor.black : UIColor.yellow).cgColor)
            context.setLineWidth((outline ? 3 : 1.2) * unit)
            context.move(to: CGPoint(x: -span, y: 0)); context.addLine(to: CGPoint(x: span, y: 0))
            context.move(to: CGPoint(x: 0, y: -span)); context.addLine(to: CGPoint(x: 0, y: span))
            context.strokePath()
        }
        context.restoreGState()
        let label = String(format: "%+.1f°", locale: Locale(identifier: "en_US_POSIX"), tilt) as NSString
        let attributes: [NSAttributedString.Key: Any] = [.font: UIFont.boldSystemFont(ofSize: 13 * unit), .foregroundColor: UIColor.yellow]
        let point = CGPoint(x: 12 * unit, y: -27 * unit)
        let textSize = label.size(withAttributes: attributes)
        context.setFillColor(UIColor.black.withAlphaComponent(0.8).cgColor)
        context.fill(CGRect(origin: CGPoint(x: point.x - 4*unit, y: point.y), size: CGSize(width: textSize.width + 8*unit, height: 19*unit)))
        UIGraphicsPushContext(context)
        label.draw(at: point, withAttributes: attributes)
        UIGraphicsPopContext()
        context.restoreGState()
    }
    static func render(_ image: UIImage, tilt: Double) -> UIImage {
        let format = UIGraphicsImageRendererFormat(); format.scale = 1
        return UIGraphicsImageRenderer(size: image.size, format: format).image { context in
            image.draw(at: .zero)
            draw(context.cgContext, size: image.size, tilt: tilt)
        }
    }
}

struct LevelCrosshairView: UIViewRepresentable {
    let tilt: Double?
    func makeUIView(context: Context) -> LevelCanvas {
        let view = LevelCanvas(); view.backgroundColor = .clear; view.isOpaque = false; view.isUserInteractionEnabled = false
        view.isAccessibilityElement = false; return view
    }
    func updateUIView(_ view: LevelCanvas, context: Context) { view.tilt = tilt; view.setNeedsDisplay() }
}
final class LevelCanvas: UIView {
    var tilt: Double?
    override func draw(_ rect: CGRect) {
        guard let context = UIGraphicsGetCurrentContext(), let tilt = tilt else { return }
        LevelCrosshairRenderer.draw(context, size: bounds.size, tilt: tilt)
    }
}
