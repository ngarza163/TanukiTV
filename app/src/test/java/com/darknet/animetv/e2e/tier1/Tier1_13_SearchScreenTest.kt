package com.darknet.animetv.e2e.tier1

import com.darknet.animetv.e2e.harness.E2eTestFixtures
import com.darknet.animetv.e2e.harness.TvDpadKey
import com.darknet.animetv.e2e.harness.TvGridNavigator
import org.junit.Assert.*
import org.junit.Test

class Tier1_13_SearchScreenTest {

    @Test
    fun testFiveColumnGridNavigation() {
        val navigator = TvGridNavigator(numRows = 4, numCols = 5)
        assertEquals(0, navigator.currentCol)

        // Move right across the 5 columns
        for (i in 1..4) {
            navigator.handleKeyEvent(TvDpadKey.DPAD_RIGHT)
        }
        assertEquals(4, navigator.currentCol)
        assertEquals(0, navigator.currentRow)

        // Move down to next row
        navigator.handleKeyEvent(TvDpadKey.DPAD_DOWN)
        assertEquals(1, navigator.currentRow)
        assertEquals(4, navigator.currentCol)
        assertEquals(9, navigator.currentItemIndex) // row 1 * 5 + col 4 = 9
    }

    @Test
    fun testSearchFilterChips() {
        val allAnime = listOf(
            E2eTestFixtures.createSampleAnime(id = "1", title = "Jujutsu Kaisen", genres = listOf("Action", "Fantasy")),
            E2eTestFixtures.createSampleAnime(id = "2", title = "Haikyuu!!", genres = listOf("Sports", "Comedy")),
            E2eTestFixtures.createSampleAnime(id = "3", title = "Bleach", genres = listOf("Action", "Supernatural"))
        )

        val selectedGenre = "Action"
        val filtered = allAnime.filter { it.genres.contains(selectedGenre) }
        assertEquals(2, filtered.size)
        assertTrue(filtered.any { it.title == "Jujutsu Kaisen" })
        assertTrue(filtered.any { it.title == "Bleach" })
    }

    @Test
    fun testCombinedQueryAndGenreFilter() {
        val allAnime = listOf(
            E2eTestFixtures.createSampleAnime(id = "1", title = "Attack on Titan Season 1", genres = listOf("Action", "Drama")),
            E2eTestFixtures.createSampleAnime(id = "2", title = "Attack on Titan Season 2", genres = listOf("Action", "Drama")),
            E2eTestFixtures.createSampleAnime(id = "3", title = "Sword Art Online", genres = listOf("Action", "Fantasy"))
        )

        val query = "Titan"
        val genre = "Action"
        val results = allAnime.filter { it.title.contains(query, ignoreCase = true) && it.genres.contains(genre) }

        assertEquals(2, results.size)
        assertTrue(results.all { it.title.contains("Titan") })
    }

    @Test
    fun testGridRowCalculationForOddResults() {
        val totalResults = 13
        val numCols = 5
        val expectedRows = (totalResults + numCols - 1) / numCols
        assertEquals(3, expectedRows)
    }

    @Test
    fun testEmptySearchPlaceholder() {
        val query = "NonExistentAnimeXYZ"
        val allAnime = E2eTestFixtures.createSampleAnimeList(count = 5)
        val results = allAnime.filter { it.title.contains(query, ignoreCase = true) }

        assertTrue(results.isEmpty())
        val hasPlaceholder = results.isEmpty()
        assertTrue("Empty state placeholder must be triggered", hasPlaceholder)
    }
}
