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
                val card = a.parents().firstOrNull { it.select("img").isNotEmpty() && it.select("a[href]").size <= 5 } ?: a.parent()
                val img = a.selectFirst("img") ?: card?.selectFirst("img") ?: continue
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
            "xfree" -> (path == "/video" && runCatching { URI(href).rawQuery.orEmpty().contains("id=") }.getOrDefault(false)) || path.startsWith("/video/")
            "eporner" -> path.contains("/video/") || path.contains("/video-") || path.contains("/hd-porn/") || path.contains("/porn-video/")
            "xhamster" -> path.contains("/videos/") || path.contains("/shorts/")
            else -> false
        }
    }

    suspend fun resolve(item: VideoItem): StreamCandidate? = withContext(Dispatchers.IO) {
        try {
            val html = requestText(item.pageUrl, item.pageUrl) ?: return@withContext null
            if (item.siteId == "eporner") {
                resolveEporner(item, html)?.let { return@withContext it }
            }
            if (item.siteId == "xhamster") {
                resolveXHamster(item, html)?.let { return@withContext it }
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
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36",
            "Referer" to referer
        )
        return try {
            val headBuilder = Request.Builder().url(url).head()
            headers.forEach { (key, value) -> headBuilder.header(key, value) }
            val headOk = client.newCall(headBuilder.build()).execute().use { it.isSuccessful }
            if (headOk) true else {
                val ranged = Request.Builder().url(url).get()
                    .header("User-Agent", headers.getValue("User-Agent"))
                    .header("Referer", referer)
                    .header("Range", "bytes=0-0").build()
                client.newCall(ranged).execute().use { it.code == 200 || it.code == 206 }
            }
        } catch (_: Exception) {
            false
        }
    }


    private fun resolveXHamster(item: VideoItem, html: String): StreamCandidate? {
        return try {
            val initialText = Regex(
                """window\.initials\s*=\s*(\{.+?\})\s*;\s*</script>""",
                setOf(RegexOption.DOT_MATCHES_ALL)
            ).find(html)?.groupValues?.get(1) ?: return null
            val initials = JSONObject(initialText)
            val av1 = LinkedHashSet<String>()
            val standard = LinkedHashSet<String>()

            val videoModel = initials.optJSONObject("videoModel")
            val modelSources = videoModel?.optJSONObject("sources")
            if (modelSources != null) {
                val ids = modelSources.keys()
                while (ids.hasNext()) {
                    val formatId = ids.next()
                    val variants = modelSources.optJSONObject(formatId) ?: continue
                    val qualities = variants.keys()
                    while (qualities.hasNext()) {
                        val raw = variants.optString(qualities.next())
                        val decoded = decipherXHamsterUrl(raw) ?: continue
                        if (formatId.contains("av1", true) || decoded.contains(".av1.", true)) av1 += decoded
                        else standard += decoded
                    }
                }
            }

            val xplayerSources = initials.optJSONObject("xplayerSettings")?.optJSONObject("sources")
            val standardSources = xplayerSources?.optJSONObject("standard")
            if (standardSources != null) {
                val ids = standardSources.keys()
                while (ids.hasNext()) {
                    val formatId = ids.next()
                    val formats = standardSources.optJSONArray(formatId) ?: continue
                    for (i in 0 until formats.length()) {
                        val format = formats.optJSONObject(i) ?: continue
                        for (key in listOf("url", "fallback")) {
                            val raw = format.optString(key)
                            if (raw.isBlank()) continue
                            val decoded = decipherXHamsterUrl(raw) ?: continue
                            if (formatId.contains("av1", true) || decoded.contains(".av1.", true)) av1 += decoded
                            else standard += decoded
                        }
                    }
                }
            }

            val hlsSources = xplayerSources?.optJSONObject("hls")
            if (hlsSources != null) {
                for (key in listOf("url", "fallback")) {
                    val raw = hlsSources.optString(key)
                    if (raw.isBlank()) continue
                    decipherXHamsterUrl(raw)?.let { standard += it }
                }
            }

            val selectedAv1 = av1.firstOrNull { it.startsWith("https://") }
            val selected = selectedAv1 ?: standard.firstOrNull { it.startsWith("https://") } ?: return null
            StreamCandidate(
                url = selected,
                mimeType = if (selected.contains(".m3u8")) "application/x-mpegURL" else "video/mp4",
                codec = if (selectedAv1 != null) "AV1 variant decoded from XHamster player config; verify actual decoder" else "XHamster fallback; codec pending player inspection",
                referer = item.pageUrl,
                siteName = item.siteName
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun decipherXHamsterUrl(value: String): String? {
        return try {
            if (Regex("[0-9a-fA-F]{12,}").matches(value)) {
                return decipherXHamsterHex(value)
            }
            val uri = URI(value)
            val match = Regex("^/([0-9a-fA-F]{12,})([/,].+)$").find(uri.rawPath ?: "") ?: return if (uri.scheme == "https" && uri.host != null) value else null
            val encrypted = match.groupValues[1]
            val remainder = match.groupValues[2]
            val decoded = decipherXHamsterHex(encrypted) ?: return null
            value.replace("/" + encrypted + remainder, "/" + decoded + remainder)
        } catch (_: Exception) {
            null
        }
    }

    private fun decipherXHamsterHex(hex: String): String? {
        return try {
            val bytes = ByteArray(hex.length / 2) { index ->
                hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
            if (hex.length % 2 != 0 || bytes.size < 6) return null
            val algorithm = bytes[0].toInt() and 0xff
            val seed = java.nio.ByteBuffer.wrap(bytes.copyOfRange(1, 5))
                .order(java.nio.ByteOrder.LITTLE_ENDIAN).int
            val state = intArrayOf(seed)
            val output = ByteArray(bytes.size - 5)
            for (i in output.indices) output[i] = (bytes[i + 5].toInt() xor nextXHamsterByte(algorithm, state)).toByte()
            String(output, Charsets.ISO_8859_1)
        } catch (_: Exception) {
            null
        }
    }

    private fun nextXHamsterByte(algorithm: Int, state: IntArray): Int {
        var s = state[0]
        var nextState = s
        val output: Int
        when (algorithm) {
            1 -> {
                s = s * 1664525 + 1013904223
                nextState = s
                output = s
            }
            2 -> {
                s = s xor (s shl 13)
                s = s xor (s ushr 17)
                s = s xor (s shl 5)
                nextState = s
                output = s
            }
            3 -> {
                s += 0x9e3779b9.toInt()
                nextState = s
                s = s xor (s ushr 16)
                s *= 0x85ebca77.toInt()
                s = s xor (s ushr 13)
                s *= 0xc2b2ae3d.toInt()
                output = s xor (s ushr 16)
            }
            4 -> {
                s += 0x6d2b79f5
                nextState = s
                s = Integer.rotateLeft(s, 7)
                s += 0x9e3779b9.toInt()
                s = s xor (s ushr 11)
                output = s * 0x27d4eb2d
            }
            5 -> {
                s = s xor (s shl 7)
                s = s xor (s ushr 9)
                s = s xor (s shl 8)
                s += 0xa5a5a5a5.toInt()
                nextState = s
                output = s
            }
            6 -> {
                s = s * 0x2c9277b5 + 0xac564b05.toInt()
                nextState = s
                val mixed = s xor (s ushr 18)
                val shift = (s ushr 27) and 31
                output = mixed ushr shift
            }
            7 -> {
                s += 0x9e3779b9.toInt()
                nextState = s
                var e = s xor (s shl 5)
                e *= 0x7feb352d
                e = e xor (e ushr 15)
                output = e * 0x846ca68b.toInt()
            }
            else -> return 0
        }
        state[0] = nextState
        return output and 0xff
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
