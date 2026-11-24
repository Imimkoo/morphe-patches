package com.ibrahim.av1mix.data

import android.content.Context
import com.google.gson.Gson

private data class PreferenceWeights(
    val tags: MutableMap<String, Int> = mutableMapOf(),
    val performers: MutableMap<String, Int> = mutableMapOf()
)

class LocalPreferenceStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("av1mix_local_preferences", Context.MODE_PRIVATE)
    private val gson = Gson()

    @Synchronized
    fun recordFeedback(video: NormalizedVideo, liked: Boolean) {
        val weights = load()
        val delta = if (liked) 2 else -2
        video.tags.forEach { tag ->
            val key = tag.trim().lowercase()
            if (key.isNotBlank()) weights.tags[key] = ((weights.tags[key] ?: 0) + delta).coerceIn(-20, 20)
        }
        video.performerIds.forEach { performer ->
            val key = performer.trim().lowercase()
            if (key.isNotBlank()) {
                val value = if (liked) 1 else -1
                weights.performers[key] = ((weights.performers[key] ?: 0) + value).coerceIn(-10, 10)
            }
        }
        preferences.edit().putString("weights", gson.toJson(weights)).apply()
    }

    @Synchronized
    fun score(video: NormalizedVideo): Int {
        val weights = load()
        val tagScore = video.tags.sumOf { weights.tags[it.trim().lowercase()] ?: 0 }
        val performerScore = video.performerIds.sumOf { weights.performers[it.trim().lowercase()] ?: 0 }
        return tagScore + performerScore
    }

    fun rank(videos: List<NormalizedVideo>): List<NormalizedVideo> =
        videos.withIndex()
            .sortedWith(compareByDescending<IndexedValue<NormalizedVideo>> { score(it.value) }.thenBy { it.index })
            .map { it.value }

    @Synchronized
    fun clear() {
        preferences.edit().remove("weights").apply()
    }

    private fun load(): PreferenceWeights =
        runCatching {
            gson.fromJson(preferences.getString("weights", null), PreferenceWeights::class.java)
        }.getOrNull() ?: PreferenceWeights()
}
