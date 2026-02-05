import Foundation
import SQLite3

final class PromptDatabase {
    private let sqlite: SQLiteDatabase

    init(path: String) throws {
        sqlite = try SQLiteDatabase(path: path)
        try createSchema()
    }

    private func createSchema() throws {
        let schema = """
        CREATE TABLE IF NOT EXISTS records (
            id TEXT PRIMARY KEY,
            content TEXT NOT NULL,
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL,
            week_key TEXT NOT NULL,
            deleted INTEGER NOT NULL DEFAULT 0,
            local_version INTEGER NOT NULL DEFAULT 0,
            server_rev INTEGER,
            last_sync_at TEXT,
            sync_enabled INTEGER NOT NULL DEFAULT 0,
            cloud_delete_pending INTEGER NOT NULL DEFAULT 0
        );

        CREATE TABLE IF NOT EXISTS meta (
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL
        );

        CREATE TABLE IF NOT EXISTS tags (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL UNIQUE,
            is_system INTEGER NOT NULL DEFAULT 0
        );

        CREATE TABLE IF NOT EXISTS record_tags (
            record_id TEXT NOT NULL,
            tag_id INTEGER NOT NULL,
            PRIMARY KEY (record_id, tag_id),
            FOREIGN KEY (record_id) REFERENCES records(id) ON DELETE CASCADE,
            FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE
        );

        CREATE VIRTUAL TABLE IF NOT EXISTS record_fts
        USING fts5(content, record_id, tokenize='unicode61');

        CREATE INDEX IF NOT EXISTS idx_records_week ON records(week_key);
        CREATE INDEX IF NOT EXISTS idx_records_updated ON records(updated_at);
        """
        try sqlite.execute(schema)
        try? sqlite.execute("ALTER TABLE records ADD COLUMN sync_enabled INTEGER NOT NULL DEFAULT 0;")
        try? sqlite.execute("ALTER TABLE records ADD COLUMN cloud_delete_pending INTEGER NOT NULL DEFAULT 0;")
        try? sqlite.execute("""
            UPDATE records
            SET sync_enabled = 1
            WHERE sync_enabled = 0 AND (server_rev IS NOT NULL OR last_sync_at IS NOT NULL);
            """
        )
    }

    func fetchGroupSummaries(mode: GroupingMode) throws -> [GroupSummary] {
        try sqlite.withConnection { db in
            let sql: String
            switch mode {
            case .week:
                sql = """
                SELECT week_key, COUNT(*)
                FROM records
                WHERE deleted = 0
                GROUP BY week_key
                ORDER BY week_key DESC;
                """
            case .day:
                sql = """
                SELECT substr(created_at, 1, 10) AS day_key, COUNT(*)
                FROM records
                WHERE deleted = 0
                GROUP BY day_key
                ORDER BY day_key DESC;
                """
            case .month:
                sql = """
                SELECT substr(created_at, 1, 7) AS month_key, COUNT(*)
                FROM records
                WHERE deleted = 0
                GROUP BY month_key
                ORDER BY month_key DESC;
                """
            }
            let statement = try sqlite.prepare(sql, db: db)
            defer { sqlite3_finalize(statement) }

            var results: [GroupSummary] = []
            while sqlite3_step(statement) == SQLITE_ROW {
                let groupKey = SQLiteColumn.text(statement, index: 0)
                let count = SQLiteColumn.int(statement, index: 1)
                results.append(GroupSummary(id: groupKey, count: count))
            }
            return results
        }
    }

