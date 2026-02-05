import Foundation

@MainActor
final class RecordStore: ObservableObject {
    @Published var groups: [GroupSummary] = []
    @Published var records: [RecordSummary] = []
    @Published var selectedGroupKey: String? = nil
    @Published var groupingMode: GroupingMode = .week
    @Published var selectedRecordId: String? = nil
    @Published var selectedRecord: Record? = nil
    @Published var searchText: String = ""
    @Published var tagFilterMode: TagFilterMode = .and
    @Published var selectedTags: [String] = []
    @Published var tagSearchText: String = ""
    @Published var tagSummaries: [TagSummary] = []
    @Published var syncStatus: String = ""

    private let database: PromptDatabase
    private let syncClient = SyncClient()

    init() {
        do {
            database = try PromptDatabase(path: AppPaths.databaseURL.path)
            reloadAll()
            if UserDefaults.standard.bool(forKey: "sync_enabled")
                && UserDefaults.standard.bool(forKey: "sync_on_launch") {
                syncNow()
            }
        } catch {
            fatalError("Failed to open database: \(error)")
        }
    }

    func reloadAll() {
        do {
            groups = try database.fetchGroupSummaries(mode: groupingMode)
            tagSummaries = try database.fetchTagSummaries()
            reloadRecords()
        } catch {
            print("Reload failed: \(error)")
        }
    }

    func reloadRecords() {
        do {
            let tags = selectedTags
            let ftsQuery = SearchQueryBuilder.make(from: searchText)
            records = try database.fetchRecordSummaries(
                groupKey: selectedGroupKey,
                groupingMode: groupingMode,
                searchQuery: ftsQuery,
                tags: tags,
                tagMode: tagFilterMode
            )
            if let selectedRecordId, !records.contains(where: { $0.id == selectedRecordId }) {
                self.selectedRecordId = nil
                self.selectedRecord = nil
            }
        } catch {
            print("Record reload failed: \(error)")
        }
    }

    func filteredTagSummaries() -> [TagSummary] {
        let trimmed = tagSearchText.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !trimmed.isEmpty else { return tagSummaries }
        return tagSummaries.filter { $0.name.lowercased().contains(trimmed) }
    }


    func selectRecord(id: String?) {
        selectedRecordId = id
        guard let id else {
            selectedRecord = nil
            return
        }
        do {
            selectedRecord = try database.fetchRecord(id: id)
        } catch {
            print("Fetch record failed: \(error)")
        }
    }

    func createRecord() {
        do {
            let record = try database.createRecord(content: "")
            selectedGroupKey = groupingMode.key(from: record.createdAt)
            reloadAll()
            selectRecord(id: record.id)
        } catch {
            print("Create record failed: \(error)")
        }
    }

    func saveSelectedRecord(content: String) {
        guard let selectedRecordId else { return }
        saveRecord(id: selectedRecordId, content: content)
    }

    func saveRecord(id: String, content: String) {
        do {
            let saved = try database.updateRecord(id: id, content: content)
            reloadAll()
            if selectedRecordId == id {
                selectedRecord = saved
            }
        } catch {
            print("Save record failed: \(error)")
        }
    }

    func deleteSelectedRecord() {
        guard let selectedRecordId else { return }
        deleteRecord(id: selectedRecordId)
    }

    func deleteRecord(id: String) {
        do {
            try database.softDelete(id: id)
            if selectedRecordId == id {
                selectedRecord = nil
                selectedRecordId = nil
            }
            reloadAll()
        } catch {
            print("Delete record failed: \(error)")
        }
    }

    func setSyncEnabled(for record: Record, enabled: Bool) {
        do {
            let hasRemote = record.serverRev != nil || record.lastSyncAt != nil
            let markCloudDelete = !enabled && hasRemote
            selectedRecord = try database.setSyncEnabled(
                recordId: record.id,
                enabled: enabled,
                markCloudDelete: markCloudDelete
            )
            reloadAll()
            selectRecord(id: record.id)
        } catch {
            print("Toggle sync failed: \(error)")
        }
    }

    func removeTag(_ tag: String) {
        selectedTags.removeAll { $0 == tag }
        reloadRecords()
    }

    func clearTags() {
        selectedTags.removeAll()
        reloadRecords()
    }

    func applyTagFilter(_ tag: String) {
        if !selectedTags.contains(tag) {
            selectedTags.append(tag)
            selectedTags.sort()
        }
        reloadRecords()
    }

