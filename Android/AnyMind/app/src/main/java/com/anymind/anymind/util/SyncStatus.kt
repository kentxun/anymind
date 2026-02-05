package com.anymind.anymind.util

import com.anymind.anymind.R
import java.time.Instant


data class SyncIcon(val iconRes: Int, val tintRes: Int, val label: String)

object SyncStatus {
    fun icon(updatedAt: Instant, lastSyncAt: Instant?, syncEnabled: Boolean): SyncIcon {
        if (!syncEnabled) {
            return SyncIcon(R.drawable.ic_cloud_off, R.color.sync_red, "Sync disabled")
        }
        if (lastSyncAt == null || updatedAt.isAfter(lastSyncAt)) {
            return SyncIcon(R.drawable.ic_cloud, R.color.sync_gray, "Not synced")
        }
        return SyncIcon(R.drawable.ic_cloud_done, R.color.sync_blue, "Synced")
    }
}
