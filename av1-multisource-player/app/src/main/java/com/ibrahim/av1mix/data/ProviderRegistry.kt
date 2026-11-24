package com.ibrahim.av1mix.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Central admission boundary. Unapproved sources never receive search or playback requests.
 * Individual provider failures are isolated so one broken source cannot blank the whole feed.
 */
class ProviderRegistry(providers: Collection<VideoCatalogProvider>) {
    val admittedProviders: List<VideoCatalogProvider> =
        providers.filter {
            it.approval.admitted &&
                it.capabilities.playbackKind != PlaybackKind.UNAVAILABLE
        }

    suspend fun search(query: String, page: Int = 1): List<NormalizedVideo> =
        coroutineScope {
            admittedProviders.map { provider ->
                async {
                    runCatching { provider.search(query, page) }
                        .getOrDefault(emptyList())
                }
            }.awaitAll()
                .flatten()
                .distinctBy { "${it.providerId}:${it.canonicalId}" }
        }

    suspend fun related(video: NormalizedVideo): List<NormalizedVideo> =
        admittedProviders
            .firstOrNull { it.providerId == video.providerId }
            ?.let { provider ->
                runCatching { provider.related(video.canonicalId) }
                    .getOrDefault(emptyList())
            }
            .orEmpty()

    suspend fun isAvailable(video: NormalizedVideo): Boolean {
        val provider = admittedProviders.firstOrNull { it.providerId == video.providerId }
            ?: return false
        return runCatching { provider.isAvailable(video.canonicalId) }
            .getOrDefault(false)
    }
}
