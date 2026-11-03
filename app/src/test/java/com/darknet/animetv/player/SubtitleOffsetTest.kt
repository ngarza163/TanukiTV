package com.darknet.animetv.player

import com.darknet.animetv.data.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleOffsetTest {

    @Test
    fun testDefaultSubtitleOffset() {
        val settings = UserSettings()
        assertEquals(0L, settings.subtitleOffsetMs)
    }

    @Test
    fun testSubtitleOffsetValues() {
        val minus500 = UserSettings(subtitleOffsetMs = -500L)
        assertEquals(-500L, minus500.subtitleOffsetMs)

        val plus1500 = UserSettings(subtitleOffsetMs = 1500L)
        assertEquals(1500L, plus1500.subtitleOffsetMs)
    }

    @Test
    fun testEffectiveTimestampCalculation() {
        val currentPositionMs = 120_000L // 2 minutes in

        // -500ms delay: subtitle displays earlier relative to audio
        val earlyOffset = -500L
        val effectivePositionEarly = (currentPositionMs + earlyOffset).coerceAtLeast(0L)
        assertEquals(119_500L, effectivePositionEarly)

        // +1000ms delay: subtitle displays later
        val lateOffset = 1000L
        val effectivePositionLate = (currentPositionMs + lateOffset).coerceAtLeast(0L)
        assertEquals(121_000L, effectivePositionLate)

        // Underflow clamp at 0ms
        val extremeNegativeOffset = -200_000L
        val clampedPosition = (currentPositionMs + extremeNegativeOffset).coerceAtLeast(0L)
        assertEquals(0L, clampedPosition)
    }
}
