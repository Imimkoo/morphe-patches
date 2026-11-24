package com.ibrahim.av1mix.data

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdultEmbedProviderTest {
    private fun provider(json: String, requests: MutableList<String> = mutableListOf()) =
        AdultEmbedProvider(
            okhttp3.OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain ->
                    requests += chain.request().url.toString()
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(json.toResponseBody("application/json".toMediaType()))
                        .build()
                })
                .build()
        )

    @Test
    fun searchMapsOnlyTheOfficialPlayerUrl() = runBlocking {
        val requests = mutableListOf<String>()
        val source = provider(
            """{"results":[{"id":12345,"title":"Fixture title","duration_seconds":95,"thumbnail":"https://adultembed.com/thumb.webp","tags":[{"slug":"tag-one"}],"performers":[{"slug":"performer-one"}],"studio":{"slug":"studio-one"},"published_at":"2026-01-02T03:04:05Z","embed":{"url":"https://adultembed.com/player/embed/12345/"}}]}""",
            requests
        )

        val result = source.search("fixture", 1).single()

        assertEquals("12345", result.canonicalId)
        assertEquals("Fixture title", result.title)
        assertEquals(95L, result.durationSeconds)
        assertEquals(listOf("tag-one"), result.tags)
        assertEquals(listOf("performer-one"), result.performerIds)
        assertEquals("studio-one", result.studioId)
        assertEquals("https://adultembed.com/player/embed/12345/", result.playbackUrl)
        assertTrue(requests.single().contains("lang=ar"))
        assertTrue(requests.single().contains("query=fixture"))
    }

    @Test
    fun providerDoesNotClaimDirectStreamOfflineOrCodecControl() {
        val source = provider("""{"results":[]}""")
        assertEquals(PlaybackKind.OFFICIAL_EMBED, source.capabilities.playbackKind)
        assertFalse(source.capabilities.supportsOfflineDownload)
        assertFalse(source.capabilities.supportsCodecSelection)
    }

    @Test
    fun statusEndpointControlsAvailability() = runBlocking {
        val active = provider("""{"active":true}""")
        val inactive = provider("""{"active":false}""")
        assertTrue(active.isAvailable("12345"))
        assertFalse(inactive.isAvailable("12345"))
    }

    @Test
    fun playerUrlMustMatchTheExactVideoId() = runBlocking {
        val source = provider(
            """{"results":[{"id":1234,"title":"Fixture","embed":{"url":"https://adultembed.com/player/embed/12345/"}}]}"""
        )
        assertEquals(
            "https://adultembed.com/player/embed/1234/",
            source.search("", 1).single().playbackUrl
        )
    }

    @Test
    fun unsafeEmbedHostIsReplacedByProviderCanonicalPlayer() = runBlocking {
        val source = provider(
            """{"results":[{"id":12345,"title":"Fixture","embed":{"url":"https://evil.invalid/player/embed/12345/"}}]}"""
        )
        assertEquals(
            "https://adultembed.com/player/embed/12345/",
            source.search("", 1).single().playbackUrl
        )
    }
}
