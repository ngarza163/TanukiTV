package com.darknet.animetv.player

import com.darknet.animetv.data.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BufferTuningTest {

    @Test
    fun testBufferModesAvailable() {
        assertTrue(VideoPlayerManager.AVAILABLE_BUFFER_MODES.isNotEmpty())
        assertTrue(VideoPlayerManager.AVAILABLE_BUFFER_MODES.any { it.contains("120s") })
        assertTrue(VideoPlayerManager.AVAILABLE_BUFFER_MODES.any { it.contains("50s") })
        assertTrue(VideoPlayerManager.AVAILABLE_BUFFER_MODES.any { it.contains("20s") })
    }

    @Test
    fun testBitrateCapsParsing() {
        assertEquals(20_000_000, VideoPlayerManager.parseBitrateCap("20 Mbps (4K UHD)"))
        assertEquals(10_000_000, VideoPlayerManager.parseBitrateCap("10 Mbps (1080p HQ)"))
        assertEquals(5_000_000, VideoPlayerManager.parseBitrateCap("5 Mbps (720p)"))
        assertEquals(2_500_000, VideoPlayerManager.parseBitrateCap("2.5 Mbps (Saver)"))
        assertEquals(Int.MAX_VALUE, VideoPlayerManager.parseBitrateCap("Unlimited"))
    }

    @Test
    fun testCreateLoadControlDoesNotCrash() {
        for (mode in VideoPlayerManager.AVAILABLE_BUFFER_MODES) {
            val loadControl = VideoPlayerManager.createLoadControl(mode)
            assertNotNull(loadControl)
        }
    }

    @Test
    fun testUserSettingsBufferDefaults() {
        val defaultSettings = UserSettings()
        assertEquals("Balanced (50s)", defaultSettings.bufferMode)
        assertEquals("Unlimited", defaultSettings.maxBitrateCap)
    }
}
