import Foundation

/// Presentation-only data. Stored presets and UserDefaults keys stay unchanged.
struct StampLines {
    var head: String?
    var date: String?
    var project: String?
    var address: String?
    var custom: [String]
    var rows: [String]

    enum Role: Equatable { case place, time, date, address, custom, detail }
    struct Entry {
        let text: String
        let role: Role
    }

    static func normalizedLabel(_ label: String) -> String {
        label.folding(options: [.diacriticInsensitive, .caseInsensitive],
                      locale: Locale(identifier: "vi_VN"))
            .lowercased()
            .replacingOccurrences(of: "đ", with: "d")
            .split(whereSeparator: { $0.isWhitespace })
            .joined(separator: " ")
    }

    static func isPlace(_ field: String) -> Bool {
        guard let colon = field.firstIndex(of: ":") else { return false }
        return normalizedLabel(String(field[..<colon])) == "dia diem"
    }

    /// All render paths consume this order; values are never exchanged with GPS.
    var orderedBody: [Entry] {
        var result = custom.filter(Self.isPlace).map { Entry(text: $0, role: .place) }
        for (text, role) in [(head, Role.time), (date, .date), (address, .address)] {
            if let text = text, !text.isEmpty { result.append(Entry(text: text, role: role)) }
        }
        result += custom.filter { !Self.isPlace($0) }.map { Entry(text: $0, role: .custom) }
        result += rows.map { Entry(text: $0, role: .detail) }
        return result
    }
}
