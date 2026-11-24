package com.ibrahim.av1mix.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class WatchHistoryEntry(
    val providerId: String,
    val canonicalId: String,
    val title: String,
    val playbackUrl: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long?
)

class WatchHistoryStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("av1mix_local_history", Context.MODE_PRIVATE)

    fun add(video: NormalizedVideo) {
        val current = all().filterNot {
            it.providerId == video.providerId && it.canonicalId == video.canonicalId
        }.toMutableList()
        current.add(
            0,
            WatchHistoryEntry(
                providerId = video.providerId,
                canonicalId = video.canonicalId,
                title = video.title,
                playbackUrl = video.playbackUrl.orEmpty(),
                thumbnailUrl = video.thumbnailUrl,
                durationSeconds = video.durationSeconds
            )
        )
        val array = JSONArray()
        current.take(200).forEach { entry ->
            array.put(
                JSONObject()
                    .put("provider", entry.providerId)
                    .put("id", entry.canonicalId)
                    .put("title", entry.title)
                    .put("url", entry.playbackUrl)
                    .put("thumbnail", entry.thumbnailUrl)
                    .put("duration", entry.durationSeconds)
            )
        }
        preferences.edit().putString("items", array.toString()).apply()
    }

    fun all(): List<WatchHistoryEntry> {
        val array = runCatching {
            JSONArray(preferences.getString("items", "[]"))
        }.getOrDefault(JSONArray())
        return buildList {
            for (index in 0 until array.length()) {
                val row = array.optJSONObject(index) ?: continue
                val url = row.optString("url")
                if (!url.startsWith("https://")) continue
                add(
                    WatchHistoryEntry(
                        providerId = row.optString("provider"),
                        canonicalId = row.optString("id"),
                        title = row.optString("title"),
                        playbackUrl = url,
                        thumbnailUrl = row.optString("thumbnail").takeIf { it.startsWith("https://") },
                        durationSeconds = row.optLong("duration").takeIf { it > 0L }
                    )
                )
            }
        }
    }

    fun clear() {
        preferences.edit().remove("items").apply()
    }
}
