package com.darknet.animetv.audio

import com.darknet.animetv.data.model.UserSettings
import com.darknet.animetv.data.storage.AppStorage
import com.darknet.animetv.player.audio.AudioEffectsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AudioEffectsTest {

    @Test
    fun testAvailableAudioPresets() {
        val presets = AudioEffectsManager.AVAILABLE_PRESETS
        assertEquals(4, presets.size)
        assertTrue(presets.contains("Standard"))
        assertTrue(presets.contains("Clear Voice"))
        assertTrue(presets.contains("Night Mode"))
        assertTrue(presets.contains("Bass Boost"))
    }

    @Test
    fun testAudioEffectsManagerLifecycleWithoutThrowing() {
        val manager = AudioEffectsManager()

        // Should handle session ID 0 gracefully
        manager.attachToSession(0)
        manager.applyPreset("Clear Voice")
        manager.applyPreset("Night Mode")
        manager.applyPreset("Bass Boost")
        manager.applyPreset("Standard")
        manager.release()

        assertTrue(true)
    }

    @Test
    fun testUserSettingsAudioPersistence() {
        val storage = AppStorage(RuntimeEnvironment.getApplication())
        val initialSettings = storage.getUserSettings()

        assertEquals("Standard", initialSettings.audioPreset)
        assertFalse(initialSettings.audioPassthrough)

        val updatedSettings = initialSettings.copy(
            audioPreset = "Clear Voice",
            audioPassthrough = true
        )
        storage.saveUserSettings(updatedSettings)

        val loaded = storage.getUserSettings()
        assertEquals("Clear Voice", loaded.audioPreset)
        assertTrue(loaded.audioPassthrough)
    }
}
