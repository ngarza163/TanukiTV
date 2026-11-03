package com.darknet.animetv.storage

import android.content.ContentValues
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.darknet.animetv.data.model.Anime
import com.darknet.animetv.data.model.CustomSource
import com.darknet.animetv.data.model.WatchProgress
import com.darknet.animetv.data.storage.AppDatabaseHelper
import com.darknet.animetv.data.storage.AppStorage
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
class AppStorageEmpiricalTest {

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

    // ==========================================
    // 1. EMPTY STRINGS & WHITESPACE EDGE CASES
    // ==========================================

    @Test
    fun testEmptyStringsInWatchProgress() {
        val emptyProgress = WatchProgress(
            animeId = "",
            animeTitle = "",
            posterUrl = "",
            episodeId = "",
            episodeNumber = 0,
            episodeTitle = "",
            positionMs = 0L,
            durationMs = 0L,
            lastWatchedTimestamp = 0L
        )
        storage.saveWatchProgress(emptyProgress)
        val retrieved = storage.getWatchProgress("")
        assertNotNull("Should retrieve entry with empty animeId", retrieved)
        assertEquals("", retrieved?.animeId)
        assertEquals("", retrieved?.animeTitle)
        assertEquals("", retrieved?.posterUrl)
        assertEquals("", retrieved?.episodeId)
        assertEquals("", retrieved?.episodeTitle)
    }

    @Test
    fun testWhitespaceOnlyStringsInWatchProgress() {
        val whitespaceProgress = WatchProgress(
            animeId = "   ",
            animeTitle = " \t \n ",
            posterUrl = "   ",
            episodeId = " ",
            episodeNumber = 1,
            episodeTitle = "\n\r\t",
            positionMs = 100L,
            durationMs = 200L
        )
        storage.saveWatchProgress(whitespaceProgress)
        val retrieved = storage.getWatchProgress("   ")
        assertNotNull(retrieved)
        assertEquals("   ", retrieved?.animeId)
        assertEquals(" \t \n ", retrieved?.animeTitle)
    }

    @Test
    fun testEmptyStringsInFavorite() {
        val emptyAnime = Anime(
            id = "",
            title = "",
            posterUrl = "",
            backdropUrl = "",
            score = 0.0,
            year = 0,
            status = "",
            genres = emptyList(),
            synopsis = ""
        )
        storage.addFavorite(emptyAnime)
        assertTrue(storage.isFavorite(""))
        val retrieved = storage.getFavorite("")
        assertNotNull(retrieved)
        assertEquals("", retrieved?.id)
        assertEquals("", retrieved?.title)
        assertEquals(emptyList<String>(), retrieved?.genres)
    }

    @Test
    fun testEmptyStringsInCustomSource() {
        val emptySource = CustomSource(
            id = 0,
            name = "",
            baseUrl = "",
            animeListSelector = "",
            episodeSelector = "",
            streamUrlSelector = "",
            isEnabled = false,
            sourceType = ""
        )
        val generatedId = storage.saveSource(emptySource)
        assertTrue("Generated ID should be > 0", generatedId > 0)
        val retrieved = storage.getSource(generatedId)
        assertNotNull(retrieved)
        assertEquals("", retrieved?.name)
        assertEquals("", retrieved?.baseUrl)
        assertFalse(retrieved!!.isEnabled)
    }

    // ==========================================
    // 2. EXTREME TIMESTAMPS & NUMERIC BOUNDARIES
    // ==========================================

    @Test
    fun testExtremeNumericValuesInWatchProgress() {
        val extremeProgress = WatchProgress(
            animeId = "extreme_nums",
            animeTitle = "Extreme Numbers",
            posterUrl = "url",
            episodeId = "ep1",
            episodeNumber = Int.MAX_VALUE,
            episodeTitle = "Max Ep",
            positionMs = Long.MAX_VALUE,
            durationMs = Long.MAX_VALUE,
            lastWatchedTimestamp = Long.MAX_VALUE
        )
        storage.saveWatchProgress(extremeProgress)
        val retrieved = storage.getWatchProgress("extreme_nums")
        assertNotNull(retrieved)
        assertEquals(Int.MAX_VALUE, retrieved?.episodeNumber)
        assertEquals(Long.MAX_VALUE, retrieved?.positionMs)
        assertEquals(Long.MAX_VALUE, retrieved?.durationMs)
    }

