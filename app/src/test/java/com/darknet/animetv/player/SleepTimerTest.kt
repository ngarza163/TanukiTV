package com.darknet.animetv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerTest {

    @Test
    fun testInitialState() {
        val manager = SleepTimerManager()
        assertEquals(SleepTimerManager.SleepTimerMode.OFF, manager.activeMode)
        assertFalse(manager.isActive)
        assertFalse(manager.isSleepTriggered)
        assertEquals(0L, manager.remainingTimeMs)
        assertEquals(0, manager.remainingEpisodes)
        assertEquals("⏰ Sleep: Off", manager.getFormattedBadgeText())
    }

    @Test
    fun testTimeCountdown() {
        val manager = SleepTimerManager()
        manager.setTimer(SleepTimerManager.SleepTimerMode.MINUTES_30)

        assertTrue(manager.isActive)
        assertEquals(30 * 60 * 1000L, manager.remainingTimeMs)
        assertEquals("⏰ Sleep: 30:00", manager.getFormattedBadgeText())

        // Tick 60 seconds
        val triggered1 = manager.tick(60_000L)
        assertFalse(triggered1)
        assertEquals(29 * 60 * 1000L, manager.remainingTimeMs)
        assertEquals("⏰ Sleep: 29:00", manager.getFormattedBadgeText())

        // Tick remaining time
        val triggered2 = manager.tick(29 * 60 * 1000L)
        assertTrue(triggered2)
        assertTrue(manager.isSleepTriggered)
        assertFalse(manager.isActive)
    }

    @Test
    fun testEpisodeBasedLimits() {
        val manager = SleepTimerManager()
        manager.setTimer(SleepTimerManager.SleepTimerMode.AFTER_2_EPISODES)

        assertTrue(manager.isActive)
        assertEquals(2, manager.remainingEpisodes)
        assertEquals("⏰ Sleep: 2 Ep left", manager.getFormattedBadgeText())

        // Episode 1 ends
        val shouldSleep1 = manager.onEpisodeFinished()
        assertFalse(shouldSleep1)
        assertEquals(1, manager.remainingEpisodes)
        assertEquals("⏰ Sleep: 1 Ep left", manager.getFormattedBadgeText())

        // Episode 2 ends
        val shouldSleep2 = manager.onEpisodeFinished()
        assertTrue(shouldSleep2)
        assertTrue(manager.isSleepTriggered)
        assertFalse(manager.isActive)
    }

    @Test
    fun testAudioFadeMultiplier() {
        val manager = SleepTimerManager()
        manager.setTimer(SleepTimerManager.SleepTimerMode.MINUTES_30)

        // Far away from end: full volume
        assertEquals(1.0f, manager.calculateFadeMultiplier(3000L), 0.001f)

        // Set remaining time to 1.5s inside 3s fade window: half volume
        manager.tick((30 * 60 * 1000L) - 1500L)
        assertEquals(0.5f, manager.calculateFadeMultiplier(3000L), 0.05f)

        // Reaching 0ms: volume muted
        manager.tick(1500L)
        assertEquals(0.0f, manager.calculateFadeMultiplier(3000L), 0.001f)
    }

    @Test
    fun testCancel() {
        val manager = SleepTimerManager()
        manager.setTimer(SleepTimerManager.SleepTimerMode.MINUTES_60)
        assertTrue(manager.isActive)

        manager.cancel()
        assertFalse(manager.isActive)
        assertEquals(SleepTimerManager.SleepTimerMode.OFF, manager.activeMode)
    }
}
