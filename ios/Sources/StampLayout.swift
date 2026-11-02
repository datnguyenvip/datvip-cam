import UIKit

/// Measured once, then painted by the shared preview/photo/video renderer.
struct StampLayout {
    enum Mode: Equatable { case corner, footer }
    struct Text {
        let value: String
        let role: StampLines.Role?
        let font: UIFont
        let color: UIColor
        var rect: CGRect
    }
    let mode: Mode
    var rect: CGRect
    var logoRect: CGRect?
    var texts: [Text]

    static func make(size: CGSize, content: StampLines, logoSize: CGSize?) -> StampLayout {
        let factor = min(size.width, size.height) / 1200
        guard factor > 0 else { return StampLayout(mode: .corner, rect: .zero, logoRect: nil, texts: []) }
        var result = resolve(size: CGSize(width: size.width / factor, height: size.height / factor),
                             content: content, logoSize: logoSize)
        let transform = CGAffineTransform(scaleX: factor, y: factor)
        result.rect = result.rect.applying(transform)
        result.logoRect = result.logoRect?.applying(transform)
        result.texts = result.texts.map {
            Text(value: $0.value, role: $0.role, font: $0.font.withSize($0.font.pointSize * factor),
                 color: $0.color, rect: $0.rect.applying(transform))
        }
        return result
    }

    private static func resolve(size: CGSize, content: StampLines, logoSize: CGSize?) -> StampLayout {
        let u = min(size.width, size.height) / 100
        let margin = 3 * u
        let availableWidth = size.width - 2 * margin
        let availableHeight = size.height - 2 * margin
        let drawingUnit = u * StampPreferences.displaySize
        let compact = measure(width: availableWidth / 2, unit: drawingUnit, scale: 1,
                              mode: .corner, content: content, logoSize: logoSize)
        var result: StampLayout
        if compact.rect.height <= availableHeight * 0.4 {
            result = compact
        } else {
            // Expand before reducing text size, so dense notes remain readable.
            var scale: CGFloat = 1
            result = measure(width: availableWidth, unit: drawingUnit, scale: scale,
                             mode: .footer, content: content, logoSize: logoSize)
            while result.rect.height > availableHeight && scale > 0.15 {
                scale *= 0.9
                result = measure(width: availableWidth, unit: drawingUnit, scale: scale,
                                 mode: .footer, content: content, logoSize: logoSize)
            }
        }
        let origin = CGPoint(x: margin, y: size.height - margin - result.rect.height)
        result.rect.origin = origin
        result.logoRect = result.logoRect?.offsetBy(dx: origin.x, dy: origin.y)
        for index in result.texts.indices {
            result.texts[index].rect = result.texts[index].rect.offsetBy(dx: origin.x, dy: origin.y)
        }
        return result
    }

    private static func measure(width: CGFloat, unit u: CGFloat, scale: CGFloat,
                                mode: Mode, content: StampLines, logoSize: CGSize?) -> StampLayout {
        let compact = mode == .corner
        let padding = (compact ? 1.2 : 2.8) * u
        let bar = 1.4 * u, gap = (compact ? 0.7 : 1.2) * u * scale
        let left = bar + padding
        let textWidth = max(1, width - left - padding)
        var texts: [Text] = []
        func font(_ role: StampLines.Role?) -> UIFont {
            let points: CGFloat
            switch role {
            case nil: points = compact ? 3 : 3.8
            case .time: points = compact ? 3.8 : 7.5
            case .date: points = compact ? 2.6 : 3.4
            case .place, .address: points = compact ? 3 : 3.4
            case .custom: points = compact ? 2.9 : 3.3
            case .detail: points = compact ? 2.8 : 3.2
            }
            let bold = role == nil || role == .time || role == .place || role == .address
            return .systemFont(ofSize: max(1, points * u * scale), weight: bold ? .bold : .regular)
        }
        func color(_ role: StampLines.Role?) -> UIColor {
            if role == nil { return UIColor(red: 1, green: 0.64, blue: 0.36, alpha: 1) }
            if role == .time { return UIColor(red: 1, green: 0.82, blue: 0.25, alpha: 1) }
            return .white
        }
        func height(_ value: String, _ font: UIFont, _ width: CGFloat) -> CGFloat {
            let paragraph = NSMutableParagraphStyle(); paragraph.lineBreakMode = .byWordWrapping
            let bounds = (value as NSString).boundingRect(
                with: CGSize(width: max(1, width), height: .greatestFiniteMagnitude),
                options: [.usesLineFragmentOrigin, .usesFontLeading],
                attributes: [.font: font, .paragraphStyle: paragraph], context: nil)
            return ceil(bounds.height) + 0.15 * u
        }
        @discardableResult
        func append(_ value: String, _ role: StampLines.Role?, x: CGFloat, y: CGFloat, width: CGFloat) -> CGFloat {
            var face = font(role)
            var ink = color(role)
            if role == .detail && value.hasPrefix("Mã xác thực") {
                face = .systemFont(ofSize: max(1, 2.6 * u * scale))
                ink = UIColor(white: 0.65, alpha: 1)
            }
            let h = height(value, face, width)
            texts.append(Text(value: value, role: role, font: face, color: ink,
                              rect: CGRect(x: x, y: y, width: width, height: h)))
            return h
        }

        var logoRect: CGRect?
        if let logo = logoSize, logo.width > 0, logo.height > 0 {
            let fit = min((compact ? 6 : 9) * u * scale / logo.height,
                          (compact ? 9 : 24) * u * scale / logo.width)
            logoRect = CGRect(x: left, y: padding, width: logo.width * fit, height: logo.height * fit)
        }
        let headerX = left + (logoRect.map { $0.width + gap } ?? 0)
        let headerWidth = max(1, textWidth - (headerX - left))
        var headerY = padding
        if let project = content.project, !project.isEmpty {
            headerY += append(project, nil, x: headerX, y: headerY, width: headerWidth) + gap
        }
        // Place stays below the task, beside the logo; GPS remains a separate body row.
        for place in content.orderedBody where place.role == .place {
            headerY += append(place.text, .place, x: headerX, y: headerY, width: headerWidth) + gap
        }
        var y = max(headerY, logoRect?.maxY ?? padding)
        if y > padding { y += gap }
        let body = content.orderedBody.filter { $0.role != .place }
        var index = 0
        while index < body.count {
            let entry = body[index]
            if entry.role == .time, index + 1 < body.count, body[index + 1].role == .date {
                let date = body[index + 1].text
                let timeWidth = ceil((entry.text as NSString).size(withAttributes: [.font: font(.time)]).width) + u
                let dateWidth = textWidth - timeWidth - gap
                if dateWidth >= textWidth * 0.35 {
                    let timeH = height(entry.text, font(.time), timeWidth)
                    let dateH = height(date, font(.date), dateWidth)
                    let stackedH = height(entry.text, font(.time), textWidth) + gap + height(date, font(.date), textWidth)
                    if max(timeH, dateH) < stackedH {
                        append(entry.text, .time, x: left, y: y, width: timeWidth)
                        append(date, .date, x: left + timeWidth + gap, y: y + max(0, (timeH - dateH) / 2), width: dateWidth)
                        y += max(timeH, dateH) + gap
                        index += 2
                        continue
                    }
                }
            }
            y += append(entry.text, entry.role, x: left, y: y, width: textWidth) + gap
            index += 1
        }
        return StampLayout(mode: mode, rect: CGRect(x: 0, y: 0, width: width, height: y + padding),
                           logoRect: logoRect, texts: texts)
    }
}
