package com.ibrahim.av1mix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialEmbedPageTest {
    @Test
    fun createsTheDocumentedResponsiveIframe() {
        val html = OfficialEmbedPage.build("https://adultembed.com/player/embed/12345/")
        assertTrue(html.contains("<iframe"))
        assertTrue(html.contains("src=\"https://adultembed.com/player/embed/12345/\""))
        assertTrue(html.contains("allow=\"autoplay; fullscreen; encrypted-media; picture-in-picture\""))
        assertTrue(html.contains("allowfullscreen"))
        assertTrue(html.contains("width:100%; height:100%"))
    }

    @Test
    fun acceptsTheProviderReturnedPlayerHost() {
        assertTrue(OfficialEmbedPage.isAllowedPlayerUrl("https://pornalizer.app/player/embed/12345/"))
    }

    @Test
    fun wrapperUsesThePlayerOrigin() {
        assertEquals("https://pornalizer.app/", OfficialEmbedPage.origin("https://pornalizer.app/player/embed/12345/"))
        assertEquals("https://adultembed.com/", OfficialEmbedPage.origin("https://adultembed.com/player/embed/12345/"))
    }

    @Test
    fun rejectsNonHttpsAndUntrustedHosts() {
        assertFalse(OfficialEmbedPage.isAllowedPlayerUrl("http://adultembed.com/player/embed/12345/"))
        assertFalse(OfficialEmbedPage.isAllowedPlayerUrl("https://evil.example/player/embed/12345/"))
        assertFalse(OfficialEmbedPage.isAllowedPlayerUrl("https://adultembed.com.evil.example/player/embed/12345/"))
    }

    @Test
    fun rejectsPathConfusionAndNonNumericIds() {
        assertFalse(OfficialEmbedPage.isAllowedPlayerUrl("https://adultembed.com/player/embed/12345/extra"))
        assertFalse(OfficialEmbedPage.isAllowedPlayerUrl("https://adultembed.com/player/embed/abc/"))
        assertFalse(OfficialEmbedPage.isAllowedPlayerUrl("https://user@adultembed.com/player/embed/12345/"))
    }
}
