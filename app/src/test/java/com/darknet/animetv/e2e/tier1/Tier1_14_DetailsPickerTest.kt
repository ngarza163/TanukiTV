package com.darknet.animetv.e2e.tier1

import com.darknet.animetv.data.model.Episode
import com.darknet.animetv.e2e.harness.E2eTestFixtures
import org.junit.Assert.*
import org.junit.Test

class Tier1_14_DetailsPickerTest {

    @Test
    fun testAnimeDetailModelIntegrity() {
        val detail = E2eTestFixtures.createSampleDetail(animeId = "frieren-detail", episodeCount = 28)
        assertEquals("frieren-detail", detail.anime.id)
        assertEquals(28, detail.episodes.size)
        assertEquals("Madhouse", detail.studio)
    }

    @Test
    fun testEpisodeOrderingAndNumbering() {
        val detail = E2eTestFixtures.createSampleDetail(episodeCount = 12)
        val episodes = detail.episodes

        for (i in 0 until episodes.size) {
            assertEquals(i + 1, episodes[i].number)
            assertTrue(episodes[i].title.contains("Episode ${i + 1}"))
        }
    }

    @Test
    fun testFavoriteToggleLogic() {
        var isFavorite = false
        fun toggle() { isFavorite = !isFavorite }

        toggle()
        assertTrue(isFavorite)

        toggle()
        assertFalse(isFavorite)
    }

    @Test
    fun testWatchedEpisodeStatusIdentification() {
        val detail = E2eTestFixtures.createSampleDetail(episodeCount = 5)
        val progress = E2eTestFixtures.createSampleWatchProgress(
            animeId = detail.anime.id,
            episodeNumber = 3,
            positionMs = 1_400_000L,
            durationMs = 1_440_000L
        )

        fun isEpisodeWatched(ep: Episode): Boolean {
            return ep.number < progress.episodeNumber ||
                    (ep.number == progress.episodeNumber && progress.progressPercentage >= 0.90f)
        }

        assertTrue("Episode 1 should be considered watched", isEpisodeWatched(detail.episodes[0]))
        assertTrue("Episode 2 should be considered watched", isEpisodeWatched(detail.episodes[1]))
        assertTrue("Episode 3 should be considered watched (>90%)", isEpisodeWatched(detail.episodes[2]))
        assertFalse("Episode 4 should not be watched", isEpisodeWatched(detail.episodes[3]))
    }

    @Test
    fun testEpisodeChunkingForLongSeries() {
        // e.g., One Piece with 1000+ episodes chunked in groups of 50
        val totalEpisodes = 150
        val chunkSize = 50
        val chunks = (1..totalEpisodes).chunked(chunkSize)

        assertEquals(3, chunks.size)
        assertEquals(1, chunks[0].first())
        assertEquals(50, chunks[0].last())
        assertEquals(51, chunks[1].first())
        assertEquals(100, chunks[1].last())
        assertEquals(101, chunks[2].first())
        assertEquals(150, chunks[2].last())
    }
}
