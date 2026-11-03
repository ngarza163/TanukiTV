package com.darknet.animetv.player

/**
 * TV Sleep Timer & Auto-Off Controller for late-night binge watching.
 * Supports:
 * - Time-based countdown (30m, 60m, 90m)
 * - Episode-based limits (End of current episode, After 2 episodes)
 * - Smooth 3-second audio fade-out volume interpolation
 */
class SleepTimerManager {

    enum class SleepTimerMode(val label: String, val durationMinutes: Int = 0, val episodeLimit: Int = 0) {
        OFF("Off"),
        END_OF_EPISODE("End of current episode", episodeLimit = 1),
        AFTER_2_EPISODES("After 2 episodes", episodeLimit = 2),
        MINUTES_30("30 Minutes", durationMinutes = 30),
        MINUTES_60("60 Minutes", durationMinutes = 60),
        MINUTES_90("90 Minutes", durationMinutes = 90)
    }

    var activeMode: SleepTimerMode = SleepTimerMode.OFF
        private set

    var remainingTimeMs: Long = 0L
        private set

    var remainingEpisodes: Int = 0
        private set

    var isSleepTriggered: Boolean = false
        private set

    val isActive: Boolean
        get() = activeMode != SleepTimerMode.OFF && !isSleepTriggered

    fun setTimer(mode: SleepTimerMode) {
        activeMode = mode
        isSleepTriggered = false
        if (mode.durationMinutes > 0) {
            remainingTimeMs = mode.durationMinutes * 60 * 1000L
            remainingEpisodes = 0
        } else if (mode.episodeLimit > 0) {
            remainingEpisodes = mode.episodeLimit
            remainingTimeMs = 0L
        } else {
            remainingTimeMs = 0L
            remainingEpisodes = 0
        }
    }

    fun cancel() {
        activeMode = SleepTimerMode.OFF
        remainingTimeMs = 0L
        remainingEpisodes = 0
        isSleepTriggered = false
    }

    /**
     * Decrements the countdown timer. If remainingTimeMs hits 0, triggers sleep.
     * @return true if sleep was just triggered on this tick.
     */
    fun tick(elapsedMs: Long = 1000L): Boolean {
        if (!isActive || activeMode.durationMinutes == 0) return false

        remainingTimeMs = (remainingTimeMs - elapsedMs).coerceAtLeast(0L)
        if (remainingTimeMs == 0L) {
            isSleepTriggered = true
            return true
        }
        return false
    }

    /**
     * Called when an episode finishes playback.
     * Decrements episode counter for episode-based timers.
     * @return true if sleep should trigger and playback should pause.
     */
    fun onEpisodeFinished(): Boolean {
        if (!isActive || activeMode.episodeLimit == 0) return false

        remainingEpisodes = (remainingEpisodes - 1).coerceAtLeast(0)
        if (remainingEpisodes == 0) {
            isSleepTriggered = true
            return true
        }
        return false
    }

    /**
     * Formats remaining time for Leanback HUD badges (e.g. "⏰ 28m 45s" or "⏰ 1 Ep left").
     */
    fun getFormattedBadgeText(): String {
        return when {
            !isActive -> "⏰ Sleep: Off"
            activeMode.durationMinutes > 0 -> {
                val totalSeconds = (remainingTimeMs / 1000L).coerceAtLeast(0L)
                val minutes = totalSeconds / 60
                val seconds = totalSeconds % 60
                String.format("⏰ Sleep: %02d:%02d", minutes, seconds)
            }
            activeMode.episodeLimit > 0 -> {
                "⏰ Sleep: $remainingEpisodes Ep left"
            }
            else -> "⏰ Sleep: Off"
        }
    }

    /**
     * Computes smooth volume fade multiplier (0.0f..1.0f) during the final [fadeDurationMs]
     * of the timer countdown so audio doesn't abruptly cut off.
     */
    fun calculateFadeMultiplier(fadeDurationMs: Long = 3000L): Float {
        if (isSleepTriggered) return 0.0f
        if (!isActive || activeMode.durationMinutes == 0) return 1.0f
        return if (remainingTimeMs <= fadeDurationMs) {
            (remainingTimeMs.toFloat() / fadeDurationMs.toFloat()).coerceIn(0.0f, 1.0f)
        } else {
            1.0f
        }
    }

    fun resetSleepTrigger() {
        isSleepTriggered = false
    }
}
