package com.darknet.animetv.source

import com.darknet.animetv.data.model.CustomSource
import com.darknet.animetv.data.source.DynamicHtmlSource
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicHtmlSourceTest {

    @Test
    fun testDynamicHtmlSourceFromJsonDsl() {
        val dslJson = """
            {
                "id": "my-custom-source",
                "name": "My Custom Anime",
                "baseUrl": "https://mycustomanime.example.com",
                "isEnabled": true,
                "headers": {
                    "User-Agent": "CustomApp/1.0",
                    "Referer": "https://mycustomanime.example.com/"
                },
                "search": {
                    "endpoint": "/browse?q={query}",
                    "itemSelector": "ul.anime-list li.item",
                    "titleSelector": "h3.title",
                    "posterSelector": "img.poster@src",
                    "idSelector": "a@href",
                    "idRegex": "/anime/([a-zA-Z0-9-]+)"
                },
                "episodes": {
                    "endpoint": "/anime/{animeId}",
                    "itemSelector": "ul.episodes li a",
                    "numberSelector": "@data-number",
                    "titleSelector": "span.ep-title",
                    "idSelector": "@href"
                },
                "streams": {
                    "endpoint": "/watch/{episodeId}",
                    "iframeSelector": "div.player iframe@src"
                }
            }
        """.trimIndent()

        val source = DynamicHtmlSource.fromJsonDsl(dslJson)
        assertEquals("my-custom-source", source.sourceId)
        assertEquals("My Custom Anime", source.sourceName)
        assertEquals("https://mycustomanime.example.com", source.baseUrl)
        assertTrue(source.isEnabled)
        assertEquals("CustomApp/1.0", source.config.headers["User-Agent"])
        assertEquals("ul.anime-list li.item", source.config.searchItemSelector)
    }

    @Test
    fun testDynamicHtmlSourceFromCustomSourceEntity() {
        val customSource = CustomSource(
            id = 42,
            name = "Test DB Source",
            baseUrl = "https://testsource.org",
            animeListSelector = "div.card",
            episodeSelector = "a.ep-link",
            streamUrlSelector = "video@src",
            isEnabled = true
        )

        val source = DynamicHtmlSource(customSource)
        assertEquals("custom-42", source.sourceId)
        assertEquals("Test DB Source", source.sourceName)
        assertEquals("https://testsource.org", source.baseUrl)
        assertTrue(source.isEnabled)
    }

    @Test
    fun testExtractValueAndMultiValues() {
        val dslJson = """
            {
                "id": "test-dsl",
                "name": "Test DSL",
                "baseUrl": "https://test.org"
            }
        """.trimIndent()
        val source = DynamicHtmlSource.fromJsonDsl(dslJson)

        val html = """
            <div class="card">
                <a href="/anime/naruto-shippuden-100" data-number="12">
                    <img class="poster" src="https://test.org/poster.jpg" />
                    <h3 class="title">Naruto Shippuden</h3>
                </a>
            </div>
        """.trimIndent()
        val doc = Jsoup.parse(html)
        val cardEl = doc.selectFirst(".card")!!

        val title = source.extractValue(cardEl, "h3.title")
        assertEquals("Naruto Shippuden", title)

        val poster = source.extractValue(cardEl, "img.poster@src")
        assertEquals("https://test.org/poster.jpg", poster)

        val href = source.extractValue(cardEl, "a@href")
        assertEquals("/anime/naruto-shippuden-100", href)

        val dataNum = source.extractValue(cardEl, "a@data-number")
        assertEquals("12", dataNum)

        // Comma-separated fallback
        val fallback = source.extractValue(cardEl, "span.missing, h3.title")
        assertEquals("Naruto Shippuden", fallback)
    }
}
