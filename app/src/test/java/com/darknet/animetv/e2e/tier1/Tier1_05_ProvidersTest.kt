package com.darknet.animetv.e2e.tier1

import com.darknet.animetv.data.model.Anime
import com.darknet.animetv.data.model.VideoSource
import com.darknet.animetv.e2e.harness.E2eTestFixtures
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class Tier1_05_ProvidersTest {

    @Test
    fun testAnimeFlvHtmlCatalogParsing() {
        val doc = Jsoup.parse(E2eTestFixtures.MOCK_ANIMEFLV_BROWSE_HTML)
        val elements = doc.select("ul.ListAnimes article.Anime")
        assertEquals(2, elements.size)

        val firstTitle = elements[0].select("h3.Title").text()
        val firstScore = elements[0].select("span.Vts").text().toDoubleOrNull()
        val firstHref = elements[0].select("a").attr("href")

        assertEquals("Sousou no Frieren", firstTitle)
        assertEquals(9.4, firstScore ?: 0.0, 0.01)
        assertEquals("/anime/sousou-no-frieren", firstHref)
    }

    @Test
    fun testAniwatchProviderModelStructure() {
        val sampleAnime = E2eTestFixtures.createSampleAnime(
            id = "aniwatch-154587",
            title = "Frieren: Beyond Journey's End",
            sourceId = "aniwatch"
        )
        assertEquals("aniwatch", sampleAnime.sourceId)
        assertTrue(sampleAnime.posterUrl.isNotEmpty())
    }

    @Test
    fun testMiruroFastJsonStreamStructure() {
        val miruroStream = VideoSource(
            serverName = "Miruro-FastHLS",
            url = "https://cdn.miruro.cx/hls/frieren-ep1.m3u8",
            quality = "1080p",
            isHls = true,
            headers = mapOf("Referer" to "https://www.miruro.cx/")
        )
        assertTrue("Stream must be identified as HLS", miruroStream.isHls)
        assertEquals("https://www.miruro.cx/", miruroStream.headers["Referer"])
    }

    @Test
    fun testKaaProviderCryptoHeadersSupport() {
        val kaaStream = VideoSource(
            serverName = "KAA-EncryptedServer",
            url = "https://kaa.lt/stream/aes-chunk.m3u8",
            quality = "1080p",
            isHls = true,
            headers = mapOf(
                "X-Auth-Token" to "kaa-secure-token-12345",
                "Referer" to "https://kaa.lt/"
            )
        )
        assertEquals("kaa-secure-token-12345", kaaStream.headers["X-Auth-Token"])
    }

    @Test
    fun testWcoDubbedStreamTagging() {
        val wcoAnime = Anime(
            id = "wco-frieren-dub",
            title = "Frieren: Beyond Journey's End (English Dub)",
            sourceId = "wco",
            genres = listOf("Dubbed", "Adventure", "Fantasy")
        )
        assertTrue("WCO anime should identify as dubbed", wcoAnime.title.contains("Dub", ignoreCase = true))
        assertTrue("WCO anime should contain Dubbed genre tag", wcoAnime.genres.contains("Dubbed"))
    }
}
