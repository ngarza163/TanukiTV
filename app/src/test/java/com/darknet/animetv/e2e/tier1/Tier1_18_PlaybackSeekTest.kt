package com.darknet.animetv.e2e.tier1

import com.darknet.animetv.e2e.harness.TvDpadKey
import com.darknet.animetv.e2e.harness.TvPlayerHudSimulator
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class Tier1_18_PlaybackSeekTest {

    private lateinit var hud: TvPlayerHudSimulator

    @Before
    fun setUp() {
        hud = TvPlayerHudSimulator(seekStepMs = 10_000L)
        hud.setPosition(positionMs = 60_000L, totalDurationMs = 1_440_000L) // 1 min in 24 min
    }

    @Test
    fun testPlayPauseToggle() {
        assertTrue("Initially playing", hud.isPlaying)

        hud.onDpadKey(TvDpadKey.MEDIA_PLAY_PAUSE)
        assertFalse("Paused after toggle", hud.isPlaying)

        hud.onDpadKey(TvDpadKey.MEDIA_PLAY_PAUSE)
        assertTrue("Playing after second toggle", hud.isPlaying)
    }

    @Test
    fun testSeekForwardTenSecondsWithPill() {
        assertEquals(60_000L, hud.currentPositionMs)
        hud.onDpadKey(TvDpadKey.DPAD_RIGHT)

        assertEquals(70_000L, hud.currentPositionMs)
        assertTrue(hud.seekPillVisible)
        assertEquals(+10L, hud.seekPillOffset)
    }

    @Test
    fun testSeekRewindTenSecondsWithPill() {
        assertEquals(60_000L, hud.currentPositionMs)
        hud.onDpadKey(TvDpadKey.DPAD_LEFT)

        assertEquals(50_000L, hud.currentPositionMs)
        assertTrue(hud.seekPillVisible)
        assertEquals(-10L, hud.seekPillOffset)
    }

    @Test
    fun testSeekClampingAtStartOfVideo() {
        hud.setPosition(positionMs = 5_000L, totalDurationMs = 1_440_000L)
        hud.seekRewind() // 5s - 10s -> clamped to 0

        assertEquals(0L, hud.currentPositionMs)
    }

    @Test
    fun testSeekClampingAtEndOfVideo() {
        hud.setPosition(positionMs = 1_435_000L, totalDurationMs = 1_440_000L)
        hud.seekForward() // 1435s + 10s -> clamped to 1440s

        assertEquals(1_440_000L, hud.currentPositionMs)
    }
}
