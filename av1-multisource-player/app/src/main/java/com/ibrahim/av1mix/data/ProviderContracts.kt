package com.ibrahim.av1mix.data

/**
 * A provider must declare exactly what kind of playback it is allowed to expose.
 * Metadata availability alone never grants playback or download rights.
 */
enum class PlaybackKind {
    OFFICIAL_EMBED,
    AUTHORIZED_DIRECT_STREAM,
    EXTERNAL_ONLY,
    UNAVAILABLE
}

data class ProviderApproval(
    val termsReviewed: Boolean,
    val playbackPermissionConfirmed: Boolean,
    val moderationAndTakedownReviewed: Boolean,
    val adultContentProvenanceReviewed: Boolean,
) {
    val admitted: Boolean
        get() = termsReviewed &&
            playbackPermissionConfirmed &&
            moderationAndTakedownReviewed &&
            adultContentProvenanceReviewed
}

data class ProviderCapabilities(
    val playbackKind: PlaybackKind,
    val supportsSearch: Boolean,
    val supportsRelated: Boolean,
    val supportsRemovalFeed: Boolean,
    val supportsArabicMetadata: Boolean,
    val supportsOfflineDownload: Boolean = false,
    val supportsCodecSelection: Boolean = false,
)

data class NormalizedVideo(
    val providerId: String,
    val canonicalId: String,
    val title: String,
    val durationSeconds: Long?,
    val thumbnailUrl: String?,
    val canonicalPageUrl: String,
    val tags: List<String>,
    val performerIds: List<String>,
    val studioId: String?,
    val updatedAtEpochSeconds: Long?,
    /** Official provider player URL; never a guessed or scraped media-file URL. */
    val playbackUrl: String? = null,
)

/**
 * Implementations must use documented provider interfaces only.
 * No HTML scraping, hidden endpoints, DRM or access-control bypass.
 */
interface VideoCatalogProvider {
    val providerId: String
    val capabilities: ProviderCapabilities
    val approval: ProviderApproval

    suspend fun search(query: String, page: Int): List<NormalizedVideo>
    suspend fun related(canonicalId: String): List<NormalizedVideo>
    suspend fun isAvailable(canonicalId: String): Boolean
}