    func fetchTagSummaries() throws -> [TagSummary] {
        try sqlite.withConnection { db in
            let sql = """
            SELECT t.name, COUNT(rt.record_id)
            FROM tags t
            JOIN record_tags rt ON rt.tag_id = t.id
            JOIN records r ON r.id = rt.record_id AND r.deleted = 0
            GROUP BY t.id
            ORDER BY COUNT(rt.record_id) DESC, t.name ASC;
            """
            let statement = try sqlite.prepare(sql, db: db)
            defer { sqlite3_finalize(statement) }

            var results: [TagSummary] = []
            while sqlite3_step(statement) == SQLITE_ROW {
                let name = SQLiteColumn.text(statement, index: 0)
                let count = SQLiteColumn.int(statement, index: 1)
                results.append(TagSummary(name: name, count: count))
            }
            return results
        }
    }

    func fetchRecordSummaries(
        groupKey: String?,
        groupingMode: GroupingMode,
        searchQuery: String?,
        tags: [String],
        tagMode: TagFilterMode
    ) throws -> [RecordSummary] {
        try sqlite.withConnection { db in
            var sql = """
            SELECT r.id, r.content, r.updated_at, r.week_key, r.last_sync_at, r.sync_enabled,
                   COALESCE(
                     (SELECT GROUP_CONCAT(t.name, ' ')
                      FROM record_tags rt
                      JOIN tags t ON t.id = rt.tag_id
                      WHERE rt.record_id = r.id),
                     ''
                   ) AS tag_list
            FROM records r
            """
            var conditions: [String] = ["r.deleted = 0"]
            var bindings: [BindingValue] = []

            if let searchQuery {
                sql += " JOIN record_fts f ON f.record_id = r.id"
                conditions.append("f.content MATCH ?")
                bindings.append(.text(searchQuery))
            }

            if !tags.isEmpty {
                sql += " JOIN record_tags rt_filter ON rt_filter.record_id = r.id"
                sql += " JOIN tags t_filter ON t_filter.id = rt_filter.tag_id"
                let placeholders = tags.map { _ in "?" }.joined(separator: ",")
                conditions.append("t_filter.name IN (\(placeholders))")
                bindings.append(contentsOf: tags.map { .text($0) })
            }

            if let groupKey {
                switch groupingMode {
                case .week:
                    conditions.append("r.week_key = ?")
                    bindings.append(.text(groupKey))
                case .day:
                    conditions.append("substr(r.created_at, 1, 10) = ?")
                    bindings.append(.text(groupKey))
                case .month:
                    conditions.append("substr(r.created_at, 1, 7) = ?")
                    bindings.append(.text(groupKey))
                }
            }

            if !conditions.isEmpty {
                sql += " WHERE " + conditions.joined(separator: " AND ")
            }

            sql += " GROUP BY r.id"
            if !tags.isEmpty && tagMode == .and {
                sql += " HAVING COUNT(DISTINCT t_filter.name) = ?"
                bindings.append(.int(tags.count))
            }

            sql += " ORDER BY r.updated_at DESC;"

            let statement = try sqlite.prepare(sql, db: db)
            defer { sqlite3_finalize(statement) }

            var index: Int32 = 1
            for value in bindings {
                switch value {
                case .text(let text):
                    SQLiteBind.text(statement, index: index, value: text)
                case .int(let int):
                    SQLiteBind.int(statement, index: index, value: int)
                }
                index += 1
            }

            var results: [RecordSummary] = []
            while sqlite3_step(statement) == SQLITE_ROW {
                let id = SQLiteColumn.text(statement, index: 0)
                let content = SQLiteColumn.text(statement, index: 1)
                let updatedAt = DateCodec.decode(SQLiteColumn.text(statement, index: 2))
                let weekKey = SQLiteColumn.text(statement, index: 3)
                let lastSyncAt = DateCodec.decodeOptional(SQLiteColumn.text(statement, index: 4))
                let syncEnabled = SQLiteColumn.bool(statement, index: 5)
                let tagList = SQLiteColumn.text(statement, index: 6)
                let preview = RecordPreview.make(from: content)
                let tags = tagList
                    .split(separator: " ")
                    .map { String($0) }
                    .filter { !$0.isEmpty }
                results.append(
                    RecordSummary(
                        id: id,
                        contentPreview: preview,
                        updatedAt: updatedAt,
                        weekKey: weekKey,
                        tags: tags,
                        lastSyncAt: lastSyncAt,
                        syncEnabled: syncEnabled
                    )
                )
            }
            return results
        }
    }

