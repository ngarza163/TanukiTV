package com.darknet.animetv.sync

import com.darknet.animetv.data.model.AniListUser
import com.darknet.animetv.data.model.Anime
import com.darknet.animetv.data.model.WatchProgress
import com.darknet.animetv.data.repository.AniListSyncManager
import com.darknet.animetv.data.source.JikanSource
import com.darknet.animetv.data.storage.AppStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ChallengerSyncAndDatabaseAdversarialTest {

    private lateinit var storage: AppStorage
    private lateinit var jikanSource: JikanSource

    @Before
    fun setUp() {
        storage = AppStorage(RuntimeEnvironment.getApplication())
        storage.saveAniListToken(null)
        storage.saveAniListUser(null)
        storage.clearWatchlist()
        storage.clearWatchHistory()
        AniListSyncManager.anilistEndpoint = "http://127.0.0.1:65534/graphql" // dead loopback port
        jikanSource = JikanSource()
    }

    @After
    fun tearDown() {
        storage.clearWatchlist()
        storage.clearWatchHistory()
        storage.saveAniListToken(null)
        storage.saveAniListUser(null)
        AniListSyncManager.anilistEndpoint = "https://graphql.anilist.co"
    }

    // =========================================================================
    // 1. JikanSource Stream Resolution Isolation
    // =========================================================================

    @Test
    fun testJikanSource_GetEpisodeStreamsStrictlyReturnsEmptyList() = runBlocking {
        // Assert that regardless of animeId and episodeId, JikanSource strictly returns emptyList()
        val streamAttempts = listOf(
            Pair("1", "1"),
            Pair("16498", "ep-1"),
            Pair("attack-on-titan", "episode-24"),
            Pair("", ""),
            Pair("999999", "ep-999999")
        )

        for ((animeId, epId) in streamAttempts) {
            val streams = jikanSource.getEpisodeStreams(animeId, epId)
            assertNotNull("Streams list must not be null", streams)
            assertTrue("JikanSource must strictly return emptyList for streams ($animeId, $epId)", streams.isEmpty())
        }
    }

    // =========================================================================
    // 2. Demo Sync Strict Isolation & Real Account Guarding
    // =========================================================================

    @Test
    fun testDemoSyncIsolation_UnauthenticatedUserNeverTriggersDemoSync() = runBlocking {
        storage.saveAniListToken(null)
        storage.saveAniListUser(null)

        val result = AniListSyncManager.syncAniListWatchlist(storage)
        assertFalse("Unauthenticated sync must fail", result.isSuccess)
        assertEquals("No AniList account linked.", result.message)
        assertEquals(0, result.syncedCount)

        val favorites = storage.getFavorites()
        assertTrue("No demo favorites should exist in storage", favorites.isEmpty())
    }

    @Test
    fun testDemoSyncIsolation_RealUserWithNetworkFailureNeverTriggersDemoSync() = runBlocking {
        val realUser = AniListUser(
            id = 424242,
            name = "AuthenticAnimeWatcher",
            avatarUrl = "https://example.com/avatar.png",
            totalAnime = 100,
            episodesWatched = 1500
        )
        storage.saveAniListUser(realUser)
        storage.saveAniListToken(null)

        val result = AniListSyncManager.syncAniListWatchlist(storage)
        assertFalse("Offline/unreachable GraphQL must fail", result.isSuccess)
        assertTrue("Result message must indicate sync error", result.message.contains("Sync Error") || result.message.contains("HTTP"))

        val favorites = storage.getFavorites()
        assertEquals(0, favorites.size)
        assertFalse("Demo title must NOT be present", favorites.any { it.title.contains("Attack on Titan Final Season") })
        assertFalse("Demo title One Piece must NOT be present", favorites.any { it.title.contains("One Piece") })
    }

    @Test
    fun testDemoSyncIsolation_RealOAuthTokenWithNetworkFailureNeverTriggersDemoSync() = runBlocking {
        val realUser = AniListUser(
            id = 112233,
            name = "OAuthUser",
            avatarUrl = "",
            totalAnime = 50,
            episodesWatched = 400
        )
        storage.saveAniListUser(realUser)
        storage.saveAniListToken("bearer_oauth_token_production_xyz")

        val result = AniListSyncManager.syncAniListWatchlist(storage)
        assertFalse(result.isSuccess)

        val favorites = storage.getFavorites()
        assertTrue("No demo items should be inserted on token failure", favorites.isEmpty())
    }

    @Test
    fun testDemoSyncIsolation_UsernameStartingWithDemoNeverTriggersDemoSync() = runBlocking {
        val userWithDemoPrefix = AniListUser(
            id = 70707,
            name = "demo_account_real",
            avatarUrl = "",
            totalAnime = 10,
            episodesWatched = 15
        )
        storage.saveAniListUser(userWithDemoPrefix)
        storage.saveAniListToken(null)

        val result = AniListSyncManager.syncAniListWatchlist(storage)
        assertFalse(result.isSuccess)
        assertTrue("Favorites must remain empty when GraphQL fails for demo_ username", storage.getFavorites().isEmpty())
    }

    @Test
    fun testDemoSyncIsolation_ExplicitDemoTokenTriggersDemoAndCanBeCleaned() = runBlocking {
        storage.saveAniListToken("demo_token_explicit_test")
        val result = AniListSyncManager.syncAniListWatchlist(storage)
        assertTrue(result.isSuccess)
        assertEquals(4, result.syncedCount)
        assertEquals(4, storage.getFavorites().size)

        // Clear auth and verify state
        storage.clearAniListAuth()
        assertNull(storage.getAniListToken())
        assertNull(storage.getAniListUser())

        // Clearing watchlist
        storage.clearWatchlist()
        assertEquals(0, storage.getFavorites().size)

        // Next sync fails cleanly
        val nextSync = AniListSyncManager.syncAniListWatchlist(storage)
        assertFalse(nextSync.isSuccess)
        assertEquals(0, storage.getFavorites().size)
    }

    // =========================================================================
    // 3. AppDatabase / AppStorage Favorites & Dub Attributes Roundtrip
    // =========================================================================

    @Test
    fun testAppDatabase_FavoritesDubAttributesPreservation() {
        val dubbedAnime = Anime(
            id = "test-dub-99",
            title = "Chainsaw Man (Dub)",
            posterUrl = "https://example.com/csm.jpg",
            backdropUrl = "https://example.com/csm_bg.jpg",
            score = 8.7,
            year = 2022,
            status = "Finished",
            genres = listOf("Action", "Supernatural", "Gore"),
            synopsis = "Denji is robbed of a normal teenage life...",
            totalEpisodes = 12,
            sourceId = "wco",
            hasDub = true,
            dubbedEpisodesCount = 12
        )

        storage.addFavorite(dubbedAnime)

        val retrieved = storage.getFavorite("test-dub-99")
        assertNotNull("Favorite must be retrieved", retrieved)
        assertEquals("test-dub-99", retrieved?.id)
        assertEquals("Chainsaw Man (Dub)", retrieved?.title)
        assertEquals(12, retrieved?.totalEpisodes)
        assertTrue("hasDub must be preserved as true", retrieved?.hasDub == true)
        assertEquals(12, retrieved?.dubbedEpisodesCount)
        assertEquals(listOf("Action", "Supernatural", "Gore"), retrieved?.genres)

        val allFavorites = storage.getFavorites()
        assertEquals(1, allFavorites.size)
        assertEquals(12, allFavorites[0].totalEpisodes)
        assertTrue(allFavorites[0].hasDub)
        assertEquals(12, allFavorites[0].dubbedEpisodesCount)
    }

    @Test
    fun testAppDatabase_ToggleFavoriteIntegrity() {
        val subAnime = Anime(
            id = "test-sub-101",
            title = "Bocchi the Rock!",
            posterUrl = "https://example.com/bocchi.jpg",
            backdropUrl = "https://example.com/bocchi_bg.jpg",
            score = 8.9,
            year = 2022,
            status = "Finished",
            genres = listOf("Comedy", "Music"),
            synopsis = "Hitori Gotoh is a high school girl...",
            totalEpisodes = 12,
            sourceId = "aniwatch",
            hasDub = false,
            dubbedEpisodesCount = 0
        )

        // Initial toggle: adds favorite
        val added = storage.toggleFavorite(subAnime)
        assertTrue("First toggle must add favorite", added)
        assertTrue(storage.isFavorite("test-sub-101"))
        assertEquals(1, storage.getWatchlistCount())

        val retrieved = storage.getFavorite("test-sub-101")
        assertFalse(retrieved?.hasDub ?: true)
        assertEquals(0, retrieved?.dubbedEpisodesCount)

        // Second toggle: removes favorite
        val removed = storage.toggleFavorite(subAnime)
        assertFalse("Second toggle must remove favorite", removed)
        assertFalse(storage.isFavorite("test-sub-101"))
        assertEquals(0, storage.getWatchlistCount())
    }

    // =========================================================================
    // 4. AppDatabase / AppStorage Watch Progress Stress & Boundary Testing
    // =========================================================================

    @Test
    fun testAppDatabase_WatchProgressBoundaryValues() {
        val progressExtreme = WatchProgress(
            animeId = "extreme-id",
            animeTitle = "Extreme Title with Special Chars: 日本語 & 🦝 'quotes' \"double\"",
            posterUrl = "https://example.com/extreme.jpg",
            episodeId = "extreme-ep-9999",
            episodeNumber = 9999,
            episodeTitle = "Episode 9999 - The End",
            positionMs = Long.MAX_VALUE - 1000L,
            durationMs = Long.MAX_VALUE,
            lastWatchedTimestamp = 1700000000000L
        )

        storage.saveWatchProgress(progressExtreme)

        val retrieved = storage.getWatchProgress("extreme-id")
        assertNotNull(retrieved)
        assertEquals(9999, retrieved?.episodeNumber)
        assertEquals(Long.MAX_VALUE - 1000L, retrieved?.positionMs)
        assertEquals(Long.MAX_VALUE, retrieved?.durationMs)
        assertEquals(1700000000000L, retrieved?.lastWatchedTimestamp)

        // Update progress with smaller position
        val progressUpdated = progressExtreme.copy(
            positionMs = 123456L,
            lastWatchedTimestamp = 1700000005000L
        )
        storage.saveWatchProgress(progressUpdated)

        val updatedRetrieved = storage.getWatchProgress("extreme-id")
        assertEquals(123456L, updatedRetrieved?.positionMs)
        assertEquals(1700000005000L, updatedRetrieved?.lastWatchedTimestamp)
        assertEquals(1, storage.getWatchHistoryCount())
    }

    // =========================================================================
    // 5. Concurrency Stress Testing
    // =========================================================================

    @Test
    fun testAppDatabase_ConcurrentReadWriteStress() = runBlocking(Dispatchers.IO) {
        val totalOperations = 50
        val successCount = AtomicInteger(0)

        val deferreds = (1..totalOperations).map { index ->
            async {
                val anime = Anime(
                    id = "concurrent-$index",
                    title = "Concurrent Anime #$index",
                    posterUrl = "https://example.com/$index.jpg",
                    backdropUrl = "",
                    score = 8.0,
                    year = 2024,
                    status = "Finished",
                    genres = listOf("Action", "Tech"),
                    synopsis = "Concurrent stress test payload $index",
                    totalEpisodes = 24,
                    hasDub = index % 2 == 0,
                    dubbedEpisodesCount = if (index % 2 == 0) 24 else 0
                )

                storage.addFavorite(anime)
                val fav = storage.getFavorite("concurrent-$index")
                if (fav != null && fav.id == "concurrent-$index") {
                    successCount.incrementAndGet()
                }

                storage.saveWatchProgress(
                    WatchProgress(
                        animeId = "concurrent-$index",
                        animeTitle = "Concurrent Anime #$index",
                        posterUrl = "https://example.com/$index.jpg",
                        episodeId = "$index-ep-1",
                        episodeNumber = 1,
                        episodeTitle = "Ep 1",
                        positionMs = 5000L * index,
                        durationMs = 1440000L
                    )
                )
            }
        }

        deferreds.awaitAll()

        assertEquals("All concurrent write-read operations must succeed", totalOperations, successCount.get())
        assertEquals(totalOperations, storage.getWatchlistCount())
        assertEquals(totalOperations, storage.getWatchHistoryCount())
    }

    @Test
    fun testAniListSyncManager_ConcurrentScrobbleProgress() = runBlocking(Dispatchers.IO) {
        // When unauthenticated, scrobbleProgress returns false without throwing
        storage.saveAniListToken(null)
        val deferreds = (1..30).map { ep ->
            async {
                AniListSyncManager.scrobbleProgress(
                    mediaIdStr = "16498",
                    episodeNumber = ep,
                    totalEpisodes = 24,
                    storage = storage,
                    animeTitle = "Attack on Titan"
                )
            }
        }

        val results = deferreds.awaitAll()
        assertTrue("All scrobbles while unauthenticated should return false safely", results.all { !it })
    }
}