    func syncNow() {
        Task {
            await syncNowAsync()
        }
    }

    private func syncNowAsync() async {
        guard let config = SyncConfig.load() else {
            await MainActor.run { self.syncStatus = "Sync disabled or missing config" }
            return
        }

        await MainActor.run { self.syncStatus = "Syncing…" }
        do {
            let pending = try database.fetchPendingSyncChanges()
            let pendingById = Dictionary(uniqueKeysWithValues: pending.map { ($0.id, $0) })
            let pushChanges = pending.map { record in
                SyncChange(
                    id: record.id,
                    content: record.content,
                    systemTags: record.systemTags,
                    userTags: record.userTags,
                    createdAt: DateCodec.encode(record.createdAt),
                    updatedAt: DateCodec.encode(record.updatedAt),
                    deleted: record.deleted || record.cloudDeletePending,
                    baseRev: record.serverRev
                )
            }
            var pushMaxRev: Int64 = 0
            if !pushChanges.isEmpty {
                let pushRequest = SyncPushRequest(
                    spaceId: config.spaceId,
                    spaceSecret: config.spaceSecret,
                    deviceId: config.deviceId,
                    changes: pushChanges
                )
                let pushResponse = try await syncClient.push(baseURL: config.baseURL, request: pushRequest)
                pushMaxRev = pushResponse.serverRevMax
                let syncTime = Date()
                for result in pushResponse.results {
                    try database.markSynced(recordId: result.id, serverRev: result.serverRev, syncTime: syncTime)
                    if let original = pendingById[result.id], original.cloudDeletePending {
                        try database.clearCloudDeletePending(recordId: result.id)
                    }
                    if result.conflict, let original = pendingById[result.id] {
                        _ = try database.createConflictCopy(original: original)
                    }
                }
            }

            let cursor = try database.loadSyncCursor()
            let pullRequest = SyncPullRequest(
                spaceId: config.spaceId,
                spaceSecret: config.spaceSecret,
                sinceRev: cursor,
                limit: 200
            )
            let pullResponse = try await syncClient.pull(baseURL: config.baseURL, request: pullRequest)
            for change in pullResponse.changes {
                try database.applyRemoteChange(change)
            }
            let nextCursor = max(pushMaxRev, pullResponse.serverRevMax)
            if nextCursor > 0 {
                try database.saveSyncCursor(nextCursor)
            }

            await MainActor.run {
                self.reloadAll()
                if let selectedId = self.selectedRecordId {
                    self.selectRecord(id: selectedId)
                }
                self.syncStatus = "Sync complete"
            }
            Task { @MainActor in
                try? await Task.sleep(nanoseconds: 2_000_000_000)
                self.syncStatus = ""
            }
        } catch {
            await MainActor.run { self.syncStatus = "Sync failed: \(error)" }
        }
    }
}

struct SyncConfig {
    let baseURL: URL
    let spaceId: String
    let spaceSecret: String
    let deviceId: String

    static func load() -> SyncConfig? {
        let defaults = UserDefaults.standard
        guard defaults.bool(forKey: "sync_enabled") else { return nil }
        guard let urlString = defaults.string(forKey: "sync_server_url"),
              let baseURL = URL(string: urlString),
              let spaceId = defaults.string(forKey: "sync_space_id"),
              let spaceSecret = defaults.string(forKey: "sync_space_secret"),
              !spaceId.isEmpty, !spaceSecret.isEmpty else {
            return nil
        }
        let deviceId = defaults.string(forKey: "sync_device_id") ?? UUID().uuidString
        if defaults.string(forKey: "sync_device_id") == nil {
            defaults.set(deviceId, forKey: "sync_device_id")
        }
        return SyncConfig(baseURL: baseURL, spaceId: spaceId, spaceSecret: spaceSecret, deviceId: deviceId)
    }
}

enum SearchQueryBuilder {
    static func make(from input: String) -> String? {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        let tokens = trimmed.split { $0.isWhitespace }.map(String.init)
        guard !tokens.isEmpty else { return nil }
        let cleanedTokens = tokens.compactMap { token -> String? in
            let cleaned = token
                .replacingOccurrences(of: "\"", with: "")
                .trimmingCharacters(in: CharacterSet(charactersIn: "#"))
            return cleaned.isEmpty ? nil : cleaned
        }
        guard !cleanedTokens.isEmpty else { return nil }
        let sanitized = cleanedTokens.map { $0 + "*" }
        return sanitized.joined(separator: " AND ")
    }
}
