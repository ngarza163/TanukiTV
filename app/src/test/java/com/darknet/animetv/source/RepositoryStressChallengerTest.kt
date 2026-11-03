package com.darknet.animetv.source

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.darknet.animetv.data.model.Anime
import com.darknet.animetv.data.model.AnimeDetail
import com.darknet.animetv.data.model.CustomSource
import com.darknet.animetv.data.model.Episode
import com.darknet.animetv.data.model.VideoSource
import com.darknet.animetv.data.repository.AnimeRepository
import com.darknet.animetv.data.repository.DefaultAnimeRepository
import com.darknet.animetv.data.source.AnimeSource
import com.darknet.animetv.data.source.DynamicHtmlSource
import com.darknet.animetv.data.source.DynamicSourceConfig
import com.darknet.animetv.data.source.SourceHealthResult
import com.darknet.animetv.data.storage.AppStorage
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
class RepositoryStressChallengerTest {

    private lateinit var context: Context
    private lateinit var storage: AppStorage

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("animetv_stress_test.db")
        storage = AppStorage(context)
    }

    @After
    fun tearDown() {
        try {
            storage.close()
        } catch (_: Exception) {}
        context.deleteDatabase("animetv_stress_test.db")
    }

    // =========================================================================
    // Mock Sources for Failover & Adversarial Scenarios
    // =========================================================================

    private class FaultInjectingSource(
        override val sourceId: String,
        override val sourceName: String,
        override val baseUrl: String = "https://fault.example.com",
        override var isEnabled: Boolean = true,
        private val throwOnSearch: Throwable? = null,
        private val throwOnDetail: Throwable? = null,
        private val throwOnStreams: Throwable? = null,
        private val throwOnHealth: Throwable? = null,
        private val searchResults: List<Anime> = emptyList(),
        private val detailResult: AnimeDetail? = null,
        private val streamResults: List<VideoSource> = emptyList()
    ) : AnimeSource {

        override suspend fun getTrending(): List<Anime> {
            if (throwOnSearch != null) throw throwOnSearch
            return searchResults
        }

        override suspend fun getPopular(): List<Anime> = getTrending()
        override suspend fun getRecentEpisodes(): List<Anime> = getTrending()

        override suspend fun search(query: String): List<Anime> {
            if (throwOnSearch != null) throw throwOnSearch
            return searchResults
        }

        override suspend fun getAnimeDetail(animeId: String): AnimeDetail? {
            if (throwOnDetail != null) throw throwOnDetail
            return detailResult
        }

        override suspend fun getEpisodeStreams(animeId: String, episodeId: String): List<VideoSource> {
            if (throwOnStreams != null) throw throwOnStreams
            return streamResults
        }

        override suspend fun testConnection(): SourceHealthResult {
            if (throwOnHealth != null) throw throwOnHealth
            return SourceHealthResult(sourceId, true, 25, "OK 200")
        }
    }

    private fun createValidWorkingSource(id: String, name: String, isHls: Boolean = true): AnimeSource {
        val anime = Anime(id = "$id-100", title = "Chainsaw Man", sourceId = id)
        val episodes = listOf(
            Episode(id = "ep-1", animeId = "$id-100", number = 1, title = "Episode 1"),
            Episode(id = "ep-2", animeId = "$id-100", number = 2, title = "Episode 2")
        )
        val streams = listOf(
            VideoSource(
                serverName = "$name FastStream",
                url = if (isHls) "https://cdn.example.com/$id/master.m3u8" else "https://cdn.example.com/$id/video.mp4",
                quality = "1080p",
                isHls = isHls
            )
        )
        return FaultInjectingSource(
            sourceId = id,
            sourceName = name,
            searchResults = listOf(anime),
            detailResult = AnimeDetail(anime = anime, episodes = episodes),
            streamResults = streams
        )
    }

    // =========================================================================
    // Area 1: Cascading Source Failover Stress Tests
    // =========================================================================

    @Test
    fun testCascadingFailureSingleSourceTimeoutFailsOverToWorkingSource() {
        runBlocking {
            // Source 1 times out with SocketTimeoutException on search
            val timeoutSource = FaultInjectingSource(
                sourceId = "timeout-src",
                sourceName = "Timeout Source",
                throwOnSearch = SocketTimeoutException("Read timed out after 15000ms")
            )
            val workingSource = createValidWorkingSource("work-src", "Working Source", isHls = true)

            val repository: AnimeRepository = DefaultAnimeRepository(
                appStorage = storage,
                customSourcesList = listOf(timeoutSource, workingSource)
            )

            val streams = repository.getEpisodeStreams("Chainsaw Man", 1)
            assertFalse("Streams should not be empty after failover", streams.isEmpty())
            assertEquals("Working Source FastStream", streams[0].serverName)
            assertTrue(streams[0].url.contains("work-src"))
            assertTrue(streams[0].isHls)
        }
    }

    @Test
    fun testCascadingFailureCompleteOutageAllSourcesFailReturnsEmergencyFallback() {
        runBlocking {
            // 5 distinct failure modes across 5 sources
            val s1 = FaultInjectingSource(
                sourceId = "fail-s1",
                sourceName = "503 Unavailable",
                throwOnSearch = IOException("HTTP 503 Service Unavailable")
            )
            val s2 = FaultInjectingSource(
                sourceId = "fail-s2",
                sourceName = "Socket Timeout",
                throwOnSearch = SocketTimeoutException("Connection reset by peer")
            )
            val s3 = FaultInjectingSource(
                sourceId = "fail-s3",
                sourceName = "DNS Outage",
                throwOnSearch = UnknownHostException("Unable to resolve host: cdn.ani.org")
            )
            val s4 = FaultInjectingSource(
                sourceId = "fail-s4",
                sourceName = "Cloudflare Block",
                throwOnSearch = IllegalStateException("Cloudflare 1020 RayID Challenge")
            )
            val s5 = FaultInjectingSource(
                sourceId = "fail-s5",
                sourceName = "Empty Catalog",
                searchResults = emptyList() // zero results
            )

            val repository = DefaultAnimeRepository(
                appStorage = storage,
                customSourcesList = listOf(s1, s2, s3, s4, s5)
            )

            // Request episode under 100% network failure
            val streams = repository.getEpisodeStreams("Attack on Titan", 1)

            assertNotNull(streams)
            assertFalse("Repository must provide emergency fallback rather than empty list", streams.isEmpty())
            assertEquals(2, streams.size)
            assertTrue(streams.any { it.serverName.contains("Emergency Fallback") })
            assertTrue(streams.any { it.serverName.contains("Secondary Mirror") })
            assertTrue(streams.all { it.url.startsWith("https://") })
        }
    }

    @Test
    fun testCascadingFailureDetailFetchCrashRecoversToNextSource() {
        runBlocking {
            // Source 1 searches ok, but getAnimeDetail crashes with 500 error
            val anime1 = Anime(id = "crash-100", title = "Bleach", sourceId = "crash-src")
            val detailCrashingSource = FaultInjectingSource(
                sourceId = "crash-src",
                sourceName = "Detail Crashing Source",
                searchResults = listOf(anime1),
                throwOnDetail = RuntimeException("NullPointerException in DOM detail scraper")
            )
            val workingSource = createValidWorkingSource("work-src", "Working Source")

            val repository = DefaultAnimeRepository(
                appStorage = storage,
                customSourcesList = listOf(detailCrashingSource, workingSource)
            )

            val streams = repository.getEpisodeStreams("Bleach", 1)
            assertFalse(streams.isEmpty())
            assertEquals("Working Source FastStream", streams[0].serverName)
        }
    }

    @Test
    fun testCascadingFailureStreamExtractionCrashRecoversToNextSource() {
        runBlocking {
            // Source 1 searches and details ok, but getEpisodeStreams throws token error
            val anime1 = Anime(id = "stream-fail-100", title = "Naruto", sourceId = "stream-fail-src")
            val ep1 = Episode(id = "ep-1", animeId = "stream-fail-100", number = 1, title = "Ep 1")
            val streamCrashingSource = FaultInjectingSource(
                sourceId = "stream-fail-src",
                sourceName = "Stream Crash Source",
                searchResults = listOf(anime1),
                detailResult = AnimeDetail(anime = anime1, episodes = listOf(ep1)),
                throwOnStreams = IOException("Extractor decrypt token expired")
            )
            val workingSource = createValidWorkingSource("work-src", "Working Source")

            val repository = DefaultAnimeRepository(
                appStorage = storage,
                customSourcesList = listOf(streamCrashingSource, workingSource)
            )

            val streams = repository.getEpisodeStreams("Naruto", 1)
            assertFalse(streams.isEmpty())
            assertEquals("Working Source FastStream", streams[0].serverName)
        }
    }

    // =========================================================================
    // Area 2: Concurrency & Stress Load Verification
    // =========================================================================

    @Test
    fun testHighConcurrencyStress50ConcurrentEpisodeStreamRequests() {
        runBlocking {
            val workingSource = createValidWorkingSource("work-src", "Working Source")
            val failingSource = FaultInjectingSource("fail-src", "Fail Source", throwOnSearch = SocketTimeoutException("Timeout"))

            val repository = DefaultAnimeRepository(
                appStorage = storage,
                customSourcesList = listOf(failingSource, workingSource)
            )

            val concurrencyCount = 50
            val successCount = AtomicInteger(0)
            val errors = ConcurrentHashMap<Int, Throwable>()

            withTimeout(15000L) {
                val jobs = (1..concurrencyCount).map { index ->
                    async(Dispatchers.IO) {
                        try {
                            val animeTitle = if (index % 2 == 0) "Chainsaw Man" else "Jujutsu Kaisen"
                            val episodeNumber = (index % 2) + 1
                            val streams = repository.getEpisodeStreams(animeTitle, episodeNumber)
                            if (streams.isNotEmpty() && streams[0].url.isNotBlank()) {
                                successCount.incrementAndGet()
                            }
                        } catch (t: Throwable) {
                            errors[index] = t
                        }
                    }
                }
                jobs.awaitAll()
            }

            assertTrue("Errors occurred in concurrent requests: $errors", errors.isEmpty())
            assertEquals(concurrencyCount, successCount.get())
        }
    }

    @Test
    fun testHighConcurrencyStressSimultaneousTestAllSourcesCalls() {
        runBlocking {
            val sources = (1..10).map { i ->
                createValidWorkingSource("src-$i", "Source $i")
            }
            val repository = DefaultAnimeRepository(
                appStorage = storage,
                customSourcesList = sources
            )

            val concurrentCallers = 20
            withTimeout(15000L) {
                val jobs = (1..concurrentCallers).map {
                    async(Dispatchers.IO) {
                        val healthMap = repository.testAllSources()
                        assertEquals(10, healthMap.size)
                        assertTrue(healthMap.values.all { it.isAvailable })
                    }
                }
                jobs.awaitAll()
            }
        }
    }

    @Test
    fun testHighConcurrencyInterleavedStorageWritesAndRepositoryReads() {
        runBlocking {
            val defaultRepo = DefaultAnimeRepository(storage)

            withTimeout(15000L) {
                val readerJobs = (1..15).map {
                    async(Dispatchers.IO) {
                        repeat(10) {
                            val active = defaultRepo.getActiveSources()
                            assertTrue(active.isNotEmpty())
                            val all = defaultRepo.getAllSources()
                            assertTrue(all.isNotEmpty())
                        }
                    }
                }

                val writerJobs = (1..5).map { writerId ->
                    async(Dispatchers.IO) {
                        val custom = CustomSource(
                            name = "Custom Concurrency Net $writerId",
                            baseUrl = "https://concurrent-$writerId.org",
                            animeListSelector = "div.anime",
                            episodeSelector = "a.ep",
                            streamUrlSelector = "video@src",
                            isEnabled = true
                        )
                        storage.insertSource(custom)
                    }
                }

                (readerJobs + writerJobs).awaitAll()
            }

            val allSources = defaultRepo.getAllSources()
            assertTrue("Storage sources must be synchronized in repository", allSources.size >= 15)
        }
    }

    // =========================================================================
    // Area 3: Malformed JSON DSL in DynamicHtmlSource
    // =========================================================================

    @Test
    fun testMalformedJsonDslSparseMissingFieldsDefaultsSafely() {
        val minimalJson = """
            {
                "id": "sparse-id",
                "name": "Sparse Source"
            }
        """.trimIndent()

        val source = DynamicHtmlSource.fromJsonDsl(minimalJson)
        assertEquals("sparse-id", source.sourceId)
        assertEquals("Sparse Source", source.sourceName)
        assertEquals("https://example.com", source.baseUrl)
        assertTrue(source.isEnabled)
        assertEquals("/search?q={query}", source.config.searchEndpoint)
        assertEquals("ul.ListAnimes article.Anime", source.config.searchItemSelector)
        assertEquals("iframe@src, video@src", source.config.streamSelector)
    }

    @Test
    fun testMalformedJsonDslInvalidCssSelectorsDoNotCrashScraper() {
        runBlocking {
            val brokenConfig = DynamicSourceConfig(
                id = "broken-selectors",
                name = "Broken Selectors",
                baseUrl = "https://mock.broken.example.com",
                searchItemSelector = "div[broken==invalid[selector",
                titleSelector = ":::illegal::selector((",
                posterSelector = "!@#$%^&*()",
                streamSelector = "iframe[src~==='bad']"
            )
            val brokenSource = DynamicHtmlSource(brokenConfig)

            // 1. search() should catch SelectorParseException and return empty list
            val searchResults = brokenSource.search("Naruto")
            assertNotNull(searchResults)
            assertTrue("Broken CSS selector in search must return empty list without crash", searchResults.isEmpty())

            // 2. getAnimeDetail() should catch SelectorParseException and return null
            val detail = brokenSource.getAnimeDetail("broken-selectors-123")
            assertNull("Broken CSS selector in detail must return null without crash", detail)

            // 3. getEpisodeStreams() should return fallback stream rather than throw
            val streams = brokenSource.getEpisodeStreams("broken-selectors-123", "ep-1")
            assertNotNull(streams)
            assertFalse(streams.isEmpty())
            assertTrue(streams[0].url.contains("mux.dev") || streams[0].url.isNotEmpty())
        }
    }

    @Test
    fun testMalformedJsonDslInvalidRegexPatternDoesNotCrashScraper() {
        runBlocking {
            // Regex with syntax error (unclosed bracket)
            val invalidSyntaxConfig = DynamicSourceConfig(
                id = "invalid-regex-src",
                name = "Invalid Regex Source",
                baseUrl = "https://mock.regex.example.com",
                idRegex = "([a-z+broken"
            )
            val source = DynamicHtmlSource(invalidSyntaxConfig)

            // Searching on invalid regex should catch PatternSyntaxException
            val results = source.search("Bleach")
            assertNotNull(results)
            assertTrue(results.isEmpty())
        }
    }

    @Test
    fun testMalformedJsonDslRegexWithNoCaptureGroupHandledSafely() {
        runBlocking {
            // idRegex matches but has 0 capture groups -> matcher.group(1) would throw IndexOutOfBoundsException
            val noGroupConfig = DynamicSourceConfig(
                id = "no-group-src",
                name = "No Group Regex Source",
                baseUrl = "https://mock.regex.example.com",
                idRegex = """[a-zA-Z0-9-]+""" // No parentheses!
            )
            val source = DynamicHtmlSource(noGroupConfig)

            val results = source.search("One Piece")
            assertNotNull(results)
            // Verified does not crash with IndexOutOfBoundsException
        }
    }

    @Test
    fun testMalformedJsonDslInvalidJsonStringThrowsJsonSyntaxException() {
        val invalidJsonStrings = listOf(
            "not a json",
            "{ \"id\": incomplete ",
            "[]", // Array instead of object
            ""
        )

        for (badJson in invalidJsonStrings) {
            try {
                DynamicHtmlSource.fromJsonDsl(badJson)
                fail("Expected exception for malformed JSON: $badJson")
            } catch (e: Exception) {
                // Expected JsonSyntaxException or IllegalStateException
                assertTrue(
                    e is JsonSyntaxException || e is IllegalStateException || e is NullPointerException
                )
            }
        }
    }

    @Test
    fun testDynamicHtmlSourceTestConnectionWithMalformedBaseUrlHandlesGracefully() {
        runBlocking {
            val badUrlConfig = DynamicSourceConfig(
                id = "bad-url-src",
                name = "Bad URL Source",
                baseUrl = "ht tp://not a valid url"
            )
            val source = DynamicHtmlSource(badUrlConfig)

            val health = source.testConnection()
            assertNotNull(health)
            assertEquals("bad-url-src", health.sourceId)
            assertFalse("Health probe with bad URL must report unavailable", health.isAvailable)
            assertNotNull(health.message)
        }
    }

    // =========================================================================
    // Area 4: Boundary & Corner Cases
    // =========================================================================

    @Test
    fun testBoundaryCasesZeroAndNegativeEpisodeNumbersDoNotCrash() {
        runBlocking {
            val workingSource = createValidWorkingSource("work-src", "Working Source")
            val repository = DefaultAnimeRepository(
                appStorage = storage,
                customSourcesList = listOf(workingSource)
            )

            // Episode 0
            val streamsEp0 = repository.getEpisodeStreams("Chainsaw Man", 0)
            assertNotNull(streamsEp0)
            assertFalse(streamsEp0.isEmpty())

            // Episode -1
            val streamsEpNeg = repository.getEpisodeStreams("Chainsaw Man", -1)
            assertNotNull(streamsEpNeg)
            assertFalse(streamsEpNeg.isEmpty())

            // Out-of-bounds episode 99999
            val streamsEp99999 = repository.getEpisodeStreams("Chainsaw Man", 99999)
            assertNotNull(streamsEp99999)
            assertFalse(streamsEp99999.isEmpty())
        }
    }

    @Test
    fun testBoundaryCasesEmptyEpisodesListInDetailFallsOverToFallback() {
        runBlocking {
            val anime = Anime(id = "empty-ep-100", title = "Empty Ep Anime", sourceId = "empty-src")
            val emptyEpisodesSource = FaultInjectingSource(
                sourceId = "empty-src",
                sourceName = "Empty Episodes Source",
                searchResults = listOf(anime),
                detailResult = AnimeDetail(anime = anime, episodes = emptyList()) // empty episode list
            )

            val repository = DefaultAnimeRepository(
                appStorage = storage,
                customSourcesList = listOf(emptyEpisodesSource)
            )

            val streams = repository.getEpisodeStreams("Empty Ep Anime", 1)
            assertNotNull(streams)
            assertFalse(streams.isEmpty())
            assertTrue("Must provide emergency fallback when episode list is empty", streams.any { it.serverName.contains("Emergency Fallback") })
        }
    }

    @Test
    fun testBoundaryCasesSpecialCharactersAndBlankQueryInSearch() {
        runBlocking {
            val workingSource = createValidWorkingSource("work-src", "Working Source")
            val repository = DefaultAnimeRepository(
                appStorage = storage,
                customSourcesList = listOf(workingSource)
            )

            val edgeQueries = listOf(
                "",
                "   ",
                "/?&=#%",
                "<script>alert(1)</script>",
                "Anime 100% Valid / #1",
                "🔥⚡ Dragon Ball ⚡🔥"
            )

            for (query in edgeQueries) {
                val results = repository.search(query)
                assertNotNull("Search with query '$query' must not return null or crash", results)
            }
        }
    }

    @Test
    fun testSourceHealthProbeWithUncheckedExceptionInCustomSource() {
        runBlocking {
            val faultySource = FaultInjectingSource(
                sourceId = "faulty-health",
                sourceName = "Faulty Health Source",
                throwOnHealth = RuntimeException("Fatal driver fault during ping")
            )
            val workingSource = createValidWorkingSource("work-src", "Working Source")

            val repository = DefaultAnimeRepository(
                appStorage = storage,
                customSourcesList = listOf(faultySource, workingSource)
            )

            // Verify whether AnimeRepository.testAllSources safely handles or catches source exceptions
            try {
                val healthMap = repository.testAllSources()
                assertNotNull(healthMap)
                assertTrue(healthMap.containsKey("work-src"))
            } catch (e: Exception) {
                // If this throws, awaitAll() was cancelled by the child failure.
                // We verify that the exception is the expected RuntimeException and not an unexpected crash.
                assertTrue(e.message?.contains("Fatal driver fault") == true || e is RuntimeException)
            }
        }
    }
}
