package com.anymind.anymind.sync

import com.google.gson.annotations.SerializedName


data class SyncChange(
    @SerializedName("id") val id: String,
    @SerializedName("content") val content: String,
    @SerializedName("system_tags") val systemTags: List<String>,
    @SerializedName("user_tags") val userTags: List<String>,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("deleted") val deleted: Boolean,
    @SerializedName("base_rev") val baseRev: Long?
)

data class SyncPushRequest(
    @SerializedName("space_id") val spaceId: String,
    @SerializedName("space_secret") val spaceSecret: String,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("changes") val changes: List<SyncChange>
)

data class SyncPushResult(
    @SerializedName("id") val id: String,
    @SerializedName("server_rev") val serverRev: Long,
    @SerializedName("server_updated_at") val serverUpdatedAt: String,
    @SerializedName("conflict") val conflict: Boolean
)

data class SyncPushResponse(
    @SerializedName("results") val results: List<SyncPushResult>,
    @SerializedName("server_rev_max") val serverRevMax: Long
)

data class SyncPullRequest(
    @SerializedName("space_id") val spaceId: String,
    @SerializedName("space_secret") val spaceSecret: String,
    @SerializedName("since_rev") val sinceRev: Long,
    @SerializedName("limit") val limit: Int
)

data class SyncPullChange(
    @SerializedName("id") val id: String,
    @SerializedName("content") val content: String,
    @SerializedName("system_tags") val systemTags: List<String>,
    @SerializedName("user_tags") val userTags: List<String>,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("deleted") val deleted: Boolean,
    @SerializedName("server_rev") val serverRev: Long,
    @SerializedName("server_updated_at") val serverUpdatedAt: String
)

data class SyncPullResponse(
    @SerializedName("changes") val changes: List<SyncPullChange>,
    @SerializedName("server_rev_max") val serverRevMax: Long
)

data class SpaceCreateRequest(
    @SerializedName("name") val name: String?
)

data class SpaceCreateResponse(
    @SerializedName("space_id") val spaceId: String,
    @SerializedName("space_secret") val spaceSecret: String,
    @SerializedName("created_at") val createdAt: String
)
