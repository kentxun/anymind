import Foundation

struct Record: Identifiable, Equatable {
    let id: String
    var content: String
    var createdAt: Date
    var updatedAt: Date
    var weekKey: String
    var userTags: [String]
    var systemTags: [String]
    var deleted: Bool
    var serverRev: Int64?
    var lastSyncAt: Date?
    var syncEnabled: Bool
    var cloudDeletePending: Bool
}

struct RecordSummary: Identifiable, Equatable {
    let id: String
    let contentPreview: String
    let updatedAt: Date
    let weekKey: String
    let tags: [String]
    let lastSyncAt: Date?
    let syncEnabled: Bool
}

struct GroupSummary: Identifiable, Equatable {
    let id: String
    let count: Int
}

struct TagSummary: Identifiable, Equatable {
    let name: String
    let count: Int

    var id: String { name }
}

enum TagFilterMode: String, CaseIterable, Identifiable {
    case and
    case or

    var id: String { rawValue }
    var title: String {
        switch self {
        case .and: return "AND"
        case .or: return "OR"
        }
    }
}

enum GroupingMode: String, CaseIterable, Identifiable {
    case day
    case week
    case month

    var id: String { rawValue }
    var title: String {
        switch self {
        case .day: return "Day"
        case .week: return "Week"
        case .month: return "Month"
        }
    }

    func key(from date: Date) -> String {
        switch self {
        case .day:
            return GroupingMode.dayFormatter.string(from: date)
        case .week:
            return WeekKey.from(date: date)
        case .month:
            return GroupingMode.monthFormatter.string(from: date)
        }
    }

    private static let dayFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        return formatter
    }()

    private static let monthFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM"
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        return formatter
    }()
}