    func fetchRecord(id: String) throws -> Record? {
        try sqlite.withConnection { db in
            let sql = """
            SELECT id, content, created_at, updated_at, week_key, deleted, server_rev, last_sync_at, sync_enabled, cloud_delete_pending
            FROM records
            WHERE id = ?;
            """
            let statement = try sqlite.prepare(sql, db: db)
            defer { sqlite3_finalize(statement) }
            SQLiteBind.text(statement, index: 1, value: id)

            guard sqlite3_step(statement) == SQLITE_ROW else { return nil }
            let recordId = SQLiteColumn.text(statement, index: 0)
            let content = SQLiteColumn.text(statement, index: 1)
            let createdAt = DateCodec.decode(SQLiteColumn.text(statement, index: 2))
            let updatedAt = DateCodec.decode(SQLiteColumn.text(statement, index: 3))
            let weekKey = SQLiteColumn.text(statement, index: 4)
            let deleted = SQLiteColumn.bool(statement, index: 5)
            let serverRev = SQLiteColumn.int64(statement, index: 6)
            let lastSyncAt = DateCodec.decodeOptional(SQLiteColumn.text(statement, index: 7))
            let syncEnabled = SQLiteColumn.bool(statement, index: 8)
            let cloudDeletePending = SQLiteColumn.bool(statement, index: 9)

            let tags = try fetchTags(recordId: recordId, db: db)

            return Record(
                id: recordId,
                content: content,
                createdAt: createdAt,
                updatedAt: updatedAt,
                weekKey: weekKey,
                userTags: tags.user.sorted(),
                systemTags: tags.system.sorted(),
                deleted: deleted,
                serverRev: serverRev == 0 ? nil : serverRev,
                lastSyncAt: lastSyncAt,
                syncEnabled: syncEnabled,
                cloudDeletePending: cloudDeletePending
            )
        }
    }

    func createRecord(content: String) throws -> Record {
        let now = Date()
        let id = UUID().uuidString
        let weekKey = WeekKey.from(date: now)
        let parsedTags = TagParser.splitTags(TagParser.extractTags(from: content))

        try sqlite.withConnection { db in
            let sql = """
            INSERT INTO records
            (id, content, created_at, updated_at, week_key, deleted, local_version, server_rev, last_sync_at, sync_enabled, cloud_delete_pending)
            VALUES (?, ?, ?, ?, ?, 0, 1, NULL, NULL, 0, 0);
            """
            let statement = try sqlite.prepare(sql, db: db)
            defer { sqlite3_finalize(statement) }

            SQLiteBind.text(statement, index: 1, value: id)
            SQLiteBind.text(statement, index: 2, value: content)
            SQLiteBind.text(statement, index: 3, value: DateCodec.encode(now))
            SQLiteBind.text(statement, index: 4, value: DateCodec.encode(now))
            SQLiteBind.text(statement, index: 5, value: weekKey)

            guard sqlite3_step(statement) == SQLITE_DONE else {
                throw SQLiteError.stepFailed(String(cString: sqlite3_errmsg(db)))
            }

            try updateTags(recordId: id, systemTags: parsedTags.system, userTags: parsedTags.user, db: db)
            try updateFTS(recordId: id, content: content, db: db)
        }

        return Record(
            id: id,
            content: content,
            createdAt: now,
            updatedAt: now,
            weekKey: weekKey,
            userTags: parsedTags.user,
            systemTags: parsedTags.system,
            deleted: false,
            serverRev: nil,
            lastSyncAt: nil,
            syncEnabled: false,
            cloudDeletePending: false
        )
    }

