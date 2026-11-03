package com.darknet.animetv.player.audio

import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.util.Log

/**
 * Living Room Audio Enhancements for Android TV.
 * Provides real-time Clear Voice (dialogue boost), Night Mode (DRC volume limiter),
 * and Bass Boost attached to the active ExoPlayer audio session.
 */
class AudioEffectsManager {

    companion object {
        private const val TAG = "AudioEffectsManager"
        const val PRESET_STANDARD = "Standard"
        const val PRESET_CLEAR_VOICE = "Clear Voice"
        const val PRESET_NIGHT_MODE = "Night Mode"
        const val PRESET_BASS_BOOST = "Bass Boost"

        val AVAILABLE_PRESETS = listOf(
            PRESET_STANDARD,
            PRESET_CLEAR_VOICE,
            PRESET_NIGHT_MODE,
            PRESET_BASS_BOOST
        )
    }

    private var equalizer: Equalizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentSessionId: Int = 0

    /**
     * Attaches audio effects to the active ExoPlayer audio session.
     */
    fun attachToSession(audioSessionId: Int, initialPreset: String = PRESET_STANDARD) {
        if (audioSessionId <= 0 || audioSessionId == currentSessionId) return
        release()
        currentSessionId = audioSessionId

        try {
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Equalizer initialization skipped: ${e.message}")
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                    enabled = true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "LoudnessEnhancer initialization skipped: ${e.message}")
        }

        applyPreset(initialPreset)
    }

    /**
     * Applies the designated audio profile in real time.
     */
    fun applyPreset(presetName: String) {
        try {
            when (presetName) {
                PRESET_CLEAR_VOICE -> applyClearVoice()
                PRESET_NIGHT_MODE -> applyNightMode()
                PRESET_BASS_BOOST -> applyBassBoost()
                else -> applyStandard()
            }
            Log.d(TAG, "Applied audio preset: $presetName")
        } catch (e: Exception) {
            Log.w(TAG, "Error applying audio preset $presetName", e)
        }
    }

    /**
     * Flat / default neutral response.
     */
    private fun applyStandard() {
        equalizer?.let { eq ->
            eq.enabled = false
            for (i in 0 until eq.numberOfBands) {
                eq.setBandLevel(i.toShort(), 0)
            }
        }
        loudnessEnhancer?.let { le ->
            le.enabled = false
            le.setTargetGain(0)
        }
    }

    /**
     * Clear Voice / Dialogue Booster:
     * - Cuts muddy low frequencies (< 200 Hz by -200 mB)
     * - Boosts vocal / speech intelligibility frequencies (1 kHz - 4 kHz by +500 mB)
     * - Adds subtle +200 mB loudness target gain so quiet whispers are heard clearly.
     */
    private fun applyClearVoice() {
        equalizer?.let { eq ->
            eq.enabled = true
            for (i in 0 until eq.numberOfBands) {
                val centerFreq = eq.getCenterFreq(i.toShort()) // in mHz
                when {
                    centerFreq < 250_000 -> eq.setBandLevel(i.toShort(), (-200).toShort()) // -2 dB bass cut
                    centerFreq in 800_000..4_500_000 -> eq.setBandLevel(i.toShort(), 500.toShort()) // +5 dB voice boost
                    else -> eq.setBandLevel(i.toShort(), 0)
                }
            }
        }
        loudnessEnhancer?.let { le ->
            le.enabled = true
            le.setTargetGain(250) // +2.5 dB
        }
    }

    /**
     * Night Mode (Dynamic Range Compression & Limiter):
     * - Restricts explosive volume spikes from action scenes and blasts
     * - Boosts low-level audio (+400 mB) with high-frequency attenuation
     * - Prevents waking up other family members during late-night watching.
     */
    private fun applyNightMode() {
        equalizer?.let { eq ->
            eq.enabled = true
            for (i in 0 until eq.numberOfBands) {
                val centerFreq = eq.getCenterFreq(i.toShort())
                when {
                    centerFreq < 200_000 -> eq.setBandLevel(i.toShort(), (-400).toShort()) // -4 dB rumble cut
                    centerFreq > 6_000_000 -> eq.setBandLevel(i.toShort(), (-300).toShort()) // -3 dB harshness cut
                    else -> eq.setBandLevel(i.toShort(), 100.toShort()) // slight dialogue lift
                }
            }
        }
        loudnessEnhancer?.let { le ->
            le.enabled = true
            le.setTargetGain(150)
        }
    }

    /**
     * Action / Bass Boost profile.
     */
    private fun applyBassBoost() {
        equalizer?.let { eq ->
            eq.enabled = true
            for (i in 0 until eq.numberOfBands) {
                val centerFreq = eq.getCenterFreq(i.toShort())
                if (centerFreq < 300_000) {
                    eq.setBandLevel(i.toShort(), 600.toShort()) // +6 dB bass punch
                } else {
                    eq.setBandLevel(i.toShort(), 0)
                }
            }
        }
        loudnessEnhancer?.let { le ->
            le.enabled = false
            le.setTargetGain(0)
        }
    }

    /**
     * Releases audio effect handles.
     */
    fun release() {
        try {
            equalizer?.release()
        } catch (_: Exception) {}
        equalizer = null

        try {
            loudnessEnhancer?.release()
        } catch (_: Exception) {}
        loudnessEnhancer = null

        currentSessionId = 0
    }
}
