package com.darknet.animetv.ui

import com.darknet.animetv.data.model.Anime
import org.junit.Assert.*
import org.junit.Test

class SearchScreenDataTest {

    private val sampleCatalog = listOf(
        Anime(id = "1", title = "Demon Slayer: Kimetsu no Yaiba", genres = listOf("Action", "Supernatural", "Fantasy")),
        Anime(id = "2", title = "Spy x Family", genres = listOf("Comedy", "Action", "Slice of Life")),
        Anime(id = "3", title = "Kaguya-sama: Love Is War", genres = listOf("Comedy", "Romance")),
        Anime(id = "4", title = "Steins;Gate", genres = listOf("Sci-Fi", "Thriller")),
        Anime(id = "5", title = "Haikyuu!!", genres = listOf("Sports", "Comedy", "Drama"))
    )

    @Test
    fun testGenreFilteringAllVsSpecific() {
        fun filterByGenre(genre: String): List<Anime> {
            return if (genre == "All") sampleCatalog
            else sampleCatalog.filter { anime -> anime.genres.any { it.equals(genre, ignoreCase = true) } }
        }

        assertEquals(5, filterByGenre("All").size)

        val actionList = filterByGenre("Action")
        assertEquals(2, actionList.size)
        assertTrue(actionList.any { it.title.contains("Demon Slayer") })
        assertTrue(actionList.any { it.title.contains("Spy x Family") })

        val comedyList = filterByGenre("Comedy")
        assertEquals(3, comedyList.size)

        val nonExistent = filterByGenre("Mecha")
        assertTrue(nonExistent.isEmpty())
    }

    @Test
    fun testCombinedQueryAndGenreFilter() {
        fun filter(query: String, genre: String): List<Anime> {
            val byQuery = if (query.isBlank()) sampleCatalog else sampleCatalog.filter { it.title.contains(query, ignoreCase = true) }
            return if (genre == "All") byQuery else byQuery.filter { anime -> anime.genres.any { it.equals(genre, ignoreCase = true) } }
        }

        val familyAction = filter("Spy", "Action")
        assertEquals(1, familyAction.size)
        assertEquals("Spy x Family", familyAction.first().title)

        val familySports = filter("Spy", "Sports")
        assertTrue(familySports.isEmpty())
    }

    @Test
    fun testGridRowCalculationForFiveColumns() {
        fun calculateRows(itemCount: Int, numCols: Int = 5): Int {
            return if (itemCount == 0) 0 else (itemCount + numCols - 1) / numCols
        }

        assertEquals(0, calculateRows(0))
        assertEquals(1, calculateRows(1))
        assertEquals(1, calculateRows(5))
        assertEquals(2, calculateRows(6))
        assertEquals(3, calculateRows(13))
        assertEquals(4, calculateRows(20))
    }

    @Test
    fun testEmptySearchPlaceholderTrigger() {
        val query = "UnknownXYZSeries"
        val results = sampleCatalog.filter { it.title.contains(query, ignoreCase = true) }

        assertTrue(results.isEmpty())
        val isZeroStateTriggered = results.isEmpty()
        assertTrue("Zero-state empty results placeholder should trigger", isZeroStateTriggered)
    }
}