    func updateRecord(id: String, content: String) throws -> Record? {
        try sqlite.withConnection { db in
            let now = Date()
            let parsedTags = TagParser.splitTags(TagParser.extractTags(from: content))

            let sql = """
            UPDATE records
            SET content = ?, updated_at = ?, local_version = local_version + 1
            WHERE id = ?;
            """
            let statement = try sqlite.prepare(sql, db: db)
            defer { sqlite3_finalize(statement) }

            SQLiteBind.text(statement, index: 1, value: content)
            SQLiteBind.text(statement, index: 2, value: DateCodec.encode(now))
            SQLiteBind.text(statement, index: 3, value: id)

            guard sqlite3_step(statement) == SQLITE_DONE else {
                throw SQLiteError.stepFailed(String(cString: sqlite3_errmsg(db)))
            }

            try updateTags(recordId: id, systemTags: parsedTags.system, userTags: parsedTags.user, db: db)
            try updateFTS(recordId: id, content: content, db: db)
        }

        return try fetchRecord(id: id)
    }

    func softDelete(id: String) throws {
        try sqlite.withConnection { db in
            let now = Date()
            let sql = """
            UPDATE records
            SET deleted = 1, updated_at = ?, local_version = local_version + 1
            WHERE id = ?;
            """
            let statement = try sqlite.prepare(sql, db: db)
            defer { sqlite3_finalize(statement) }
            SQLiteBind.text(statement, index: 1, value: DateCodec.encode(now))
            SQLiteBind.text(statement, index: 2, value: id)

            guard sqlite3_step(statement) == SQLITE_DONE else {
                throw SQLiteError.stepFailed(String(cString: sqlite3_errmsg(db)))
            }

            let deleteFts = "DELETE FROM record_fts WHERE record_id = ?;"
            let ftsStmt = try sqlite.prepare(deleteFts, db: db)
            defer { sqlite3_finalize(ftsStmt) }
            SQLiteBind.text(ftsStmt, index: 1, value: id)
            _ = sqlite3_step(ftsStmt)
        }
    }

    func fetchPendingSyncChanges() throws -> [Record] {
        try sqlite.withConnection { db in
            let sql = """
            SELECT id, content, created_at, updated_at, week_key, deleted, server_rev, last_sync_at, sync_enabled, cloud_delete_pending
            FROM records
            WHERE (last_sync_at IS NULL OR updated_at > last_sync_at OR cloud_delete_pending = 1)
              AND (sync_enabled = 1 OR cloud_delete_pending = 1);
            """
            let statement = try sqlite.prepare(sql, db: db)
            defer { sqlite3_finalize(statement) }

            var results: [Record] = []
            while sqlite3_step(statement) == SQLITE_ROW {
                let recordId = SQLiteColumn.text(statement, index: 0)
                let content = SQLiteColumn.text(statement, index: 1)
                let createdAt = DateCodec.decode(SQLiteColumn.text(statement, index: 2))
                let updatedAt = DateCodec.decode(SQLiteColumn.text(statement, index: 3))
                let weekKey = SQLiteColumn.text(statement, index: 4)
                let deleted = SQLiteColumn.bool(statement, index: 5)
                let serverRev = SQLiteColumn.int64(statement, index: 6)
                let lastSyncAt = DateCodec.decodeOptional(SQLiteColumn.text(statement, index: 7))
                let syncEnabled = SQLiteColumn.bool(statement, index: 8)
                let cloudDeletePending = SQLiteColumn.bool(statement, index: 9)
                let tags = try fetchTags(recordId: recordId, db: db)
                let record = Record(
                    id: recordId,
                    content: content,
                    createdAt: createdAt,
                    updatedAt: updatedAt,
                    weekKey: weekKey,
                    userTags: tags.user,
                    systemTags: tags.system,
                    deleted: deleted,
                    serverRev: serverRev == 0 ? nil : serverRev,
                    lastSyncAt: lastSyncAt,
                    syncEnabled: syncEnabled,
                    cloudDeletePending: cloudDeletePending
                )
                results.append(record)
            }
            return results
        }
    }

