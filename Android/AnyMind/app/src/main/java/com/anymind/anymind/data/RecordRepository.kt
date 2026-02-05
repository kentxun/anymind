package com.anymind.anymind.data

import android.content.Context
import com.anymind.anymind.sync.SyncChange
import com.anymind.anymind.sync.SyncClient
import com.anymind.anymind.sync.SyncConfig
import com.anymind.anymind.sync.SyncPullRequest
import com.anymind.anymind.sync.SyncPushRequest
import com.anymind.anymind.util.DateCodec
import com.anymind.anymind.util.GroupingMode
import com.anymind.anymind.util.SearchQueryBuilder
import com.anymind.anymind.util.TagFilterMode
import java.time.Instant


data class SyncResult(val success: Boolean, val message: String)

class RecordRepository(private val context: Context) {
    private val db = PromptDatabase(context)
    private val syncClient = SyncClient()

    fun fetchGroupSummaries(mode: GroupingMode): List<GroupSummary> {
        return db.fetchGroupSummaries(mode)
    }

    fun fetchTagSummaries(): List<TagSummary> {
        return db.fetchTagSummaries()
    }

    fun fetchRecordSummaries(
        groupKey: String?,
        groupingMode: GroupingMode,
        searchText: String,
        tags: List<String>,
        tagMode: TagFilterMode
    ): List<RecordSummary> {
        val normalizedTags = tags.map { it.trim().lowercase() }
        val query = RecordQuery(
            groupKey = groupKey,
            groupingMode = groupingMode,
            searchQuery = SearchQueryBuilder.make(searchText),
            tags = normalizedTags,
            tagMode = tagMode
        )
        val results = db.fetchRecordSummaries(query)
        if (normalizedTags.isEmpty() || results.isNotEmpty()) {
            return results
        }
        val fallbackQuery = query.copy(tags = emptyList())
        val all = db.fetchRecordSummaries(fallbackQuery)
        return if (tagMode == TagFilterMode.AND) {
            all.filter { summary ->
                val recordTags = summary.tags.map { it.trim().lowercase() }
                normalizedTags.all { recordTags.contains(it) }
            }
        } else {
            all.filter { summary ->
                val recordTags = summary.tags.map { it.trim().lowercase() }
                normalizedTags.any { recordTags.contains(it) }
            }
        }
    }

    fun fetchRecord(id: String): Record? = db.fetchRecord(id)

    fun createRecord(content: String): Record = db.createRecord(content)

    fun updateRecord(id: String, content: String): Record? = db.updateRecord(id, content)

    fun deleteRecord(id: String) = db.softDelete(id)

    fun setSyncEnabled(recordId: String, enabled: Boolean): Record? {
        val record = db.fetchRecord(recordId)
        val hasRemote = record?.serverRev != null || record?.lastSyncAt != null
        val markCloudDelete = !enabled && hasRemote
        return db.setSyncEnabled(recordId, enabled, markCloudDelete)
    }

    fun syncNow(): SyncResult {
        val config = SyncConfig.load(context) ?: return SyncResult(false, "Sync disabled or missing config")
        return try {
            val pending = db.fetchPendingSyncChanges()
            val pendingById = pending.associateBy { it.id }
            val pushChanges = pending.map { record ->
                SyncChange(
                    id = record.id,
                    content = record.content,
                    systemTags = record.systemTags,
                    userTags = record.userTags,
                    createdAt = DateCodec.encode(record.createdAt),
                    updatedAt = DateCodec.encode(record.updatedAt),
                    deleted = record.deleted || record.cloudDeletePending,
                    baseRev = record.serverRev
                )
            }

            var pushMaxRev = 0L
            if (pushChanges.isNotEmpty()) {
                val pushRequest = SyncPushRequest(
                    spaceId = config.spaceId,
                    spaceSecret = config.spaceSecret,
                    deviceId = config.deviceId,
                    changes = pushChanges
                )
                val pushResponse = syncClient.push(config.baseUrl, pushRequest)
                pushMaxRev = pushResponse.serverRevMax
                val syncTime = Instant.now()
                pushResponse.results.forEach { result ->
                    db.markSynced(result.id, result.serverRev, syncTime)
                    val original = pendingById[result.id]
                    if (original != null && original.cloudDeletePending) {
                        db.clearCloudDeletePending(result.id)
                    }
                    if (result.conflict && original != null) {
                        db.createConflictCopy(original)
                    }
                }
            }

            val cursor = db.loadSyncCursor()
            val pullRequest = SyncPullRequest(
                spaceId = config.spaceId,
                spaceSecret = config.spaceSecret,
                sinceRev = cursor,
                limit = 200
            )
            val pullResponse = syncClient.pull(config.baseUrl, pullRequest)
            pullResponse.changes.forEach { change ->
                db.applyRemoteChange(change)
            }
            val nextCursor = maxOf(pushMaxRev, pullResponse.serverRevMax)
            if (nextCursor > 0) {
                db.saveSyncCursor(nextCursor)
            }
            SyncResult(true, "Sync complete")
        } catch (ex: Exception) {
            SyncResult(false, "Sync failed: ${ex.message}")
        }
    }
}