    @Test
    fun testNegativeNumericValuesInWatchProgress() {
        val negativeProgress = WatchProgress(
            animeId = "neg_nums",
            animeTitle = "Negative Numbers",
            posterUrl = "url",
            episodeId = "ep1",
            episodeNumber = -1,
            episodeTitle = "Neg Ep",
            positionMs = -5000L,
            durationMs = -10000L,
            lastWatchedTimestamp = -1L
        )
        storage.saveWatchProgress(negativeProgress)
        val retrieved = storage.getWatchProgress("neg_nums")
        assertNotNull(retrieved)
        assertEquals(-1, retrieved?.episodeNumber)
        assertEquals(-5000L, retrieved?.positionMs)
        assertEquals(-10000L, retrieved?.durationMs)
    }

    @Test
    fun testLastWatchedTimestampPreservationHypothesis() {
        val customTimestamp = 123456789000L // historical timestamp (year 1973)
        val progress = WatchProgress(
            animeId = "timestamp_test",
            animeTitle = "Timestamp Test",
            posterUrl = "url",
            episodeId = "ep1",
            episodeNumber = 1,
            episodeTitle = "Ep 1",
            positionMs = 1000L,
            durationMs = 2000L,
            lastWatchedTimestamp = customTimestamp
        )
        storage.saveWatchProgress(progress)

        val retrieved = storage.getWatchProgress("timestamp_test")
        assertNotNull(retrieved)

        val actualTimestamp = retrieved!!.lastWatchedTimestamp
        val preserved = (actualTimestamp == customTimestamp)
        println("Empirical Check - customTimestamp: $customTimestamp vs actualStored: $actualTimestamp (Preserved=$preserved)")
        assertTrue("Custom lastWatchedTimestamp must be preserved across storage round-trip", preserved)
        assertEquals(customTimestamp, actualTimestamp)
    }

    @Test
    fun testLastWatchedTimestampDefaultWhenZeroOrNegative() {
        val before = System.currentTimeMillis()
        val zeroProgress = WatchProgress(
            animeId = "zero_ts",
            animeTitle = "Zero Timestamp",
            posterUrl = "url",
            episodeId = "ep1",
            episodeNumber = 1,
            episodeTitle = "Ep 1",
            positionMs = 100L,
            durationMs = 200L,
            lastWatchedTimestamp = 0L
        )
        storage.saveWatchProgress(zeroProgress)
        val after = System.currentTimeMillis()
        val retrievedZero = storage.getWatchProgress("zero_ts")
        assertNotNull(retrievedZero)
        assertTrue("When lastWatchedTimestamp is 0, should default to current time window",
            retrievedZero!!.lastWatchedTimestamp in before..after)

        val beforeNeg = System.currentTimeMillis()
        val negProgress = WatchProgress(
            animeId = "neg_ts",
            animeTitle = "Negative Timestamp",
            posterUrl = "url",
            episodeId = "ep1",
            episodeNumber = 1,
            episodeTitle = "Ep 1",
            positionMs = 100L,
            durationMs = 200L,
            lastWatchedTimestamp = -100L
        )
        storage.saveWatchProgress(negProgress)
        val afterNeg = System.currentTimeMillis()
        val retrievedNeg = storage.getWatchProgress("neg_ts")
        assertNotNull(retrievedNeg)
        assertTrue("When lastWatchedTimestamp is negative, should default to current time window",
            retrievedNeg!!.lastWatchedTimestamp in beforeNeg..afterNeg)
    }

