package com.darknet.animetv.e2e.tier1

import com.darknet.animetv.e2e.harness.DynamicHtmlSourceEngine
import com.darknet.animetv.e2e.harness.E2eTestFixtures
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class Tier1_08_CustomSourceDslTest {

    private lateinit var engine: DynamicHtmlSourceEngine

    @Before
    fun setUp() {
        engine = DynamicHtmlSourceEngine()
    }

    @Test
    fun testCustomSourceModelInstantiation() {
        val customSource = E2eTestFixtures.createSampleCustomSource(
            id = 42,
            name = "Community Scraper",
            baseUrl = "https://community.anime.mock"
        )
        assertEquals(42L, customSource.id)
        assertEquals("Community Scraper", customSource.name)
        assertTrue(customSource.isEnabled)
    }

    @Test
    fun testDslHtmlAnimeListParsing() {
        val customSource = E2eTestFixtures.createSampleCustomSource()
        val mockHtml = """
            <div class="catalog">
                <div class="card"><a href="/watch/one-piece">One Piece Wano</a></div>
                <div class="card"><a href="/watch/bleach-tybw">Bleach TYBW</a></div>
            </div>
        """.trimIndent()

        val parsedList = engine.parseAnimeList(mockHtml, customSource)
        assertEquals(2, parsedList.size)
        assertEquals("One Piece Wano", parsedList[0].title)
        assertEquals("Bleach TYBW", parsedList[1].title)
    }

    @Test
    fun testDslStreamUrlSelectorExtraction() {
        val customSource = E2eTestFixtures.createSampleCustomSource(
            streamUrlSelector = "div.player iframe"
        )
        val mockHtml = """
            <div class="player">
                <iframe src="https://embed.streamserver.mock/v/9988"></iframe>
            </div>
        """.trimIndent()

        val streamUrl = engine.parseStreamUrl(mockHtml, customSource)
        assertNotNull("Stream URL must be extracted", streamUrl)
        assertEquals("https://embed.streamserver.mock/v/9988", streamUrl)
    }

    @Test
    fun testDslFallbackWhenSelectorMatchesNothing() {
        val customSource = E2eTestFixtures.createSampleCustomSource(
            animeListSelector = "div.nonexistent-class a"
        )
        val mockHtml = "<div><p>Empty page</p></div>"
        val parsedList = engine.parseAnimeList(mockHtml, customSource)
        assertTrue("List should be empty when selector doesn't match", parsedList.isEmpty())
    }

    @Test
    fun testDisabledCustomSourceSkipped() {
        val disabledSource = E2eTestFixtures.createSampleCustomSource(isEnabled = false)
        assertFalse("Disabled custom source should have isEnabled=false", disabledSource.isEnabled)
    }
}
