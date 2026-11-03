package com.darknet.animetv.e2e.tier1

import com.darknet.animetv.data.source.AnimeSource
import com.darknet.animetv.e2e.harness.MockAnimeSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class Tier1_04_ScraperArchTest {

    private lateinit var source: AnimeSource

    @Before
    fun setUp() {
        source = MockAnimeSource(
            sourceId = "modular-test",
            sourceName = "Modular Test Source"
        )
    }

    @Test
    fun testSourceIdentifiersContract() {
        assertEquals("modular-test", source.sourceId)
        assertEquals("Modular Test Source", source.sourceName)
    }

    @Test
    fun testGetTrendingReturnsValidAnimeList() = runBlocking {
        val trending = source.getTrending()
        assertNotNull("Trending list must not be null", trending)
        assertFalse("Trending list should contain items", trending.isEmpty())
        assertTrue("Each item must have a valid non-empty title", trending.all { it.title.isNotEmpty() })
    }

    @Test
    fun testSearchFiltersMatchingTitles() = runBlocking {
        val results = source.search("Frieren")
        assertNotNull("Search result must not be null", results)
        assertTrue("Results should contain 'Frieren'", results.any { it.title.contains("Frieren", ignoreCase = true) })
    }

    @Test
    fun testGetAnimeDetailContainsEpisodes() = runBlocking {
        val detail = source.getAnimeDetail("anime-100")
        assertNotNull("Detail must not be null", detail)
        assertEquals("anime-100", detail?.anime?.id)
        assertFalse("Episodes list should not be empty", detail?.episodes.isNullOrEmpty())
    }

    @Test
    fun testGetEpisodeStreamsProvidesPlayableSources() = runBlocking {
        val streams = source.getEpisodeStreams("anime-100", "ep-100-1")
        assertNotNull("Streams list must not be null", streams)
        assertFalse("Should return at least one stream", streams.isEmpty())
        assertTrue("Stream must have valid server name", streams[0].serverName.isNotEmpty())
        assertTrue("Stream URL must be HTTP or HTTPS", streams[0].url.startsWith("http"))
    }
}
