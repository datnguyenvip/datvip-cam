import SwiftUI

enum AppLanguage {
    static let key = "interfaceLanguage"
    static var code: String {
        let choice = UserDefaults.standard.string(forKey: key) ?? "system"
        if choice == "vi" || choice == "en" { return choice }
        return Locale.preferredLanguages.first?.hasPrefix("vi") == true ? "vi" : "en"
    }
    static var locale: Locale { Locale(identifier: code == "vi" ? "vi_VN" : "en_US") }
    static var name: String { code == "vi" ? "Camera HT – Hiện Trường" : "Field Camera – GPS Stamp" }
    private static let english: [String: String] = {
        guard let url = Bundle.main.url(forResource: "ui-en", withExtension: "json") ?? Bundle(for: LanguageSelection.self).url(forResource: "ui-en", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let catalog = try? JSONDecoder().decode([String: String].self, from: data) else { return [:] }
        return catalog
    }()
    static func text(_ source: String, _ values: String...) -> String {
        var result = code == "vi" ? source : english[source] ?? source
        let regex = try! NSRegularExpression(pattern: "\\{(\\d+)\\}")
        let matches = regex.matches(in: result, range: NSRange(result.startIndex..., in: result))
        for match in matches.reversed() {
            guard let numberRange = Range(match.range(at: 1), in: result),
                  let index = Int(result[numberRange]), values.indices.contains(index),
                  let whole = Range(match.range, in: result) else { continue }
            result.replaceSubrange(whole, with: values[index])
        }
        return result
    }
    static func direction(_ degrees: Double) -> String {
        let names = code == "vi" ? ["B", "ĐB", "Đ", "ĐN", "N", "TN", "T", "TB"] : ["N", "NE", "E", "SE", "S", "SW", "W", "NW"]
        return names[Int((degrees / 45).rounded()) % 8]
    }
}

final class LanguageSelection: ObservableObject {
    @Published var choice: String {
        didSet { UserDefaults.standard.set(choice, forKey: AppLanguage.key) }
    }
    init() { choice = UserDefaults.standard.string(forKey: AppLanguage.key) ?? "system" }
}
