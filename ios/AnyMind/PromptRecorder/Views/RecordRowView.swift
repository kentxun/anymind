import SwiftUI

struct RecordRowView: View {
    let record: RecordSummary

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text(record.contentPreview)
                    .font(.subheadline)
                    .lineLimit(2)
                Spacer(minLength: 4)
                let icon = SyncStatus.icon(
                    updatedAt: record.updatedAt,
                    lastSyncAt: record.lastSyncAt,
                    syncEnabled: record.syncEnabled
                )
                Image(systemName: icon.name)
                    .font(.caption)
                    .foregroundStyle(icon.color)
                    .accessibilityLabel(icon.label)
            }
            if !record.tags.isEmpty {
                HStack(spacing: 4) {
                    ForEach(record.tags.prefix(3), id: \.self) { tag in
                        TagChipView(tag: tag, accent: .blue)
                    }
                    if record.tags.count > 3 {
                        Text("+\(record.tags.count - 3)")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            Text(DateFormatters.shortDateTime.string(from: record.updatedAt))
                .font(.caption2)
                .foregroundStyle(.secondary)
        }
        .padding(.vertical, 4)
    }
}

enum DateFormatters {
    static let shortDateTime: Foundation.DateFormatter = {
        let formatter = Foundation.DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter
    }()
}
