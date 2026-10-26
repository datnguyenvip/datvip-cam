import UIKit

extension UIImage {
    /// Removes only fully transparent margins; opaque white pixels are left intact.
    func trimmingTransparentMargins() -> UIImage {
        let image = normalized()
        guard let cg = image.cgImage else { return image }
        let width = cg.width, height = cg.height
        var pixels = [UInt8](repeating: 0, count: width * height * 4)
        let drawn = pixels.withUnsafeMutableBytes { buffer -> Bool in
            guard let context = CGContext(data: buffer.baseAddress, width: width, height: height,
                bitsPerComponent: 8, bytesPerRow: width * 4, space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGBitmapInfo.byteOrder32Big.rawValue | CGImageAlphaInfo.premultipliedLast.rawValue) else { return false }
            context.draw(cg, in: CGRect(x: 0, y: 0, width: CGFloat(width), height: CGFloat(height)))
            return true
        }
        guard drawn else { return image }
        var left = width, top = height, right = -1, bottom = -1
        for y in 0..<height {
            for x in 0..<width where pixels[(y * width + x) * 4 + 3] != 0 {
                left = min(left, x); right = max(right, x); top = min(top, y); bottom = max(bottom, y)
            }
        }
        guard right >= left, bottom >= top else { return image }
        guard left != 0 || top != 0 || right != width - 1 || bottom != height - 1 else { return image }
        guard let cropped = cg.cropping(to: CGRect(x: CGFloat(left), y: CGFloat(top), width: CGFloat(right - left + 1), height: CGFloat(bottom - top + 1))) else { return image }
        return UIImage(cgImage: cropped)
    }
}

enum LogoImage {
    static func load() -> UIImage? {
        UIImage(contentsOfFile: CameraModel.logoURL.path)?.trimmingTransparentMargins()
    }

    static func displaySize(_ image: UIImage, unit: CGFloat) -> CGSize {
        let scale = min(24 * unit / max(image.size.width, 1), 9 * unit / max(image.size.height, 1))
        return CGSize(width: image.size.width * scale, height: image.size.height * scale)
    }
}
