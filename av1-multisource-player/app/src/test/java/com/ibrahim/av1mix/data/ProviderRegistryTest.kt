package com.ibrahim.av1mix.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRegistryTest {
    private fun provider(
        id: String,
        approved: Boolean,
        supportsSearch: Boolean = true,
        playbackKind: PlaybackKind = PlaybackKind.OFFICIAL_EMBED
    ) = object : VideoCatalogProvider {
        override val providerId = id
        override val capabilities = ProviderCapabilities(
            playbackKind = playbackKind,
            supportsSearch = supportsSearch,
            supportsRelated = true,
            supportsRemovalFeed = true,
            supportsArabicMetadata = true,
        )
        override val approval = ProviderApproval(
            termsReviewed = approved,
            playbackPermissionConfirmed = approved,
            moderationAndTakedownReviewed = approved,
            adultContentProvenanceReviewed = approved,
        )
        override suspend fun search(query: String, page: Int) =
            listOf(video(id, "one"))
        override suspend fun related(canonicalId: String) = emptyList<NormalizedVideo>()
        override suspend fun isAvailable(canonicalId: String) = true
    }

    private fun video(providerId: String, id: String) = NormalizedVideo(
        providerId = providerId,
        canonicalId = id,
        title = "Fixture",
        durationSeconds = 60,
        thumbnailUrl = null,
        canonicalPageUrl = "https://example.invalid/$id",
        tags = emptyList(),
        performerIds = emptyList(),
        studioId = null,
        updatedAtEpochSeconds = null,
    )

    @Test
    fun onlyFullyApprovedProvidersAreAdmitted() {
        val registry = ProviderRegistry(
            listOf(provider("approved", true), provider("pending", false))
        )
        assertEquals(listOf("approved"), registry.admittedProviders.map { it.providerId })
    }

    @Test
    fun emptyRegistryIsSafe() {
        val registry = ProviderRegistry(emptyList())
        assertTrue(registry.admittedProviders.isEmpty())
    }

    @Test
    fun searchSkipsProvidersWithoutSearchCapability() = runBlocking {
        val registry = ProviderRegistry(
            listOf(provider("search", true), provider("no-search", true, supportsSearch = false))
        )
        assertEquals(listOf("search"), registry.search("fixture").map { it.providerId })
    }

    @Test
    fun duplicateProviderIdsAreCollapsed() {
        val registry = ProviderRegistry(
            listOf(provider("same", true), provider("same", true))
        )
        assertEquals(1, registry.admittedProviders.size)
    }

    @Test
    fun unavailableProvidersAreNeverAdmitted() {
        val registry = ProviderRegistry(
            listOf(provider("offline", true, playbackKind = PlaybackKind.UNAVAILABLE))
        )
        assertTrue(registry.admittedProviders.isEmpty())
    }
}
