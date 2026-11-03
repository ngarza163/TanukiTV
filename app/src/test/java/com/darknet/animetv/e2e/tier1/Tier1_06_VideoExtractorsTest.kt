package com.darknet.animetv.e2e.tier1

import com.darknet.animetv.data.model.VideoSource
import com.darknet.animetv.e2e.harness.MegaCloudExtractor
import com.darknet.animetv.e2e.harness.Mp4UploadExtractor
import com.darknet.animetv.e2e.harness.StreamWishExtractor
import org.junit.Assert.*
import org.junit.Test

class Tier1_06_VideoExtractorsTest {

    @Test
    fun testMegaCloudExtractor() {
        val extractor = MegaCloudExtractor()
        assertTrue(extractor.canHandle("https://megacloud.tv/embed-2/e-1?z="))
        assertFalse(extractor.canHandle("https://streamwish.to/e/123"))

        val streams = extractor.extract("https://megacloud.tv/embed-2/e-1")
        assertEquals(1, streams.size)
        assertEquals("MegaCloud", streams[0].serverName)
        assertTrue(streams[0].isHls)
        assertEquals("1080p", streams[0].quality)
    }

    @Test
    fun testStreamWishExtractor() {
        val extractor = StreamWishExtractor()
        assertTrue(extractor.canHandle("https://streamwish.to/e/abc123xyz"))
        assertFalse(extractor.canHandle("https://mp4upload.com/embed-xyz"))

        val streams = extractor.extract("https://streamwish.to/e/abc123xyz")
        assertEquals(1, streams.size)
        assertEquals("StreamWish", streams[0].serverName)
        assertTrue(streams[0].isHls)
        assertEquals("720p", streams[0].quality)
    }

    @Test
    fun testMp4UploadExtractor() {
        val extractor = Mp4UploadExtractor()
        assertTrue(extractor.canHandle("https://www.mp4upload.com/embed-987.html"))
        assertFalse(extractor.canHandle("https://megacloud.tv/embed"))

        val streams = extractor.extract("https://www.mp4upload.com/embed-987.html")
        assertEquals(1, streams.size)
        assertEquals("Mp4Upload", streams[0].serverName)
        assertFalse("Mp4Upload direct stream should not be HLS", streams[0].isHls)
        assertEquals("480p", streams[0].quality)
    }

    @Test
    fun testVideoSourceHlsAutoDetection() {
        val hlsStream = VideoSource(
            serverName = "TestHls",
            url = "https://cdn.mock/playlist.m3u8"
        )
        assertTrue("URL ending with .m3u8 should auto-detect as HLS", hlsStream.isHls)

        val mp4Stream = VideoSource(
            serverName = "TestMp4",
            url = "https://cdn.mock/video.mp4"
        )
        assertFalse("URL ending with .mp4 should auto-detect as not HLS", mp4Stream.isHls)
    }

    @Test
    fun testExtractorHeaderInjection() {
        val extractor = MegaCloudExtractor()
        val customReferer = "https://aniwatchtv.ro/watch/frieren-154587"
        val streams = extractor.extract("https://megacloud.tv/embed-2", referer = customReferer)

        assertEquals(customReferer, streams[0].headers["Referer"])
    }
}
