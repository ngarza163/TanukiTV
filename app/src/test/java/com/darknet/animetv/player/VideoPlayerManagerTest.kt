package com.darknet.animetv.player

import android.content.Context
import androidx.media3.common.MimeTypes
import androidx.test.core.app.ApplicationProvider
import com.darknet.animetv.data.model.SubtitleTrack
import com.darknet.animetv.data.model.VideoSource
import com.darknet.animetv.data.model.WatchProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VideoPlayerManagerTest {

    @Test
    fun testBuildMediaItemForHlsAndSubtitles() {
        val source = VideoSource(
            serverName = "MegaCloud",
            url = "https://cdn.example.com/hls/master.m3u8",
            quality = "1080p",
            isHls = true
        )
        val subtitle = SubtitleTrack(
            language = "en",
            label = "English",
            url = "https://cdn.example.com/subs/en.vtt"
        )

        val item = VideoPlayerManager.buildMediaItem(source, subtitle)
        assertNotNull(item.localConfiguration)
        assertEquals(MimeTypes.APPLICATION_M3U8, item.localConfiguration?.mimeType)
        assertEquals(1, item.localConfiguration?.subtitleConfigurations?.size)
        val subCfg = item.localConfiguration!!.subtitleConfigurations[0]
        assertEquals("en", subCfg.language)
        assertEquals("English", subCfg.label)
        assertEquals(MimeTypes.TEXT_VTT, subCfg.mimeType)
    }

    @Test
    fun testBuildMediaItemForMp4AndSrtSubtitles() {
        val source = VideoSource(
            serverName = "Mp4Upload",
            url = "https://cdn.example.com/videos/ep1.mp4",
            quality = "720p",
            isHls = false
        )
        val subtitle = SubtitleTrack(
            language = "es",
            label = "Spanish",
            url = "https://cdn.example.com/subs/es.srt"
        )

        val item = VideoPlayerManager.buildMediaItem(source, subtitle)
        assertNotNull(item.localConfiguration)
        assertEquals(MimeTypes.VIDEO_MP4, item.localConfiguration?.mimeType)
        val subCfg = item.localConfiguration!!.subtitleConfigurations[0]
        assertEquals(MimeTypes.APPLICATION_SUBRIP, subCfg.mimeType)
    }

    @Test
    fun testCreateExoPlayerAndApplyQualityConstraints() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = VideoSource(
            serverName = "Aniwatch",
            url = "https://cdn.example.com/stream.m3u8",
            headers = mapOf("Referer" to "https://aniwatchtv.ro/")
        )

        val player = VideoPlayerManager.createExoPlayer(context, source)
        try {
            VideoPlayerManager.applyQualityTrackSelection(player, "1080p")
            assertEquals(1920, player.trackSelectionParameters.maxVideoWidth)
            assertEquals(1080, player.trackSelectionParameters.maxVideoHeight)

            VideoPlayerManager.applyQualityTrackSelection(player, "720p")
            assertEquals(1280, player.trackSelectionParameters.maxVideoWidth)
            assertEquals(720, player.trackSelectionParameters.maxVideoHeight)

            VideoPlayerManager.applyQualityTrackSelection(player, "480p")
            assertEquals(854, player.trackSelectionParameters.maxVideoWidth)
            assertEquals(480, player.trackSelectionParameters.maxVideoHeight)

            VideoPlayerManager.applyQualityTrackSelection(player, "360p")
            assertEquals(640, player.trackSelectionParameters.maxVideoWidth)
            assertEquals(360, player.trackSelectionParameters.maxVideoHeight)

            VideoPlayerManager.applyQualityTrackSelection(player, "Auto")
            assertEquals(Int.MAX_VALUE, player.trackSelectionParameters.maxVideoWidth)
        } finally {
            player.release()
        }
    }

    @Test
    fun testComputeSeekPositionClamping() {
        val duration = 1_440_000L // 24m

        // Normal forward +10s
        assertEquals(70_000L, VideoPlayerManager.computeSeekPosition(60_000L, +10_000L, duration))

        // Normal rewind -10s
        assertEquals(50_000L, VideoPlayerManager.computeSeekPosition(60_000L, -10_000L, duration))

        // Clamp at start (0ms)
        assertEquals(0L, VideoPlayerManager.computeSeekPosition(4_000L, -10_000L, duration))

        // Clamp at end (duration)
        assertEquals(duration, VideoPlayerManager.computeSeekPosition(1_435_000L, +10_000L, duration))

        // Unknown duration (0L)
        assertEquals(25_000L, VideoPlayerManager.computeSeekPosition(15_000L, +10_000L, 0L))
    }

    @Test
    fun testResolveAutoResumePosition() {
        // 1. Null progress -> 0L
        assertEquals(0L, VideoPlayerManager.resolveAutoResumePosition(null, 1))

        // 2. Different episode -> 0L
        val ep1Progress = WatchProgress(
            animeId = "a1",
            animeTitle = "Test",
            posterUrl = "",
            episodeId = "e1",
            episodeNumber = 1,
            episodeTitle = "Ep 1",
            positionMs = 300_000L,
            durationMs = 1_440_000L
        )
        assertEquals(0L, VideoPlayerManager.resolveAutoResumePosition(ep1Progress, 2))

        // 3. Same episode, in middle (<90%, >10s) -> resumes at 300_000L
        assertEquals(300_000L, VideoPlayerManager.resolveAutoResumePosition(ep1Progress, 1))

        // 4. Completed episode (>=90%) -> starts from 0L
        val completedProgress = ep1Progress.copy(positionMs = 1_350_000L)
        assertEquals(0L, VideoPlayerManager.resolveAutoResumePosition(completedProgress, 1))

        // 5. Early position (<10s) -> starts from 0L
        val earlyProgress = ep1Progress.copy(positionMs = 5_000L)
        assertEquals(0L, VideoPlayerManager.resolveAutoResumePosition(earlyProgress, 1))
    }

    @Test
    fun testShouldPersistProgressAndFormatDuration() {
        assertFalse(VideoPlayerManager.shouldPersistProgress(3_000L, 1_000L))
        assertTrue(VideoPlayerManager.shouldPersistProgress(6_000L, 1_000L))

        assertEquals("00:00", VideoPlayerManager.formatDuration(0L))
        assertEquals("01:05", VideoPlayerManager.formatDuration(65_000L))
        assertEquals("24:00", VideoPlayerManager.formatDuration(1_440_000L))
        assertEquals("1:02:03", VideoPlayerManager.formatDuration(3_723_000L))
    }
}
