package com.ibrahim.av1mix.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URI
import java.math.BigInteger
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SiteRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(18, TimeUnit.SECONDS)
        .readTimeout(22, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun loadFeed(mode: FeedMode): List<VideoItem> = coroutineScope {
        SiteCatalog.sites.filter { it.mode == mode }.map { site ->
            async(Dispatchers.IO) { loadSite(site) }
        }.awaitAll().flatten().distinctBy { it.pageUrl }.take(80)
    }

    private fun loadSite(site: SourceSite): List<VideoItem> {
        return try {
            val html = requestText(site.homeUrl, site.homeUrl) ?: return emptyList()
            val doc = Jsoup.parse(html, site.homeUrl)
            val found = LinkedHashMap<String, VideoItem>()
            for (a in doc.select("a[href]")) {
                val href = a.absUrl("href").trim()
                if (!isLikelyVideoPage(site, href)) continue
                val img = a.selectFirst("img") ?: continue
                val title = sequenceOf(a.attr("title"), a.attr("aria-label"), img.attr("alt"), a.text())
                    .map { it.trim().replace(Regex("\\s+"), " ") }
                    .firstOrNull { it.length >= 4 } ?: continue
                val thumb = sequenceOf("data-src", "data-original", "data-lazy-src", "src")
                    .map { img.absUrl(it) }.firstOrNull { it.startsWith("http") }
                val tags = Regex("#[\\p{L}\\p{N}_-]+").findAll(a.text() + " " + img.attr("alt"))
                    .map { it.value.removePrefix("#").lowercase() }.distinct().take(12).toList()
                found.putIfAbsent(href, VideoItem(
                    id = site.id + ":" + href.hashCode(), siteId = site.id, siteName = site.name,
                    title = title, pageUrl = href, thumbnailUrl = thumb, mode = site.mode,
                    codecEvidence = site.codecEvidence, tags = tags
                ))
                if (found.size >= 32) break
            }
            found.values.toList()
        } catch (_: Exception) { emptyList() }
    }

    private fun isLikelyVideoPage(site: SourceSite, href: String): Boolean {
        if (!href.startsWith("http")) return false
        val host = runCatching { URI(href).host.orEmpty().lowercase() }.getOrDefault("")
        val allowed = when (site.id) {
            "xfree" -> host.endsWith("xfree.com")
            "eporner" -> host.endsWith("eporner.com")
            "xhamster" -> host.endsWith("xhamster.com")
            else -> false
        }
        if (!allowed || href == site.homeUrl || href.contains("#")) return false
        val path = runCatching { URI(href).path.orEmpty().lowercase() }.getOrDefault("")
        if (listOf("/tag", "/category", "/search", "/user", "/channel", "/login", "/signup", "/playlist").any { path.startsWith(it) }) return false
        return when (site.id) {
            "xfree" -> path.length > 8 && !path.endsWith(".jpg") && !path.endsWith(".png")
            "eporner" -> path.contains("/video/") || path.contains("/video-") || path.contains("/hd-porn/") || path.contains("/porn-video/")
            "xhamster" -> path.contains("/videos/")
            else -> false
        }
    }

    suspend fun resolve(item: VideoItem): StreamCandidate? = withContext(Dispatchers.IO) {
        try {
            val html = requestText(item.pageUrl, item.pageUrl) ?: return@withContext null
            if (item.siteId == "eporner") {
                resolveEporner(item, html)?.let { return@withContext it }
            }
            val doc = Jsoup.parse(html, item.pageUrl)
            val urls = LinkedHashSet<String>()
            doc.select("video[src], video source[src], source[src], video[data-src]").forEach { el ->
                listOf("src", "data-src").forEach { key ->
                    val value = el.absUrl(key)
                    if (value.startsWith("http")) urls += value
                }
            }
            val normalizedHtml = html.replace("\\/", "/").replace("\\u0026", "&")
            val urlRegex = Regex("https?://[^\\s\\\"'<>]+\\.(?:mp4|m3u8|mpd)(?:\\?[^\\s\\\"'<>]*)?", RegexOption.IGNORE_CASE)
            urlRegex.findAll(normalizedHtml).forEach { match ->
                if (match.value.startsWith("http")) urls += match.value
            }
            val hasAv1 = html.contains("av1", ignoreCase = true) || html.contains("av01", ignoreCase = true)
            val selected = urls.sortedByDescending { url ->
                val u = url.lowercase()
                when {
                    u.contains("av1") || u.contains("av01") -> 4
                    u.endsWith(".m3u8") -> 3
                    u.endsWith(".mpd") -> 2
                    u.endsWith(".mp4") -> 1
                    else -> 0
                }
            }.firstOrNull() ?: return@withContext null
            val lower = selected.lowercase()
            val codec = when {
                lower.contains("av1") || lower.contains("av01") -> "AV1 (URL hint)"
                hasAv1 -> "AV1 mentioned in page; selected stream unverified"
                else -> "Codec pending player inspection"
            }
            StreamCandidate(
                url = selected,
                mimeType = when {
                    lower.contains(".m3u8") -> "application/x-mpegURL"
                    lower.contains(".mpd") -> "application/dash+xml"
                    else -> "video/mp4"
                },
                codec = codec, referer = item.pageUrl, siteName = item.siteName
            )
        } catch (_: Exception) { null }
    }


    private fun resolveEporner(item: VideoItem, html: String): StreamCandidate? {
        return try {
            val path = URI(item.pageUrl).path.orEmpty()
            val idMatch = Regex("(?:hd-porn|embed)/([^/]+)|video-([^/]+)").find(path) ?: return null
            val videoId = idMatch.groupValues[1].ifEmpty { idMatch.groupValues[2] }
            val hash = Regex("""hash\s*[:=]\s*["']([0-9a-fA-F]{32})""").find(html)?.groupValues?.get(1) ?: return null
            val encodedHash = hash.chunked(8).joinToString("") { BigInteger(it, 16).toString(36) }
            val apiUrl = "https://www.eporner.com/xhr/video/" + videoId +
                "?hash=" + encodedHash + "&device=generic&domain=www.eporner.com&fallback=false"
            val payload = requestText(apiUrl, item.pageUrl) ?: return null
            val sources = JSONObject(payload).optJSONObject("sources") ?: return null
            val hasAv1 = Jsoup.parse(html).getElementsByClass("download-av1").isNotEmpty()
            val standard = mutableListOf<String>()
            val av1 = mutableListOf<String>()
            val hls = mutableListOf<String>()
            val kinds = sources.keys()
            while (kinds.hasNext()) {
                val kind = kinds.next()
                val formats = sources.optJSONObject(kind) ?: continue
                val formatKeys = formats.keys()
                while (formatKeys.hasNext()) {
                    val format = formats.optJSONObject(formatKeys.next()) ?: continue
                    val src = format.optString("src")
                    if (!src.startsWith("https://")) continue
                    if (kind == "hls") {
                        hls += src
                    } else {
                        standard += src
                        if (hasAv1 && src.endsWith(".mp4")) av1 += src.removeSuffix(".mp4") + "-av1.mp4"
                    }
                }
            }
            val workingAv1 = av1.firstOrNull { isReachable(it, item.pageUrl) }
            if (workingAv1 != null) {
                return StreamCandidate(workingAv1, "video/mp4", "AV1 variant advertised by Eporner; verify decoder at runtime", item.pageUrl, item.siteName)
            }
            val fallback = standard.firstOrNull() ?: hls.firstOrNull() ?: return null
            StreamCandidate(
                fallback,
                if (fallback.contains(".m3u8")) "application/x-mpegURL" else "video/mp4",
                "Eporner fallback stream; codec must be read from player",
                item.pageUrl,
                item.siteName
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun isReachable(url: String, referer: String): Boolean {
        return try {
            val request = Request.Builder().url(url).head()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36")
                .header("Referer", referer).build()
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    private fun requestText(url: String, referer: String): String? {
        val request = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Referer", referer).build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.string()
        }
    }
}
