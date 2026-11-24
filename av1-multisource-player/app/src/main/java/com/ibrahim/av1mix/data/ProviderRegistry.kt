package com.ibrahim.av1mix.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope

/**
 * Central admission boundary. No provider is queried until all independent approval gates pass.
 * A provider must also advertise the capability for the operation being requested.
 */
class ProviderRegistry(providers: Collection<VideoCatalogProvider>) {
    val admittedProviders: List<VideoCatalogProvider> =
        providers
            .filter { it.approval.admitted && it.capabilities.playbackKind != PlaybackKind.UNAVAILABLE }
            .distinctBy { it.providerId }

    suspend fun search(query: String, page: Int = 1): List<NormalizedVideo> =
        supervisorScope {
            admittedProviders
                .filter { it.capabilities.supportsSearch }
                .map { provider ->
                    async {
                        try {
                            provider.search(query, page)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }
                }
                .awaitAll()
                .flatten()
                .distinctBy { "${it.providerId}:${it.canonicalId}" }
        }

    suspend fun discover(): List<NormalizedVideo> =
        supervisorScope {
            admittedProviders
                .filter { it.capabilities.supportsSearch }
                .map { provider ->
                    async {
                        try {
                            provider.discover()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }
                }
                .awaitAll()
                .flatten()
                .distinctBy { "${it.providerId}:${it.canonicalId}" }
        }

    suspend fun related(video: NormalizedVideo): List<NormalizedVideo> {
        val provider = admittedProviders.firstOrNull {
            it.providerId == video.providerId && it.capabilities.supportsRelated
        } ?: return emptyList()
        return try {
            provider.related(video.canonicalId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun isAvailable(video: NormalizedVideo): Boolean {
        val provider = admittedProviders.firstOrNull { it.providerId == video.providerId }
            ?: return false
        return try {
            provider.isAvailable(video.canonicalId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }
}
