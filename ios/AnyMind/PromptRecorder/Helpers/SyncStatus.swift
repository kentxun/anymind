import SwiftUI

struct SyncIcon {
    let name: String
    let color: Color
    let label: String
}

enum SyncStatus {
    static func icon(updatedAt: Date, lastSyncAt: Date?, syncEnabled: Bool) -> SyncIcon {
        if !syncEnabled {
            return SyncIcon(name: "icloud.slash", color: .red, label: "Sync disabled")
        }
        guard let lastSyncAt else {
            return SyncIcon(name: "icloud", color: .gray, label: "Not synced")
        }
        if updatedAt > lastSyncAt {
            return SyncIcon(name: "icloud", color: .gray, label: "Not synced")
        }
        return SyncIcon(name: "icloud.fill", color: .blue, label: "Synced")
    }
}
