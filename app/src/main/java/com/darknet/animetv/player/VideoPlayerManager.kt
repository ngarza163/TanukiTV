package com.darknet.animetv.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.darknet.animetv.data.model.SubtitleTrack
import com.darknet.animetv.data.model.VideoSource
import com.darknet.animetv.data.model.WatchProgress

/**
 * Pure state & logic controller for the Android TV Video Player HUD, Seek, Track Selection,
 * and Auto-Resume calculations, plus Media3 ExoPlayer factory methods.
 */
object VideoPlayerManager {

    const val DEFAULT_AUTO_HIDE_MS = 5_000L
    const val DEFAULT_SEEK_STEP_MS = 10_000L
    const val PROGRESS_SAVE_INTERVAL_MS = 5_000L
    const val MIN_RESUME_POSITION_MS = 10_000L
    const val COMPLETED_THRESHOLD = 0.90f

    val AVAILABLE_QUALITIES = listOf("Auto", "1080p", "720p", "480p", "360p")
    val AVAILABLE_BUFFER_MODES = listOf(
        "Balanced (50s)",
        "Stable TV Wi-Fi (120s)",
        "Aggressive (180s)",
        "Low Latency (20s)"
    )
    val AVAILABLE_BITRATE_CAPS = listOf(
        "Unlimited",
        "20 Mbps (4K UHD)",
        "10 Mbps (1080p HQ)",
        "5 Mbps (720p)",
        "2.5 Mbps (Saver)"
    )

    /**
     * Constructs a Media3 [MediaItem] from a [VideoSource] and optional active [SubtitleTrack],
     * configuring appropriate MIME types for HLS (.m3u8) vs MP4 streams.
     */
    fun buildMediaItem(
        source: VideoSource,
        activeSubtitle: SubtitleTrack? = null
    ): MediaItem {
        val mimeType = if (source.isHls || source.url.contains(".m3u8", ignoreCase = true)) {
            MimeTypes.APPLICATION_M3U8
        } else {
            MimeTypes.VIDEO_MP4
        }

        val builder = MediaItem.Builder()
            .setUri(source.url)
            .setMimeType(mimeType)

        if (activeSubtitle != null && activeSubtitle.url.isNotBlank() && !activeSubtitle.url.contains(".local/")) {
            val subMimeType = when {
                activeSubtitle.url.endsWith(".srt", ignoreCase = true) -> MimeTypes.APPLICATION_SUBRIP
                else -> MimeTypes.TEXT_VTT
            }
            val subConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(activeSubtitle.url))
                .setMimeType(subMimeType)
                .setLanguage(activeSubtitle.language)
                .setLabel(activeSubtitle.label)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            builder.setSubtitleConfigurations(listOf(subConfig))
        }

