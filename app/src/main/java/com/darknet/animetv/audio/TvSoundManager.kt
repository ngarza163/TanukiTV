package com.darknet.animetv.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Zero-asset procedural PCM wave synthesizer for Android TV 10-foot UI sound effects.
 * Synthesizes subtle, pleasant living-room UI audio cues in memory at 44.1kHz 16-bit mono:
 * - Focus Tick: soft high-tech micro-click when D-pad focus moves between cards/buttons
 * - Action Select: warm harmonic chord tap on DPAD_CENTER / ENTER
 * - Tab Switch: gentle frequency sweep when switching top-bar navigation tabs
 * - Back / Dismiss: soft descending two-note tone
 * - Watchlist Add: bright ascending triad confirmation chime
 */
object TvSoundManager {

    const val SAMPLE_RATE = 44100
    private val enabled = AtomicBoolean(true)
    private val lastFocusTickMs = AtomicLong(0L)
    private const val FOCUS_TICK_COOLDOWN_MS = 35L

    private val soundExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "TanukiTvSoundSynth").apply { isDaemon = true }
    }

    // Pre-synthesized 16-bit PCM buffers for <5ms latency playback
    val focusTickPcm: ShortArray by lazy { synthesizeFocusTick() }
    val actionSelectPcm: ShortArray by lazy { synthesizeActionSelect() }
    val tabSwitchPcm: ShortArray by lazy { synthesizeTabSwitch() }
    val backDismissPcm: ShortArray by lazy { synthesizeBackDismiss() }
    val watchlistAddPcm: ShortArray by lazy { synthesizeWatchlistAdd() }

    fun init(context: Context? = null) {
        // Warm up lazy PCM buffers on background thread
        soundExecutor.execute {
            focusTickPcm
            actionSelectPcm
            tabSwitchPcm
            backDismissPcm
            watchlistAddPcm
        }
    }

    fun setEnabled(isEnabled: Boolean) {
        enabled.set(isEnabled)
    }

    fun isEnabled(): Boolean = enabled.get()

    fun playFocusTick() {
        if (!enabled.get()) return
        val now = System.currentTimeMillis()
        val prev = lastFocusTickMs.get()
        if (now - prev < FOCUS_TICK_COOLDOWN_MS) return
        lastFocusTickMs.set(now)
        playPcmBufferAsync(focusTickPcm, volume = 0.22f)
    }

    fun playActionSelect() {
        if (!enabled.get()) return
        playPcmBufferAsync(actionSelectPcm, volume = 0.34f)
    }

    fun playTabSwitch() {
        if (!enabled.get()) return
        playPcmBufferAsync(tabSwitchPcm, volume = 0.28f)
    }

    fun playBackDismiss() {
        if (!enabled.get()) return
        playPcmBufferAsync(backDismissPcm, volume = 0.28f)
    }

    fun playWatchlistAdd() {
        if (!enabled.get()) return
        playPcmBufferAsync(watchlistAddPcm, volume = 0.36f)
    }

    fun release() {
        // Static buffers are lightweight in-memory arrays; nothing to leak
    }

    private fun playPcmBufferAsync(pcm: ShortArray, volume: Float) {
        if (pcm.isEmpty()) return
        try {
            soundExecutor.execute {
                playPcmBlocking(pcm, volume)
            }
        } catch (_: Throwable) {
            // Ignore in headless unit test environments
        }
    }

    private fun playPcmBlocking(pcm: ShortArray, volume: Float) {
        var track: AudioTrack? = null
        try {
            val byteSize = pcm.size * 2
            val minBuffer = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSizeInBytes = maxOf(byteSize, minBuffer.coerceAtLeast(byteSize))

            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSizeInBytes)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(pcm, 0, pcm.size)
            track.setVolume(volume.coerceIn(0f, 1f))
            track.play()

            val durationMs = ((pcm.size * 1000L) / SAMPLE_RATE).coerceAtLeast(25L)
            Thread.sleep(durationMs + 15L)
        } catch (_: Throwable) {
            // Gracefully ignore in JVM unit tests or devices without audio output
        } finally {
            try {
                track?.stop()
            } catch (_: Throwable) {}
            try {
                track?.release()
            } catch (_: Throwable) {}
        }
    }

    /**
     * 18ms soft crystal/wooden micro-tick (980Hz -> 1360Hz with fast exponential decay).
     */
    internal fun synthesizeFocusTick(): ShortArray {
        val durationSec = 0.018
        val numSamples = (SAMPLE_RATE * durationSec).toInt()
        val samples = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = i.toDouble() / numSamples
            // Quick attack (first 8%) + smooth exponential decay
            val attack = (progress / 0.08).coerceAtMost(1.0)
            val envelope = attack * exp(-t * 190.0)
            val freq = 980.0 + (380.0 * progress)
            val wave = sin(2.0 * PI * freq * t) + 0.35 * sin(2.0 * PI * (freq * 2.0) * t)
            val normalized = (wave / 1.35) * envelope * 0.65
            samples[i] = (normalized * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return samples
    }

    /**
     * 55ms warm harmonic chord tap (C5 523.25Hz + E5 659.25Hz + G5 783.99Hz).
     */
    internal fun synthesizeActionSelect(): ShortArray {
        val durationSec = 0.055
        val numSamples = (SAMPLE_RATE * durationSec).toInt()
        val samples = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = i.toDouble() / numSamples
            val attack = (progress / 0.06).coerceAtMost(1.0)
            val envelope = attack * exp(-t * 62.0)
            val c5 = sin(2.0 * PI * 523.25 * t)
            val e5 = 0.7 * sin(2.0 * PI * 659.25 * t)
            val g5 = 0.55 * sin(2.0 * PI * 783.99 * t)
            val normalized = ((c5 + e5 + g5) / 2.25) * envelope * 0.8
            samples[i] = (normalized * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return samples
    }

    /**
     * 65ms gentle upward frequency sweep (440Hz -> 660Hz) for top-bar tab navigation.
     */
    internal fun synthesizeTabSwitch(): ShortArray {
        val durationSec = 0.065
        val numSamples = (SAMPLE_RATE * durationSec).toInt()
        val samples = ShortArray(numSamples)
        var phase = 0.0
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = i.toDouble() / numSamples
            val attack = (progress / 0.1).coerceAtMost(1.0)
            val envelope = attack * exp(-t * 48.0)
            val instFreq = 440.0 + (220.0 * progress)
            phase += (2.0 * PI * instFreq) / SAMPLE_RATE
            val wave = sin(phase) + 0.3 * sin(phase * 1.5)
            val normalized = (wave / 1.3) * envelope * 0.75
            samples[i] = (normalized * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return samples
    }

    /**
     * 55ms soft descending tone (587.33Hz D5 -> 392.00Hz G4) for Back / Dismiss.
     */
    internal fun synthesizeBackDismiss(): ShortArray {
        val durationSec = 0.055
        val numSamples = (SAMPLE_RATE * durationSec).toInt()
        val samples = ShortArray(numSamples)
        var phase = 0.0
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = i.toDouble() / numSamples
            val attack = (progress / 0.08).coerceAtMost(1.0)
            val envelope = attack * exp(-t * 55.0)
            val instFreq = 587.33 - (195.33 * progress)
            phase += (2.0 * PI * instFreq) / SAMPLE_RATE
            val normalized = sin(phase) * envelope * 0.72
            samples[i] = (normalized * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return samples
    }

    /**
     * 95ms bright ascending triad arpeggio chime (C5 -> E5 -> C6) for Watchlist add.
     */
    internal fun synthesizeWatchlistAdd(): ShortArray {
        val durationSec = 0.095
        val numSamples = (SAMPLE_RATE * durationSec).toInt()
        val samples = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = i.toDouble() / numSamples
            val freq = when {
                progress < 0.33 -> 523.25
                progress < 0.66 -> 659.25
                else -> 1046.50
            }
            val localProgress = (progress % 0.333) / 0.333
            val attack = (localProgress / 0.12).coerceAtMost(1.0)
            val envelope = attack * exp(-localProgress * 2.2)
            val wave = sin(2.0 * PI * freq * t) + 0.4 * sin(2.0 * PI * (freq * 2.0) * t)
            val normalized = (wave / 1.4) * envelope * 0.8
            samples[i] = (normalized * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return samples
    }
}
