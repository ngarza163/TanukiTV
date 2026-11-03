package com.darknet.animetv.dub

import com.darknet.animetv.data.model.Anime
import com.darknet.animetv.data.model.Episode
import com.darknet.animetv.data.model.VideoSource
import com.darknet.animetv.data.repository.AniListSyncManager
import com.darknet.animetv.data.repository.DefaultAnimeRepository
import com.darknet.animetv.data.source.AniListCatalogSource
import com.darknet.animetv.data.source.AniwatchSource
import com.darknet.animetv.data.source.WcoSource
import com.darknet.animetv.data.storage.AppStorage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DubbedExperienceTest {

    @Test
    fun testAnimeModelDubFieldsDefaultAndCustom() {
        val defaultAnime = Anime(id = "1", title = "Subbed Anime")
        assertFalse(defaultAnime.hasDub)
        assertEquals(0, defaultAnime.dubbedEpisodesCount)

        val dubbedAnime = Anime(
            id = "2",
            title = "Cowboy Bebop",
            hasDub = true,
            dubbedEpisodesCount = 26
        )
        assertTrue(dubbedAnime.hasDub)
        assertEquals(26, dubbedAnime.dubbedEpisodesCount)
    }

    @Test
    fun testEpisodeModelAudioTrack() {
        val defaultEp = Episode(id = "ep-1", animeId = "1", number = 1, title = "Episode 1")
        assertFalse(defaultEp.hasDub)
        assertEquals("SUB", defaultEp.audioTrack)

        val dubbedEp = defaultEp.copy(hasDub = true, audioTrack = "DUB")
        assertTrue(dubbedEp.hasDub)
        assertEquals("DUB", dubbedEp.audioTrack)
    }

    @Test
    fun testVideoSourceAudioTrack() {
        val defaultSource = VideoSource(serverName = "Server 1", url = "https://example.com/stream.m3u8")
        assertEquals("SUB", defaultSource.audioTrack)

        val dubSource = defaultSource.copy(audioTrack = "DUB")
        assertEquals("DUB", dubSource.audioTrack)
    }

    @Test
    fun testDefaultAnimeRepositoryGetDubbedAnime() = runBlocking {
        val repository = DefaultAnimeRepository()
        val dubbedList = repository.getDubbedAnime()

        assertTrue("Dubbed catalog should contain items", dubbedList.isNotEmpty())
        assertTrue("Every anime in getDubbedAnime must be marked hasDub=true", dubbedList.all { it.hasDub })
        val titles = dubbedList.map { it.title }
        assertTrue("Should contain iconic Cowboy Bebop", titles.any { it.contains("Cowboy Bebop", ignoreCase = true) })
        assertTrue("Should contain Fullmetal Alchemist", titles.any { it.contains("Fullmetal Alchemist", ignoreCase = true) })
    }

    @Test
    fun testEpisodeStreamsAudioPreferencePriority() = runBlocking {
        val repository = DefaultAnimeRepository()

        // When preferredAudio is DUB, streams prioritized should be DUB
        val dubStreams = repository.getEpisodeStreams("Cowboy Bebop", 1, preferredAudio = "DUB")
        assertTrue(dubStreams.isNotEmpty())
        val firstDubStream = dubStreams.first()
        assertTrue(
            "Primary stream when preferredAudio=DUB should be DUB",
            firstDubStream.audioTrack.equals("DUB", ignoreCase = true) ||
                firstDubStream.serverName.contains("Dub", ignoreCase = true)
        )

        // When preferredAudio is SUB, streams prioritized should be SUB
        val subStreams = repository.getEpisodeStreams("Cowboy Bebop", 1, preferredAudio = "SUB")
        assertTrue(subStreams.isNotEmpty())
        val firstSubStream = subStreams.first()
        assertTrue(
            "Primary stream when preferredAudio=SUB should be SUB",
            firstSubStream.audioTrack.equals("SUB", ignoreCase = true) ||
                !firstSubStream.serverName.contains("Dub", ignoreCase = true)
        )
    }

    @Test
    fun testAniListCatalogSourceReturnsEmptyEpisodeStreams() = runBlocking {
        val anilistSource = AniListCatalogSource()
        val streams = anilistSource.getEpisodeStreams("16498", "al-16498-ep-1")
        assertTrue("AniListCatalogSource must act purely as metadata and return empty streams", streams.isEmpty())
    }

    @Test
    fun testAniListCatalogSourceAiringScheduleMethod() = runBlocking {
        val anilistSource = AniListCatalogSource()
        // In local test environment without network, it returns an empty list gracefully
        val schedule = anilistSource.getAiringSchedule("Monday")
        assertNotNull(schedule)
    }

    @Test
    fun testAniListCatalogSourceCharacterCastMethod() = runBlocking {
        val anilistSource = AniListCatalogSource()
        // In local test environment without network, it returns an empty list gracefully
        val cast = anilistSource.getCharacterCast("16498", "Attack on Titan")
        assertNotNull(cast)
    }

    @Test
    fun testWcoSourceDubbedAttributes() = runBlocking {
        val wco = WcoSource()
        val dubbedAnime = wco.getDubbedAnime()
        assertTrue(dubbedAnime.isNotEmpty())
        assertTrue("WCO anime should have hasDub=true", dubbedAnime.all { it.hasDub })

        val streams = wco.getEpisodeStreams("wco-test", "wco-test-ep-1")
        assertTrue(streams.isNotEmpty())
        assertTrue("WCO streams must have audioTrack=DUB", streams.all { it.audioTrack == "DUB" })
    }

    @Test
    fun testAniwatchSourceDubbedAnime() = runBlocking {
        val aniwatch = AniwatchSource()
        val dubbedAnime = aniwatch.getDubbedAnime()
        assertNotNull(dubbedAnime)
    }

    @Test
    fun testAniListSyncManagerLoginWithUsernameBlankFails() = runBlocking {
        val storage = AppStorage(RuntimeEnvironment.getApplication())
        val res = AniListSyncManager.loginWithUsername("   ", storage)
        assertFalse(res.isSuccess)
    }

    @Test
    fun testAniListSyncManagerUnauthenticatedSyncDoesNotInjectDemo() = runBlocking {
        val storage = AppStorage(RuntimeEnvironment.getApplication())
        storage.saveAniListToken(null)
        storage.saveAniListUser(null)

        val res = AniListSyncManager.syncAniListWatchlist(storage)
        assertFalse("Unauthenticated sync without account should fail gracefully", res.isSuccess)
        val favorites = storage.getFavorites()
        assertFalse("Real unauthenticated sync must not inject fake demo anime", favorites.any { it.title.contains("Attack on Titan Final Season") })
    }
}
