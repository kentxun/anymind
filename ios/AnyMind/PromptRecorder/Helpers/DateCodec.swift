import Foundation

enum DateCodec {
    static let formatter: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        return formatter
    }()

    static func encode(_ date: Date) -> String {
        formatter.string(from: date)
    }

    static func decode(_ value: String) -> Date {
        formatter.date(from: value) ?? Date(timeIntervalSince1970: 0)
    }

    static func decodeOptional(_ value: String) -> Date? {
        guard !value.isEmpty else { return nil }
        return formatter.date(from: value)
    }
}

enum WeekKey {
    static func from(date: Date) -> String {
        let calendar = Calendar(identifier: .iso8601)
        let week = calendar.component(.weekOfYear, from: date)
        let year = calendar.component(.yearForWeekOfYear, from: date)
        return String(format: "%04d-W%02d", year, week)
    }
}
