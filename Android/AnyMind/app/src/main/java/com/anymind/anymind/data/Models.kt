package com.anymind.anymind.data

import com.anymind.anymind.util.GroupingMode
import com.anymind.anymind.util.TagFilterMode
import java.time.Instant


data class Record(
    val id: String,
    val content: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val weekKey: String,
    val userTags: List<String>,
    val systemTags: List<String>,
    val deleted: Boolean,
    val serverRev: Long?,
    val lastSyncAt: Instant?,
    val syncEnabled: Boolean,
    val cloudDeletePending: Boolean
)

data class RecordSummary(
    val id: String,
    val contentPreview: String,
    val updatedAt: Instant,
    val weekKey: String,
    val tags: List<String>,
    val lastSyncAt: Instant?,
    val syncEnabled: Boolean
)

data class RecordSummaryRow(
    val id: String,
    val preview: String,
    val updatedAt: Instant,
    val tags: List<String>,
    val lastSyncAt: Instant?,
    val syncEnabled: Boolean
)

data class GroupSummary(
    val id: String,
    val count: Int
)

data class TagSummary(
    val name: String,
    val count: Int
)

data class RecordQuery(
    val groupKey: String?,
    val groupingMode: GroupingMode,
    val searchQuery: String?,
    val tags: List<String>,
    val tagMode: TagFilterMode
)
