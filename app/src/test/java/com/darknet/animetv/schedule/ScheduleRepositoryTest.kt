package com.darknet.animetv.schedule

import com.darknet.animetv.data.model.AiringScheduleItem
import com.darknet.animetv.data.repository.DefaultAnimeRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScheduleRepositoryTest {

    private lateinit var repository: DefaultAnimeRepository

    @Before
    fun setup() {
        repository = DefaultAnimeRepository()
    }

    @Test
    fun testGetAiringScheduleReturnsFullWeek() = runBlocking {
        val allSchedule = repository.getAiringSchedule(null)
        assertTrue("Schedule should contain simulcasts across all days", allSchedule.size >= 10)

        val days = allSchedule.map { it.dayOfWeek }.toSet()
        assertTrue(days.contains("Monday"))
        assertTrue(days.contains("Wednesday"))
        assertTrue(days.contains("Saturday"))
        assertTrue(days.contains("Sunday"))
    }

    @Test
    fun testFilterScheduleByDay() = runBlocking {
        val mondaySchedule = repository.getAiringSchedule("Monday")
        assertTrue(mondaySchedule.isNotEmpty())
        assertTrue(mondaySchedule.all { it.dayOfWeek.equals("Monday", ignoreCase = true) })

        val saturdaySchedule = repository.getAiringSchedule("Saturday")
        assertTrue(saturdaySchedule.isNotEmpty())
        assertTrue(saturdaySchedule.all { it.dayOfWeek.equals("Saturday", ignoreCase = true) })
    }

    @Test
    fun testScheduleItemConversionToAnime() {
        val item = AiringScheduleItem(
            id = "test-1",
            animeId = "dandadan",
            title = "DAN DA DAN",
            posterUrl = "https://example.com/poster.jpg",
            bannerUrl = "https://example.com/banner.jpg",
            episodeNumber = 6,
            airingAt = 1730000000L,
            timeUntilAiringSeconds = 7200L,
            dayOfWeek = "Thursday",
            genres = listOf("Action", "Comedy"),
            score = 8.7
        )

        val anime = item.toAnime()
        assertEquals("dandadan", anime.id)
        assertEquals("DAN DA DAN", anime.title)
        assertEquals("https://example.com/poster.jpg", anime.posterUrl)
        assertEquals(8.7, anime.score, 0.01)
        assertEquals(6, anime.totalEpisodes)
    }

    @Test
    fun testCountdownFormatting() {
        val liveItem = AiringScheduleItem(
            id = "test-live",
            animeId = "test",
            title = "Test",
            posterUrl = "",
            episodeNumber = 1,
            airingAt = 0L,
            timeUntilAiringSeconds = -100L,
            dayOfWeek = "Friday"
        )
        assertEquals("Aired Today", liveItem.formatCountdown())

        val upcomingItem = AiringScheduleItem(
            id = "test-up",
            animeId = "test",
            title = "Test",
            posterUrl = "",
            episodeNumber = 1,
            airingAt = 0L,
            timeUntilAiringSeconds = 3600L * 2 + 15 * 60,
            dayOfWeek = "Friday"
        )
        assertEquals("In 2h 15m", upcomingItem.formatCountdown())
    }
}
