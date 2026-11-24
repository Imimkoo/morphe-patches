package com.ibrahim.av1mix.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRegistryTest {
    private fun provider(id: String, approved: Boolean) = object : VideoCatalogProvider {
        override val providerId = id
        override val capabilities = ProviderCapabilities(
            playbackKind = PlaybackKind.OFFICIAL_EMBED,
            supportsSearch = true,
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
        override suspend fun search(query: String, page: Int) = emptyList<NormalizedVideo>()
        override suspend fun related(canonicalId: String) = emptyList<NormalizedVideo>()
        override suspend fun isAvailable(canonicalId: String) = false
    }

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
}
