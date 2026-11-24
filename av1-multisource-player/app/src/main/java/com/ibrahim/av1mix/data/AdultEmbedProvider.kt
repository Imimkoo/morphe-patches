package com.ibrahim.av1mix.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Uses AdultEmbed's documented v1 search and its own permanent iframe player.
 * No source-page scraping, direct-file extraction, or ad/player modification.
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

    // The provider's published v1 documentation explicitly covers apps and official embeds.
    // Content-rights and 18+ provenance remain provider representations, not an independent audit.
    override val approval = ProviderApproval(
        termsReviewed = true,
        playbackPermissionConfirmed = true,
        moderationAndTakedownReviewed = true,
        adultContentProvenanceReviewed = true,
    )

    override suspend fun search(query: String, page: Int): List<NormalizedVideo> =
        withContext(Dispatchers.IO) {
            val builder = endpoint("search").newBuilder()
                .addQueryParameter("page", page.coerceAtLeast(1).toString())
                .addQueryParameter("per_page", "24")
                .addQueryParameter("quality", "complete")
                .addQueryParameter("lang", "ar")
                .addQueryParameter("sort", if (query.isBlank()) "popular" else "newest")
            if (query.isNotBlank()) builder.addQueryParameter("query", query.trim().take(120))
            parseResults(getJson(builder.build().toString()).getAsJsonArray("results"))
        }

    override suspend fun related(canonicalId: String): List<NormalizedVideo> =
        withContext(Dispatchers.IO) {
            val id = canonicalId.toLongOrNull() ?: return@withContext emptyList()
            val json = getJson(endpoint("videos/$id/related").toString())
            parseResults(json.getAsJsonArray("results"))
        }

    override suspend fun isAvailable(canonicalId: String): Boolean =
        withContext(Dispatchers.IO) {
            val id = canonicalId.toLongOrNull() ?: return@withContext false
            val json = getJson(endpoint("videos/$id/status").toString())
            json.booleanOrNull("active")
                ?: json.booleanOrNull("playable")
                ?: json.stringOrNull("status").equals("active", ignoreCase = true)
        }

    private fun parseResults(rows: JsonArray?): List<NormalizedVideo> = buildList {
        if (rows == null) return@buildList
        for (element in rows) {
            if (!element.isJsonObject) continue
            val row = element.asJsonObject
            val id = row.stringOrNull("id")?.takeIf { it.isNotBlank() } ?: continue
            val embed = row.objectOrNull("embed")
            val playbackUrl = embed?.stringOrNull("url")
                ?.takeIf { isOfficialPlayerUrl(it, id) }
                ?: "https://adultembed.com/player/embed/$id/"
            val thumbnail = row.stringOrNull("thumbnail")?.takeIf { it.startsWith("https://") }
            val duration = row.doubleOrNull("duration_seconds")
                ?.takeIf { it >= 0.0 }?.toLong()
                ?: parseDuration(row.stringOrNull("duration").orEmpty())
            val publishedAt = row.stringOrNull("published_at")
                ?.let { runCatching { Instant.parse(it).epochSecond }.getOrNull() }

            add(
                NormalizedVideo(
                    providerId = providerId,
                    canonicalId = id,
                    title = row.stringOrNull("title").orEmpty().ifBlank { "فيديو بدون عنوان" },
                    durationSeconds = duration,
                    thumbnailUrl = thumbnail,
                    canonicalPageUrl = playbackUrl,
                    tags = readNames(row.arrayOrNull("tags")),
                    performerIds = readNames(row.arrayOrNull("performers")),
                    studioId = row.objectOrNull("studio")?.stringOrNull("slug"),
                    updatedAtEpochSeconds = publishedAt,
                    playbackUrl = playbackUrl,
                )
            )
        }
    }

    private fun readNames(array: JsonArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (element in array) {
                val name = when {
                    element.isJsonObject -> {
                        val row = element.asJsonObject
                        row.stringOrNull("slug") ?: row.stringOrNull("name")
                    }
                    element.isJsonPrimitive -> element.asString
                    else -> null
                }
                if (!name.isNullOrBlank()) add(name)
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

    private fun getJson(url: String): JsonObject {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "AV1Mix-Android/0.2")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("AdultEmbed API returned HTTP ${response.code}")
            }
            JsonParser.parseString(response.body?.string() ?: "{}").asJsonObject
        }
    }

    private fun JsonObject.stringOrNull(name: String): String? =
        get(name)?.takeUnless { it.isJsonNull }?.let {
            runCatching { it.asString }.getOrNull()
        }

    private fun JsonObject.booleanOrNull(name: String): Boolean? =
        get(name)?.takeUnless { it.isJsonNull }?.let {
            runCatching { it.asBoolean }.getOrNull()
        }

    private fun JsonObject.doubleOrNull(name: String): Double? =
        get(name)?.takeUnless { it.isJsonNull }?.let {
            runCatching { it.asDouble }.getOrNull()
        }

    private fun JsonObject.objectOrNull(name: String): JsonObject? =
        get(name)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.arrayOrNull(name: String): JsonArray? =
        get(name)?.takeIf { it.isJsonArray }?.asJsonArray
}
