
import UIKit

extension UIImage {
    /// Remove only near-white pixels connected to the outer edge, preserving
    /// enclosed white details. Work on a copy; never overwrite the user's logo.
    func removingWhiteBackdrop() -> UIImage {
        let original = normalized()
        let scale = min(1, 1024 / max(original.size.width, original.size.height))
        let format = UIGraphicsImageRendererFormat(); format.scale = 1
        let image = scale < 1 ? UIGraphicsImageRenderer(
            size: CGSize(width: original.size.width * scale, height: original.size.height * scale), format: format
        ).image { _ in original.draw(in: CGRect(x: 0, y: 0, width: original.size.width * scale, height: original.size.height * scale)) } : original
        guard let cg = image.cgImage else { return image }
        let width = cg.width, height = cg.height
        var pixels = [UInt8](repeating: 0, count: width * height * 4)
        let info = CGBitmapInfo.byteOrder32Big.rawValue | CGImageAlphaInfo.premultipliedLast.rawValue
        let drawn = pixels.withUnsafeMutableBytes { buffer -> Bool in
            guard let context = CGContext(data: buffer.baseAddress, width: width, height: height,
                                          bitsPerComponent: 8, bytesPerRow: width * 4,
                                          space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: info) else { return false }
            context.draw(cg, in: CGRect(x: 0, y: 0, width: width, height: height))
            return true
        }
        guard drawn else { return image }
        var visited = [UInt8](repeating: 0, count: width * height)
        var queue: [Int] = []
        func enqueue(_ index: Int) {
            guard visited[index] == 0 else { return }
            visited[index] = 1
            let pixel = index * 4
            let alpha = UInt16(pixels[pixel + 3])
            if alpha == 0 || (0...2).allSatisfy({ UInt16(pixels[pixel + $0]) * 255 >= 230 * alpha }) {
                queue.append(index)
            }
        }
        for x in 0..<width { enqueue(x); enqueue((height - 1) * width + x) }
        for y in 0..<height { enqueue(y * width); enqueue(y * width + width - 1) }
        var cursor = 0
        while cursor < queue.count {
            let index = queue[cursor]; cursor += 1
            let pixel = index * 4
            for channel in 0...3 { pixels[pixel + channel] = 0 }
            let x = index % width, y = index / width
            if x > 0 { enqueue(index - 1) }
            if x + 1 < width { enqueue(index + 1) }
            if y > 0 { enqueue(index - width) }
            if y + 1 < height { enqueue(index + width) }
        }
        guard let provider = CGDataProvider(data: Data(pixels) as CFData),
              let cutout = CGImage(width: width, height: height, bitsPerComponent: 8, bitsPerPixel: 32,
                                   bytesPerRow: width * 4, space: CGColorSpaceCreateDeviceRGB(),
                                   bitmapInfo: CGBitmapInfo(rawValue: info), provider: provider,
                                   decode: nil, shouldInterpolate: true, intent: .defaultIntent) else { return image }
        return UIImage(cgImage: cutout).trimmingTransparentMargins()
    }
    /// Remove fully transparent margins.
    func trimmingTransparentMargins() -> UIImage {
        let image = normalized()
        guard let cg = image.cgImage else { return image }

        let width = cg.width
        let height = cg.height

        var pixels = [UInt8](
            repeating: 0,
            count: width * height * 4
        )

        let drawn = pixels.withUnsafeMutableBytes { buffer -> Bool in
            guard let context = CGContext(
                data: buffer.baseAddress,
                width: width,
                height: height,
                bitsPerComponent: 8,
                bytesPerRow: width * 4,
                space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo:
                    CGBitmapInfo.byteOrder32Big.rawValue |
                    CGImageAlphaInfo.premultipliedLast.rawValue
            ) else {
                return false
            }

            context.draw(
                cg,
                in: CGRect(
                    x: 0,
                    y: 0,
                    width: CGFloat(width),
                    height: CGFloat(height)
                )
            )

            return true
        }

        guard drawn else { return image }

        var left = width
        var top = height
        var right = -1
        var bottom = -1

        for y in 0..<height {
            for x in 0..<width {
                let alpha = pixels[(y * width + x) * 4 + 3]

                if alpha != 0 {
                    left = min(left, x)
                    right = max(right, x)
                    top = min(top, y)
                    bottom = max(bottom, y)
                }
            }
        }

        guard right >= left, bottom >= top else {
            return image
        }

        guard left != 0 ||
              top != 0 ||
              right != width - 1 ||
              bottom != height - 1 else {
            return image
        }

        guard let cropped = cg.cropping(
            to: CGRect(
                x: CGFloat(left),
                y: CGFloat(top),
                width: CGFloat(right - left + 1),
                height: CGFloat(bottom - top + 1)
            )
        ) else {
            return image
        }

        return UIImage(cgImage: cropped)
    }
}

enum LogoImage {
    private static let lock = NSLock()
    private static var cachedSignature = ""
    private static var cachedImage: UIImage?
    static func load() -> UIImage? {
        lock.lock()
        defer { lock.unlock() }
        let removeWhite = UserDefaults.standard.bool(forKey: StampPreferences.removeLogoBackgroundKey)
        let attrs = try? FileManager.default.attributesOfItem(atPath: CameraModel.logoURL.path)
        let modified = (attrs?[.modificationDate] as? Date)?.timeIntervalSince1970 ?? 0
        let signature = "\(modified)|\(attrs?[.size] ?? 0)|\(removeWhite)"
        if signature == cachedSignature { return cachedImage }
        let source = UIImage(contentsOfFile: CameraModel.logoURL.path)
        cachedImage = removeWhite ? source?.removingWhiteBackdrop() : source?.trimmingTransparentMargins()
        cachedSignature = signature
        return cachedImage
    }

    static func displaySize(
        _ image: UIImage,
        unit: CGFloat
    ) -> CGSize {
        let scale = min(
            24 * unit / max(image.size.width, 1),
            9 * unit / max(image.size.height, 1)
        )

        return CGSize(
            width: image.size.width * scale,
            height: image.size.height * scale
        )
    }
}
