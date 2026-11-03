package com.darknet.animetv.franchise

import com.darknet.animetv.data.model.Anime
import com.darknet.animetv.data.model.FranchiseRelation
import com.darknet.animetv.data.model.FranchiseRelationType
import com.darknet.animetv.data.model.FranchiseWatchOrderManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FranchiseWatchOrderTest {

    @Test
    fun testChronologicalOrdering() {
        val raw = listOf(
            FranchiseRelation(animeId = "3", title = "Movie Sequel", relationType = FranchiseRelationType.MOVIE, year = 2021),
            FranchiseRelation(animeId = "1", title = "Season 1", relationType = FranchiseRelationType.MAIN, year = 2018),
            FranchiseRelation(animeId = "0", title = "Prologue OVA", relationType = FranchiseRelationType.PREQUEL, year = 2017),
            FranchiseRelation(animeId = "2", title = "Season 2", relationType = FranchiseRelationType.SEQUEL, year = 2020)
        )

        val ordered = FranchiseWatchOrderManager.orderChronologically(raw)

        assertEquals("Prologue OVA", ordered[0].title)
        assertEquals(FranchiseRelationType.PREQUEL, ordered[0].relationType)
        assertEquals(1, ordered[0].chronologicalOrder)

        assertEquals("Season 1", ordered[1].title)
        assertEquals(FranchiseRelationType.MAIN, ordered[1].relationType)
        assertEquals(2, ordered[1].chronologicalOrder)

        assertEquals("Season 2", ordered[2].title)
        assertEquals(FranchiseRelationType.SEQUEL, ordered[2].relationType)
        assertEquals(3, ordered[2].chronologicalOrder)

        assertEquals("Movie Sequel", ordered[3].title)
        assertEquals(FranchiseRelationType.MOVIE, ordered[3].relationType)
        assertEquals(4, ordered[3].chronologicalOrder)
    }

    @Test
    fun testAttackOnTitanFranchiseRelations() {
        val aot = Anime(
            id = "shingeki-no-kyojin",
            title = "Attack on Titan",
            year = 2013,
            genres = listOf("Action", "Drama", "Fantasy")
        )

        val relations = FranchiseWatchOrderManager.getRelationsForAnime(aot)

        assertTrue(relations.size >= 4)
        // Should contain prequel or OVA
        assertTrue(relations.any { it.relationType == FranchiseRelationType.PREQUEL || it.format == "OVA" })
        // Should contain main series
        assertTrue(relations.any { it.relationType == FranchiseRelationType.MAIN })
        // Sequence should be strictly increasing in chronological order
        for (i in 0 until relations.size - 1) {
            assertTrue(relations[i].chronologicalOrder <= relations[i + 1].chronologicalOrder)
        }
    }

    @Test
    fun testDynamicFranchiseSynthesisForGenericAnime() {
        val generic = Anime(
            id = "frieren-beyond-journeys-end",
            title = "Frieren: Beyond Journey's End",
            year = 2023,
            genres = listOf("Adventure", "Drama", "Fantasy"),
            score = 9.3
        )

        val relations = FranchiseWatchOrderManager.getRelationsForAnime(generic)

        assertEquals(4, relations.size)
        assertEquals(FranchiseRelationType.PREQUEL, relations[0].relationType)
        assertEquals(FranchiseRelationType.MAIN, relations[1].relationType)
        assertEquals(FranchiseRelationType.SEQUEL, relations[2].relationType)
        assertEquals(FranchiseRelationType.MOVIE, relations[3].relationType)
    }

    @Test
    fun testSmartRecommendations() {
        val target = Anime(
            id = "jujutsu-kaisen",
            title = "Jujutsu Kaisen",
            genres = listOf("Action", "Supernatural", "Fantasy"),
            score = 8.6
        )

        val catalog = listOf(
            Anime(id = "jujutsu-kaisen", title = "Jujutsu Kaisen", genres = listOf("Action")),
            Anime(id = "bleach", title = "Bleach", genres = listOf("Action", "Supernatural"), score = 8.8),
            Anime(id = "chainsaw-man", title = "Chainsaw Man", genres = listOf("Action", "Supernatural", "Horror"), score = 8.5),
            Anime(id = "k-on", title = "K-On!", genres = listOf("Slice of Life", "Music"), score = 7.8),
            Anime(id = "solo-leveling", title = "Solo Leveling", genres = listOf("Action", "Fantasy"), score = 8.3)
        )

        val recommendations = FranchiseWatchOrderManager.findSmartRecommendations(target, catalog, limit = 5)

        // Excludes self
        assertFalse(recommendations.any { it.id == target.id })

        // High overlap (Action + Supernatural) should rank higher than Slice of Life
        val topIds = recommendations.map { it.id }
        assertTrue(topIds.contains("bleach"))
        assertTrue(topIds.contains("chainsaw-man"))
        assertTrue(topIds.indexOf("bleach") < topIds.indexOf("k-on"))
    }
}
