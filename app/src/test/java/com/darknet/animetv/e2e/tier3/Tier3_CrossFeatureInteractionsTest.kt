package com.darknet.animetv.e2e.tier3

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.test.core.app.ApplicationProvider
import com.darknet.animetv.data.model.CustomSource
import com.darknet.animetv.data.storage.AppStorage
import com.darknet.animetv.e2e.harness.DynamicHtmlSourceEngine
import com.darknet.animetv.e2e.harness.E2eTestFixtures
import com.darknet.animetv.e2e.harness.MegaCloudExtractor
import com.darknet.animetv.e2e.harness.MockAnimeSource
import com.darknet.animetv.e2e.harness.MockFailoverRepository
import com.darknet.animetv.e2e.harness.TvPlayerHudSimulator
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class Tier3_CrossFeatureInteractionsTest {

    private lateinit var context: Context
    private lateinit var storage: AppStorage

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("animetv_v2.db")
        storage = AppStorage(context)
    }

    @After
    fun tearDown() {
        storage.close()
        context.deleteDatabase("animetv_v2.db")
    }

    @Test
    fun testSearchToDetailToStreamToWatchProgress() = runBlocking {
        val source = MockAnimeSource(sourceId = "aniwatch", sourceName = "Aniwatch")

        // 1. Search for title
        val searchResults = source.search("Frieren")
        assertFalse(searchResults.isEmpty())
        val selectedAnime = searchResults[0]

        // 2. Fetch Detail
        val detail = source.getAnimeDetail(selectedAnime.id)
        assertNotNull(detail)
        assertFalse(detail!!.episodes.isEmpty())
        val firstEpisode = detail.episodes[0]

        // 3. Resolve Streams
        val streams = source.getEpisodeStreams(selectedAnime.id, firstEpisode.id)
        assertFalse(streams.isEmpty())
        val selectedStream = streams[0]
        assertTrue(selectedStream.isHls)

        // 4. Record Playback Progress in SQLite
        val progress = E2eTestFixtures.createSampleWatchProgress(
            animeId = selectedAnime.id,
            episodeNumber = firstEpisode.number,
            positionMs = 350_000L,
            durationMs = 1_440_000L
        )
        storage.saveWatchProgress(progress)

        // 5. Verify Persistence Consistency
        val savedProgress = storage.getWatchProgress(selectedAnime.id)
        assertNotNull(savedProgress)
        assertEquals(firstEpisode.number, savedProgress?.episodeNumber)
        assertEquals(350_000L, savedProgress?.positionMs)
    }

    @Test
    fun testSourceFailoverToExtractorToPlayerConfig() = runBlocking {
        // Primary source throws network error
        val primarySource = MockAnimeSource(
            sourceId = "aniwatch-down",
            sourceName = "Aniwatch (Down)",
            shouldFail = true,
            failureException = IOException("Connection timed out")
        )
        // Secondary source succeeds
        val secondarySource = MockAnimeSource(
            sourceId = "miruro-up",
            sourceName = "Miruro (Up)"
        )

        val repo = MockFailoverRepository(mutableListOf(primarySource, secondarySource))

        // 1. Failover stream resolution
        val streams = repo.getEpisodeStreamsWithFailover("anime-100", "ep-100-1")
        assertFalse(streams.isEmpty())
        assertEquals(1, secondarySource.streamsCallCount)

        // 2. Pass to Video Extractor
        val extractor = MegaCloudExtractor()
        val extractedStreams = extractor.extract("https://megacloud.tv/embed-2/test")
        assertFalse(extractedStreams.isEmpty())
        val finalStream = extractedStreams[0]

        // 3. Construct Media3 MediaItem
        val mediaItem = MediaItem.Builder()
            .setUri(finalStream.url)
            .setMimeType(if (finalStream.isHls) MimeTypes.APPLICATION_M3U8 else MimeTypes.VIDEO_MP4)
            .build()

        assertNotNull(mediaItem.localConfiguration)
        assertEquals(MimeTypes.APPLICATION_M3U8, mediaItem.localConfiguration?.mimeType)
    }

    @Test
    fun testFavoriteToggleReflectsInWatchlistAndShelf() {
        val anime = E2eTestFixtures.createSampleAnime(id = "fav-cross-1", title = "Demon Slayer")

        // 1. Initial state: not favorite
        assertFalse(storage.isFavorite(anime.id))

        // 2. User presses Favorite button on Details Screen
        storage.addFavorite(anime)
        assertTrue(storage.isFavorite(anime.id))

        // 3. Home Screen Watchlist Shelf queries all favorites
        val favoritesShelf = storage.getAllFavorites()
        assertEquals(1, favoritesShelf.size)
        assertEquals("Demon Slayer", favoritesShelf[0].title)

        // 4. User un-favorites from Details Screen
        storage.removeFavorite(anime.id)
        assertFalse(storage.isFavorite(anime.id))
        assertTrue(storage.getAllFavorites().isEmpty())
    }

    @Test
    fun testPlayerSeekProgressSaveAndColdRestart() {
        val hud = TvPlayerHudSimulator(seekStepMs = 10_000L)
        hud.setPosition(positionMs = 100_000L, totalDurationMs = 1_440_000L)

        // Seek forward 3 times (+30s)
        hud.seekForward()
        hud.seekForward()
        hud.seekForward()
        assertEquals(130_000L, hud.currentPositionMs)

        // Snapshot progress to SQLite
        val progress = E2eTestFixtures.createSampleWatchProgress(
            animeId = "anime-cold-boot",
            positionMs = hud.currentPositionMs,
            durationMs = hud.durationMs
        )
        storage.saveWatchProgress(progress)
        storage.close()

        // Simulate Cold App Launch: new AppStorage instance
        val newStorageInstance = AppStorage(context)
        val restoredProgress = newStorageInstance.getWatchProgress("anime-cold-boot")

        assertNotNull(restoredProgress)
        assertEquals(130_000L, restoredProgress?.positionMs)
        newStorageInstance.close()
    }

    @Test
    fun testCustomSourceRegistrationToCatalogQuery() {
        // 1. Add custom source in database
        val customSource = E2eTestFixtures.createSampleCustomSource(
            id = 99,
            name = "Community Provider",
            baseUrl = "https://community.mock"
        )
        storage.saveSource(customSource)

        val enabledSources = storage.getEnabledSources()
        val foundSource = enabledSources.find { it.name == "Community Provider" }
        assertNotNull(foundSource)

        // 2. Execute scrape query via DynamicHtmlSourceEngine
        val mockHtml = """
            <div class="catalog">
                <div class="card"><a href="/anime/naruto">Naruto Shippuden</a></div>
            </div>
        """.trimIndent()
        val engine = DynamicHtmlSourceEngine()
        val animeList = engine.parseAnimeList(mockHtml, foundSource!!)

        assertEquals(1, animeList.size)
        assertEquals("Naruto Shippuden", animeList[0].title)
    }

    @Test
    fun testHealthCheckAndLatencyProbeReroutesTraffic() = runBlocking {
        val fastSource = MockAnimeSource("fast-src", "Fast Source", simulatedLatencyMs = 25L)
        val slowSource = MockAnimeSource("slow-src", "Slow Source", simulatedLatencyMs = 650L)

        val repo = MockFailoverRepository(mutableListOf(slowSource, fastSource))

        // Latency test
        val healthResults = repo.testAllSources()
        assertTrue(healthResults["fast-src"]!!.latencyMs < healthResults["slow-src"]!!.latencyMs)

        // Reroute / reorder so lowest latency is primary
        val reordered = listOf(fastSource, slowSource)
        repo.setSources(reordered)

        // Search routes to fast source first
        repo.searchWithFailover("Bleach")
        assertEquals(1, fastSource.searchCallCount)
        assertEquals(0, slowSource.searchCallCount)
    }
}
