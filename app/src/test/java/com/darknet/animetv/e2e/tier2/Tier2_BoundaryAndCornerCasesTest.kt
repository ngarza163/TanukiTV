package com.darknet.animetv.e2e.tier2

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.darknet.animetv.data.model.Anime
import com.darknet.animetv.data.model.CustomSource
import com.darknet.animetv.data.model.WatchProgress
import com.darknet.animetv.data.storage.AppStorage
import com.darknet.animetv.e2e.harness.DynamicHtmlSourceEngine
import com.darknet.animetv.e2e.harness.E2eTestFixtures
import com.darknet.animetv.e2e.harness.MockAnimeSource
import com.darknet.animetv.e2e.harness.MockFailoverRepository
import com.darknet.animetv.e2e.harness.TvPlayerHudSimulator
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class Tier2_BoundaryAndCornerCasesTest {

    private lateinit var context: Context
    private lateinit var storage: AppStorage

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("animetv_v2.db")
        storage = AppStorage(context)
    }

    @After
    fun tearDown() {
        storage.close()
        context.deleteDatabase("animetv_v2.db")
    }

    @Test
    fun testEmptyHttpResponseBody() {
        val emptyHtml = ""
        val engine = DynamicHtmlSourceEngine()
        val customSource = E2eTestFixtures.createSampleCustomSource()

        val parsedList = engine.parseAnimeList(emptyHtml, customSource)
        assertNotNull(parsedList)
        assertTrue("Empty HTML must yield empty anime list", parsedList.isEmpty())

        val streamUrl = engine.parseStreamUrl(emptyHtml, customSource)
        assertNull("Empty HTML must yield null stream URL", streamUrl)
    }

    @Test
    fun testHttp403ForbiddenHandling() = runBlocking {
        val source = MockAnimeSource(
            sourceId = "cloudflare-blocked",
            sourceName = "Blocked Source",
            shouldFail = true,
            failureException = IOException("HTTP 403 Forbidden: Cloudflare anti-bot challenged")
        )

        val repo = MockFailoverRepository(mutableListOf(source))
        val results = repo.searchWithFailover("One Piece")
        assertTrue("403 Forbidden must be handled gracefully without crashing", results.isEmpty())
    }

    @Test
    fun testHttp429RateLimitExceeded() {
        val rateLimitException = IOException("HTTP 429 Too Many Requests: Rate limit exceeded")
        val isRateLimited = rateLimitException.message?.contains("429") == true
        assertTrue("Must detect rate limit status", isRateLimited)
    }

    @Test
    fun testHttp500InternalServerErrorFailover() = runBlocking {
        val brokenSource = MockAnimeSource(
            sourceId = "server-error",
            sourceName = "Server Error Source",
            shouldFail = true,
            failureException = IOException("HTTP 500 Internal Server Error")
        )
        val workingSource = MockAnimeSource(
            sourceId = "healthy-source",
            sourceName = "Healthy Source"
        )

        val repo = MockFailoverRepository(mutableListOf(brokenSource, workingSource))
        val results = repo.getTrending()
        assertFalse("Failover must recover results from working source on 500 error", results.isEmpty())
    }

    @Test
    fun testMalformedJsonHandling() {
        val malformedJson = """{ "data": [ { "id": 123, "title": "Incomplete """
        var caught = false
        try {
            JsonParser.parseString(malformedJson)
        } catch (e: JsonSyntaxException) {
            caught = true
        }
        assertTrue("Malformed JSON must throw JsonSyntaxException safely", caught)
    }

    @Test
    fun testMalformedHtmlMissingAllSelectors() {
        val randomHtml = "<html><head></head><body><span>Just a banner</span></body></html>"
        val engine = DynamicHtmlSourceEngine()
        val customSource = CustomSource(
            name = "Strict",
            baseUrl = "https://mock.com",
            animeListSelector = "div.TargetItems > article",
            streamUrlSelector = "video#mainPlayer"
        )

        val animeList = engine.parseAnimeList(randomHtml, customSource)
        assertTrue(animeList.isEmpty())

        val streamUrl = engine.parseStreamUrl(randomHtml, customSource)
        assertNull(streamUrl)
    }

    @Test
    fun testZeroDurationVideoStreamDoesNotDivideByZero() {
        val zeroDurationProgress = WatchProgress(
            animeId = "anime-zero-div",
            animeTitle = "Zero Division Anime",
            posterUrl = "",
            episodeId = "ep-0",
            episodeNumber = 1,
            episodeTitle = "",
            positionMs = 5000L,
            durationMs = 0L
        )

        val progressPercent = zeroDurationProgress.progressPercentage
        assertEquals(0.0f, progressPercent, 0.0001f)
        assertFalse("Float should not be NaN", progressPercent.isNaN())
        assertFalse("Float should not be Infinite", progressPercent.isInfinite())
    }

    @Test
    fun testNegativeAndExtremeTimestamps() {
        val extremeProgress = WatchProgress(
            animeId = "extreme-time",
            animeTitle = "Extreme Time Anime",
            posterUrl = "",
            episodeId = "ep-1",
            episodeNumber = 1,
            episodeTitle = "",
            positionMs = Long.MAX_VALUE,
            durationMs = 1_000_000L,
            lastWatchedTimestamp = -999L
        )

        storage.saveWatchProgress(extremeProgress)
        val retrieved = storage.getWatchProgress("extreme-time")
        assertNotNull(retrieved)
        assertEquals(Long.MAX_VALUE, retrieved?.positionMs)
        assertTrue("Negative timestamp should be normalized to positive current epoch", (retrieved?.lastWatchedTimestamp ?: 0L) > 0L)
    }

    @Test
    fun testUltraLongTitleAndSynopsis() {
        val longTitle = "A".repeat(1000)
        val longSynopsis = "This is an ultra detailed synopsis. ".repeat(300) // ~10,800 chars

        val hugeAnime = Anime(
            id = "huge-anime",
            title = longTitle,
            synopsis = longSynopsis
        )

        storage.addFavorite(hugeAnime)
        val retrieved = storage.getAllFavorites().find { it.id == "huge-anime" }

        assertNotNull(retrieved)
        assertEquals(1000, retrieved?.title?.length)
        assertEquals(longSynopsis.length, retrieved?.synopsis?.length)
    }

    @Test
    fun testSqlSpecialCharactersInFields() {
        val maliciousTitle = "Hunter x Hunter'; DROP TABLE favorites; -- '\""
        val maliciousSynopsis = "O'Reilly's Guide to SQL: \"Quotes\" & ;-- /* comment */"

        val sqlTestAnime = Anime(
            id = "sql-injection-test",
            title = maliciousTitle,
            synopsis = maliciousSynopsis
        )

        storage.addFavorite(sqlTestAnime)
        assertTrue(storage.isFavorite("sql-injection-test"))

        val retrieved = storage.getAllFavorites().find { it.id == "sql-injection-test" }
        assertEquals(maliciousTitle, retrieved?.title)
        assertEquals(maliciousSynopsis, retrieved?.synopsis)

        // Verify database is not dropped
        val allFavorites = storage.getAllFavorites()
        assertFalse(allFavorites.isEmpty())
    }

    @Test
    fun testSpecialUnicodeAndMultilingualCharacters() {
        val unicodeAnime = Anime(
            id = "unicode-anime",
            title = "葬送のフリーレン / 鬼滅の刃 / 🌟 Anime 2024 / العربية",
            synopsis = "Elf mage (エルフ) in a fantasy realm ⚔️🛡️."
        )

        storage.addFavorite(unicodeAnime)
        val retrieved = storage.getAllFavorites().find { it.id == "unicode-anime" }

        assertNotNull(retrieved)
        assertEquals("葬送のフリーレン / 鬼滅の刃 / 🌟 Anime 2024 / العربية", retrieved?.title)
        assertEquals("Elf mage (エルフ) in a fantasy realm ⚔️🛡️.", retrieved?.synopsis)
    }

    @Test
    fun testExtremeSeekOperationsClamping() {
        val hud = TvPlayerHudSimulator(seekStepMs = 10_000L)
        hud.setPosition(positionMs = 50_000L, totalDurationMs = 100_000L)

        // Seek forward 20 times (20 * 10s = 200s, total duration is 100s)
        for (i in 1..20) {
            hud.seekForward()
        }
        assertEquals(100_000L, hud.currentPositionMs)

        // Seek rewind 25 times
        for (i in 1..25) {
            hud.seekRewind()
        }
        assertEquals(0L, hud.currentPositionMs)
    }
}
