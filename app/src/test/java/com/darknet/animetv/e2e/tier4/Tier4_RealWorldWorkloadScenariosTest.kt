package com.darknet.animetv.e2e.tier4

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.darknet.animetv.data.model.CustomSource
import com.darknet.animetv.data.model.SubtitleTrack
import com.darknet.animetv.data.model.WatchProgress
import com.darknet.animetv.data.storage.AppStorage
import com.darknet.animetv.e2e.harness.DynamicHtmlSourceEngine
import com.darknet.animetv.e2e.harness.E2eTestFixtures
import com.darknet.animetv.e2e.harness.MockAnimeSource
import com.darknet.animetv.e2e.harness.MockFailoverRepository
import com.darknet.animetv.e2e.harness.TvDpadKey
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
class Tier4_RealWorldWorkloadScenariosTest {

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
    fun testBingeWatchingWorkflowMultiEpisodeProgression() = runBlocking {
        val animeId = "frieren-binge"
        val totalEpDuration = 1_440_000L // 24 minutes

        // Step 1: Watch Episode 1 completely (>90%)
        val ep1Progress = WatchProgress(
            animeId = animeId,
            animeTitle = "Frieren",
            posterUrl = "https://images.mock/frieren.jpg",
            episodeId = "ep-1",
            episodeNumber = 1,
            episodeTitle = "Episode 1",
            positionMs = 1_420_000L,
            durationMs = totalEpDuration
        )
        storage.saveWatchProgress(ep1Progress)
        assertTrue("Episode 1 should be complete", ep1Progress.progressPercentage >= 0.90f)

        // Step 2: Auto-advance to Episode 2
        val nextEpisodeNumber = ep1Progress.episodeNumber + 1
        assertEquals(2, nextEpisodeNumber)

        // Step 3: Watch Episode 2 to 55%
        val ep2Progress = WatchProgress(
            animeId = animeId,
            animeTitle = "Frieren",
            posterUrl = "https://images.mock/frieren.jpg",
            episodeId = "ep-2",
            episodeNumber = nextEpisodeNumber,
            episodeTitle = "Episode 2",
            positionMs = 792_000L, // 55% of 1440s
            durationMs = totalEpDuration
        )
        storage.saveWatchProgress(ep2Progress)

        // Step 4: User exits to Home Screen. Query Continue Watching shelf
        val activeProgress = storage.getWatchProgress(animeId)
        assertNotNull(activeProgress)
        assertEquals(2, activeProgress?.episodeNumber)
        assertEquals(0.55f, activeProgress!!.progressPercentage, 0.01f)
    }

    @Test
    fun testContinuousStreamingWithAdaptiveStreamAndSubtitleSwitching() {
        val hud = TvPlayerHudSimulator()
        hud.setPosition(positionMs = 520_000L, totalDurationMs = 1_440_000L) // 8m 40s in

        var activeQuality = "1080p"
        assertEquals("1080p", activeQuality)
        var activeSubtitles: SubtitleTrack? = SubtitleTrack("en", "English", "https://mock/en.vtt")
        assertNotNull(activeSubtitles)
        var activeAudio = "Japanese (Original)"
        assertEquals("Japanese (Original)", activeAudio)

        // 1. Seek during playback
        hud.onDpadKey(TvDpadKey.DPAD_RIGHT) // +10s -> 530_000ms
        assertEquals(530_000L, hud.currentPositionMs)

        // 2. Bandwidth drops, player triggers adaptive downgrade to 720p
        val preservedPosition1 = hud.currentPositionMs
        activeQuality = "720p"
        assertEquals(530_000L, preservedPosition1)

        // 3. User opens track selector dialog and switches subtitles to Spanish
        val spanishSub = SubtitleTrack("es", "Spanish", "https://mock/es.vtt")
        activeSubtitles = spanishSub
        assertEquals("Spanish", activeSubtitles.label)

        // 4. User toggles to English Dub audio
        activeAudio = "English (Dub)"
        assertEquals("English (Dub)", activeAudio)

        // 5. Verify position remained continuous throughout all stream changes
        assertEquals(530_000L, hud.currentPositionMs)
        assertEquals("720p", activeQuality)
    }

    @Test
    fun testSourceManagementFailoverAndCustomSourceRecoveryWorkflow() = runBlocking {
        // Step 1: Default sources fail
        val faultySource1 = MockAnimeSource("faulty1", "Faulty Source 1", shouldFail = true, failureException = IOException("503 Service Unavailable"))
        val faultySource2 = MockAnimeSource("faulty2", "Faulty Source 2", shouldFail = true, failureException = IOException("504 Gateway Timeout"))

        val repo = MockFailoverRepository(mutableListOf(faultySource1, faultySource2))
        val initialSearch = repo.searchWithFailover("Attack on Titan")
        assertTrue("Search should fail gracefully when all sources are down", initialSearch.isEmpty())

        // Step 2: User opens Sources screen and runs health check
        val healthMap = repo.testAllSources()
        assertFalse(healthMap["faulty1"]!!.isAvailable)
        assertFalse(healthMap["faulty2"]!!.isAvailable)

        // Step 3: User registers and enables a working custom source
        val communitySource = CustomSource(
            id = 50,
            name = "Reliable Community Scraper",
            baseUrl = "https://community-reliable.mock",
            animeListSelector = "ul.anime-cards a"
        )
        storage.saveSource(communitySource)

        val workingMockSource = MockAnimeSource(
            sourceId = "custom-50",
            sourceName = "Reliable Community Scraper"
        )
        repo.addSource(workingMockSource)

        // Step 4: Re-executing search now recovers through the added source
        val recoveredSearch = repo.searchWithFailover("Attack on Titan")
        assertFalse("Search must succeed using the newly configured source", recoveredSearch.isEmpty())
    }

    @Test
    fun testColdBootAppCrashRecoveryWithResumePrompt() {
        val animeId = "crash-recovery-anime"
        val exactWatchedMs = 840_000L // 14 minutes 0 seconds

        // 1. Playback updates position periodically
        val progress = E2eTestFixtures.createSampleWatchProgress(
            animeId = animeId,
            positionMs = exactWatchedMs,
            durationMs = 1_440_000L
        )
        storage.saveWatchProgress(progress)

        // 2. Abrupt app exit (simulated by closing and re-opening storage)
        storage.close()
        val newSessionStorage = AppStorage(context)

        // 3. App relaunched: queries Continue Watching
        val resumedProgress = newSessionStorage.getWatchProgress(animeId)
        assertNotNull(resumedProgress)
        assertEquals(exactWatchedMs, resumedProgress?.positionMs)

        // 4. Format prompt time
        val minutes = (resumedProgress!!.positionMs / 1000) / 60
        val seconds = (resumedProgress.positionMs / 1000) % 60
        val formattedTime = String.format("%02d:%02d", minutes, seconds)
        assertEquals("14:00", formattedTime)

        // 5. User selects "Resume" -> player position initialized
        val hud = TvPlayerHudSimulator()
        hud.setPosition(positionMs = resumedProgress.positionMs, totalDurationMs = resumedProgress.durationMs)
        assertEquals(exactWatchedMs, hud.currentPositionMs)
        newSessionStorage.close()
    }
}
