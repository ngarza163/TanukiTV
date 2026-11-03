package com.darknet.animetv.e2e.tier1

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.darknet.animetv.data.model.CustomSource
import com.darknet.animetv.data.storage.AppStorage
import com.darknet.animetv.e2e.harness.E2eTestFixtures
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Tier1_03_StorageTest {

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
    fun testSaveAndRetrieveWatchProgress() {
        val progress = E2eTestFixtures.createSampleWatchProgress(
            animeId = "anime-test-1",
            episodeNumber = 3,
            positionMs = 300_000L,
            durationMs = 1_400_000L
        )
        storage.saveWatchProgress(progress)

        val retrieved = storage.getWatchProgress("anime-test-1")
        assertNotNull("WatchProgress should be retrieved", retrieved)
        assertEquals("anime-test-1", retrieved?.animeId)
        assertEquals(3, retrieved?.episodeNumber)
        assertEquals(300_000L, retrieved?.positionMs)
        assertEquals(1_400_000L, retrieved?.durationMs)
    }

    @Test
    fun testWatchProgressUpsertUpdatesExisting() {
        val initial = E2eTestFixtures.createSampleWatchProgress(
            animeId = "anime-test-2",
            episodeNumber = 1,
            positionMs = 100_000L
        )
        storage.saveWatchProgress(initial)

        val updated = E2eTestFixtures.createSampleWatchProgress(
            animeId = "anime-test-2",
            episodeNumber = 2,
            positionMs = 850_000L
        )
        storage.saveWatchProgress(updated)

        val retrieved = storage.getWatchProgress("anime-test-2")
        assertEquals(2, retrieved?.episodeNumber)
        assertEquals(850_000L, retrieved?.positionMs)
    }

    @Test
    fun testFavoritesAddAndQuery() {
        val anime = E2eTestFixtures.createSampleAnime(id = "anime-fav-1", title = "Jujutsu Kaisen")
        assertFalse("Should not be favorite initially", storage.isFavorite("anime-fav-1"))

        storage.addFavorite(anime)
        assertTrue("Should be favorite after adding", storage.isFavorite("anime-fav-1"))

        val allFavorites = storage.getAllFavorites()
        assertEquals(1, allFavorites.size)
        assertEquals("anime-fav-1", allFavorites[0].id)
        assertEquals("Jujutsu Kaisen", allFavorites[0].title)
    }

    @Test
    fun testFavoritesRemove() {
        val anime = E2eTestFixtures.createSampleAnime(id = "anime-fav-2")
        storage.addFavorite(anime)
        assertTrue(storage.isFavorite("anime-fav-2"))

        storage.removeFavorite("anime-fav-2")
        assertFalse("Should no longer be favorite after removal", storage.isFavorite("anime-fav-2"))
    }

    @Test
    fun testCustomSourceSaveAndFilterEnabled() {
        val initialCount = storage.getSources().size
        val newSource = CustomSource(
            name = "Test Provider",
            baseUrl = "https://provider.mock",
            isEnabled = false,
            sourceType = "HTML_SCRAPER"
        )
        storage.saveSource(newSource)

        val allSources = storage.getSources()
        assertEquals(initialCount + 1, allSources.size)

        val enabledSources = storage.getEnabledSources()
        assertFalse("Disabled source should not be in enabled list", enabledSources.any { it.name == "Test Provider" })
    }
}