    @Test
    fun testExtremeScoresAndYearsInFavorites() {
        val extremeAnime = Anime(
            id = "extreme_anime",
            title = "Extreme Stats",
            posterUrl = "poster",
            backdropUrl = "backdrop",
            score = 999999.99,
            year = Int.MIN_VALUE,
            status = "Unknown",
            genres = listOf("G1"),
            synopsis = "Synopsis"
        )
        storage.addFavorite(extremeAnime)
        val retrieved = storage.getFavorite("extreme_anime")
        assertNotNull(retrieved)
        assertEquals(999999.99, retrieved!!.score, 0.001)
        assertEquals(Int.MIN_VALUE, retrieved.year)
    }

    // ==========================================
    // 3. RAPID SEQUENTIAL & CONCURRENT UPDATES
    // ==========================================

    @Test
    fun testRapidSequentialWatchProgressUpdates() {
        val animeId = "rapid_anime"
        val totalUpdates = 200
        for (i in 1..totalUpdates) {
            val progress = WatchProgress(
                animeId = animeId,
                animeTitle = "Rapid Updates Anime",
                posterUrl = "url",
                episodeId = "ep_$i",
                episodeNumber = i,
                episodeTitle = "Episode $i",
                positionMs = (i * 1000).toLong(),
                durationMs = 2400000L
            )
            storage.saveWatchProgress(progress)
        }

        val allHistory = storage.getAllWatchHistory()
        assertEquals("Should only have 1 row for this anime_id", 1, allHistory.size)
        val finalProgress = storage.getWatchProgress(animeId)
        assertNotNull(finalProgress)
        assertEquals(totalUpdates, finalProgress?.episodeNumber)
        assertEquals((totalUpdates * 1000).toLong(), finalProgress?.positionMs)
    }

    @Test
    fun testRapidFavoriteToggling() {
        val anime = Anime(
            id = "toggle_test",
            title = "Toggle Anime"
        )
        val toggleCount = 101 // Odd number -> final state should be isFavorite == true
        for (i in 1..toggleCount) {
            val isNowFav = storage.toggleFavorite(anime)
            assertEquals(i % 2 == 1, isNowFav)
        }
        assertTrue("After 101 toggles, should be favorite", storage.isFavorite("toggle_test"))
        assertEquals(1, storage.getAllFavorites().size)

        // Toggle once more (102nd) -> should be removed
        val removed = storage.toggleFavorite(anime)
        assertFalse(removed)
        assertFalse(storage.isFavorite("toggle_test"))
        assertEquals(0, storage.getAllFavorites().size)
    }

