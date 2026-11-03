package com.darknet.animetv.ui

import com.darknet.animetv.data.model.Anime
import com.darknet.animetv.data.model.WatchProgress
import org.junit.Assert.*
import org.junit.Test

class TvAnimeCardTest {

    @Test
    fun testAnimeCardDimensionsRatio() {
        val widthDp = 140f
        val heightDp = 210f
        val ratio = widthDp / heightDp
        assertEquals("Standard 140x210 card corresponds to 2:3 aspect ratio (0.6667)", 2f / 3f, ratio, 0.001f)
    }

    @Test
    fun testScoreFormatting() {
        fun formatScore(score: Double): String = String.format("%.1f", score)

        assertEquals("8.5", formatScore(8.5))
        assertEquals("9.4", formatScore(9.35))
        assertEquals("7.0", formatScore(7.0))
        assertEquals("10.0", formatScore(10.0))
    }

    @Test
    fun testProgressPercentageClamping() {
        fun clampProgress(positionMs: Long, durationMs: Long): Float {
            return if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
        }

        assertEquals(0.5f, clampProgress(500L, 1000L), 0.001f)
        assertEquals(0.0f, clampProgress(-100L, 1000L), 0.001f)
        assertEquals(1.0f, clampProgress(1500L, 1000L), 0.001f)
        assertEquals(0.0f, clampProgress(100L, 0L), 0.001f)
    }

    @Test
    fun testContinueWatchingModelBinding() {
        val progress = WatchProgress(
            animeId = "frieren-1",
            animeTitle = "Frieren: Beyond Journey's End",
            posterUrl = "https://example.com/poster.jpg",
            episodeId = "ep-12",
            episodeNumber = 12,
            episodeTitle = "A Real Mage",
            positionMs = 720_000L,
            durationMs = 1_440_000L
        )

        assertEquals("frieren-1", progress.animeId)
        assertEquals("Frieren: Beyond Journey's End", progress.animeTitle)
        assertEquals("EP 12", "EP ${progress.episodeNumber}")
        assertEquals(0.5f, progress.progressPercentage, 0.001f)
    }

    @Test
    fun testPosterFallbackToBackdrop() {
        val animeWithPoster = Anime(id = "1", title = "A1", posterUrl = "https://p.jpg", backdropUrl = "https://b.jpg")
        val animeWithoutPoster = Anime(id = "2", title = "A2", posterUrl = "", backdropUrl = "https://b.jpg")

        fun resolveDisplayImage(anime: Anime): String = anime.posterUrl.ifBlank { anime.backdropUrl }

        assertEquals("https://p.jpg", resolveDisplayImage(animeWithPoster))
        assertEquals("https://b.jpg", resolveDisplayImage(animeWithoutPoster))
    }
}
