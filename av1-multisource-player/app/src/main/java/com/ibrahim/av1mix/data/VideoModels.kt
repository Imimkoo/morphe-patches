package com.ibrahim.av1mix.data

enum class FeedMode { SHORT, LONG }
data class SourceSite(val id: String, val name: String, val homeUrl: String, val mode: FeedMode, val codecEvidence: String)
data class VideoItem(
    val id: String, val siteId: String, val siteName: String, val title: String,
    val pageUrl: String, val thumbnailUrl: String?, val mode: FeedMode,
    val codecEvidence: String, val tags: List<String> = emptyList()
)
data class StreamCandidate(
    val url: String, val mimeType: String?, val codec: String,
    val referer: String, val siteName: String
)