    @Test
    fun testConcurrentWatchProgressWrites() {
        val threadCount = 8
        val writesPerThread = 25
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val errorCount = AtomicInteger(0)

        for (t in 0 until threadCount) {
            executor.submit {
                try {
                    for (w in 0 until writesPerThread) {
                        val animeId = "concurrent_anime_${t}_$w"
                        storage.saveWatchProgress(
                            WatchProgress(
                                animeId = animeId,
                                animeTitle = "Thread $t Write $w",
                                posterUrl = "url",
                                episodeId = "ep_$w",
                                episodeNumber = w,
                                episodeTitle = "Ep $w",
                                positionMs = w * 100L,
                                durationMs = 10000L
                            )
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    errorCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        assertEquals("No concurrent write exceptions should occur", 0, errorCount.get())
        val allHistory = storage.getAllWatchHistory()
        assertEquals(threadCount * writesPerThread, allHistory.size)
    }

    // ==========================================
    // 4. UNICODE & SQL INJECTION STRINGS
    // ==========================================

    @Test
    fun testUnicodeAndJapaneseTitles() {
        val titles = listOf(
            "葬送のフリーレン (Frieren: Beyond Journey's End)",
            "鬼滅の刃 刀鍛冶の里編 🔥⚔️",
            "攻殻機動隊 STAND ALONE COMPLEX",
            "Re:ゼロから始める異世界生活 3rd season",
            "مرحبا بكم في عالم الأنمي (Arabic Anime)",
            "Атака титанов: Финал (Cyrillic)",
            "Koe no Katachi (聲の形) — 100% Quality & <Special> \"Quotes\""
        )

        titles.forEachIndexed { index, title ->
            val animeId = "unicode_anime_$index"
            val progress = WatchProgress(
                animeId = animeId,
                animeTitle = title,
                posterUrl = "https://example.com/poster?id=$index&lang=jp#anchor",
                episodeId = "ep_$index",
                episodeNumber = index + 1,
                episodeTitle = "第${index + 1}話: $title",
                positionMs = 12345L,
                durationMs = 67890L
            )
            storage.saveWatchProgress(progress)

            val retrieved = storage.getWatchProgress(animeId)
            assertNotNull("Failed retrieving unicode anime $animeId", retrieved)
            assertEquals("Title mismatch for unicode test", title, retrieved?.animeTitle)
            assertEquals("Episode title mismatch", "第${index + 1}話: $title", retrieved?.episodeTitle)
        }
    }

    @Test
    fun testSqlInjectionPayloads() {
        val maliciousAnimeId = "attack'; DROP TABLE watch_history; --"
        val maliciousTitle = "Normal Title', (SELECT 'hacked')); --"
        val progress = WatchProgress(
            animeId = maliciousAnimeId,
            animeTitle = maliciousTitle,
            posterUrl = "http://bad.url/';--",
            episodeId = "ep_inj",
            episodeNumber = 1,
            episodeTitle = "Injection Ep",
            positionMs = 500L,
            durationMs = 1000L
        )

        // Should not throw SQL syntax error and should treat payload literally as parameters
        storage.saveWatchProgress(progress)

        val retrieved = storage.getWatchProgress(maliciousAnimeId)
        assertNotNull("Malicious ID should be stored safely as a literal", retrieved)
        assertEquals(maliciousAnimeId, retrieved?.animeId)
        assertEquals(maliciousTitle, retrieved?.animeTitle)

        // Verify table was NOT dropped
        val allHistory = storage.getAllWatchHistory()
        assertTrue("Table should still exist and contain 1 record", allHistory.isNotEmpty())
    }

    // ==========================================
    // 5. DUPLICATE KEYS & UPSERT VS REPLACE
    // ==========================================

    @Test
    fun testWatchProgressUpsertReplacesExistingEpisode() {
        val animeId = "naruto_shippuden"

        // Watch episode 1
        storage.saveWatchProgress(
            WatchProgress(
                animeId = animeId,
                animeTitle = "Naruto Shippuden",
                posterUrl = "poster_1",
                episodeId = "ep_1",
                episodeNumber = 1,
                episodeTitle = "Homecoming",
                positionMs = 50000L,
                durationMs = 1400000L
            )
        )

        // Watch episode 2 of the same anime
        storage.saveWatchProgress(
            WatchProgress(
                animeId = animeId,
                animeTitle = "Naruto Shippuden",
                posterUrl = "poster_2",
                episodeId = "ep_2",
                episodeNumber = 2,
                episodeTitle = "The Akatsuki Makes Its Move",
                positionMs = 10000L,
                durationMs = 1400000L
            )
        )

        val allHistory = storage.getAllWatchHistory()
        // Single row because primary key is anime_id
        assertEquals(1, allHistory.size)
        val current = storage.getWatchProgress(animeId)
        assertNotNull(current)
        assertEquals(2, current?.episodeNumber)
        assertEquals("ep_2", current?.episodeId)
    }

    @Test
    fun testCustomSourceSaveBehaviorWithDuplicateOrNewIds() {
        // Test saveSource with id = 0 (new insert)
        val s1 = CustomSource(
            id = 0,
            name = "Test Source 1",
            baseUrl = "https://source1.com"
        )
        val id1 = storage.saveSource(s1)
        assertTrue(id1 > 0)

        // Test saveSource with existing id (update)
        val s1Updated = s1.copy(id = id1, name = "Test Source 1 Updated")
        val updatedId = storage.saveSource(s1Updated)
        assertEquals(id1, updatedId)
        val retrieved = storage.getSource(id1)
        assertEquals("Test Source 1 Updated", retrieved?.name)

        // Test saveSource with non-existent positive id (id = 9999)
        val sNonExistent = CustomSource(
            id = 9999L,
            name = "Non Existent ID Source",
            baseUrl = "https://source9999.com"
        )
        val resultId = storage.saveSource(sNonExistent)
        val retrievedNonExistent = storage.getSource(resultId)
        assertNotNull("Should exist after inserting with explicit non-existent ID", retrievedNonExistent)
        assertEquals("Non Existent ID Source", retrievedNonExistent?.name)
    }

    @Test
    fun testGenresSerializationWithCommas() {
        val originalGenres = listOf("Sci-Fi, Mecha", "Action", "Romance, Comedy")
        val anime = Anime(
            id = "comma_genre_test",
            title = "Comma Genre Anime",
            genres = originalGenres
        )
        storage.addFavorite(anime)

        val retrieved = storage.getFavorite("comma_genre_test")
        assertNotNull(retrieved)

        println("Original genres: $originalGenres")
        println("Retrieved genres: ${retrieved?.genres}")

        val matchesOriginal = (originalGenres == retrieved?.genres)
        println("Empirical Check - Comma in genre preserved exactly: $matchesOriginal")
        assertTrue("Comma inside a genre string should be preserved without delimiter collision",
            matchesOriginal)
        assertEquals(originalGenres, retrieved?.genres)
    }

    @Test
    fun testLegacyCommaSeparatedGenreCompatibility() {
        val helper = AppDatabaseHelper(context)
        try {
            val db = helper.writableDatabase
            val cv = ContentValues().apply {
                put("anime_id", "legacy_genre_anime")
                put("title", "Legacy Anime")
                put("poster_url", "poster")
                put("backdrop_url", "backdrop")
                put("score", 7.5)
                put("year", 2020)
                put("status", "Completed")
                put("genres", "Action,Adventure,Fantasy")
                put("synopsis", "Legacy synopsis")
                put("added_at", System.currentTimeMillis())
            }
            db.insert("favorites", null, cv)
        } finally {
            helper.close()
        }

        val favorite = storage.getFavorite("legacy_genre_anime")
        assertNotNull(favorite)
        assertEquals(listOf("Action", "Adventure", "Fantasy"), favorite?.genres)
    }

    // ==========================================
    // 6. NULL HANDLING & SCHEMA NULLABILITY HAZARD
    // ==========================================

    @Test
    fun testNullHandlingInWatchHistoryRawQuery() {
        val helper = AppDatabaseHelper(context)
        try {
            val db = helper.writableDatabase
            val cv = ContentValues().apply {
                put("anime_id", "null_fields_anime")
                putNull("anime_title") // NULL in DB!
                putNull("poster_url")
                putNull("episode_id")
                put("episode_number", 1)
                putNull("episode_title")
                put("position_ms", 0L)
                put("duration_ms", 1000L)
                put("updated_at", System.currentTimeMillis())
            }
            db.insert("watch_history", null, cv)
        } finally {
            helper.close()
        }

        val progress = storage.getWatchProgress("null_fields_anime")
        assertNotNull("Should safely handle null database fields", progress)
        assertEquals("null_fields_anime", progress?.animeId)
        assertEquals("", progress?.animeTitle)
        assertEquals("", progress?.posterUrl)
        assertEquals("", progress?.episodeId)
        assertEquals(1, progress?.episodeNumber)
        assertEquals("", progress?.episodeTitle)
        assertEquals(0L, progress?.positionMs)
        assertEquals(1000L, progress?.durationMs)
    }

    @Test
    fun testNullHandlingInFavoritesRawQuery() {
        val helper = AppDatabaseHelper(context)
        try {
            val db = helper.writableDatabase
            val cv = ContentValues().apply {
                put("anime_id", "null_fav_anime")
                putNull("title")
                putNull("poster_url")
                putNull("backdrop_url")
                put("score", 8.5)
                put("year", 2024)
                putNull("status")
                putNull("genres")
                putNull("synopsis")
                put("added_at", System.currentTimeMillis())
            }
            db.insert("favorites", null, cv)
        } finally {
            helper.close()
        }

        val favorite = storage.getFavorite("null_fav_anime")
        assertNotNull("Should safely handle null database fields in favorites", favorite)
        assertEquals("null_fav_anime", favorite?.id)
        assertEquals("", favorite?.title)
        assertEquals("", favorite?.posterUrl)
        assertEquals("", favorite?.backdropUrl)
        assertEquals(8.5, favorite?.score ?: 0.0, 0.001)
        assertEquals(2024, favorite?.year)
        assertEquals("Ongoing", favorite?.status)
        assertEquals(emptyList<String>(), favorite?.genres)
        assertEquals("", favorite?.synopsis)
    }

    @Test
    fun testNullHandlingInSourcesRawQuery() {
        val helper = AppDatabaseHelper(context)
        val rowId: Long
        try {
            val db = helper.writableDatabase
            val cv = ContentValues().apply {
                putNull("name")
                putNull("base_url")
                putNull("anime_list_selector")
                putNull("episode_selector")
                putNull("stream_url_selector")
                put("is_enabled", 1)
                putNull("source_type")
            }
            rowId = db.insert("sources", null, cv)
        } finally {
            helper.close()
        }

        val source = storage.getSource(rowId)
        assertNotNull("Should safely handle null database fields in sources", source)
        assertEquals("", source?.name)
        assertEquals("", source?.baseUrl)
        assertEquals("", source?.animeListSelector)
        assertEquals("", source?.episodeSelector)
        assertEquals("", source?.streamUrlSelector)
        assertTrue(source?.isEnabled == true)
        assertEquals("HTML_SCRAPER", source?.sourceType)
    }

    // ==========================================
    // 7. DEFAULT SEEDED SOURCES VERIFICATION
    // ==========================================

    @Test
    fun testDefaultSeededSourcesOnCreation() {
        val sources = storage.getSources()
        assertEquals("Initial database should have 10 seeded sources", 10, sources.size)

        val names = sources.map { it.name }
        assertTrue(names.any { it.contains("Aniwatch") })
        assertTrue(names.any { it.contains("Anikoto") })
        assertTrue(names.any { it.contains("Miruro") })
        assertTrue(names.any { it.contains("ReAnime") })
        assertTrue(names.any { it.contains("KAA") })
        assertTrue(names.any { it.contains("WCO") })
        assertTrue(names.any { it.contains("AnimeFLV") })
        assertTrue(names.any { it.contains("Consumet") })
        assertTrue(names.any { it.contains("Jikan") })

        val enabled = storage.getEnabledSources()
        assertEquals(10, enabled.size)
    }

    @Test
    fun testToggleSourceAndDeletion() {
        val sources = storage.getSources()
        val initialCount = sources.size
        val firstId = sources.first().id

        // Disable first source
        storage.toggleSource(firstId, false)
        val updated = storage.getSource(firstId)
        assertNotNull(updated)
        assertFalse(updated!!.isEnabled)

        val enabledSources = storage.getEnabledSources()
        assertEquals(initialCount - 1, enabledSources.size)

        // Delete first source
        storage.deleteSource(firstId)
        assertNull(storage.getSource(firstId))
        assertEquals(initialCount - 1, storage.getSources().size)
    }

    @Test
    fun testSaveSourcesOrderPersistsPriority() {
        val initial = storage.getSources()
        assertTrue(initial.size >= 3)
        val reversed = initial.reversed()
        storage.saveSourcesOrder(reversed)

        val afterReorder = storage.getSources()
        assertEquals(reversed.map { it.id }, afterReorder.map { it.id })
    }
}
