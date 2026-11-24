package com.ibrahim.av1mix.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Uses AdultEmbed's documented v1 search and its own permanent iframe player.
 * No source-page scraping, direct-file extraction, or ad/player modification.
 *
 * Admission is limited to the provider's published app/API/embed workflow:
 * the v1 docs explicitly describe app integrations and official player URLs;
 * the service states that only approved, playable entries are returned and
 * documents removal/takedown synchronization and originating-provider 18+ records.
 */
class AdultEmbedProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()
) : VideoCatalogProvider {
    override val providerId: String = "adultembed"

    override val capabilities = ProviderCapabilities(
        playbackKind = PlaybackKind.OFFICIAL_EMBED,
        supportsSearch = true,
        supportsRelated = true,
        supportsRemovalFeed = true,
        supportsArabicMetadata = true,
        supportsOfflineDownload = false,
        supportsCodecSelection = false,
    )

    override val approval = ProviderApproval(
        termsReviewed = true,
        playbackPermissionConfirmed = true,
        moderationAndTakedownReviewed = true,
        adultContentProvenanceReviewed = true,
    )

    override suspend fun search(query: String, page: Int): List<NormalizedVideo> {
        val url = endpoint("search").newBuilder()
            .addQueryParameter("query", query.trim().take(120).ifBlank { null })
            .addQueryParameter("page", page.coerceAtLeast(1).toString())
            .addQueryParameter("per_page", "24")
            .addQueryParameter("quality", "complete")
            .addQueryParameter("lang", "ar")
            .addQueryParameter("sort", if (query.isBlank()) "popular" else "newest")
            .build()
        val json = getJson(url.toString())
        return parseResults(json.optJSONArray("results"))
    }

    override suspend fun related(canonicalId: String): List<NormalizedVideo> {
        val id = canonicalId.toLongOrNull() ?: return emptyList()
        val json = getJson(endpoint("videos/$id/related").toString())
        return parseResults(json.optJSONArray("results"))
    }

    override suspend fun isAvailable(canonicalId: String): Boolean {
        val id = canonicalId.toLongOrNull() ?: return false
        val json = getJson(endpoint("videos/$id/status").toString())
        return json.optBoolean("active", json.optBoolean("playable", false)) ||
            json.optString("status").equals("active", ignoreCase = true)
    }

    private fun parseResults(rows: JSONArray?): List<NormalizedVideo> = buildList {
        if (rows == null) return@buildList
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val id = row.optString("id").takeIf { it.isNotBlank() } ?: continue
            val embed = row.optJSONObject("embed")
            val playbackUrl = embed?.optString("url")
                ?.takeIf { isOfficialPlayerUrl(it, id) }
                ?: "https://adultembed.com/player/embed/$id/"
            val thumbnail = row.optString("thumbnail").takeIf { it.startsWith("https://") }
            val tags = readNames(row.optJSONArray("tags"))
            val performers = readNames(row.optJSONArray("performers"))
            val studio = row.optJSONObject("studio")
            val duration = row.optDouble("duration_seconds", -1.0)
                .takeIf { it >= 0.0 }?.toLong()
                ?: parseDuration(row.optString("duration"))
            val publishedAt = row.optString("published_at")
                .takeIf { it.isNotBlank() }
                ?.let { runCatching { Instant.parse(it).epochSecond }.getOrNull() }

            add(
                NormalizedVideo(
                    providerId = providerId,
                    canonicalId = id,
                    title = row.optString("title").ifBlank { "فيديو بدون عنوان" },
                    durationSeconds = duration,
                    thumbnailUrl = thumbnail,
                    canonicalPageUrl = playbackUrl,
                    tags = tags,
                    performerIds = performers,
                    studioId = studio?.optString("slug")?.takeIf { it.isNotBlank() },
                    updatedAtEpochSeconds = publishedAt,
                    playbackUrl = playbackUrl,
                )
            )
        }
    }

    private fun readNames(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.opt(index)
                val name = when (value) {
                    is JSONObject -> value.optString("slug").ifBlank { value.optString("name") }
                    else -> value?.toString().orEmpty()
                }
                if (name.isNotBlank()) add(name)
            }
        }.distinct().take(24)
    }

    private fun parseDuration(value: String): Long? {
        val parts = value.split(":").mapNotNull { it.toLongOrNull() }
        if (parts.isEmpty()) return null
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            1 -> parts[0]
            else -> null
        }
    }

    private fun isOfficialPlayerUrl(value: String, id: String): Boolean {
        val parsed = runCatching { value.toHttpUrl() }.getOrNull() ?: return false
        return parsed.isHttps &&
            parsed.host == "adultembed.com" &&
            parsed.encodedPath.startsWith("/player/embed/") &&
            parsed.encodedPath.contains(id)
    }

    private fun endpoint(path: String) =
        "https://adultembed.com/api/v1/$path".toHttpUrl()

    private fun getJson(url: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "AV1Mix-Android/0.2")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("AdultEmbed API returned HTTP ${response.code}")
            }
            JSONObject(response.body?.string() ?: "{}")
        }
    }
}
