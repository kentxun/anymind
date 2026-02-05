package com.anymind.anymind.sync

import android.content.Context
import com.anymind.anymind.util.Prefs
import java.util.UUID


data class SyncConfig(
    val baseUrl: String,
    val spaceId: String,
    val spaceSecret: String,
    val deviceId: String
) {
    companion object {
        fun load(context: Context): SyncConfig? {
            val prefs = Prefs.get(context)
            if (!prefs.getBoolean("sync_enabled", false)) {
                return null
            }
            val baseUrl = prefs.getString("sync_server_url", null)?.trim()
            val spaceId = prefs.getString("sync_space_id", null)?.trim()
            val spaceSecret = prefs.getString("sync_space_secret", null)?.trim()
            if (baseUrl.isNullOrEmpty() || spaceId.isNullOrEmpty() || spaceSecret.isNullOrEmpty()) {
                return null
            }
            var deviceId = prefs.getString("sync_device_id", null)
            if (deviceId.isNullOrEmpty()) {
                deviceId = UUID.randomUUID().toString()
                prefs.edit().putString("sync_device_id", deviceId).apply()
            }
            return SyncConfig(baseUrl, spaceId, spaceSecret, deviceId)
        }
    }
}
