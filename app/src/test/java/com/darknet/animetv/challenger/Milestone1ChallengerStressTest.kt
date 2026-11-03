package com.darknet.animetv.challenger

import com.darknet.animetv.data.model.Anime
import com.darknet.animetv.data.model.AnimeDetail
import com.darknet.animetv.data.model.Episode
import com.darknet.animetv.data.model.VideoSource
import com.darknet.animetv.data.repository.AniListSyncManager
import com.darknet.animetv.data.repository.DefaultAnimeRepository
import com.darknet.animetv.data.source.AniListCatalogSource
import com.darknet.animetv.data.source.AnimeSource
import com.darknet.animetv.data.source.SourceHealthResult
import com.darknet.animetv.data.storage.AppStorage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Milestone1ChallengerStressTest {

    private lateinit var repository: DefaultAnimeRepository
    private lateinit var anilistSource: AniListCatalogSource
    private lateinit var storage: AppStorage

    @Before
    fun setup() {
        val app = RuntimeEnvironment.getApplication()
        storage = AppStorage(app)
        repository = DefaultAnimeRepository(appStorage = storage)
        anilistSource = AniListCatalogSource()
    }

    // =========================================================================
    // Challenge 1: getDubbedAnime() empirical verification & stress-testing
    // =========================================================================

    @Test
    fun testGetDubbedAnime_CompletenessAndIntegrity() = runBlocking {
        val dubbed = repository.getDubbedAnime()

        assertTrue("getDubbedAnime() must return a non-empty list", dubbed.isNotEmpty())
        assertTrue("getDubbedAnime() must return at least 5 curated titles", dubbed.size >= 5)

        // Every item must have hasDub == true
        val nonDubbed = dubbed.filterNot { it.hasDub }
        assertTrue("Every anime returned must have hasDub == true, found non-dubbed: $nonDubbed", nonDubbed.isEmpty())

        // Every item must have a valid non-blank title
        for (item in dubbed) {
            assertTrue("Anime ID must not be blank", item.id.isNotBlank())
            assertTrue("Anime title must not be blank", item.title.isNotBlank())
            assertTrue("Anime score must be >= 0.0", item.score >= 0.0)
        }

        // Deduplication check: no duplicate normalized titles
        val normalizedTitles = dubbed.map { it.title.lowercase().replace(" (dub)", "").trim() }
        val uniqueTitles = normalizedTitles.toSet()
        assertEquals(
            "Titles must be deduplicated: total ${normalizedTitles.size} vs unique ${uniqueTitles.size}",
            uniqueTitles.size,
            normalizedTitles.size
        )

        // Specific iconic dubs must be present
        val titleStrings = dubbed.map { it.title }
        assertTrue("Should contain Cowboy Bebop", titleStrings.any { it.contains("Cowboy Bebop", ignoreCase = true) })
        assertTrue("Should contain Fullmetal Alchemist: Brotherhood", titleStrings.any { it.contains("Fullmetal Alchemist", ignoreCase = true) })
        assertTrue("Should contain Death Note", titleStrings.any { it.contains("Death Note", ignoreCase = true) })
        assertTrue("Should contain Attack on Titan", titleStrings.any { it.contains("Attack on Titan", ignoreCase = true) })
        assertTrue("Should contain Cyberpunk: Edgerunners", titleStrings.any { it.contains("Cyberpunk", ignoreCase = true) })
    }

    @Test
    fun testGetDubbedAnime_ResilienceUnderCrashingSource() = runBlocking {
        // Construct an adversarial source that throws runtime exceptions on all calls
        val explodingSource = object : AnimeSource {
            override val sourceId: String = "exploding-source"
            override val sourceName: String = "Exploding Provider"
            override var isEnabled: Boolean = true
            override suspend fun getTrending(): List<Anime> = throw RuntimeException("Simulated trending network failure")
            override suspend fun getPopular(): List<Anime> = throw RuntimeException("Simulated popular network failure")
            override suspend fun getRecentEpisodes(): List<Anime> = throw RuntimeException("Simulated recent network failure")
            override suspend fun search(query: String): List<Anime> = throw RuntimeException("Simulated search timeout")
            override suspend fun getAnimeDetail(animeId: String): AnimeDetail? = throw RuntimeException("Simulated detail error")
            override suspend fun getEpisodeStreams(animeId: String, episodeId: String): List<VideoSource> = throw RuntimeException("Simulated stream error")
            override suspend fun getDubbedAnime(): List<Anime> = throw RuntimeException("Simulated dub catalog crash")
            override suspend fun testConnection(): SourceHealthResult = SourceHealthResult(sourceId, false, 0, "Crash")
        }

        val robustRepo = DefaultAnimeRepository(
            appStorage = storage,
            customSourcesList = listOf(explodingSource)
        )

        // Must not throw, and must fall back gracefully to curated dubbed anime
        val dubbed = robustRepo.getDubbedAnime()
        assertTrue("Repository must recover from failing dub sources", dubbed.isNotEmpty())
        assertTrue("Curated dubbed anime must still be served", dubbed.any { it.title.contains("Cowboy Bebop", ignoreCase = true) })
    }

    @Test
    fun testGetDubbedAnime_DeduplicationAcrossMultipleSources() = runBlocking {
        val mockSource1 = object : AnimeSource {
            override val sourceId = "mock-1"
            override val sourceName = "Mock 1"
            override var isEnabled = true
            override suspend fun getTrending() = emptyList<Anime>()
            override suspend fun getPopular() = emptyList<Anime>()
            override suspend fun getRecentEpisodes() = emptyList<Anime>()
            override suspend fun search(query: String) = emptyList<Anime>()
            override suspend fun getAnimeDetail(animeId: String) = null
            override suspend fun getEpisodeStreams(animeId: String, episodeId: String) = emptyList<VideoSource>()
            override suspend fun getDubbedAnime() = listOf(
                Anime(id = "mock-1-1", title = "Cowboy Bebop (Dub)", hasDub = true),
                Anime(id = "mock-1-2", title = "New Dub Show", hasDub = true)
            )
            override suspend fun testConnection() = SourceHealthResult(sourceId, true)
        }

        val customRepo = DefaultAnimeRepository(
            appStorage = storage,
            customSourcesList = listOf(mockSource1)
        )

        val dubbed = customRepo.getDubbedAnime()
        // Cowboy Bebop should appear only once despite being in curated and mockSource1
        val bebopCount = dubbed.count { it.title.lowercase().contains("cowboy bebop") }
        assertEquals("Cowboy Bebop must only appear once after deduplication", 1, bebopCount)
        // New Dub Show should be included
        assertTrue("New Dub Show should be merged", dubbed.any { it.title == "New Dub Show" })
    }

    // =========================================================================
    // Challenge 2: AniListCatalogSource.getEpisodeStreams() strictly emptyList()
    // =========================================================================

    @Test
    fun testAniListCatalogSource_GetEpisodeStreamsMustBeStrictlyEmpty() = runBlocking {
        // Test standard IDs
        val streams1 = anilistSource.getEpisodeStreams("16498", "al-16498-ep-1")
        assertTrue("AniListCatalogSource must return emptyList() for standard anime", streams1.isEmpty())

        // Test attack inputs: empty strings, malformed IDs, null-like strings, extreme lengths
        val attackCases = listOf(
            "" to "",
            "   " to "   ",
            "-999" to "invalid-ep",
            "0" to "0",
            "undefined" to "null",
            "SELECT * FROM anime" to "DROP TABLE episodes",
            "https://graphql.anilist.co" to "https://cdn.example.com/test.m3u8",
            "A".repeat(1000) to "B".repeat(1000)
        )

        for ((animeId, episodeId) in attackCases) {
            val streams = anilistSource.getEpisodeStreams(animeId, episodeId)
            assertNotNull("Stream result must never be null", streams)
            assertTrue(
                "AniListCatalogSource must return strictly emptyList() for input ('$animeId', '$episodeId'), but returned $streams",
                streams.isEmpty()
            )
        }
    }

    // =========================================================================
    // Challenge 3: Stream Audio Priority (DUB vs SUB)
    // =========================================================================

    @Test
    fun testStreamAudioPriority_DubPreferredSorting() = runBlocking {
        // Construct a mock streaming source with both SUB and DUB streams
        val multiAudioSource = object : AnimeSource {
            override val sourceId = "multi-audio-src"
            override val sourceName = "Multi Audio Provider"
            override var isEnabled = true
            override suspend fun getTrending() = emptyList<Anime>()
            override suspend fun getPopular() = emptyList<Anime>()
            override suspend fun getRecentEpisodes() = emptyList<Anime>()
            override suspend fun search(query: String) = listOf(
                Anime(id = "multi-1", title = query)
            )
            override suspend fun getAnimeDetail(animeId: String) = AnimeDetail(
                anime = Anime(id = animeId, title = "Multi Show"),
                episodes = listOf(
                    Episode(id = "ep-1", animeId = animeId, number = 1, title = "Episode 1")
                )
            )
            override suspend fun getEpisodeStreams(animeId: String, episodeId: String): List<VideoSource> {
                return listOf(
                    VideoSource(serverName = "Sub Server Fast", url = "https://cdn.com/sub_fast.mp4", audioTrack = "SUB", isHls = false),
                    VideoSource(serverName = "Dub Server HLS", url = "https://cdn.com/dub.m3u8", audioTrack = "DUB", isHls = true),
                    VideoSource(serverName = "Sub Server HLS", url = "https://cdn.com/sub.m3u8", audioTrack = "SUB", isHls = true),
                    VideoSource(serverName = "Dub Server Backup", url = "https://cdn.com/dub_backup.mp4", audioTrack = "DUB", isHls = false)
                )
            }
            override suspend fun testConnection() = SourceHealthResult(sourceId, true)
        }

        val customRepo = DefaultAnimeRepository(
            appStorage = storage,
            customSourcesList = listOf(multiAudioSource)
        )

        // 1. preferredAudio = "DUB"
        val dubStreams = customRepo.getEpisodeStreams("Multi Show", 1, preferredAudio = "DUB")
        assertTrue("Should resolve streams", dubStreams.isNotEmpty())
        assertEquals("Primary stream when preferredAudio=DUB must have audioTrack='DUB'", "DUB", dubStreams.first().audioTrack)
        // And among DUB streams, HLS should be prioritized first
        assertTrue("HLS stream should be preferred first", dubStreams.first().isHls)

        // 2. preferredAudio = "dub" (case insensitivity)
        val lowerDubStreams = customRepo.getEpisodeStreams("Multi Show", 1, preferredAudio = "dub")
        assertEquals("Case-insensitive 'dub' must prioritize DUB", "DUB", lowerDubStreams.first().audioTrack)

        // 3. preferredAudio = "SUB"
        val subStreams = customRepo.getEpisodeStreams("Multi Show", 1, preferredAudio = "SUB")
        assertTrue("Should resolve streams", subStreams.isNotEmpty())
        assertEquals("Primary stream when preferredAudio=SUB must have audioTrack='SUB'", "SUB", subStreams.first().audioTrack)
        assertTrue("HLS sub stream should be preferred first", subStreams.first().isHls)
    }

    @Test
    fun testEmergencyFallbackStreams_RespectAudioPreference() = runBlocking {
        // Repository with no sources returning streams -> triggers emergency fallback
        val emptyRepo = DefaultAnimeRepository(
            appStorage = storage,
            customSourcesList = emptyList()
        )

        // Fallback for DUB
        val emergencyDub = emptyRepo.getEpisodeStreams("Nonexistent Anime", 1, preferredAudio = "DUB")
        assertTrue("Emergency fallback must return streams", emergencyDub.isNotEmpty())
        assertEquals("Emergency fallback must assign audioTrack = 'DUB'", "DUB", emergencyDub.first().audioTrack)
        assertTrue("Server name must reflect DUB fallback", emergencyDub.first().serverName.contains("DUB", ignoreCase = true))

        // Fallback for SUB
        val emergencySub = emptyRepo.getEpisodeStreams("Nonexistent Anime", 1, preferredAudio = "SUB")
        assertTrue("Emergency fallback must return streams", emergencySub.isNotEmpty())
        assertEquals("Emergency fallback must assign audioTrack = 'SUB'", "SUB", emergencySub.first().audioTrack)
        assertTrue("Server name must reflect SUB fallback", emergencySub.first().serverName.contains("SUB", ignoreCase = true))
    }

    // =========================================================================
    // Challenge 4: Airing Schedule under normal, malformed, and offline scenarios
    // =========================================================================

    @Test
    fun testAiringSchedule_DayOfWeekFilteringAndCoverage() = runBlocking {
        val daysOfWeek = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

        // 1. Verify all 7 days have scheduled shows in fallback/curated dataset
        for (day in daysOfWeek) {
            val schedule = repository.getAiringSchedule(day)
            assertTrue("Schedule for $day must not be empty", schedule.isNotEmpty())
            assertTrue("All items for $day must match the requested day", schedule.all { it.dayOfWeek.equals(day, ignoreCase = true) })
        }

        // 2. Case-insensitivity check
        val lowerMon = repository.getAiringSchedule("monday")
        val upperMon = repository.getAiringSchedule("MONDAY")
        assertEquals("Day filtering must be case-insensitive", lowerMon.size, upperMon.size)

        // 3. "All" and null should return full week
        val allSchedule = repository.getAiringSchedule("All")
        val nullSchedule = repository.getAiringSchedule(null)
        val blankSchedule = repository.getAiringSchedule("   ")
        assertTrue("Full week schedule must contain 10+ entries", allSchedule.size >= 10)
        assertEquals("All and null should yield identical counts", allSchedule.size, nullSchedule.size)
        assertEquals("Blank day should yield identical count to null", nullSchedule.size, blankSchedule.size)

        // 4. Invalid day of week should return emptyList without throwing
        val invalidSchedule = repository.getAiringSchedule("Funday")
        assertTrue("Nonexistent day should return empty list", invalidSchedule.isEmpty())
    }

    @Test
    fun testAniListCatalogSource_AiringScheduleOfflineSafety() = runBlocking {
        // Without internet connectivity or against offline host, getAiringSchedule must not throw
        val schedule = anilistSource.getAiringSchedule("Monday")
        assertNotNull("Offline AniList getAiringSchedule must return a list, not null", schedule)
    }

    // =========================================================================
    // Challenge 5: Character Cast under normal, malformed, and offline scenarios
    // =========================================================================

    @Test
    fun testCharacterCast_FallbackAndNormalization() = runBlocking {
        // 1. Attack on Titan
        val aotCast = repository.getCharacterCast("al-16498", "Attack on Titan")
        assertTrue("Attack on Titan cast must not be empty", aotCast.isNotEmpty())
        val eren = aotCast.find { it.characterName == "Eren Yeager" }
        assertNotNull("Must contain Eren Yeager", eren)
        assertEquals("Eren Yeager voice actor must be normalized to 'Yūki Kaji'", "Yūki Kaji", eren?.voiceActorName)

        // 2. Demon Slayer
        val knyCast = repository.getCharacterCast("al-101922", "Demon Slayer: Kimetsu no Yaiba")
        assertTrue("Demon Slayer cast must not be empty", knyCast.isNotEmpty())
        val tanjiro = knyCast.find { it.characterName.contains("Tanjiro", ignoreCase = true) }
        assertNotNull("Must contain Tanjiro Kamado", tanjiro)
        assertEquals("Tanjiro voice actor must be Natsuki Hanae", "Natsuki Hanae", tanjiro?.voiceActorName)

        // 3. Solo Leveling
        val soloCast = repository.getCharacterCast("al-151807", "Solo Leveling")
        assertTrue("Solo Leveling cast must not be empty", soloCast.isNotEmpty())
        val jinwoo = soloCast.find { it.characterName == "Sung Jin-woo" }
        assertNotNull("Must contain Sung Jin-woo", jinwoo)
        assertEquals("Taito Ban", jinwoo?.voiceActorName)

        // 4. Jujutsu Kaisen (Live AniList returns Yuuji Itadori)
        val jjkCast = repository.getCharacterCast("al-113415", "Jujutsu Kaisen")
        assertTrue("Jujutsu Kaisen cast must not be empty", jjkCast.isNotEmpty())
        val itadori = jjkCast.find { it.characterName.contains("Itadori", ignoreCase = true) }
        assertNotNull("Must contain Itadori (Yuuji / Yuji)", itadori)
        assertEquals("Junya Enoki", itadori?.voiceActorName)

        // 5. Unknown anime fallback behavior (guarantees TV UI never crashes or displays blank state)
        val unknownCast = repository.getCharacterCast("unknown-9999", "Some Obscure Random Anime That Does Not Exist")
        assertTrue("Unknown anime should return fallback cast to prevent TV UI blank screen", unknownCast.isNotEmpty())
        assertTrue("Fallback cast should include protagonist", unknownCast.any { it.characterName.contains("Protagonist") })

        // 6. Malformed input attacks (must never crash)
        val malformed1 = repository.getCharacterCast("", "")
        assertTrue("Empty anime input must not crash", malformed1.isNotEmpty())
        val malformed2 = repository.getCharacterCast("!@#$%", "null")
        assertTrue("Malformed anime input must not crash", malformed2.isNotEmpty())
    }

    @Test
    fun testAniListCatalogSource_CharacterCastOfflineSafety() = runBlocking {
        // Without internet connectivity, getCharacterCast must return emptyList without throwing
        val cast = anilistSource.getCharacterCast("16498", "Attack on Titan")
        assertNotNull("AniList getCharacterCast must return non-null list", cast)
    }

    // =========================================================================
    // Challenge 6: AniList Sync Manager Edge Cases
    // =========================================================================

    @Test
    fun testAniListSyncManager_InputSanitization() = runBlocking {
        val whitespaceRes = AniListSyncManager.loginWithUsername("    ", storage)
        assertFalse("Whitespace-only username should fail", whitespaceRes.isSuccess)

        val emptyRes = AniListSyncManager.loginWithUsername("", storage)
        assertFalse("Empty username should fail", emptyRes.isSuccess)
    }
}
