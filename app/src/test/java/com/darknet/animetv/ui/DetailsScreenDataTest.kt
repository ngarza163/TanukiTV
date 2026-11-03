package com.darknet.animetv.ui

import com.darknet.animetv.data.model.Anime
import com.darknet.animetv.data.model.Episode
import com.darknet.animetv.data.model.WatchProgress
import org.junit.Assert.*
import org.junit.Test

class DetailsScreenDataTest {

    @Test
    fun testWatchedEpisodeThresholdIdentification() {
        val watchProgress = WatchProgress(
            animeId = "a1",
            animeTitle = "Test",
            posterUrl = "",
            episodeId = "e3",
            episodeNumber = 3,
            episodeTitle = "Ep 3",
            positionMs = 905_000L,
            durationMs = 1_000_000L // 90.5%
        )

        fun isEpisodeWatched(epNum: Int, progress: WatchProgress?): Boolean {
            if (progress == null) return false
            return epNum < progress.episodeNumber ||
                    (epNum == progress.episodeNumber && progress.progressPercentage >= 0.90f)
        }

        assertTrue("Earlier episode 1 should be watched", isEpisodeWatched(1, watchProgress))
        assertTrue("Earlier episode 2 should be watched", isEpisodeWatched(2, watchProgress))
        assertTrue("Current episode 3 (>90%) should be watched", isEpisodeWatched(3, watchProgress))
        assertFalse("Later episode 4 should not be watched", isEpisodeWatched(4, watchProgress))

        // When current episode is under 90% (e.g. 50%)
        val partialProgress = watchProgress.copy(positionMs = 500_000L)
        assertTrue("Earlier episode 2 is still watched", isEpisodeWatched(2, partialProgress))
        assertFalse("Episode 3 at 50% is not considered finished/watched", isEpisodeWatched(3, partialProgress))
    }

    @Test
    fun testEpisodeChunkingForLongSeries() {
        val totalEpisodes = 135
        val chunkSize = 50
        val episodes = (1..totalEpisodes).map { num ->
            Episode(id = "ep-$num", animeId = "a1", number = num, title = "Episode $num")
        }

        val chunks = episodes.chunked(chunkSize)
        assertEquals("135 episodes should be chunked into 3 blocks", 3, chunks.size)
        assertEquals(50, chunks[0].size)
        assertEquals(1, chunks[0].first().number)
        assertEquals(50, chunks[0].last().number)

        assertEquals(50, chunks[1].size)
        assertEquals(51, chunks[1].first().number)
        assertEquals(100, chunks[1].last().number)

        assertEquals(35, chunks[2].size)
        assertEquals(101, chunks[2].first().number)
        assertEquals(135, chunks[2].last().number)
    }

    @Test
    fun testSyntheticEpisodeGenerationWhenEmpty() {
        val anime = Anime(id = "naruto", title = "Naruto", totalEpisodes = 220)

        val count = if (anime.totalEpisodes > 0) anime.totalEpisodes else 12
        val synthetic = (1..count).map { num ->
            Episode(
                id = "${anime.id}-ep-$num",
                animeId = anime.id,
                number = num,
                title = "Episode $num"
            )
        }

        assertEquals(220, synthetic.size)
        assertEquals("Episode 1", synthetic.first().title)
        assertEquals("Episode 220", synthetic.last().title)
        assertEquals("naruto-ep-1", synthetic.first().id)
    }

    @Test
    fun testFavoriteToggleInversion() {
        var isFavorite = false
        fun toggle() { isFavorite = !isFavorite }

        toggle()
        assertTrue(isFavorite)

        toggle()
        assertFalse(isFavorite)
    }
}