        return builder.build()
    }

    /**
     * Creates a custom [DefaultLoadControl] tuned for Android TV streaming and living room Wi-Fi conditions.
     */
    @OptIn(UnstableApi::class)
    fun createLoadControl(bufferMode: String = "Balanced (50s)"): DefaultLoadControl {
        val (minBufferMs, maxBufferMs, bufferForPlaybackMs, rebufferMs) = when {
            bufferMode.contains("120s", ignoreCase = true) -> {
                // Stable TV Wi-Fi: 60s minimum, 120s maximum buffer ahead
                listOf(60_000, 120_000, 3_000, 6_000)
            }
            bufferMode.contains("180s", ignoreCase = true) -> {
                // Aggressive living room cache: 90s minimum, 180s maximum buffer ahead
                listOf(90_000, 180_000, 4_000, 8_000)
            }
            bufferMode.contains("20s", ignoreCase = true) -> {
                // Low Latency / Hotspot: 10s minimum, 20s maximum
                listOf(10_000, 20_000, 1_500, 3_000)
            }
            else -> {
                // Balanced default: 25s minimum, 50s maximum
                listOf(25_000, 50_000, 2_500, 5_000)
            }
        }

        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                minBufferMs,
                maxBufferMs,
                bufferForPlaybackMs,
                rebufferMs
            )
            .setBackBuffer(30_000, true)
            .build()
    }

    /**
     * Converts a bitrate cap string (e.g. "10 Mbps (1080p HQ)") into bits per second.
     */
    fun parseBitrateCap(cap: String): Int {
        return when {
            cap.contains("20 Mbps", ignoreCase = true) -> 20_000_000
            cap.contains("10 Mbps", ignoreCase = true) -> 10_000_000
            cap.contains("2.5 Mbps", ignoreCase = true) -> 2_500_000
            cap.contains("5 Mbps", ignoreCase = true) -> 5_000_000
            else -> Int.MAX_VALUE
        }
    }

    /**
     * Applies maximum bitrate constraint to ExoPlayer's track selection parameters.
     */
    fun applyBitrateCap(player: ExoPlayer, maxBitrateCap: String) {
        val maxBitrate = parseBitrateCap(maxBitrateCap)
        val paramsBuilder = player.trackSelectionParameters.buildUpon()
        paramsBuilder.setMaxVideoBitrate(maxBitrate)
        player.trackSelectionParameters = paramsBuilder.build()
    }

    /**
     * Builds an AndroidX Media3 [ExoPlayer] instance configured with custom anti-hotlinking
     * HTTP headers (`Referer`, `Origin`, `User-Agent`) from the [VideoSource] and
     * customized ABR LoadControl buffer sizing.
     */
    @OptIn(UnstableApi::class)
    fun createExoPlayer(
        context: Context,
        source: VideoSource,
        bufferMode: String = "Balanced (50s)"
    ): ExoPlayer {
        val defaultHeaders = mutableMapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 AnimeTV/2.0"
        )
        defaultHeaders.putAll(source.headers)

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(defaultHeaders["User-Agent"])
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
            .setDefaultRequestProperties(defaultHeaders)

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpDataSourceFactory)

        val loadControl = createLoadControl(bufferMode)

        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build()
    }

    /**
     * Prepares and loads a [VideoSource] into an existing [ExoPlayer] using per-source HTTP headers.
     */
    @OptIn(UnstableApi::class)
    fun prepareMediaSource(
        context: Context,
        player: ExoPlayer,
        source: VideoSource,
        activeSubtitle: SubtitleTrack? = null
    ) {
        val defaultHeaders = mutableMapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 AnimeTV/2.0"
        )
        defaultHeaders.putAll(source.headers)

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(defaultHeaders["User-Agent"])
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
            .setDefaultRequestProperties(defaultHeaders)

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpDataSourceFactory)

        val mediaItem = buildMediaItem(source, activeSubtitle)
        player.setMediaSource(mediaSourceFactory.createMediaSource(mediaItem))
        player.prepare()
    }

    /**
     * Applies video resolution constraints to [TrackSelectionParameters] based on quality label.
     */
    fun applyQualityTrackSelection(
        player: ExoPlayer,
        quality: String
    ) {
        val paramsBuilder = player.trackSelectionParameters.buildUpon()
        when (quality) {
            "1080p" -> paramsBuilder.setMaxVideoSize(1920, 1080).setMinVideoSize(1280, 720)
            "720p" -> paramsBuilder.setMaxVideoSize(1280, 720).setMinVideoSize(854, 480)
            "480p" -> paramsBuilder.setMaxVideoSize(854, 480).setMinVideoSize(640, 360)
            "360p" -> paramsBuilder.setMaxVideoSize(640, 360).setMinVideoSize(0, 0)
            else -> paramsBuilder.clearVideoSizeConstraints()
        }
        player.trackSelectionParameters = paramsBuilder.build()
    }

    /**
     * Computes target seek position clamped within `[0L, durationMs]`.
     */
    fun computeSeekPosition(
        currentPositionMs: Long,
        deltaMs: Long,
        durationMs: Long
    ): Long {
        val safeCurrent = currentPositionMs.coerceAtLeast(0L)
        val rawTarget = safeCurrent + deltaMs
        return if (durationMs > 0L) {
            rawTarget.coerceIn(0L, durationMs)
        } else {
            rawTarget.coerceAtLeast(0L)
        }
    }

    /**
     * Determines whether an episode should auto-resume from saved [WatchProgress].
     * Returns the target resume position in milliseconds (0L if starting from beginning).
     */
    fun resolveAutoResumePosition(
        savedProgress: WatchProgress?,
        targetEpisodeNumber: Int
    ): Long {
        if (savedProgress == null) return 0L
        if (savedProgress.episodeNumber != targetEpisodeNumber) return 0L
        if (savedProgress.progressPercentage >= COMPLETED_THRESHOLD) return 0L
        if (savedProgress.positionMs < MIN_RESUME_POSITION_MS) return 0L
        return savedProgress.positionMs
    }

    /**
     * Determines if enough time has elapsed since [lastSaveTimestampMs] to persist progress.
     */
    fun shouldPersistProgress(
        currentTimestampMs: Long,
        lastSaveTimestampMs: Long,
        intervalMs: Long = PROGRESS_SAVE_INTERVAL_MS
    ): Boolean {
        return (currentTimestampMs - lastSaveTimestampMs) >= intervalMs
    }

    /**
     * Formats milliseconds into `MM:SS` or `HH:MM:SS` for the TV player timeline.
     */
    fun formatDuration(ms: Long): String {
        val safeMs = ms.coerceAtLeast(0L)
        val totalSeconds = safeMs / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
}
