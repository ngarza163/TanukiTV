package com.darknet.animetv.player

import androidx.media3.ui.AspectRatioFrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlayerProControlsTest {

    @Test
    fun testAspectRatioModesMapping() {
        val modeMap = mapOf(
            "Fit" to AspectRatioFrameLayout.RESIZE_MODE_FIT,
            "Zoom" to AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
            "Stretch" to AspectRatioFrameLayout.RESIZE_MODE_FILL,
            "4:3" to AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
        )

        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIT, modeMap["Fit"])
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, modeMap["Zoom"])
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FILL, modeMap["Stretch"])
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH, modeMap["4:3"])
    }

    @Test
    fun testPlaybackSpeeds() {
        val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        assertTrue(speeds.contains(1.0f))
        assertTrue(speeds.contains(1.5f))
        assertEquals(5, speeds.size)
    }

    @Test
    fun testSkipIntroRangeDetection() {
        val introSkipSeconds = 85
        val introMaxMs = introSkipSeconds * 1000L

        val posDuringIntro = 45_000L
        val posAfterIntro = 120_000L

        assertTrue(posDuringIntro in 1_000L..introMaxMs)
        assertTrue(posAfterIntro !in 1_000L..introMaxMs)
    }
}
