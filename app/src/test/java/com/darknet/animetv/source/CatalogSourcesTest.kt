package com.darknet.animetv.source

import com.darknet.animetv.data.source.AniListCatalogSource
import com.darknet.animetv.data.source.JikanSource
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogSourcesTest {

    @Test
    fun testAniListCatalogSourceMediaNodeParsing() {
        val source = AniListCatalogSource()
        val json = """
            {
                "id": 169755,
                "title": {
                    "english": "Bleach: Thousand-Year Blood War - The Conflict",
                    "romaji": "Bleach: Sennen Kessen-hen - Soukoku-tan",
                    "native": "BLEACH 千年血戦篇-相剋譚-"
                },
                "coverImage": {
                    "extraLarge": "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx169755.jpg",
                    "large": "https://s4.anilist.co/file/anilistcdn/media/anime/cover/medium/bx169755.jpg"
                },
                "bannerImage": "https://s4.anilist.co/file/anilistcdn/media/anime/banner/169755-banner.jpg",
                "averageScore": 88,
                "seasonYear": 2024,
                "status": "RELEASING",
                "genres": ["Action", "Adventure", "Supernatural"],
                "description": "Third cour of Bleach: Thousand-Year Blood War.",
                "episodes": 13
            }
        """.trimIndent()

        val mediaObj = JsonParser.parseString(json).asJsonObject
        val anime = source.parseMediaNode(mediaObj)

        assertEquals("anilist-169755", anime.id)
        assertEquals("Bleach: Thousand-Year Blood War - The Conflict", anime.title)
        assertEquals("https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx169755.jpg", anime.posterUrl)
        // 16:9 banner must be prioritized for backdrop
        assertEquals("https://s4.anilist.co/file/anilistcdn/media/anime/banner/169755-banner.jpg", anime.backdropUrl)
        // Score normalized to 0-10 scale (88 -> 8.8)
        assertEquals(8.8, anime.score, 0.01)
        assertEquals(2024, anime.year)
        assertEquals("RELEASING", anime.status)
        assertEquals(3, anime.genres.size)
        assertTrue(anime.genres.contains("Action"))
        assertEquals(13, anime.totalEpisodes)
    }

    @Test
    fun testAniListBannerFallbackWhenBannerMissing() {
        val source = AniListCatalogSource()
        val json = """
            {
                "id": 99999,
                "title": { "romaji": "Anime Without Banner" },
                "coverImage": { "extraLarge": "https://example.com/cover.jpg" },
                "bannerImage": null,
                "averageScore": 75,
                "episodes": 24
            }
        """.trimIndent()

        val mediaObj = JsonParser.parseString(json).asJsonObject
        val anime = source.parseMediaNode(mediaObj)

        assertEquals("https://example.com/cover.jpg", anime.posterUrl)
        // Falls back to poster when banner is null
        assertEquals("https://example.com/cover.jpg", anime.backdropUrl)
        assertEquals(7.5, anime.score, 0.01)
    }

    @Test
    fun testJikanSourceProperties() {
        val jikan = JikanSource()
        assertEquals("jikan", jikan.sourceId)
        assertEquals("AniList / Jikan Catalog", jikan.sourceName)
        assertEquals("https://api.jikan.moe/v4", jikan.baseUrl)
        assertTrue(jikan.isEnabled)
    }
}
