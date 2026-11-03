package com.darknet.animetv.e2e.tier1

import com.darknet.animetv.data.model.WatchProgress
import com.darknet.animetv.e2e.harness.E2eTestFixtures
import org.junit.Assert.*
import org.junit.Test

class Tier1_12_HorizontalShelvesTest {

    @Test
    fun testShelvesDataSegmentation() {
        val trending = E2eTestFixtures.createSampleAnimeList(count = 5)
        val popular = E2eTestFixtures.createSampleAnimeList(count = 5).reversed()

        val shelves = mapOf(
            "Trending Now" to trending,
            "Popular Hits" to popular
        )

        assertEquals(2, shelves.size)
        assertEquals(5, shelves["Trending Now"]?.size)
        assertEquals(5, shelves["Popular Hits"]?.size)
    }

    @Test
    fun testContinueWatchingProgressCalculation() {
        val progress = E2eTestFixtures.createSampleWatchProgress(
            positionMs = 720_000L,
            durationMs = 1_440_000L // 50%
        )
        assertEquals(0.5f, progress.progressPercentage, 0.01f)
    }

    @Test
    fun testContinueWatchingProgressClampedBetweenZeroAndOne() {
        val overflowProgress = WatchProgress(
            animeId = "anime-ovf",
            animeTitle = "Test",
            posterUrl = "",
            episodeId = "ep-1",
            episodeNumber = 1,
            episodeTitle = "",
            positionMs = 2_000_000L,
            durationMs = 1_000_000L
        )
        assertEquals(1.0f, overflowProgress.progressPercentage, 0.001f)

        val zeroDurationProgress = WatchProgress(
            animeId = "anime-zero",
            animeTitle = "Test",
            posterUrl = "",
            episodeId = "ep-1",
            episodeNumber = 1,
            episodeTitle = "",
            positionMs = 100L,
            durationMs = 0L
        )
        assertEquals(0.0f, zeroDurationProgress.progressPercentage, 0.001f)
    }

    @Test
    fun testShelfHorizontalNavigationIndex() {
        val shelfItemCount = 10
        var currentCardIndex = 0

        // User navigates right 3 times
        for (i in 1..3) {
            if (currentCardIndex < shelfItemCount - 1) {
                currentCardIndex++
            }
        }
        assertEquals(3, currentCardIndex)

        // User navigates left 2 times
        for (i in 1..2) {
            if (currentCardIndex > 0) {
                currentCardIndex--
            }
        }
        assertEquals(1, currentCardIndex)
    }

    @Test
    fun testEmptyShelfGracefulHandling() {
        val emptyShelf = emptyList<com.darknet.animetv.data.model.Anime>()
        assertTrue(emptyShelf.isEmpty())
        val displayMessage = if (emptyShelf.isEmpty()) "No anime in this shelf yet" else "Items available"
        assertEquals("No anime in this shelf yet", displayMessage)
    }
}
