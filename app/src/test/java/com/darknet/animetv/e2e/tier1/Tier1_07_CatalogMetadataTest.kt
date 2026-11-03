package com.darknet.animetv.e2e.tier1

import com.darknet.animetv.e2e.harness.E2eTestFixtures
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class Tier1_07_CatalogMetadataTest {

    @Test
    fun testAniListGraphQLBannerImageParsing() {
        val root = JsonParser.parseString(E2eTestFixtures.MOCK_ANILIST_GRAPHQL_JSON).asJsonObject
        val media = root.getAsJsonObject("data").getAsJsonObject("Page").getAsJsonArray("media")[0].asJsonObject

        val banner = media.get("bannerImage")?.asString
        assertNotNull("AniList must provide 16:9 banner image", banner)
        assertTrue("Banner URL must be HTTPS", banner!!.startsWith("https://"))
    }

    @Test
    fun testAniListTitleAndGenresExtraction() {
        val root = JsonParser.parseString(E2eTestFixtures.MOCK_ANILIST_GRAPHQL_JSON).asJsonObject
        val media = root.getAsJsonObject("data").getAsJsonObject("Page").getAsJsonArray("media")[0].asJsonObject

        val titleObj = media.getAsJsonObject("title")
        val englishTitle = titleObj.get("english")?.asString
        assertEquals("Frieren: Beyond Journey's End", englishTitle)

        val genres = media.getAsJsonArray("genres").map { it.asString }
        assertTrue(genres.contains("Adventure"))
        assertTrue(genres.contains("Fantasy"))
    }

    @Test
    fun testAniListScoreNormalizationToTenPointScale() {
        val root = JsonParser.parseString(E2eTestFixtures.MOCK_ANILIST_GRAPHQL_JSON).asJsonObject
        val media = root.getAsJsonObject("data").getAsJsonObject("Page").getAsJsonArray("media")[0].asJsonObject

        val averageScore100 = media.get("averageScore").asInt
        val normalized10 = averageScore100 / 10.0
        assertEquals(9.4, normalized10, 0.01)
    }

    @Test
    fun testJikanRestFallbackParsing() {
        val root = JsonParser.parseString(E2eTestFixtures.MOCK_JIKAN_REST_JSON).asJsonObject
        val animeItem = root.getAsJsonArray("data")[0].asJsonObject

        val title = animeItem.get("title").asString
        val score = animeItem.get("score").asDouble
        val poster = animeItem.getAsJsonObject("images").getAsJsonObject("jpg").get("large_image_url").asString

        assertEquals("Sousou no Frieren", title)
        assertEquals(9.38, score, 0.01)
        assertTrue(poster.startsWith("https://"))
    }

    @Test
    fun testMetadataProviderFallbackResolution() {
        // Simulating metadata resolver that attempts AniList, falling back to Jikan if AniList banner is null
        val aniListMediaWithoutBanner = JsonParser.parseString("""
            { "bannerImage": null, "coverImage": { "extraLarge": "https://s4.anilist.co/cover.jpg" } }
        """).asJsonObject

        val resolvedBackdrop = aniListMediaWithoutBanner.get("bannerImage")?.takeIf { !it.isJsonNull }?.asString
            ?: aniListMediaWithoutBanner.getAsJsonObject("coverImage").get("extraLarge").asString

        assertEquals("https://s4.anilist.co/cover.jpg", resolvedBackdrop)
    }
}
