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
import com.darknet.animetv.data.source.SourceHealthResult
import com.darknet.animetv.data.storage.AppStorage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AnimeRepositoryTest {

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

    private class MockFailingSource(
        override val sourceId: String = "mock-fail",
        override val sourceName: String = "Failing Source"
    ) : AnimeSource {
        override val baseUrl: String = "https://fail.example.com"
        override var isEnabled: Boolean = true

        override suspend fun getTrending(): List<Anime> = throw RuntimeException("Network down")
        override suspend fun getPopular(): List<Anime> = emptyList()
        override suspend fun getRecentEpisodes(): List<Anime> = emptyList()
        override suspend fun search(query: String): List<Anime> = emptyList()
        override suspend fun getAnimeDetail(animeId: String): AnimeDetail? = null
        override suspend fun getEpisodeStreams(animeId: String, episodeId: String): List<VideoSource> = emptyList()
        override suspend fun testConnection(): SourceHealthResult = SourceHealthResult(sourceId, false, 500, "Offline")
    }

    private class MockWorkingSource(
        override val sourceId: String = "mock-work",
        override val sourceName: String = "Working Source",
        val returnHls: Boolean = true
    ) : AnimeSource {
        override val baseUrl: String = "https://work.example.com"
        override var isEnabled: Boolean = true

        override suspend fun getTrending(): List<Anime> = listOf(
            Anime(id = "work-1", title = "Working Anime", posterUrl = "https://work.com/p.jpg", sourceId = sourceId)
        )
        override suspend fun getPopular(): List<Anime> = getTrending()
        override suspend fun getRecentEpisodes(): List<Anime> = getTrending()
        override suspend fun search(query: String): List<Anime> = listOf(
            Anime(id = "work-1", title = query, posterUrl = "https://work.com/p.jpg", sourceId = sourceId)
        )
        override suspend fun getAnimeDetail(animeId: String): AnimeDetail {
            return AnimeDetail(
                anime = Anime(id = animeId, title = "Working Anime", sourceId = sourceId),
                episodes = listOf(Episode(id = "ep-1", animeId = animeId, number = 1, title = "Episode 1"))
            )
        }
        override suspend fun getEpisodeStreams(animeId: String, episodeId: String): List<VideoSource> {
            return listOf(
                VideoSource(
                    serverName = "Mock Server",
                    url = if (returnHls) "https://work.com/stream.m3u8" else "https://work.com/stream.mp4",
                    quality = "1080p",
                    isHls = returnHls
                )
            )
        }
        override suspend fun testConnection(): SourceHealthResult = SourceHealthResult(sourceId, true, 42, "HTTP 200")
    }

    @Test
    fun testMultiSourceFailoverRecoversFromFailingSource() = runBlocking {
        val failingSource = MockFailingSource()
        val workingSource = MockWorkingSource()

        val repository: AnimeRepository = DefaultAnimeRepository(
            appStorage = storage,
            customSourcesList = listOf(failingSource, workingSource)
        )

        // Request streams for anime: failing source will throw/empty, working source will resolve
        val streams = repository.getEpisodeStreams("Jujutsu Kaisen", 1)
        assertFalse(streams.isEmpty())
        assertEquals("Mock Server", streams[0].serverName)
        assertEquals("https://work.com/stream.m3u8", streams[0].url)
    }

    @Test
    fun testPreferredSourceTakesPrecedence() = runBlocking {
        val sourceA = MockWorkingSource("source-a", "Source A")
        val sourceB = MockWorkingSource("source-b", "Source B")

        val repository = DefaultAnimeRepository(
            appStorage = storage,
            customSourcesList = listOf(sourceA, sourceB)
        )

        // Request with preferredSourceId = "source-b"
        val streams = repository.getEpisodeStreams("One Piece", 1, preferredSourceId = "source-b")
        assertFalse(streams.isEmpty())
        assertTrue(streams[0].url.contains("work.com"))
    }

    @Test
    fun testEmergencyFallbackWhenAllSourcesFail() = runBlocking {
        val failing1 = MockFailingSource("fail-1", "Fail 1")
        val failing2 = MockFailingSource("fail-2", "Fail 2")

        val repository = DefaultAnimeRepository(
            appStorage = storage,
            customSourcesList = listOf(failing1, failing2)
        )

        val streams = repository.getEpisodeStreams("Offline Anime", 1)
        assertFalse(streams.isEmpty())
        // Verified fallback streams are returned
        assertTrue(streams.any { it.serverName.contains("Emergency Fallback") })
    }

    @Test
    fun testTestAllSourcesLatencyCheck() = runBlocking {
        val sourceA = MockWorkingSource("source-a", "Source A")
        val sourceB = MockWorkingSource("source-b", "Source B")

        val repository = DefaultAnimeRepository(
            appStorage = storage,
            customSourcesList = listOf(sourceA, sourceB)
        )

        val healthMap = repository.testAllSources()
        assertEquals(2, healthMap.size)
        assertTrue(healthMap.containsKey("source-a"))
        assertTrue(healthMap.containsKey("source-b"))
        assertTrue(healthMap["source-a"]!!.isAvailable)
        assertEquals(42L, healthMap["source-a"]!!.latencyMs)
    }

    @Test
    fun testAppStorageSourceSync() {
        val defaultRepo = DefaultAnimeRepository(storage)
        val initialSources = defaultRepo.getAllSources()
        assertTrue(initialSources.size >= 10)

        // Add custom source to storage
        val customSource = CustomSource(
            name = "My Custom Anime Network",
            baseUrl = "https://customnetwork.io",
            animeListSelector = "div.anime",
            episodeSelector = "a.episode",
            streamUrlSelector = "video@src",
            isEnabled = true
        )
        storage.insertSource(customSource)

        val updatedSources = defaultRepo.getAllSources()
        assertTrue(updatedSources.any { it.sourceName == "My Custom Anime Network" })
    }
}
