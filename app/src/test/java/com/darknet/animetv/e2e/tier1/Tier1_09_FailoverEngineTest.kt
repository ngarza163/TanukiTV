package com.darknet.animetv.e2e.tier1

import com.darknet.animetv.e2e.harness.MockAnimeSource
import com.darknet.animetv.e2e.harness.MockFailoverRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

class Tier1_09_FailoverEngineTest {

    private lateinit var repo: MockFailoverRepository
    private lateinit var primarySource: MockAnimeSource
    private lateinit var secondarySource: MockAnimeSource

    @Before
    fun setUp() {
        primarySource = MockAnimeSource(sourceId = "primary", sourceName = "Primary Source")
        secondarySource = MockAnimeSource(sourceId = "secondary", sourceName = "Secondary Source")
        repo = MockFailoverRepository(mutableListOf(primarySource, secondarySource))
    }

    @Test
    fun testPrimarySourceSuccessShortCircuits() = runBlocking {
        val results = repo.searchWithFailover("Frieren")
        assertFalse(results.isEmpty())
        assertEquals(1, primarySource.searchCallCount)
        assertEquals(0, secondarySource.searchCallCount)
    }

    @Test
    fun testFailoverToSecondaryWhenPrimaryThrows() = runBlocking {
        primarySource.shouldFail = true
        primarySource.failureException = IOException("HTTP 503 Service Unavailable")

        val results = repo.searchWithFailover("Frieren")
        assertFalse("Should get results from secondary source", results.isEmpty())
        assertEquals(1, primarySource.searchCallCount)
        assertEquals(1, secondarySource.searchCallCount)
    }

    @Test
    fun testAllSourcesFailingReturnsEmptyGracefully() = runBlocking {
        primarySource.shouldFail = true
        secondarySource.shouldFail = true

        val results = repo.searchWithFailover("Frieren")
        assertTrue("Should return empty list gracefully when all fail", results.isEmpty())
    }

    @Test
    fun testPreferredSourcePrioritizedInStreamResolution() = runBlocking {
        val streams = repo.getEpisodeStreamsWithFailover(
            animeId = "anime-1",
            episodeId = "ep-1",
            preferredSourceId = "secondary"
        )
        assertFalse(streams.isEmpty())
        assertEquals(1, secondarySource.streamsCallCount)
        assertEquals(0, primarySource.streamsCallCount)
    }

    @Test
    fun testConnectionTesterAndLatencyProbe() = runBlocking {
        primarySource.simulatedLatencyMs = 45L
        secondarySource.simulatedLatencyMs = 120L
        secondarySource.shouldFail = true

        val healthMap = repo.testAllSources()
        assertEquals(2, healthMap.size)

        val primaryHealth = healthMap["primary"]
        assertNotNull(primaryHealth)
        assertTrue(primaryHealth!!.isAvailable)
        assertEquals(45L, primaryHealth.latencyMs)

        val secondaryHealth = healthMap["secondary"]
        assertNotNull(secondaryHealth)
        assertFalse(secondaryHealth!!.isAvailable)
        assertEquals(120L, secondaryHealth.latencyMs)
    }
}