    func applyRemoteChange(_ change: SyncPullChange) throws {
        let now = Date()
        let systemTags = change.systemTags
        let userTags = change.userTags
        try sqlite.withConnection { db in
            let existsSql = "SELECT sync_enabled, deleted FROM records WHERE id = ?;"
            let existsStmt = try sqlite.prepare(existsSql, db: db)
            defer { sqlite3_finalize(existsStmt) }
            SQLiteBind.text(existsStmt, index: 1, value: change.id)
            let exists = sqlite3_step(existsStmt) == SQLITE_ROW
            let syncEnabled = exists ? SQLiteColumn.bool(existsStmt, index: 0) : false
            let localDeleted = exists ? SQLiteColumn.bool(existsStmt, index: 1) : false

            if exists && !syncEnabled {
                let updateSql = "UPDATE records SET server_rev = ? WHERE id = ?;"
                let stmt = try sqlite.prepare(updateSql, db: db)
                defer { sqlite3_finalize(stmt) }
                SQLiteBind.int64(stmt, index: 1, value: change.serverRev)
                SQLiteBind.text(stmt, index: 2, value: change.id)
                guard sqlite3_step(stmt) == SQLITE_DONE else {
                    throw SQLiteError.stepFailed(String(cString: sqlite3_errmsg(db)))
                }
                return
            }

            if change.deleted {
                if exists {
                    let updateSql = localDeleted
                        ? "UPDATE records SET server_rev = ?, last_sync_at = ? WHERE id = ?;"
                        : "UPDATE records SET server_rev = ?, last_sync_at = NULL WHERE id = ?;"
                    let stmt = try sqlite.prepare(updateSql, db: db)
                    defer { sqlite3_finalize(stmt) }
                    SQLiteBind.int64(stmt, index: 1, value: change.serverRev)
                    if localDeleted {
                        SQLiteBind.text(stmt, index: 2, value: DateCodec.encode(now))
                        SQLiteBind.text(stmt, index: 3, value: change.id)
                    } else {
                        SQLiteBind.text(stmt, index: 2, value: change.id)
                    }
                    guard sqlite3_step(stmt) == SQLITE_DONE else {
                        throw SQLiteError.stepFailed(String(cString: sqlite3_errmsg(db)))
                    }
                }
                return
            }

            if exists {
                let updateSql = """
                UPDATE records
                SET content = ?, created_at = ?, updated_at = ?, week_key = ?, deleted = ?, server_rev = ?, last_sync_at = ?
                WHERE id = ?;
                """
                let stmt = try sqlite.prepare(updateSql, db: db)
                defer { sqlite3_finalize(stmt) }
                SQLiteBind.text(stmt, index: 1, value: change.content)
                SQLiteBind.text(stmt, index: 2, value: change.createdAt)
                SQLiteBind.text(stmt, index: 3, value: change.updatedAt)
                SQLiteBind.text(stmt, index: 4, value: WeekKey.from(date: DateCodec.decode(change.createdAt)))
                SQLiteBind.bool(stmt, index: 5, value: change.deleted)
                SQLiteBind.int64(stmt, index: 6, value: change.serverRev)
                SQLiteBind.text(stmt, index: 7, value: DateCodec.encode(now))
                SQLiteBind.text(stmt, index: 8, value: change.id)
                guard sqlite3_step(stmt) == SQLITE_DONE else {
                    throw SQLiteError.stepFailed(String(cString: sqlite3_errmsg(db)))
                }
            } else {
                let insertSql = """
                INSERT INTO records
                (id, content, created_at, updated_at, week_key, deleted, local_version, server_rev, last_sync_at, sync_enabled, cloud_delete_pending)
                VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?, 1, 0);
                """
                let stmt = try sqlite.prepare(insertSql, db: db)
                defer { sqlite3_finalize(stmt) }
                SQLiteBind.text(stmt, index: 1, value: change.id)
                SQLiteBind.text(stmt, index: 2, value: change.content)
                SQLiteBind.text(stmt, index: 3, value: change.createdAt)
                SQLiteBind.text(stmt, index: 4, value: change.updatedAt)
                SQLiteBind.text(stmt, index: 5, value: WeekKey.from(date: DateCodec.decode(change.createdAt)))
                SQLiteBind.bool(stmt, index: 6, value: change.deleted)
                SQLiteBind.int64(stmt, index: 7, value: change.serverRev)
                SQLiteBind.text(stmt, index: 8, value: DateCodec.encode(now))
                guard sqlite3_step(stmt) == SQLITE_DONE else {
                    throw SQLiteError.stepFailed(String(cString: sqlite3_errmsg(db)))
                }
            }

            try updateTags(recordId: change.id, systemTags: systemTags, userTags: userTags, db: db)
            try updateFTS(recordId: change.id, content: change.content, db: db)
        }
    }

