package com.darknet.animetv.ui

import com.darknet.animetv.data.model.Anime
import com.darknet.animetv.data.model.WatchProgress
import org.junit.Assert.*
import org.junit.Test

class TvHeroBannerTest {

    @Test
    fun testActionLabelStateResolution() {
        fun resolveActionLabel(progress: WatchProgress?): String {
            return when {
                progress != null && progress.progressPercentage < 0.90f -> "Resume"
                progress != null && progress.progressPercentage >= 0.90f -> "Watch Again"
                else -> "Play Now"
            }
        }

        // 1. No watch progress -> "Play Now"
        assertEquals("Play Now", resolveActionLabel(null))

        // 2. In progress (e.g. 50%) -> "Resume"
        val inProgress = WatchProgress(
            animeId = "a1", animeTitle = "T1", posterUrl = "", episodeId = "e1",
            episodeNumber = 1, episodeTitle = "", positionMs = 500L, durationMs = 1000L
        )
        assertEquals("Resume", resolveActionLabel(inProgress))

        // 3. Completed (> 90%) -> "Watch Again"
        val completedProgress = WatchProgress(
            animeId = "a1", animeTitle = "T1", posterUrl = "", episodeId = "e1",
            episodeNumber = 1, episodeTitle = "", positionMs = 950L, durationMs = 1000L
        )
        assertEquals("Watch Again", resolveActionLabel(completedProgress))
    }

    @Test
    fun testCarouselAutoAdvanceRotationIndexing() {
        val count = 5
        var currentIndex = 0

        for (step in 1..count) {
            currentIndex = (currentIndex + 1) % count
            assertEquals(step % count, currentIndex)
        }
        assertEquals("Full rotation returns to initial index 0", 0, currentIndex)
    }

    @Test
    fun testHeroBannerScoreBadgeFormatting() {
        val anime = Anime(id = "1", title = "Jujutsu Kaisen", score = 8.76)
        val formatted = String.format("%.1f", anime.score)
        assertEquals("8.8", formatted)
    }

    @Test
    fun testWatchlistButtonLabelState() {
        fun getWatchlistLabel(animeId: String, watchlistIds: Set<String>): String {
            return if (watchlistIds.contains(animeId)) "✓ In Watchlist" else "+ Watchlist"
        }

        val watchlist = setOf("anime-1", "anime-2")
        assertEquals("✓ In Watchlist", getWatchlistLabel("anime-1", watchlist))
        assertEquals("+ Watchlist", getWatchlistLabel("anime-99", watchlist))
    }

    @Test
    fun testGenresDisplayFormatting() {
        val anime = Anime(
            id = "1",
            title = "Test",
            genres = listOf("Action", "Adventure", "Fantasy")
        )
        val formatted = anime.genres.joinToString(" • ")
        assertEquals("Action • Adventure • Fantasy", formatted)
    }
}
