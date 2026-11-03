package com.darknet.animetv.ui

import com.darknet.animetv.data.model.CustomSource
import com.darknet.animetv.ui.screens.isValidSource
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class SourcesScreenDataTest {

    @Test
    fun testCustomSourceValidation() {
        val validHttp = CustomSource(name = "Source 1", baseUrl = "http://my-anime.tv")
        val validHttps = CustomSource(name = "Source 2", baseUrl = "https://my-anime.tv/api")

        assertTrue(isValidSource(validHttp))
        assertTrue(isValidSource(validHttps))

        val blankName = CustomSource(name = "   ", baseUrl = "https://my-anime.tv")
        assertFalse("Blank name must be rejected", isValidSource(blankName))

        val invalidScheme = CustomSource(name = "FTP Source", baseUrl = "ftp://files.org")
        assertFalse("Non-http/https schemes must be rejected", isValidSource(invalidScheme))

        val malformedUrl = CustomSource(name = "Bad URL", baseUrl = "ht tp://invalid uri")
        assertFalse("Malformed URIs must be rejected", isValidSource(malformedUrl))

        val noHostUrl = CustomSource(name = "No Host", baseUrl = "https://")
        assertFalse("URL with no host must be rejected", isValidSource(noHostUrl))
    }

    @Test
    fun testSourcePriorityReordering() {
        val sources = mutableListOf(
            CustomSource(id = 1, name = "Aniwatch", baseUrl = "https://aniwatch.to"),
            CustomSource(id = 2, name = "Miruro", baseUrl = "https://miruro.cx"),
            CustomSource(id = 3, name = "AnimeFLV", baseUrl = "https://animeflv.net")
        )

        // Move AnimeFLV from index 2 to index 0 (top priority)
        fun moveItem(fromIndex: Int, toIndex: Int) {
            if (fromIndex in sources.indices && toIndex in sources.indices) {
                val item = sources.removeAt(fromIndex)
                sources.add(toIndex, item)
            }
        }

        moveItem(2, 0)
        assertEquals("AnimeFLV", sources[0].name)
        assertEquals("Aniwatch", sources[1].name)
        assertEquals("Miruro", sources[2].name)

        // Attempt move out of bounds (should gracefully no-op)
        moveItem(0, -1)
        assertEquals("AnimeFLV", sources[0].name)
    }

    @Test
    fun testJsonDslParsing() {
        val json = """
            {
                "name": "Custom DSL Scraper",
                "baseUrl": "https://custom-anime.com",
                "animeListSelector": "div.anime-card a",
                "episodeSelector": "ul.ep-list a",
                "streamUrlSelector": "iframe.player",
                "isEnabled": true,
                "sourceType": "HTML_SCRAPER"
            }
        """.trimIndent()

        val parsed = Gson().fromJson(json, CustomSource::class.java)
        assertNotNull(parsed)
        assertEquals("Custom DSL Scraper", parsed.name)
        assertEquals("https://custom-anime.com", parsed.baseUrl)
        assertEquals("div.anime-card a", parsed.animeListSelector)
        assertEquals("ul.ep-list a", parsed.episodeSelector)
        assertEquals("iframe.player", parsed.streamUrlSelector)
        assertTrue(parsed.isEnabled)
        assertEquals("HTML_SCRAPER", parsed.sourceType)
        assertTrue("Parsed JSON source must pass validation", isValidSource(parsed))
    }

    @Test
    fun testSourceToggleStateInversion() {
        val source = CustomSource(id = 10, name = "Test", baseUrl = "https://test.com", isEnabled = true)
        val toggled = source.copy(isEnabled = !source.isEnabled)
        assertFalse(toggled.isEnabled)

        val toggledAgain = toggled.copy(isEnabled = !toggled.isEnabled)
        assertTrue(toggledAgain.isEnabled)
    }
}