    func createConflictCopy(original: Record) throws -> Record {
        let now = Date()
        let newId = UUID().uuidString
        let header = "[CONFLICT COPY] Original ID: \(original.id)"
        let content = header + "\n\n" + original.content
        let weekKey = WeekKey.from(date: now)
        let tags = (original.systemTags + ["#conflict"], original.userTags)

        try sqlite.withConnection { db in
            let sql = """
            INSERT INTO records
            (id, content, created_at, updated_at, week_key, deleted, local_version, server_rev, last_sync_at, sync_enabled, cloud_delete_pending)
            VALUES (?, ?, ?, ?, ?, 0, 1, NULL, NULL, 0, 0);
            """
            let statement = try sqlite.prepare(sql, db: db)
            defer { sqlite3_finalize(statement) }

            SQLiteBind.text(statement, index: 1, value: newId)
            SQLiteBind.text(statement, index: 2, value: content)
            SQLiteBind.text(statement, index: 3, value: DateCodec.encode(now))
            SQLiteBind.text(statement, index: 4, value: DateCodec.encode(now))
            SQLiteBind.text(statement, index: 5, value: weekKey)

            guard sqlite3_step(statement) == SQLITE_DONE else {
                throw SQLiteError.stepFailed(String(cString: sqlite3_errmsg(db)))
            }

            try updateTags(recordId: newId, systemTags: tags.0, userTags: tags.1, db: db)
            try updateFTS(recordId: newId, content: content, db: db)
        }

        return Record(
            id: newId,
            content: content,
            createdAt: now,
            updatedAt: now,
            weekKey: weekKey,
            userTags: tags.1,
            systemTags: tags.0,
            deleted: false,
            serverRev: nil,
            lastSyncAt: nil,
            syncEnabled: false,
            cloudDeletePending: false
        )
    }

    func markSynced(recordId: String, serverRev: Int64, syncTime: Date) throws {
        try sqlite.withConnection { db in
            let sql = "UPDATE records SET server_rev = ?, last_sync_at = ? WHERE id = ?;"
            let stmt = try sqlite.prepare(sql, db: db)
            defer { sqlite3_finalize(stmt) }
            SQLiteBind.int64(stmt, index: 1, value: serverRev)
            SQLiteBind.text(stmt, index: 2, value: DateCodec.encode(syncTime))
            SQLiteBind.text(stmt, index: 3, value: recordId)
            guard sqlite3_step(stmt) == SQLITE_DONE else {
                throw SQLiteError.stepFailed(String(cString: sqlite3_errmsg(db)))
            }
        }
    }

