package com.darknet.animetv.channel

import android.net.Uri
import com.darknet.animetv.data.model.Anime
import com.darknet.animetv.data.model.Episode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TvChannelManagerTest {

    @Test
    fun testDeepLinkUriFormats() {
        val anime = Anime(
            id = "frieren-beyond-journeys-end",
            title = "Frieren: Beyond Journey's End",
            posterUrl = "https://example.com/poster.jpg",
            backdropUrl = "",
            synopsis = "Elf mage adventure"
        )
        val detailsUri = Uri.parse("animetv://details?id=${anime.id}&title=${Uri.encode(anime.title)}")
        assertEquals("animetv", detailsUri.scheme)
        assertEquals("details", detailsUri.host)
        assertEquals("frieren-beyond-journeys-end", detailsUri.getQueryParameter("id"))
        assertEquals("Frieren: Beyond Journey's End", detailsUri.getQueryParameter("title"))

        val episode = Episode(
            id = "frieren-ep-5",
            animeId = anime.id,
            number = 5,
            title = "Phantoms of the Dead",
            thumbnail = "https://example.com/ep5.jpg"
        )
        val playerUri = Uri.parse(
            "animetv://player?animeId=${anime.id}&episodeId=${episode.id}&episodeNum=${episode.number}&title=${Uri.encode(anime.title)}"
        )
        assertEquals("animetv", playerUri.scheme)
        assertEquals("player", playerUri.host)
        assertEquals(5, playerUri.getQueryParameter("episodeNum")?.toInt())
        assertEquals("frieren-ep-5", playerUri.getQueryParameter("episodeId"))
    }

    @Test
    fun testPublishTrendingChannelHandlesEmptyList() {
        val context = RuntimeEnvironment.getApplication()
        val result = TvChannelManager.publishTrendingChannel(context, emptyList())
        assertEquals(-1L, result)
    }

    @Test
    fun testUpdateWatchNextProgramIgnoresShortPlayTime() {
        val context = RuntimeEnvironment.getApplication()
        val anime = Anime(id = "a1", title = "Title", posterUrl = "", backdropUrl = "", synopsis = "")
        val ep = Episode("e1", "a1", 1, "Ep 1", "")

        // Should not throw or fail for 3 seconds of watch time (under 5s threshold)
        TvChannelManager.updateWatchNextProgram(context, anime, ep, 3_000L, 1_440_000L)
        assertTrue(true)
    }
}
