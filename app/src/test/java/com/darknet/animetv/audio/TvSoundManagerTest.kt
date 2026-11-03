package com.darknet.animetv.audio

import com.darknet.animetv.data.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TvSoundManagerTest {

    @Test
    fun `synthesized PCM buffers are non-empty and within 16-bit audio bounds`() {
        val buffers = listOf(
            "focusTick" to TvSoundManager.focusTickPcm,
            "actionSelect" to TvSoundManager.actionSelectPcm,
            "tabSwitch" to TvSoundManager.tabSwitchPcm,
            "backDismiss" to TvSoundManager.backDismissPcm,
            "watchlistAdd" to TvSoundManager.watchlistAddPcm
        )

        for ((name, pcm) in buffers) {
            assertTrue("$name buffer should not be empty", pcm.isNotEmpty())
            val maxAmplitude = pcm.maxOf { abs(it.toInt()) }
            assertTrue("$name buffer should have audible signal", maxAmplitude > 1000)
            assertTrue("$name buffer should not clip", maxAmplitude <= Short.MAX_VALUE.toInt())
        }
    }

    @Test
    fun `setEnabled toggles sound manager state and play calls are safe when disabled`() {
        TvSoundManager.setEnabled(false)
        assertFalse(TvSoundManager.isEnabled())

        // Calling play methods while disabled should be safe no-ops in JVM unit test environment
        TvSoundManager.playFocusTick()
        TvSoundManager.playActionSelect()
        TvSoundManager.playTabSwitch()
        TvSoundManager.playBackDismiss()
        TvSoundManager.playWatchlistAdd()

        TvSoundManager.setEnabled(true)
        assertTrue(TvSoundManager.isEnabled())
    }

    @Test
    fun `UserSettings defaults uiSoundEffects to true`() {
        val defaultSettings = UserSettings()
        assertTrue(defaultSettings.uiSoundEffects)

        val mutedSettings = defaultSettings.copy(uiSoundEffects = false)
        assertFalse(mutedSettings.uiSoundEffects)
        assertEquals(defaultSettings.defaultQuality, mutedSettings.defaultQuality)
    }
}