    func setSyncEnabled(recordId: String, enabled: Bool, markCloudDelete: Bool) throws -> Record? {
        try sqlite.withConnection { db in
            let now = Date()
            if enabled {
                let sql = """
                UPDATE records
                SET sync_enabled = 1, cloud_delete_pending = 0, last_sync_at = NULL, updated_at = ?, local_version = local_version + 1
                WHERE id = ?;
                """
                let stmt = try sqlite.prepare(sql, db: db)
                defer { sqlite3_finalize(stmt) }
                SQLiteBind.text(stmt, index: 1, value: DateCodec.encode(now))
                SQLiteBind.text(stmt, index: 2, value: recordId)
                guard sqlite3_step(stmt) == SQLITE_DONE else {
                    throw SQLiteError.stepFailed(String(cString: sqlite3_errmsg(db)))
                }
            } else {
                let sql = """
                UPDATE records
                SET sync_enabled = 0, cloud_delete_pending = ?, updated_at = ?, local_version = local_version + 1
                WHERE id = ?;
                """
                let stmt = try sqlite.prepare(sql, db: db)
                defer { sqlite3_finalize(stmt) }
                SQLiteBind.bool(stmt, index: 1, value: markCloudDelete)
                SQLiteBind.text(stmt, index: 2, value: DateCodec.encode(now))
                SQLiteBind.text(stmt, index: 3, value: recordId)
                guard sqlite3_step(stmt) == SQLITE_DONE else {
                    throw SQLiteError.stepFailed(String(cString: sqlite3_errmsg(db)))
                }
            }
        }
        return try fetchRecord(id: recordId)
    }

    func clearCloudDeletePending(recordId: String) throws {
        try sqlite.withConnection { db in
            let sql = "UPDATE records SET cloud_delete_pending = 0 WHERE id = ?;"
            let stmt = try sqlite.prepare(sql, db: db)
            defer { sqlite3_finalize(stmt) }
            SQLiteBind.text(stmt, index: 1, value: recordId)
            guard sqlite3_step(stmt) == SQLITE_DONE else {
                throw SQLiteError.stepFailed(String(cString: sqlite3_errmsg(db)))
            }
        }
    }

    func loadSyncCursor() throws -> Int64 {
        try sqlite.withConnection { db in
            let sql = "SELECT value FROM meta WHERE key = 'sync_cursor';"
            let stmt = try sqlite.prepare(sql, db: db)
            defer { sqlite3_finalize(stmt) }
            guard sqlite3_step(stmt) == SQLITE_ROW else { return 0 }
            let value = SQLiteColumn.text(stmt, index: 0)
            return Int64(value) ?? 0
        }
    }

    func saveSyncCursor(_ value: Int64) throws {
        try sqlite.withConnection { db in
            let sql = """
            INSERT INTO meta (key, value) VALUES ('sync_cursor', ?)
            ON CONFLICT(key) DO UPDATE SET value = excluded.value;
            """
            let stmt = try sqlite.prepare(sql, db: db)
            defer { sqlite3_finalize(stmt) }
            SQLiteBind.text(stmt, index: 1, value: String(value))
            guard sqlite3_step(stmt) == SQLITE_DONE else {
                throw SQLiteError.stepFailed(String(cString: sqlite3_errmsg(db)))
            }
        }
    }

    private func fetchTags(recordId: String, db: OpaquePointer?) throws -> (system: [String], user: [String]) {
        let tagSql = """
        SELECT t.name, t.is_system
        FROM tags t
        JOIN record_tags rt ON rt.tag_id = t.id
        WHERE rt.record_id = ?;
        """
        let tagStatement = try sqlite.prepare(tagSql, db: db)
        defer { sqlite3_finalize(tagStatement) }
        SQLiteBind.text(tagStatement, index: 1, value: recordId)

        var systemTags: [String] = []
        var userTags: [String] = []
        while sqlite3_step(tagStatement) == SQLITE_ROW {
            let name = SQLiteColumn.text(tagStatement, index: 0)
            let isSystem = SQLiteColumn.bool(tagStatement, index: 1)
            if isSystem {
                systemTags.append(name)
            } else {
                userTags.append(name)
            }
        }
        return (systemTags, userTags)
    }

