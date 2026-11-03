package com.darknet.animetv.e2e.tier1

import com.darknet.animetv.e2e.harness.TvDpadKey
import com.darknet.animetv.e2e.harness.TvPlayerHudSimulator
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class Tier1_17_PlayerHudTest {

    private lateinit var hud: TvPlayerHudSimulator

    @Before
    fun setUp() {
        hud = TvPlayerHudSimulator(autoHideTimeoutMs = 5000L)
    }

    @Test
    fun testHudInitialVisibility() {
        assertTrue("HUD should be visible initially", hud.isHudVisible)
    }

    @Test
    fun testHudAutoHidesAfterFiveSeconds() {
        val t0 = 1000L
        hud.onUserInteraction(t0)
        assertTrue(hud.isHudVisible)

        // 3 seconds passed (still visible)
        hud.onTimeTick(t0 + 3000L)
        assertTrue("HUD should still be visible after 3s", hud.isHudVisible)

        // 5 seconds passed (auto hides)
        hud.onTimeTick(t0 + 5000L)
        assertFalse("HUD should auto-hide after 5s", hud.isHudVisible)
    }

    @Test
    fun testDpadInputRevealsHiddenHudAndResetsTimer() {
        val t0 = 1000L
        hud.onUserInteraction(t0)
        hud.onTimeTick(t0 + 6000L)
        assertFalse(hud.isHudVisible)

        // User presses DPAD_UP at t0 + 7000
        hud.onDpadKey(TvDpadKey.DPAD_UP, currentTimeMs = t0 + 7000L)
        assertTrue("D-pad key should reveal HUD", hud.isHudVisible)
        assertEquals(t0 + 7000L, hud.lastInteractionTimestamp)
    }

    @Test
    fun testBackKeyHidesHudImmediately() {
        assertTrue(hud.isHudVisible)
        hud.onDpadKey(TvDpadKey.BACK)
        assertFalse("BACK key should dismiss HUD immediately", hud.isHudVisible)
    }

    @Test
    fun testContinuousInteractionsPreventAutoHide() {
        var currentTime = 1000L
        hud.onUserInteraction(currentTime)

        // User interacts every 2 seconds for 10 seconds total
        for (i in 1..5) {
            currentTime += 2000L
            hud.onDpadKey(TvDpadKey.DPAD_RIGHT, currentTime)
            hud.onTimeTick(currentTime)
            assertTrue("HUD must remain visible during continuous interactions", hud.isHudVisible)
        }
    }
}
