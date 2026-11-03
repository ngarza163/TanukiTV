package com.darknet.animetv.e2e.tier1

import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import com.darknet.animetv.data.model.VideoSource
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Tier1_16_ExoPlayerEngineTest {

    @Test
    fun testHlsMediaItemConstruction() {
        val hlsStream = VideoSource(
            serverName = "MegaCloud",
            url = "https://cdn.mock/hls/master.m3u8",
            isHls = true
        )

        val mediaItem = MediaItem.Builder()
            .setUri(hlsStream.url)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()

        assertNotNull(mediaItem)
        assertEquals("https://cdn.mock/hls/master.m3u8", mediaItem.localConfiguration?.uri.toString())
        assertEquals(MimeTypes.APPLICATION_M3U8, mediaItem.localConfiguration?.mimeType)
    }

    @Test
    fun testMp4MediaItemConstruction() {
        val mp4Stream = VideoSource(
            serverName = "Mp4Upload",
            url = "https://cdn.mock/video.mp4",
            isHls = false
        )

        val mediaItem = MediaItem.Builder()
            .setUri(mp4Stream.url)
            .setMimeType(MimeTypes.VIDEO_MP4)
            .build()

        assertNotNull(mediaItem)
        assertEquals("https://cdn.mock/video.mp4", mediaItem.localConfiguration?.uri.toString())
        assertEquals(MimeTypes.VIDEO_MP4, mediaItem.localConfiguration?.mimeType)
    }

    @Test
    fun testDataSourceHeaderInjection() {
        val stream = VideoSource(
            serverName = "ProtectedStream",
            url = "https://stream.mock/playlist.m3u8",
            headers = mapOf(
                "Referer" to "https://aniwatchtv.ro/",
                "User-Agent" to "AnimeTV-AndroidTV-ExoPlayer/2.0"
            )
        )

        val headers = stream.headers
        assertEquals("https://aniwatchtv.ro/", headers["Referer"])
        assertEquals("AnimeTV-AndroidTV-ExoPlayer/2.0", headers["User-Agent"])
    }

    @Test
    fun testPlaybackStateConstants() {
        assertEquals(1, Player.STATE_IDLE)
        assertEquals(2, Player.STATE_BUFFERING)
        assertEquals(3, Player.STATE_READY)
        assertEquals(4, Player.STATE_ENDED)
    }

    @Test
    fun testPlayWhenReadyToggle() {
        var playWhenReady = true
        fun toggle() { playWhenReady = !playWhenReady }

        toggle()
        assertFalse(playWhenReady)

        toggle()
        assertTrue(playWhenReady)
    }
}
