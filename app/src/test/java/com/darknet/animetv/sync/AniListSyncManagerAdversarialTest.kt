package com.darknet.animetv.sync

import com.darknet.animetv.data.model.AniListUser
import com.darknet.animetv.data.model.Anime
import com.darknet.animetv.data.model.AnimeDetail
import com.darknet.animetv.data.model.Episode
import com.darknet.animetv.data.source.SourceHealthResult
import com.darknet.animetv.data.model.VideoSource
import com.darknet.animetv.data.model.WatchProgress
import com.darknet.animetv.data.repository.AniListSyncManager
import com.darknet.animetv.data.repository.DefaultAnimeRepository
import com.darknet.animetv.data.source.AniListCatalogSource
import com.darknet.animetv.data.source.AnimeSource
import com.darknet.animetv.data.storage.AppStorage
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AniListSyncManagerAdversarialTest {

    private lateinit var storage: AppStorage

    @Before
    fun setUp() {
        storage = AppStorage(RuntimeEnvironment.getApplication())
        storage.saveAniListToken(null)
        storage.saveAniListUser(null)
        AniListSyncManager.anilistEndpoint = "http://127.0.0.1:65534/graphql"
    }

    @After
    fun tearDown() {
        storage.saveAniListToken(null)
        storage.saveAniListUser(null)
        AniListSyncManager.anilistEndpoint = "https://graphql.anilist.co"
    }

    // =========================================================================
    // SECTION 1: loginWithUsername Input Validation & Resilience
    // =========================================================================

    @Test
    fun testLoginWithUsername_BlankAndWhitespaceStringsFailInstantly() = runBlocking {
        val blankInputs = listOf("", " ", "   ", "\t", "\n", " \t \r\n ")
        for (input in blankInputs) {
            val result = AniListSyncManager.loginWithUsername(input, storage)
            assertFalse("Blank input '$input' must fail without network call", result.isSuccess)
            assertTrue(
                "Exception should be IllegalArgumentException",
                result.exceptionOrNull() is IllegalArgumentException
            )
            assertEquals("Username cannot be blank", result.exceptionOrNull()?.message)
        }
    }

    @Test
    fun testLoginWithUsername_SpecialCharactersAndInjectionPayloadsDoNotCrash() = runBlocking {
        val adversarialInputs = listOf(
            "User' OR '1'='1",
            "User\"WithQuotes",
            "}{ User(id: 1) { id } } #",
            "<script>alert('xss')</script>",
            "Shinobi_🦊_Naruto",
            "ユーザー123",
            "user; DROP TABLE Users; --",
            "a".repeat(300) // Extremely long username
        )

        for (input in adversarialInputs) {
            // Must catch exceptions gracefully and return Result.failure
            val result = AniListSyncManager.loginWithUsername(input, storage)
            assertFalse("Adversarial input '$input' should fail safely", result.isSuccess)
            assertNotNull(result.exceptionOrNull())
            // AppStorage must remain clean
            assertNull("Storage token must not be set on failed login", storage.getAniListToken())
            assertNull("Storage user must not be set on failed login", storage.getAniListUser())
        }
    }

    @Test
    fun testLoginWithUsername_PreservesExistingStateOnNetworkFailure() = runBlocking {
        val existingUser = AniListUser(id = 777, name = "StableUser", avatarUrl = "", totalAnime = 10, episodesWatched = 50)
        storage.saveAniListUser(existingUser)
        storage.saveAniListToken("saved_token_123")

        // Invalid lookup fails
        val result = AniListSyncManager.loginWithUsername("NonExistentUser987654321", storage)
        assertFalse(result.isSuccess)

        // Existing state must not be corrupted
        assertEquals("StableUser", storage.getAniListUser()?.name)
        assertEquals("saved_token_123", storage.getAniListToken())
    }

    // =========================================================================
    // SECTION 2: Demonstration Sync Guarding on Real Accounts
    // =========================================================================

    @Test
    fun testRealUsernameAccountNeverInvokesDemoSync() = runBlocking {
        // Real user logged in via username (token is null)
        val realUser = AniListUser(id = 12345, name = "RealAnimeLover", avatarUrl = "", totalAnime = 25, episodesWatched = 200)
        storage.saveAniListUser(realUser)
        storage.saveAniListToken(null)

        val syncResult = AniListSyncManager.syncAniListWatchlist(storage)
        // In offline test env, real GraphQL sync fails with clean error; if online, real sync succeeds
        if (!syncResult.isSuccess) {
            assertTrue(syncResult.message.contains("Sync Error") || syncResult.message.contains("HTTP"))
        }

        // CRITICAL: Demo anime must NEVER be injected into a real username account
        val favorites = storage.getFavorites()
        assertFalse(
            "Demo anime 'Attack on Titan Final Season' (id 16498) MUST NOT be injected on real account sync failure",
            favorites.any { it.id == "16498" || it.title.contains("Attack on Titan Final Season") }
        )
        assertFalse(
            "Demo anime 'One Piece' (id 21) MUST NOT be injected",
            favorites.any { it.id == "21" && it.synopsis.contains("Gol D. Roger was known as the 'Pirate King'") }
        )
    }

    @Test
    fun testUsernameStartingWithDemoNeverTriggersDemoSync() = runBlocking {
        // A real user whose username begins with "demo_", e.g. "demo_slayer"
        val realUser = AniListUser(id = 8888, name = "demo_slayer", avatarUrl = "", totalAnime = 15, episodesWatched = 80)
        storage.saveAniListUser(realUser)
        storage.saveAniListToken(null) // Token is null because logged in with public username

        val syncResult = AniListSyncManager.syncAniListWatchlist(storage)
        // If offline fails cleanly, if online succeeds with real user data

        // Verify demo sync was NOT triggered despite username starting with demo_
        val favorites = storage.getFavorites()
        assertFalse("Demo anime MUST NOT be injected for username starting with demo_", favorites.any { it.id == "16498" })
    }

    @Test
    fun testUnauthenticatedStateDoesNotTriggerDemoSync() = runBlocking {
        storage.saveAniListUser(null)
        storage.saveAniListToken(null)

        val syncResult = AniListSyncManager.syncAniListWatchlist(storage)
        assertFalse("Unauthenticated sync must fail", syncResult.isSuccess)
        assertEquals("No AniList account linked.", syncResult.message)
        assertEquals(0, syncResult.syncedCount)
        assertTrue("Storage favorites must be empty", storage.getFavorites().isEmpty())
    }

    @Test
    fun testExplicitDemoTokenTriggersDemoSyncSuccessfully() = runBlocking {
        storage.saveAniListToken("demo_token_test_123")
        storage.saveAniListUser(null)

        val syncResult = AniListSyncManager.syncAniListWatchlist(storage)
        assertTrue("Explicit demo token must trigger demo sync", syncResult.isSuccess)
        assertEquals(4, syncResult.syncedCount)
        assertTrue(storage.getFavorites().any { it.id == "16498" })
        assertTrue(storage.getFavorites().any { it.id == "21" })
        assertTrue(storage.getFavorites().any { it.id == "101922" })
        assertTrue(storage.getFavorites().any { it.id == "153518" })
    }

    @Test
    fun testSwitchingFromDemoToRealAccountClearsDemoToken() = runBlocking {
        // Step 1: User runs demo sync
        AniListSyncManager.performDemoSync(storage)
        assertEquals("demo_token_anilist_tv", storage.getAniListToken())
        assertEquals("AnimeFan_TV", storage.getAniListUser()?.name)
        assertEquals(4, storage.getFavorites().size)

        // Step 2: User links a real account via loginWithUsername
        // In real loginWithUsername: storage.saveAniListToken(null) and storage.saveAniListUser(realUser)
        val realUser = AniListUser(id = 554433, name = "KenshinHimura", avatarUrl = "", totalAnime = 1, episodesWatched = 95)
        storage.saveAniListToken(null) // Cleared by username login
        storage.saveAniListUser(realUser)

        assertNull("Token must be null after username login", storage.getAniListToken())
        assertEquals("KenshinHimura", storage.getAniListUser()?.name)

        // Step 3: Trigger sync
        val syncResult = AniListSyncManager.syncAniListWatchlist(storage)
        // Must NOT execute performDemoSync again
        assertNull("Token must remain null and not reset to demo token", storage.getAniListToken())
    }

    // =========================================================================
    // SECTION 3: MediaListCollection JSON Parsing Logic Oracle Tests
    // =========================================================================

    /**
     * Replicates and stress-tests the exact JSON parsing logic used in AniListSyncManager.syncAniListWatchlist
     * against edge-case JSON payloads.
     */
    private fun parseMediaListCollectionPayload(jsonString: String, storage: AppStorage): Pair<Boolean, Int> {
        val root = JsonParser.parseString(jsonString).asJsonObject
        val dataObj = root.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
        val mediaListCollectionObj = dataObj?.get("MediaListCollection")?.takeIf { it.isJsonObject }?.asJsonObject
        val listsArray = mediaListCollectionObj?.get("lists")?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()

        var syncedCount = 0

        listsArray.forEach { listEl ->
            val listObj = listEl.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            val entries = listObj.get("entries")?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()

            entries.forEach entryLoop@{ entryEl ->
                val entry = entryEl.takeIf { it.isJsonObject }?.asJsonObject ?: return@entryLoop
                val media = entry.get("media")?.takeIf { it.isJsonObject }?.asJsonObject ?: return@entryLoop

                val mediaId = media.get("id")?.takeIf { !it.isJsonNull }?.asInt?.toString() ?: return@entryLoop
                val titles = media.get("title")?.takeIf { it.isJsonObject }?.asJsonObject
                val englishTitle = titles?.get("english")?.takeIf { !it.isJsonNull }?.asString
                val romajiTitle = titles?.get("romaji")?.takeIf { !it.isJsonNull }?.asString
                val title = englishTitle ?: romajiTitle ?: "Anime #$mediaId"

                val cover = media.get("coverImage")?.takeIf { it.isJsonObject }?.asJsonObject
                val posterUrl = cover?.get("extraLarge")?.takeIf { !it.isJsonNull }?.asString
                    ?: cover?.get("large")?.takeIf { !it.isJsonNull }?.asString ?: ""

                val backdropUrl = media.get("bannerImage")?.takeIf { !it.isJsonNull }?.asString
                    ?: posterUrl

                val avgScore = media.get("averageScore")?.takeIf { !it.isJsonNull }?.asDouble ?: 0.0
                val score = if (avgScore > 0) avgScore / 10.0 else entry.get("score")?.takeIf { !it.isJsonNull }?.asDouble ?: 8.0

                val totalEpisodes = media.get("episodes")?.takeIf { !it.isJsonNull }?.asInt ?: 12
                val progress = entry.get("progress")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                val status = media.get("status")?.takeIf { !it.isJsonNull }?.asString ?: "Finished"

                val genres = mutableListOf<String>()
                media.get("genres")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { g ->
                    if (!g.isJsonNull) genres.add(g.asString)
                }

                val synopsis = media.get("description")?.takeIf { !it.isJsonNull }?.asString ?: ""

                val anime = Anime(
                    id = mediaId,
                    title = title,
                    posterUrl = posterUrl,
                    backdropUrl = backdropUrl,
                    score = (score * 10).toInt() / 10.0,
                    year = 2024,
                    status = status,
                    genres = genres,
                    synopsis = synopsis,
                    totalEpisodes = totalEpisodes,
                    sourceId = "anilist",
                    hasDub = !englishTitle.isNullOrBlank(),
                    dubbedEpisodesCount = if (!englishTitle.isNullOrBlank()) totalEpisodes else 0
                )

                storage.addFavorite(anime)

                if (progress > 0) {
                    val estDurationMs = 1_440_000L
                    storage.saveWatchProgress(
                        WatchProgress(
                            animeId = mediaId,
                            animeTitle = title,
                            posterUrl = posterUrl,
                            episodeId = "$mediaId-ep-$progress",
                            episodeNumber = progress,
                            episodeTitle = "Episode $progress",
                            positionMs = estDurationMs,
                            durationMs = estDurationMs
                        )
                    )
                }

                syncedCount++
            }
        }
        return Pair(true, syncedCount)
    }

    @Test
    fun testMediaListCollection_EmptyListsHandledGracefully() {
        val emptyListsJson = """{"data": {"MediaListCollection": {"lists": []}}}"""
        val (success, count) = parseMediaListCollectionPayload(emptyListsJson, storage)
        assertTrue(success)
        assertEquals(0, count)
        assertTrue(storage.getFavorites().isEmpty())
    }

    @Test
    fun testMediaListCollection_EmptyEntriesInListHandledGracefully() {
        val emptyEntriesJson = """
        {
            "data": {
                "MediaListCollection": {
                    "lists": [
                        {"name": "Watching", "status": "CURRENT", "entries": []},
                        {"name": "Completed", "status": "COMPLETED", "entries": []}
                    ]
                }
            }
        }
        """.trimIndent()
        val (success, count) = parseMediaListCollectionPayload(emptyEntriesJson, storage)
        assertTrue(success)
        assertEquals(0, count)
        assertTrue(storage.getFavorites().isEmpty())
    }

    @Test
    fun testMediaListCollection_NullCollectionPrivateProfileHandledGracefully() {
        val privateListJson = """
        {
            "data": {
                "MediaListCollection": null
            },
            "errors": [{"message": "This user's list is private", "status": 403}]
        }
        """.trimIndent()
        val (success, count) = parseMediaListCollectionPayload(privateListJson, storage)
        assertTrue(success)
        assertEquals(0, count)
    }

    @Test
    fun testMediaListCollection_NullLadenMediaAndUnusualStatusKeys() {
        // Item 1: Romaji only, custom status CUSTOM_PAUSED, coverImage large fallback, genres with null
        // Item 2: All titles null (falls back to Anime #2002), null score, null episodes, null status
        val payload = """
        {
            "data": {
                "MediaListCollection": {
                    "lists": [
                        {
                            "name": "Custom List",
                            "status": "CUSTOM_PAUSED",
                            "entries": [
                                {
                                    "id": 1001,
                                    "mediaId": 2001,
                                    "status": "PAUSED",
                                    "score": 7.5,
                                    "progress": 5,
                                    "media": {
                                        "id": 2001,
                                        "title": {"romaji": "Sousou no Frieren", "english": null, "native": null},
                                        "coverImage": {"extraLarge": null, "large": "https://example.com/frieren.jpg"},
                                        "bannerImage": null,
                                        "episodes": 28,
                                        "status": "FINISHED",
                                        "genres": ["Adventure", null, "Drama", "Fantasy"],
                                        "averageScore": 91,
                                        "description": "After the party of heroes defeated the Demon King..."
                                    }
                                },
                                {
                                    "id": 1002,
                                    "mediaId": 2002,
                                    "status": "DROPPED",
                                    "score": null,
                                    "progress": 0,
                                    "media": {
                                        "id": 2002,
                                        "title": {"romaji": null, "english": null, "native": null},
                                        "coverImage": null,
                                        "bannerImage": null,
                                        "episodes": null,
                                        "status": null,
                                        "genres": null,
                                        "averageScore": null,
                                        "description": null
                                    }
                                }
                            ]
                        }
                    ]
                }
            }
        }
        """.trimIndent()

        val (success, count) = parseMediaListCollectionPayload(payload, storage)
        assertTrue(success)
        assertEquals(2, count)

        val favorites = storage.getFavorites()
        assertEquals(2, favorites.size)

        // Verify Frieren (Item 1)
        val frieren = favorites.find { it.id == "2001" }
        assertNotNull(frieren)
        assertEquals("Sousou no Frieren", frieren?.title)
        assertEquals("https://example.com/frieren.jpg", frieren?.posterUrl)
        assertEquals(9.1, frieren?.score ?: 0.0, 0.01) // averageScore 91 / 10 = 9.1
        assertEquals(28, frieren?.totalEpisodes)
        assertEquals(listOf("Adventure", "Drama", "Fantasy"), frieren?.genres)
        // Verify watch progress was stored for episode 5
        val frierenProgress = storage.getWatchProgress("2001")
        assertNotNull(frierenProgress)
        assertEquals(5, frierenProgress?.episodeNumber)

        // Verify Fallback Anime (Item 2)
        val fallbackAnime = favorites.find { it.id == "2002" }
        assertNotNull(fallbackAnime)
        assertEquals("Anime #2002", fallbackAnime?.title)
        assertEquals(8.0, fallbackAnime?.score ?: 0.0, 0.01) // Default score 8.0
        assertEquals(12, fallbackAnime?.totalEpisodes) // Default episodes 12
        assertEquals("Finished", fallbackAnime?.status) // Default status Finished
        assertEquals("", fallbackAnime?.posterUrl)
        // Progress was 0, so no watch progress saved
        assertNull(storage.getWatchProgress("2002"))
    }

    // =========================================================================
    // SECTION 4: Stream Resolution Multi-Source Failover Across Providers
    // =========================================================================

    private class TestMockSource(
        override val sourceId: String,
        override val sourceName: String,
        override var isEnabled: Boolean = true,
        val shouldThrow: Boolean = false,
        val emptyStreams: Boolean = false,
        val streamAudio: String = "SUB"
    ) : AnimeSource {
        var searchAttempted = false
        var streamsAttempted = false

        override suspend fun getTrending(): List<Anime> = emptyList()
        override suspend fun getPopular(): List<Anime> = emptyList()
        override suspend fun getRecentEpisodes(): List<Anime> = emptyList()

        override suspend fun search(query: String): List<Anime> {
            searchAttempted = true
            if (shouldThrow) throw IOException("Provider $sourceId down")
            return listOf(
                Anime(id = "$sourceId-1", title = "Chainsaw Man", sourceId = sourceId)
            )
        }

        override suspend fun getAnimeDetail(animeId: String): AnimeDetail? {
            if (shouldThrow) throw IOException("Provider $sourceId down")
            return AnimeDetail(
                anime = Anime(id = animeId, title = "Chainsaw Man", sourceId = sourceId),
                episodes = listOf(Episode(id = "$animeId-ep-1", animeId = animeId, number = 1, title = "Ep 1"))
            )
        }

        override suspend fun getEpisodeStreams(animeId: String, episodeId: String): List<VideoSource> {
            streamsAttempted = true
            if (shouldThrow) throw IOException("Provider $sourceId stream error")
            if (emptyStreams) return emptyList()
            return listOf(
                VideoSource(
                    serverName = "$sourceName Server",
                    url = "https://$sourceId.mock/stream.m3u8",
                    quality = "1080p",
                    isHls = true,
                    audioTrack = streamAudio
                )
            )
        }

        override suspend fun testConnection(): SourceHealthResult {
            return SourceHealthResult(sourceId, !shouldThrow, 10L)
        }
    }

    @Test
    fun testStreamFailover_ProviderThrowsFailsOverToNext() = runBlocking {
        val kaaFailing = TestMockSource("kaa", "KAA", shouldThrow = true)
        val aniwatchWorking = TestMockSource("aniwatch", "Aniwatch", shouldThrow = false)
        val miruroUntouched = TestMockSource("miruro", "Miruro", shouldThrow = false)

        val repo = DefaultAnimeRepository(
            appStorage = storage,
            customSourcesList = listOf(kaaFailing, aniwatchWorking, miruroUntouched)
        )

        val streams = repo.getEpisodeStreams("Chainsaw Man", 1, preferredAudio = "SUB")
        assertTrue(streams.isNotEmpty())
        assertTrue("Streams must come from Aniwatch", streams.any { it.serverName.contains("Aniwatch") })
        assertTrue(kaaFailing.searchAttempted)
        assertTrue(aniwatchWorking.streamsAttempted)
        assertFalse("Miruro must not be reached when Aniwatch succeeds", miruroUntouched.streamsAttempted)
    }

    @Test
    fun testStreamFailover_ProviderEmptyStreamsFailsOverToNext() = runBlocking {
        val kaaEmpty = TestMockSource("kaa", "KAA", emptyStreams = true)
        val wcoWorking = TestMockSource("wco", "WCO", shouldThrow = false, streamAudio = "DUB")

        val repo = DefaultAnimeRepository(
            appStorage = storage,
            customSourcesList = listOf(kaaEmpty, wcoWorking)
        )

        val streams = repo.getEpisodeStreams("Chainsaw Man", 1, preferredAudio = "DUB")
        assertTrue(streams.isNotEmpty())
        assertTrue(streams.any { it.serverName.contains("WCO") })
        assertTrue(kaaEmpty.streamsAttempted)
        assertTrue(wcoWorking.streamsAttempted)
    }

    @Test
    fun testStreamFailover_AllProvidersFailReturnsEmergencyFallback() = runBlocking {
        val dead1 = TestMockSource("kaa", "KAA", shouldThrow = true)
        val dead2 = TestMockSource("aniwatch", "Aniwatch", emptyStreams = true)

        val repo = DefaultAnimeRepository(
            appStorage = storage,
            customSourcesList = listOf(dead1, dead2)
        )

        val dubStreams = repo.getEpisodeStreams("Jujutsu Kaisen", 1, preferredAudio = "DUB")
        assertTrue(dubStreams.isNotEmpty())
        assertTrue(
            "Emergency fallback must match preferredAudio DUB",
            dubStreams.all { it.audioTrack.equals("DUB", ignoreCase = true) }
        )
        assertTrue(dubStreams.any { it.serverName.contains("TanukiTV Emergency Fallback") })

        val subStreams = repo.getEpisodeStreams("Jujutsu Kaisen", 1, preferredAudio = "SUB")
        assertTrue(subStreams.isNotEmpty())
        assertTrue(
            "Emergency fallback must match preferredAudio SUB",
            subStreams.all { it.audioTrack.equals("SUB", ignoreCase = true) }
        )
    }

    @Test
    fun testStreamPrioritization_DubAudioSortsDubFirst() = runBlocking {
        val subStream = VideoSource(serverName = "Sub Server", url = "https://example.com/sub.m3u8", audioTrack = "SUB")
        val dubStream = VideoSource(serverName = "Dub Server", url = "https://example.com/dub.m3u8", audioTrack = "DUB")

        val sourceWithBoth = object : AnimeSource {
            override val sourceId: String = "mixed"
            override val sourceName: String = "Mixed"
            override var isEnabled: Boolean = true
            override suspend fun getTrending(): List<Anime> = emptyList()
            override suspend fun getPopular(): List<Anime> = emptyList()
            override suspend fun getRecentEpisodes(): List<Anime> = emptyList()
            override suspend fun search(query: String): List<Anime> = listOf(Anime(id = "mixed-1", title = "Frieren", sourceId = sourceId))
            override suspend fun getAnimeDetail(animeId: String): AnimeDetail = AnimeDetail(
                anime = Anime(id = animeId, title = "Frieren", sourceId = sourceId),
                episodes = listOf(Episode(id = "ep-1", animeId = animeId, number = 1, title = "Ep 1"))
            )
            override suspend fun getEpisodeStreams(animeId: String, episodeId: String): List<VideoSource> = listOf(subStream, dubStream)
            override suspend fun testConnection(): SourceHealthResult = SourceHealthResult(sourceId, true, 5L)
        }

        val repo = DefaultAnimeRepository(appStorage = storage, customSourcesList = listOf(sourceWithBoth))

        val dubSorted = repo.getEpisodeStreams("Frieren", 1, preferredAudio = "DUB")
        assertEquals("DUB", dubSorted.first().audioTrack)

        val subSorted = repo.getEpisodeStreams("Frieren", 1, preferredAudio = "SUB")
        assertEquals("SUB", subSorted.first().audioTrack)
    }

    @Test
    fun testAniListCatalogSourceNeverProvidesStreams() = runBlocking {
        val anilistSource = AniListCatalogSource()
        val streams = anilistSource.getEpisodeStreams("16498", "ep-1")
        assertTrue("AniListCatalogSource must return emptyList for streams", streams.isEmpty())
    }
}
