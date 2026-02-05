import Foundation

enum TagParser {
    private static let tagPattern = "#([\\p{L}\\p{N}_-]+)"
    private static let systemTagSet: Set<String> = ["temp", "longterm", "p1", "p2", "conflict"]

    static func extractTags(from content: String) -> [String] {
        guard let regex = try? NSRegularExpression(pattern: tagPattern, options: []) else {
            return []
        }
        let range = NSRange(content.startIndex..<content.endIndex, in: content)
        let matches = regex.matches(in: content, options: [], range: range)
        let tags = matches.compactMap { match -> String? in
            guard let tagRange = Range(match.range(at: 1), in: content) else { return nil }
            let raw = String(content[tagRange]).lowercased()
            guard !raw.isEmpty else { return nil }
            return "#" + raw
        }
        return Array(Set(tags)).sorted()
    }

    static func splitTags(_ tags: [String]) -> (system: [String], user: [String]) {
        var system: [String] = []
        var user: [String] = []
        for tag in tags {
            let trimmed = tag.trimmingCharacters(in: .whitespacesAndNewlines)
            let normalized = trimmed.lowercased()
            let name = normalized.hasPrefix("#") ? String(normalized.dropFirst()) : normalized
            if systemTagSet.contains(name) {
                system.append("#" + name)
            } else {
                user.append("#" + name)
            }
        }
        return (Array(Set(system)).sorted(), Array(Set(user)).sorted())
    }

    static func isSystemTag(_ tag: String) -> Bool {
        let trimmed = tag.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let name = trimmed.hasPrefix("#") ? String(trimmed.dropFirst()) : trimmed
        return systemTagSet.contains(name)
    }

    static func isTagOnlyLine(_ line: String) -> Bool {
        let trimmed = line.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            return true
        }
        guard let regex = try? NSRegularExpression(pattern: tagPattern, options: []) else {
            return false
        }
        let range = NSRange(trimmed.startIndex..<trimmed.endIndex, in: trimmed)
        let stripped = regex.stringByReplacingMatches(in: trimmed, options: [], range: range, withTemplate: "")
        let cleaned = stripped
            .replacingOccurrences(of: ",", with: " ")
            .replacingOccurrences(of: "，", with: " ")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return cleaned.isEmpty
    }

    static func parseFilterInput(_ input: String) -> [String] {
        let tags = extractTags(from: input)
        if !tags.isEmpty {
            return tags
        }
        let tokens = input
            .split { $0 == "," || $0.isWhitespace }
            .map { String($0).lowercased() }
            .filter { !$0.isEmpty }
        return Array(Set(tokens.map { $0.hasPrefix("#") ? $0 : "#" + $0 })).sorted()
    }
}