    private func updateTags(
        recordId: String,
        systemTags: [String],
        userTags: [String],
        db: OpaquePointer?
    ) throws {
        let deleteSql = "DELETE FROM record_tags WHERE record_id = ?;"
        let deleteStmt = try sqlite.prepare(deleteSql, db: db)
        defer { sqlite3_finalize(deleteStmt) }
        SQLiteBind.text(deleteStmt, index: 1, value: recordId)
        _ = sqlite3_step(deleteStmt)

        let allTags = systemTags.map { ($0, true) } + userTags.map { ($0, false) }
        for (tag, isSystem) in allTags {
            let tagId = try upsertTag(name: tag, isSystem: isSystem, db: db)
            let insertSql = "INSERT OR IGNORE INTO record_tags (record_id, tag_id) VALUES (?, ?);"
            let insertStmt = try sqlite.prepare(insertSql, db: db)
            defer { sqlite3_finalize(insertStmt) }
            SQLiteBind.text(insertStmt, index: 1, value: recordId)
            SQLiteBind.int64(insertStmt, index: 2, value: tagId)
            _ = sqlite3_step(insertStmt)
        }
    }

    private func upsertTag(name: String, isSystem: Bool, db: OpaquePointer?) throws -> Int64 {
        let insertSql = """
        INSERT INTO tags (name, is_system) VALUES (?, ?)
        ON CONFLICT(name) DO UPDATE SET is_system = excluded.is_system;
        """
        let insertStmt = try sqlite.prepare(insertSql, db: db)
        defer { sqlite3_finalize(insertStmt) }
        SQLiteBind.text(insertStmt, index: 1, value: name)
        SQLiteBind.bool(insertStmt, index: 2, value: isSystem)
        _ = sqlite3_step(insertStmt)

        let selectSql = "SELECT id FROM tags WHERE name = ?;"
        let selectStmt = try sqlite.prepare(selectSql, db: db)
        defer { sqlite3_finalize(selectStmt) }
        SQLiteBind.text(selectStmt, index: 1, value: name)

        guard sqlite3_step(selectStmt) == SQLITE_ROW else {
            throw SQLiteError.stepFailed(String(cString: sqlite3_errmsg(db)))
        }
        return SQLiteColumn.int64(selectStmt, index: 0)
    }

    private func updateFTS(recordId: String, content: String, db: OpaquePointer?) throws {
        let deleteSql = "DELETE FROM record_fts WHERE record_id = ?;"
        let deleteStmt = try sqlite.prepare(deleteSql, db: db)
        defer { sqlite3_finalize(deleteStmt) }
        SQLiteBind.text(deleteStmt, index: 1, value: recordId)
        _ = sqlite3_step(deleteStmt)

        let insertSql = "INSERT INTO record_fts (content, record_id) VALUES (?, ?);"
        let insertStmt = try sqlite.prepare(insertSql, db: db)
        defer { sqlite3_finalize(insertStmt) }
        SQLiteBind.text(insertStmt, index: 1, value: content)
        SQLiteBind.text(insertStmt, index: 2, value: recordId)
        _ = sqlite3_step(insertStmt)
    }
}

enum RecordPreview {
    static func make(from content: String) -> String {
        let lines = content.split(separator: "\n", omittingEmptySubsequences: false)
        var candidate: String? = nil
        for line in lines {
            let value = String(line)
            if TagParser.isTagOnlyLine(value) {
                continue
            }
            candidate = value
            break
        }
        let trimmed = (candidate ?? content).trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            return "(empty)"
        }
        if trimmed.count > 120 {
            let idx = trimmed.index(trimmed.startIndex, offsetBy: 120)
            return String(trimmed[..<idx]) + "…"
        }
        return trimmed
    }
}

private enum BindingValue {
    case text(String)
    case int(Int)
}
