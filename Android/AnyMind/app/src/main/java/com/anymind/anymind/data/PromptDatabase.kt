package com.anymind.anymind.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.anymind.anymind.sync.SyncPullChange
import com.anymind.anymind.util.DateCodec
import com.anymind.anymind.util.GroupingMode
import com.anymind.anymind.util.RecordPreview
import com.anymind.anymind.util.TagFilterMode
import com.anymind.anymind.util.TagParser
import com.anymind.anymind.util.WeekKey
import java.time.Instant
import java.util.UUID

class PromptDatabase(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        val statements = listOf(
            """
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
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS meta (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            );
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS tags (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                is_system INTEGER NOT NULL DEFAULT 0
            );
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS record_tags (
                record_id TEXT NOT NULL,
                tag_id INTEGER NOT NULL,
                PRIMARY KEY (record_id, tag_id),
                FOREIGN KEY (record_id) REFERENCES records(id) ON DELETE CASCADE,
                FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE
            );
            """.trimIndent(),
            """
            CREATE VIRTUAL TABLE IF NOT EXISTS record_fts
            USING fts4(content, record_id, tokenize='unicode61');
            """.trimIndent(),
            "CREATE INDEX IF NOT EXISTS idx_records_week ON records(week_key);",
            "CREATE INDEX IF NOT EXISTS idx_records_updated ON records(updated_at);"
        )
        statements.forEach { db.execSQL(it) }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            execSafely(db, "ALTER TABLE records ADD COLUMN sync_enabled INTEGER NOT NULL DEFAULT 0;")
            execSafely(db, "ALTER TABLE records ADD COLUMN cloud_delete_pending INTEGER NOT NULL DEFAULT 0;")
        }
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        execSafely(db, "ALTER TABLE records ADD COLUMN sync_enabled INTEGER NOT NULL DEFAULT 0;")
        execSafely(db, "ALTER TABLE records ADD COLUMN cloud_delete_pending INTEGER NOT NULL DEFAULT 0;")
        normalizeTagTable(db)
        execSafely(
            db,
            "UPDATE records SET sync_enabled = 1 WHERE sync_enabled = 0 AND (server_rev IS NOT NULL OR last_sync_at IS NOT NULL);"
        )
    }

    fun fetchGroupSummaries(mode: GroupingMode): List<GroupSummary> {
        val sql = when (mode) {
            GroupingMode.WEEK -> """
                SELECT week_key, COUNT(*)
                FROM records
                WHERE deleted = 0
                GROUP BY week_key
                ORDER BY week_key DESC;
            """.trimIndent()
            GroupingMode.DAY -> """
                SELECT substr(created_at, 1, 10) AS day_key, COUNT(*)
                FROM records
                WHERE deleted = 0
                GROUP BY day_key
                ORDER BY day_key DESC;
            """.trimIndent()
            GroupingMode.MONTH -> """
                SELECT substr(created_at, 1, 7) AS month_key, COUNT(*)
                FROM records
                WHERE deleted = 0
                GROUP BY month_key
                ORDER BY month_key DESC;
            """.trimIndent()
        }
        val db = readableDatabase
        val cursor = db.rawQuery(sql, null)
        return cursor.use { c ->
            val results = mutableListOf<GroupSummary>()
            while (c.moveToNext()) {
                results.add(GroupSummary(id = c.getString(0), count = c.getInt(1)))
            }
            results
        }
    }

    fun fetchTagSummaries(): List<TagSummary> {
        val sql = """
            SELECT t.name, COUNT(rt.record_id)
            FROM tags t
            JOIN record_tags rt ON rt.tag_id = t.id
            JOIN records r ON r.id = rt.record_id AND r.deleted = 0
            GROUP BY t.id
            ORDER BY COUNT(rt.record_id) DESC, t.name ASC;
        """.trimIndent()
        val db = readableDatabase
        val cursor = db.rawQuery(sql, null)
        return cursor.use { c ->
            val results = mutableListOf<TagSummary>()
            while (c.moveToNext()) {
                results.add(TagSummary(name = c.getString(0), count = c.getInt(1)))
            }
            results
        }
    }

    fun fetchRecordSummaries(query: RecordQuery): List<RecordSummary> {
        val sql = StringBuilder()
        val args = mutableListOf<String>()
        sql.append(
            """
            SELECT r.id, r.content, r.updated_at, r.week_key, r.last_sync_at, r.sync_enabled,
                   COALESCE(
                     (SELECT GROUP_CONCAT(t.name, ' ')
                      FROM record_tags rt
                      JOIN tags t ON t.id = rt.tag_id
                      WHERE rt.record_id = r.id),
                     ''
                   ) AS tag_list
            FROM records r
            """.trimIndent()
        )

        val conditions = mutableListOf<String>()
        conditions.add("r.deleted = 0")

        if (query.searchQuery != null) {
            sql.append(" JOIN record_fts f ON f.record_id = r.id")
            conditions.add("f.content MATCH ?")
            args.add(query.searchQuery)
        }

        if (query.tags.isNotEmpty()) {
            sql.append(" JOIN record_tags rt_filter ON rt_filter.record_id = r.id")
            sql.append(" JOIN tags t_filter ON t_filter.id = rt_filter.tag_id")
            val placeholders = query.tags.joinToString(",") { "?" }
            conditions.add("t_filter.name IN ($placeholders)")
            args.addAll(query.tags)
        }

        if (query.groupKey != null) {
            when (query.groupingMode) {
                GroupingMode.WEEK -> conditions.add("r.week_key = ?")
                GroupingMode.DAY -> conditions.add("substr(r.created_at, 1, 10) = ?")
                GroupingMode.MONTH -> conditions.add("substr(r.created_at, 1, 7) = ?")
            }
            args.add(query.groupKey)
        }

        if (conditions.isNotEmpty()) {
            sql.append(" WHERE ")
            sql.append(conditions.joinToString(" AND "))
        }

        sql.append(" GROUP BY r.id")
        if (query.tags.isNotEmpty() && query.tagMode == TagFilterMode.AND) {
            sql.append(" HAVING COUNT(DISTINCT t_filter.name) = ?")
            args.add(query.tags.size.toString())
        }

        sql.append(" ORDER BY r.updated_at DESC;")

        val db = readableDatabase
        val cursor = db.rawQuery(sql.toString(), args.toTypedArray())
        return cursor.use { c ->
            val results = mutableListOf<RecordSummary>()
            while (c.moveToNext()) {
                val id = c.getString(0)
                val content = c.getString(1)
                val updatedAt = DateCodec.decode(c.getString(2))
                val weekKey = c.getString(3)
                val lastSyncAt = DateCodec.decodeOptional(c.getString(4))
                val syncEnabled = c.getInt(5) == 1
                val tagList = c.getString(6) ?: ""
                val tags = tagList.split(" ").filter { it.isNotBlank() }
                val preview = RecordPreview.make(content)
                results.add(
                    RecordSummary(
                        id = id,
                        contentPreview = preview,
                        updatedAt = updatedAt,
                        weekKey = weekKey,
                        tags = tags,
                        lastSyncAt = lastSyncAt,
                        syncEnabled = syncEnabled
                    )
                )
            }
            results
        }
    }

    fun fetchRecord(id: String): Record? {
        val sql = """
            SELECT id, content, created_at, updated_at, week_key, deleted, server_rev, last_sync_at,
                   sync_enabled, cloud_delete_pending
            FROM records
            WHERE id = ?;
        """.trimIndent()
        val db = readableDatabase
        val cursor = db.rawQuery(sql, arrayOf(id))
        return cursor.use { c ->
            if (!c.moveToFirst()) {
                return null
            }
            val recordId = c.getString(0)
            val content = c.getString(1)
            val createdAt = DateCodec.decode(c.getString(2))
            val updatedAt = DateCodec.decode(c.getString(3))
            val weekKey = c.getString(4)
            val deleted = c.getInt(5) == 1
            val serverRev = if (c.isNull(6)) null else c.getLong(6)
            val lastSyncAt = DateCodec.decodeOptional(c.getString(7))
            val syncEnabled = c.getInt(8) == 1
            val cloudDeletePending = c.getInt(9) == 1
            val tags = fetchTags(recordId)
            Record(
                id = recordId,
                content = content,
                createdAt = createdAt,
                updatedAt = updatedAt,
                weekKey = weekKey,
                userTags = tags.second.sorted(),
                systemTags = tags.first.sorted(),
                deleted = deleted,
                serverRev = serverRev,
                lastSyncAt = lastSyncAt,
                syncEnabled = syncEnabled,
                cloudDeletePending = cloudDeletePending
            )
        }
    }

    fun createRecord(content: String): Record {
        val now = Instant.now()
        val id = UUID.randomUUID().toString()
        val weekKey = WeekKey.from(now)
        val parsed = TagParser.splitTags(TagParser.extractTags(content))
        val db = writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put("id", id)
                put("content", content)
                put("created_at", DateCodec.encode(now))
                put("updated_at", DateCodec.encode(now))
                put("week_key", weekKey)
                put("deleted", 0)
                put("local_version", 1)
                putNull("server_rev")
                putNull("last_sync_at")
                put("sync_enabled", 0)
                put("cloud_delete_pending", 0)
            }
            db.insertOrThrow("records", null, values)
            updateTags(db, id, parsed.first, parsed.second)
            updateFts(db, id, content)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return Record(
            id = id,
            content = content,
            createdAt = now,
            updatedAt = now,
            weekKey = weekKey,
            userTags = parsed.second,
            systemTags = parsed.first,
            deleted = false,
            serverRev = null,
            lastSyncAt = null,
            syncEnabled = false,
            cloudDeletePending = false
        )
    }

    fun updateRecord(id: String, content: String): Record? {
        val now = Instant.now()
        val parsed = TagParser.splitTags(TagParser.extractTags(content))
        val db = writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put("content", content)
                put("updated_at", DateCodec.encode(now))
            }
            db.update("records", values, "id = ?", arrayOf(id))
            db.execSQL("UPDATE records SET local_version = local_version + 1 WHERE id = ?", arrayOf(id))
            updateTags(db, id, parsed.first, parsed.second)
            updateFts(db, id, content)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return fetchRecord(id)
    }

    fun softDelete(id: String) {
        val now = Instant.now()
        val db = writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put("deleted", 1)
                put("updated_at", DateCodec.encode(now))
            }
            db.update("records", values, "id = ?", arrayOf(id))
            db.execSQL("UPDATE records SET local_version = local_version + 1 WHERE id = ?", arrayOf(id))
            db.execSQL("DELETE FROM record_fts WHERE record_id = ?", arrayOf(id))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun fetchPendingSyncChanges(): List<Record> {
        val sql = """
            SELECT id, content, created_at, updated_at, week_key, deleted, server_rev, last_sync_at,
                   sync_enabled, cloud_delete_pending
            FROM records
            WHERE (last_sync_at IS NULL OR updated_at > last_sync_at OR cloud_delete_pending = 1)
              AND (sync_enabled = 1 OR cloud_delete_pending = 1);
        """.trimIndent()
        val db = readableDatabase
        val cursor = db.rawQuery(sql, null)
        return cursor.use { c ->
            val results = mutableListOf<Record>()
            while (c.moveToNext()) {
                val recordId = c.getString(0)
                val content = c.getString(1)
                val createdAt = DateCodec.decode(c.getString(2))
                val updatedAt = DateCodec.decode(c.getString(3))
                val weekKey = c.getString(4)
                val deleted = c.getInt(5) == 1
                val serverRev = if (c.isNull(6)) null else c.getLong(6)
                val lastSyncAt = DateCodec.decodeOptional(c.getString(7))
                val syncEnabled = c.getInt(8) == 1
                val cloudDeletePending = c.getInt(9) == 1
                val tags = fetchTags(recordId)
                results.add(
                    Record(
                        id = recordId,
                        content = content,
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                        weekKey = weekKey,
                        userTags = tags.second,
                        systemTags = tags.first,
                        deleted = deleted,
                        serverRev = serverRev,
                        lastSyncAt = lastSyncAt,
                        syncEnabled = syncEnabled,
                        cloudDeletePending = cloudDeletePending
                    )
                )
            }
            results
        }
    }

    fun applyRemoteChange(change: SyncPullChange) {
        val now = Instant.now()
        val db = writableDatabase
        db.beginTransaction()
        try {
            val cursor = db.rawQuery(
                "SELECT sync_enabled, deleted FROM records WHERE id = ?",
                arrayOf(change.id)
            )
            var exists = false
            var syncEnabled = false
            var localDeleted = false
            cursor.use { c ->
                if (c.moveToFirst()) {
                    exists = true
                    syncEnabled = c.getInt(0) == 1
                    localDeleted = c.getInt(1) == 1
                }
            }

            if (exists && !syncEnabled) {
                val values = ContentValues().apply {
                    put("server_rev", change.serverRev)
                }
                db.update("records", values, "id = ?", arrayOf(change.id))
                db.setTransactionSuccessful()
                return
            }

            if (change.deleted) {
                if (exists) {
                    val values = ContentValues().apply {
                        put("server_rev", change.serverRev)
                        if (localDeleted) {
                            put("last_sync_at", DateCodec.encode(now))
                        } else {
                            putNull("last_sync_at")
                        }
                    }
                    db.update("records", values, "id = ?", arrayOf(change.id))
                }
                db.setTransactionSuccessful()
                return
            }

            if (exists) {
                val values = ContentValues().apply {
                    put("content", change.content)
                    put("created_at", change.createdAt)
                    put("updated_at", change.updatedAt)
                    put("week_key", WeekKey.from(DateCodec.decode(change.createdAt)))
                    put("deleted", if (change.deleted) 1 else 0)
                    put("server_rev", change.serverRev)
                    put("last_sync_at", DateCodec.encode(now))
                }
                db.update("records", values, "id = ?", arrayOf(change.id))
            } else {
                val values = ContentValues().apply {
                    put("id", change.id)
                    put("content", change.content)
                    put("created_at", change.createdAt)
                    put("updated_at", change.updatedAt)
                    put("week_key", WeekKey.from(DateCodec.decode(change.createdAt)))
                    put("deleted", if (change.deleted) 1 else 0)
                    put("local_version", 0)
                    put("server_rev", change.serverRev)
                    put("last_sync_at", DateCodec.encode(now))
                    put("sync_enabled", 1)
                    put("cloud_delete_pending", 0)
                }
                db.insertOrThrow("records", null, values)
            }

            updateTags(db, change.id, change.systemTags, change.userTags)
            updateFts(db, change.id, change.content)

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun createConflictCopy(original: Record): Record {
        val now = Instant.now()
        val newId = UUID.randomUUID().toString()
        val header = "[CONFLICT COPY] Original ID: ${original.id}"
        val content = header + "\n\n" + original.content
        val weekKey = WeekKey.from(now)
        val tags = (original.systemTags + "#conflict") to original.userTags
        val db = writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put("id", newId)
                put("content", content)
                put("created_at", DateCodec.encode(now))
                put("updated_at", DateCodec.encode(now))
                put("week_key", weekKey)
                put("deleted", 0)
                put("local_version", 1)
                putNull("server_rev")
                putNull("last_sync_at")
                put("sync_enabled", 0)
                put("cloud_delete_pending", 0)
            }
            db.insertOrThrow("records", null, values)
            updateTags(db, newId, tags.first, tags.second)
            updateFts(db, newId, content)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return Record(
            id = newId,
            content = content,
            createdAt = now,
            updatedAt = now,
            weekKey = weekKey,
            userTags = tags.second,
            systemTags = tags.first,
            deleted = false,
            serverRev = null,
            lastSyncAt = null,
            syncEnabled = false,
            cloudDeletePending = false
        )
    }

    fun markSynced(recordId: String, serverRev: Long, syncTime: Instant) {
        val values = ContentValues().apply {
            put("server_rev", serverRev)
            put("last_sync_at", DateCodec.encode(syncTime))
        }
        writableDatabase.update("records", values, "id = ?", arrayOf(recordId))
    }

    fun setSyncEnabled(recordId: String, enabled: Boolean, markCloudDelete: Boolean): Record? {
        val now = Instant.now()
        val db = writableDatabase
        db.beginTransaction()
        try {
            if (enabled) {
                val values = ContentValues().apply {
                    put("sync_enabled", 1)
                    put("cloud_delete_pending", 0)
                    putNull("last_sync_at")
                    put("updated_at", DateCodec.encode(now))
                }
                db.update("records", values, "id = ?", arrayOf(recordId))
            } else {
                val values = ContentValues().apply {
                    put("sync_enabled", 0)
                    put("cloud_delete_pending", if (markCloudDelete) 1 else 0)
                    put("updated_at", DateCodec.encode(now))
                }
                db.update("records", values, "id = ?", arrayOf(recordId))
            }
            db.execSQL("UPDATE records SET local_version = local_version + 1 WHERE id = ?", arrayOf(recordId))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return fetchRecord(recordId)
    }

    fun clearCloudDeletePending(recordId: String) {
        val values = ContentValues().apply {
            put("cloud_delete_pending", 0)
        }
        writableDatabase.update("records", values, "id = ?", arrayOf(recordId))
    }

    fun loadSyncCursor(): Long {
        val cursor = readableDatabase.rawQuery(
            "SELECT value FROM meta WHERE key = 'sync_cursor'",
            null
        )
        return cursor.use { c ->
            if (!c.moveToFirst()) {
                0L
            } else {
                c.getString(0).toLongOrNull() ?: 0L
            }
        }
    }

    fun saveSyncCursor(value: Long) {
        val values = ContentValues().apply {
            put("key", "sync_cursor")
            put("value", value.toString())
        }
        writableDatabase.insertWithOnConflict(
            "meta",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    private fun fetchTags(recordId: String): Pair<List<String>, List<String>> {
        val sql = """
            SELECT t.name, t.is_system
            FROM tags t
            JOIN record_tags rt ON rt.tag_id = t.id
            WHERE rt.record_id = ?;
        """.trimIndent()
        val cursor = readableDatabase.rawQuery(sql, arrayOf(recordId))
        val system = mutableListOf<String>()
        val user = mutableListOf<String>()
        cursor.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0)
                val isSystem = c.getInt(1) == 1
                if (isSystem) {
                    system.add(name)
                } else {
                    user.add(name)
                }
            }
        }
        return system to user
    }

    private fun updateTags(db: SQLiteDatabase, recordId: String, systemTags: List<String>, userTags: List<String>) {
        db.execSQL("DELETE FROM record_tags WHERE record_id = ?", arrayOf(recordId))
        val all = mutableListOf<Pair<String, Boolean>>()
        systemTags.forEach { all.add(normalizeTagName(it) to true) }
        userTags.forEach { all.add(normalizeTagName(it) to false) }
        for ((tag, isSystem) in all) {
            val tagId = upsertTag(db, tag, isSystem)
            db.execSQL(
                "INSERT OR IGNORE INTO record_tags (record_id, tag_id) VALUES (?, ?)",
                arrayOf(recordId, tagId)
            )
        }
    }

    private fun upsertTag(db: SQLiteDatabase, name: String, isSystem: Boolean): Long {
        val normalized = normalizeTagName(name)
        db.execSQL(
            "INSERT OR IGNORE INTO tags (name, is_system) VALUES (?, ?)",
            arrayOf(normalized, if (isSystem) 1 else 0)
        )
        db.execSQL(
            "UPDATE tags SET is_system = ? WHERE name = ?",
            arrayOf(if (isSystem) 1 else 0, normalized)
        )
        val cursor = db.rawQuery("SELECT id FROM tags WHERE name = ?", arrayOf(normalized))
        return cursor.use { c ->
            if (!c.moveToFirst()) {
                throw SQLException("Tag insert failed")
            }
            c.getLong(0)
        }
    }

    private fun updateFts(db: SQLiteDatabase, recordId: String, content: String) {
        db.execSQL("DELETE FROM record_fts WHERE record_id = ?", arrayOf(recordId))
        db.execSQL(
            "INSERT INTO record_fts (content, record_id) VALUES (?, ?)",
            arrayOf(content, recordId)
        )
    }

    private fun normalizeTagTable(db: SQLiteDatabase) {
        try {
            db.beginTransaction()
            val cursor = db.rawQuery("SELECT id, name, is_system FROM tags", null)
            val keepByName = mutableMapOf<String, Long>()
            val systemByName = mutableMapOf<String, Boolean>()
            val updateName = mutableListOf<Pair<Long, String>>()
            val toDelete = mutableListOf<Long>()
            cursor.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val name = c.getString(1)
                    val isSystem = c.getInt(2) == 1
                    val normalized = normalizeTagName(name)
                    val keepId = keepByName[normalized]
                    if (keepId == null) {
                        keepByName[normalized] = id
                        systemByName[normalized] = isSystem
                        if (name != normalized) {
                            updateName.add(id to normalized)
                        }
                    } else {
                        // merge duplicate into keepId
                        db.execSQL(
                            "UPDATE record_tags SET tag_id = ? WHERE tag_id = ?",
                            arrayOf(keepId, id)
                        )
                        toDelete.add(id)
                        if (isSystem) {
                            systemByName[normalized] = true
                        }
                    }
                }
            }
            updateName.forEach { (id, normalized) ->
                // If normalized already belongs to another id, we already merged; skip.
                val keepId = keepByName[normalized]
                if (keepId != null && keepId == id) {
                    db.execSQL("UPDATE tags SET name = ? WHERE id = ?", arrayOf(normalized, id))
                }
            }
            systemByName.forEach { (name, isSystem) ->
                if (isSystem) {
                    val keepId = keepByName[name] ?: return@forEach
                    db.execSQL("UPDATE tags SET is_system = 1 WHERE id = ?", arrayOf(keepId))
                }
            }
            toDelete.forEach { id ->
                db.execSQL("DELETE FROM tags WHERE id = ?", arrayOf(id))
            }
            db.setTransactionSuccessful()
        } catch (_: SQLException) {
            // ignore
        } finally {
            try {
                db.endTransaction()
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    private fun normalizeTagName(input: String): String {
        val trimmed = input.trim().lowercase()
        if (trimmed.isEmpty()) {
            return trimmed
        }
        return if (trimmed.startsWith("#")) trimmed else "#$trimmed"
    }

    private fun execSafely(db: SQLiteDatabase, sql: String) {
        try {
            db.execSQL(sql)
        } catch (ex: SQLException) {
            // ignore
        }
    }

    companion object {
        private const val DATABASE_NAME = "prompt_recorder.db"
        private const val DATABASE_VERSION = 2
    }
}
