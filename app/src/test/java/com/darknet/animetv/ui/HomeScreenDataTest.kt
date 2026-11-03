package com.darknet.animetv.ui

import com.darknet.animetv.data.model.Anime
import com.darknet.animetv.data.model.WatchProgress
import org.junit.Assert.*
import org.junit.Test

class HomeScreenDataTest {

    @Test
    fun testShelvesSegmentation() {
        val trending = listOf(
            Anime(id = "t1", title = "Trending 1"),
            Anime(id = "t2", title = "Trending 2")
        )
        val popular = listOf(
            Anime(id = "p1", title = "Popular 1"),
            Anime(id = "p2", title = "Popular 2")
        )
        val recent = listOf(
            Anime(id = "r1", title = "Recent 1")
        )

        val shelves = mapOf(
            "Trending" to trending,
            "Popular" to popular,
            "Recent" to recent
        )

        assertEquals(3, shelves.size)
        assertEquals(2, shelves["Trending"]?.size)
        assertEquals(2, shelves["Popular"]?.size)
        assertEquals(1, shelves["Recent"]?.size)
    }

    @Test
    fun testTopRatedShelfDerivation() {
        val trending = listOf(
            Anime(id = "1", title = "Anime A", score = 7.5),
            Anime(id = "2", title = "Anime B", score = 9.2)
        )
        val popular = listOf(
            Anime(id = "2", title = "Anime B", score = 9.2), // Duplicate
            Anime(id = "3", title = "Anime C", score = 8.8),
            Anime(id = "4", title = "Anime D", score = 9.5)
        )

        val topRated = (trending + popular)
            .distinctBy { it.id }
            .sortedByDescending { it.score }
            .take(15)

        assertEquals(4, topRated.size)
        assertEquals("Anime D", topRated[0].title)
        assertEquals(9.5, topRated[0].score, 0.001)
        assertEquals("Anime B", topRated[1].title)
        assertEquals(9.2, topRated[1].score, 0.001)
        assertEquals("Anime C", topRated[2].title)
        assertEquals(8.8, topRated[2].score, 0.001)
        assertEquals("Anime A", topRated[3].title)
        assertEquals(7.5, topRated[3].score, 0.001)
    }

    @Test
    fun testFeaturedHeroCarouselItemLimit() {
        val catalog = (1..10).map { num ->
            Anime(id = "a$num", title = "Anime $num", score = 8.0)
        }

        val featured = if (catalog.isNotEmpty()) catalog.take(5) else emptyList()
        assertEquals(5, featured.size)
        assertEquals("Anime 1", featured.first().title)
        assertEquals("Anime 5", featured.last().title)
    }

    @Test
    fun testEmptyShelfPlaceholderMessage() {
        val emptyList = emptyList<Anime>()
        val message = if (emptyList.isEmpty()) "No anime in this shelf yet" else "Items present"
        assertEquals("No anime in this shelf yet", message)
    }

    @Test
    fun testWatchProgressMapIndexing() {
        val progressList = listOf(
            WatchProgress("a1", "T1", "", "e1", 1, "", 100L, 200L),
            WatchProgress("a2", "T2", "", "e2", 2, "", 300L, 400L)
        )
        val map = progressList.associateBy { it.animeId }

        assertEquals(2, map.size)
        assertTrue(map.containsKey("a1"))
        assertTrue(map.containsKey("a2"))
        assertEquals(1, map["a1"]?.episodeNumber)
        assertEquals(2, map["a2"]?.episodeNumber)
    }
}
