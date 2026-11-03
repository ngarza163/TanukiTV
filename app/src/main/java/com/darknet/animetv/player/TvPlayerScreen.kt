package com.darknet.animetv.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.darknet.animetv.data.model.Anime
import com.darknet.animetv.data.model.Episode
import com.darknet.animetv.data.model.SubtitleTrack
import com.darknet.animetv.data.model.UserSettings
import com.darknet.animetv.data.model.VideoSource
import com.darknet.animetv.data.model.WatchProgress
import com.darknet.animetv.data.repository.AniListSyncManager
import com.darknet.animetv.data.repository.AnimeRepository
import com.darknet.animetv.data.storage.AppStorage
import com.darknet.animetv.player.aniskip.AniSkipClient
import com.darknet.animetv.player.aniskip.SkipInterval
import com.darknet.animetv.player.aniskip.SkipType
import com.darknet.animetv.ui.components.TvTanukiLoadingIndicator
import com.darknet.animetv.ui.components.tvFocusableItem
import com.darknet.animetv.ui.theme.TvAccentPurple
import com.darknet.animetv.ui.theme.TvGoldStar
import com.darknet.animetv.ui.theme.TvPrimaryRed
import com.darknet.animetv.ui.theme.TvSuccessGreen
import com.darknet.animetv.ui.theme.TvTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Full-screen Android TV Media3 ExoPlayer video player with D-pad remote HUD,
 * ±10s fast seek pills, quality/subtitle/server switching, multi-source failover,
 * and automatic watch progress persistence & resumption.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvPlayerScreen(
    anime: Anime,
    episode: Episode,
    allEpisodes: List<Episode>,
    repository: AnimeRepository,
    storage: AppStorage,
    initialSeekMs: Long = 0L,
    onSelectEpisode: (Episode) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val rootFocusRequester = remember { FocusRequester() }
    val playNowFocusRequester = remember { FocusRequester() }

    val userSettings = remember { storage.getUserSettings() }

    // Stream & Track States
    var isLoadingStreams by remember(episode.id) { mutableStateOf(true) }
    var availableSources by remember(episode.id) { mutableStateOf<List<VideoSource>>(emptyList()) }
    var activeSourceIndex by remember(episode.id) { mutableIntStateOf(0) }
    var streamError by remember(episode.id) { mutableStateOf<String?>(null) }
    var streamRetryTick by remember(episode.id) { mutableIntStateOf(0) }
    var selectedQuality by remember { mutableStateOf(userSettings.defaultQuality) }
    var audioMode by remember(episode.id, episode.audioTrack) {
        mutableStateOf(
            if (episode.audioTrack.equals("DUB", ignoreCase = true) || episode.audioTrack.equals("SUB", ignoreCase = true)) {
                episode.audioTrack.uppercase()
            } else {
                userSettings.preferredAudio
            }
        )
    }
    var activeSubtitle by remember(episode.id) { mutableStateOf<SubtitleTrack?>(null) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    var resizeModeName by remember { mutableStateOf("Fit") }
    var activeAudioPreset by remember { mutableStateOf(userSettings.audioPreset) }
    val audioEffectsManager = remember { com.darknet.animetv.player.audio.AudioEffectsManager() }
    val sleepTimerManager = remember { SleepTimerManager() }
    var subtitleOffsetMs by remember { mutableStateOf(userSettings.subtitleOffsetMs) }
    val aniSkipClient = remember { AniSkipClient() }
    var skipIntervals by remember(episode.id) { mutableStateOf<List<SkipInterval>>(emptyList()) }
    var hasAutoSkippedOp by remember(episode.id) { mutableStateOf(false) }
    var nextEpisodeCountdownSeconds by remember(episode.id) { mutableIntStateOf(10) }
    var isNextEpisodeCardDismissed by remember(episode.id) { mutableStateOf(false) }
    var isNextEpisodeCountdownActive by remember(episode.id) { mutableStateOf(false) }
    var consecutiveBufferSeconds by remember { mutableIntStateOf(0) }
    var assCues by remember(activeSubtitle) { mutableStateOf<List<com.darknet.animetv.player.subtitles.AssCue>>(emptyList()) }

    // Fetch and parse .ass subtitles if activeSubtitle is an ASS track
    LaunchedEffect(activeSubtitle) {
        val sub = activeSubtitle ?: return@LaunchedEffect
        if (sub.url.endsWith(".ass", ignoreCase = true) || sub.label.contains("ASS", ignoreCase = true)) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val client = okhttp3.OkHttpClient()
                    val req = okhttp3.Request.Builder().url(sub.url).build()
                    client.newCall(req).execute().use { resp ->
                        val body = resp.body?.string()
                        if (!body.isNullOrBlank()) {
                            assCues = com.darknet.animetv.player.subtitles.AssSubtitleParser.parse(body)
                        }
                    }
                } catch (_: Exception) {}
            }
        } else {
            assCues = emptyList()
        }
    }

    // Playback & HUD States
    var isHudVisible by remember { mutableStateOf(true) }
    var activeDialog by remember { mutableStateOf(PlayerDialogType.NONE) }
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var currentPositionMs by remember(episode.id) { mutableLongStateOf(0L) }
    var bufferedPositionMs by remember(episode.id) { mutableLongStateOf(0L) }
    var durationMs by remember(episode.id) { mutableLongStateOf(0L) }
    var hasAutoResumed by remember(episode.id) { mutableStateOf(false) }
    var hasScrobbledCurrentEpisode by remember(episode.id) { mutableStateOf(false) }

    // Visual Indicators
    var seekPillText by remember { mutableStateOf<String?>(null) }
    var seekPillTick by remember { mutableIntStateOf(0) }
    var resumeBannerText by remember(episode.id) { mutableStateOf<String?>(null) }
    var failoverBannerText by remember { mutableStateOf<String?>(null) }
    var interactionTick by remember { mutableIntStateOf(0) }
    var lastPersistTimestampMs by remember { mutableLongStateOf(0L) }

    // Resolve episode list navigation
    val currentEpIdx = remember(episode.id, allEpisodes) {
        allEpisodes.indexOfFirst { it.id == episode.id || it.number == episode.number }
    }
    val prevEpisode = remember(currentEpIdx, allEpisodes) {
        if (currentEpIdx > 0) allEpisodes.getOrNull(currentEpIdx - 1) else null
    }
    val nextEpisode = remember(currentEpIdx, allEpisodes, episode) {
        if (currentEpIdx >= 0 && currentEpIdx < allEpisodes.lastIndex) {
            allEpisodes.getOrNull(currentEpIdx + 1)
        } else {
            val nextNum = episode.number + 1
            Episode(
                id = "${anime.id}-ep-$nextNum",
                animeId = anime.id,
                number = nextNum,
                title = "Episode $nextNum",
                thumbnail = anime.backdropUrl.ifBlank { anime.posterUrl }
            )
        }
    }

    // 1. Resolve Video Streams with Failover
    LaunchedEffect(anime.id, episode.id, audioMode, streamRetryTick) {
        isLoadingStreams = true
        streamError = null
        try {
            val preferredSource = anime.sourceId.takeIf { it.isNotBlank() && it != "default" }
            val resolved = repository.getEpisodeStreams(
                animeTitle = anime.title,
                episodeNumber = episode.number,
                preferredSourceId = preferredSource,
                preferredAudio = audioMode
            )
            availableSources = resolved
            activeSourceIndex = 0
            if (resolved.isEmpty()) {
                streamError = if (audioMode == "DUB") {
                    "No English dub stream found for Episode ${episode.number}."
                } else {
                    "No stream found for Episode ${episode.number}."
                }
            }
            val initialSubtitles = resolved.firstOrNull()?.subtitles.orEmpty()
                .filter { !it.url.contains(".local/") }
            if (activeSubtitle == null && initialSubtitles.isNotEmpty() && audioMode != "DUB") {
                activeSubtitle = initialSubtitles.firstOrNull { it.language.startsWith("en", ignoreCase = true) }
                    ?: initialSubtitles.first()
            }
        } catch (e: Exception) {
            availableSources = emptyList()
            streamError = "Couldn't reach streaming servers. Check your connection and try again."
        } finally {
            isLoadingStreams = false
        }
    }

    val activeSource = availableSources.getOrNull(activeSourceIndex)
    val availableSubtitles = remember(activeSource, availableSources) {
        val fromActive = activeSource?.subtitles.orEmpty()
        if (fromActive.isNotEmpty()) fromActive
        else availableSources.flatMap { it.subtitles }.distinctBy { it.language + it.label }
    }

    // Helper to persist current progress to SQLite
    fun persistCurrentProgress(positionToSave: Long = currentPositionMs, durationToSave: Long = durationMs) {
        val effectiveDuration = if (durationToSave > 0L) durationToSave else 1_440_000L
        if (positionToSave > 0L) {
            storage.saveWatchProgress(
                WatchProgress(
                    animeId = anime.id,
                    animeTitle = anime.title,
                    posterUrl = anime.posterUrl.ifBlank { anime.backdropUrl },
                    episodeId = episode.id,
                    episodeNumber = episode.number,
                    episodeTitle = episode.title,
                    positionMs = positionToSave,
                    durationMs = effectiveDuration,
                    lastWatchedTimestamp = System.currentTimeMillis()
                )
            )
            lastPersistTimestampMs = System.currentTimeMillis()

            // Update Android TV Home Launcher "Watch Next" program row
            try {
                if (effectiveDuration > 0 && positionToSave.toFloat() / effectiveDuration > 0.92f) {
                    com.darknet.animetv.channel.TvChannelManager.removeWatchNextProgram(context, anime.id)
                } else {
                    com.darknet.animetv.channel.TvChannelManager.updateWatchNextProgram(
                        context = context,
                        anime = anime,
                        episode = episode,
                        positionMs = positionToSave,
                        durationMs = effectiveDuration
                    )
                }
            } catch (_: Exception) {}

            // Auto-Scrobble to AniList / MyAnimeList when >= 80% watched
            if (userSettings.autoScrobble && !hasScrobbledCurrentEpisode && effectiveDuration > 0 && positionToSave.toFloat() / effectiveDuration >= 0.80f) {
                hasScrobbledCurrentEpisode = true
                coroutineScope.launch {
                    try {
                        val scrobbled = com.darknet.animetv.data.repository.AniListSyncManager.scrobbleProgress(
                            mediaIdStr = anime.id,
                            episodeNumber = episode.number,
                            totalEpisodes = anime.totalEpisodes,
                            storage = storage,
                            animeTitle = anime.title
                        )
                        if (scrobbled) {
                            seekPillText = "\u2714 Scrobbled Ep ${episode.number} to AniList"
                            seekPillTick++
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    // 2. Create & Manage ExoPlayer Instance with ABR Wi-Fi Buffer Tuning
    val exoPlayer = remember(context) {
        val initialSource = VideoSource(
            serverName = "Default",
            url = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
            quality = "1080p",
            isHls = true
        )
        VideoPlayerManager.createExoPlayer(context, initialSource, userSettings.bufferMode).apply {
            playWhenReady = true
            VideoPlayerManager.applyBitrateCap(this, userSettings.maxBitrateCap)
        }
    }

    // Sleep Timer countdown ticker & audio fade-out loop
    LaunchedEffect(sleepTimerManager.isActive, isPlaying) {
        while (sleepTimerManager.isActive && isPlaying) {
            kotlinx.coroutines.delay(1000L)
            val triggered = sleepTimerManager.tick(1000L)
            val fade = sleepTimerManager.calculateFadeMultiplier(3000L)
            exoPlayer.volume = fade
            if (triggered) {
                exoPlayer.pause()
                exoPlayer.volume = 1.0f
                resumeBannerText = "🌙 Sleep Timer: Playback Paused"
                isHudVisible = true
            }
        }
    }

    // 2a. AniSkip v2 Interval Fetching
    LaunchedEffect(episode.id, durationMs) {
        val malId = anime.id.toIntOrNull() ?: 0
        val effectiveDuration = if (durationMs > 0L) durationMs else 1440_000L
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val intervals = aniSkipClient.getSkipTimes(
                malId = malId,
                episodeNumber = episode.number,
                episodeDurationMs = effectiveDuration
            )
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                skipIntervals = intervals
            }
        }
    }

    val activeSkipInterval = remember(currentPositionMs, skipIntervals) {
        skipIntervals.firstOrNull { interval ->
            currentPositionMs >= interval.startMs && currentPositionMs < interval.endMs
        }
    }

    // Auto-Skip Opening if enabled in settings
    LaunchedEffect(activeSkipInterval, userSettings.autoSkipIntro) {
        val interval = activeSkipInterval ?: return@LaunchedEffect
        if (interval.isOpening && userSettings.autoSkipIntro && !hasAutoSkippedOp) {
            hasAutoSkippedOp = true
            exoPlayer.seekTo(interval.endMs)
            currentPositionMs = interval.endMs
            seekPillText = "⏩ Auto-Skipped Opening (+${interval.durationMs / 1000}s)"
            seekPillTick++
        }
    }

    // 2b. Mid-Stream Stall Auto-Recovery: if buffering stalls for >= 6 seconds, hot-swap source
    LaunchedEffect(isBuffering, isPlaying, availableSources.size) {
        while (true) {
            delay(1000L)
            if (isBuffering && isPlaying && availableSources.size > 1) {
                consecutiveBufferSeconds++
                if (consecutiveBufferSeconds >= 6) {
                    consecutiveBufferSeconds = 0
                    val nextIdx = (activeSourceIndex + 1) % availableSources.size
                    val nextServer = availableSources[nextIdx].serverName
                    failoverBannerText = "⚠️ Stream stalled • Hot-swapping to $nextServer..."
                    activeSourceIndex = nextIdx
                }
            } else {
                consecutiveBufferSeconds = 0
            }
        }
    }

    // 2c. Next Episode Auto-Play Countdown Trigger
    LaunchedEffect(currentPositionMs, durationMs, activeSkipInterval, isNextEpisodeCardDismissed) {
        if (nextEpisode != null && !isNextEpisodeCardDismissed && !isNextEpisodeCountdownActive) {
            val isCredits = (activeSkipInterval?.isEnding == true) ||
                (durationMs > 120_000L && (durationMs - currentPositionMs) in 1L..60_000L)
            if (isCredits) {
                isNextEpisodeCountdownActive = true
                nextEpisodeCountdownSeconds = 10
            }
        }
    }

    // Next Episode Countdown Ticker
    LaunchedEffect(isNextEpisodeCountdownActive, isPlaying) {
        if (isNextEpisodeCountdownActive) {
            while (nextEpisodeCountdownSeconds > 0 && isNextEpisodeCountdownActive) {
                delay(1000L)
                if (isPlaying) {
                    nextEpisodeCountdownSeconds--
                }
            }
            if (nextEpisodeCountdownSeconds == 0 && isNextEpisodeCountdownActive && !isNextEpisodeCardDismissed) {
                isNextEpisodeCountdownActive = false
                nextEpisode?.let { next ->
                    persistCurrentProgress()
                    onSelectEpisode(next)
                }
            }
        }
    }

    // Attach Player Listener for state changes & automatic server failover
    DisposableEffect(exoPlayer, availableSources, episode.id) {
        val listener = object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                audioEffectsManager.attachToSession(audioSessionId, activeAudioPreset)
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (!playing) {
                    persistCurrentProgress(exoPlayer.currentPosition, exoPlayer.duration)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                    if (!hasAutoResumed) {
                        hasAutoResumed = true
                        val resumePos = if (initialSeekMs > 0L) {
                            initialSeekMs
                        } else {
                            val saved = storage.getWatchProgress(anime.id)
                            VideoPlayerManager.resolveAutoResumePosition(saved, episode.number)
                        }
                        if (resumePos > 0L) {
                            exoPlayer.seekTo(resumePos)
                            currentPositionMs = resumePos
                            resumeBannerText = "Resumed from ${VideoPlayerManager.formatDuration(resumePos)}"
                        }
                    }
                } else if (playbackState == Player.STATE_ENDED) {
                    persistCurrentProgress(durationMs, durationMs)
                    if (userSettings.autoScrobble) {
                        coroutineScope.launch {
                            val scrobbled = AniListSyncManager.scrobbleProgress(
                                mediaIdStr = anime.id,
                                episodeNumber = episode.number,
                                totalEpisodes = anime.totalEpisodes,
                                storage = storage
                            )
                            if (scrobbled) {
                                seekPillText = "\u2714 Scrobbled EP ${episode.number} to AniList"
                                seekPillTick++
                            }
                        }
                    }
                    val sleepTriggered = sleepTimerManager.onEpisodeFinished()
                    if (sleepTriggered) {
                        exoPlayer.pause()
                        resumeBannerText = "🌙 Sleep Timer: Playback Paused (End of Episode)"
                        isHudVisible = true
                    } else if (userSettings.autoPlayNextEpisode && nextEpisode != null) {
                        onSelectEpisode(nextEpisode)
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (activeSubtitle != null) {
                    // Clear failing external subtitle track first before failing over video server
                    activeSubtitle = null
                    return
                }
                // Automatic stream server failover if another source is available
                if (activeSourceIndex + 1 < availableSources.size) {
                    val nextIdx = activeSourceIndex + 1
                    val nextServer = availableSources[nextIdx].serverName
                    failoverBannerText = "Stream error • Failing over to $nextServer..."
                    activeSourceIndex = nextIdx
                } else {
                    isBuffering = false
                    streamError = "All servers failed to play Episode ${episode.number} (${error.errorCodeName})."
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // Load MediaItem whenever activeSource or activeSubtitle changes
    LaunchedEffect(activeSource, activeSubtitle) {
        val source = activeSource ?: return@LaunchedEffect
        val preservedPosition = currentPositionMs
        VideoPlayerManager.prepareMediaSource(context, exoPlayer, source, activeSubtitle)
        if (preservedPosition > 0L && hasAutoResumed) {
            exoPlayer.seekTo(preservedPosition)
        }
        exoPlayer.playWhenReady = true
    }

    // Release ExoPlayer & persist progress when exiting TvPlayerScreen
    DisposableEffect(Unit) {
        onDispose {
            persistCurrentProgress(
                positionToSave = exoPlayer.currentPosition.coerceAtLeast(currentPositionMs),
                durationToSave = exoPlayer.duration.coerceAtLeast(durationMs)
            )
            audioEffectsManager.release()
            exoPlayer.release()
        }
    }

    // 3. Poll Playback Position & Save Progress Every 5 Seconds
    LaunchedEffect(exoPlayer, isPlaying) {
        while (true) {
            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
            val buf = exoPlayer.bufferedPosition.coerceAtLeast(pos)
            val dur = exoPlayer.duration.coerceAtLeast(0L)
            currentPositionMs = pos
            bufferedPositionMs = buf
            if (dur > 0L) durationMs = dur

            val now = System.currentTimeMillis()
            if (isPlaying && pos > 0L && VideoPlayerManager.shouldPersistProgress(now, lastPersistTimestampMs)) {
                persistCurrentProgress(pos, durationMs)
            }
            delay(500L)
        }
    }

    // 4. 5-Second Auto-Hide Timer for HUD
    LaunchedEffect(isHudVisible, interactionTick, isPlaying, activeDialog) {
        if (isHudVisible && isPlaying && activeDialog == PlayerDialogType.NONE) {
            delay(VideoPlayerManager.DEFAULT_AUTO_HIDE_MS)
            isHudVisible = false
        }
    }

    // Auto-clear Seek Pill after 1.2s
    LaunchedEffect(seekPillTick) {
        if (seekPillText != null) {
            delay(1200L)
            seekPillText = null
        }
    }

    // Auto-clear Resume Banner after 3.5s
    LaunchedEffect(resumeBannerText) {
        if (resumeBannerText != null) {
            delay(3500L)
            resumeBannerText = null
        }
    }

    // Auto-clear Failover Banner after 3.5s
    LaunchedEffect(failoverBannerText) {
        if (failoverBannerText != null) {
            delay(3500L)
            failoverBannerText = null
        }
    }

    // Seek Helper Functions
    fun performSeek(deltaMs: Long) {
        val stepMs = (userSettings.seekStepSeconds * 1000L).coerceAtLeast(5000L)
        val effectiveDelta = if (deltaMs >= 0) stepMs else -stepMs
        val target = VideoPlayerManager.computeSeekPosition(
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(currentPositionMs),
            deltaMs = effectiveDelta,
            durationMs = durationMs
        )
        exoPlayer.seekTo(target)
        currentPositionMs = target
        seekPillText = if (effectiveDelta >= 0) ">> +${userSettings.seekStepSeconds}s" else "<< -${userSettings.seekStepSeconds}s"
        seekPillTick++
        interactionTick++
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
            isPlaying = false
        } else {
            exoPlayer.play()
            isPlaying = true
        }
        interactionTick++
    }

    // Handle System Back Navigation
    BackHandler {
        when {
            activeDialog != PlayerDialogType.NONE -> {
                activeDialog = PlayerDialogType.NONE
            }
            isHudVisible -> {
                isHudVisible = false
            }
            else -> {
                persistCurrentProgress()
                onBack()
            }
        }
    }

    LaunchedEffect(isHudVisible, activeSkipInterval, isNextEpisodeCountdownActive, activeDialog) {
        if (!isHudVisible && activeSkipInterval == null && !isNextEpisodeCountdownActive && activeDialog == PlayerDialogType.NONE) {
            try {
                rootFocusRequester.requestFocus()
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(isNextEpisodeCountdownActive) {
        if (isNextEpisodeCountdownActive) {
            try {
                playNowFocusRequester.requestFocus()
            } catch (_: Exception) {
            }
        }
    }

    val currentResizeMode = when (resizeModeName) {
        "Zoom" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        "Stretch" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
        "4:3" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
        else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    interactionTick++
                    when (event.key) {
                        Key.Back, Key.Escape -> {
                            when {
                                activeDialog != PlayerDialogType.NONE -> {
                                    activeDialog = PlayerDialogType.NONE
                                    true
                                }
                                isHudVisible -> {
                                    isHudVisible = false
                                    true
                                }
                                else -> {
                                    persistCurrentProgress()
                                    onBack()
                                    true
                                }
                            }
                        }
                        Key.MediaPlayPause, Key.Spacebar -> {
                            togglePlayPause()
                            isHudVisible = true
                            true
                        }
                        Key.MediaPlay -> {
                            exoPlayer.play()
                            isPlaying = true
                            isHudVisible = true
                            true
                        }
                        Key.MediaPause -> {
                            exoPlayer.pause()
                            isPlaying = false
                            isHudVisible = true
                            true
                        }
                        Key.MediaFastForward -> {
                            performSeek(+VideoPlayerManager.DEFAULT_SEEK_STEP_MS)
                            true
                        }
                        Key.MediaRewind -> {
                            performSeek(-VideoPlayerManager.DEFAULT_SEEK_STEP_MS)
                            true
                        }
                        Key.DirectionLeft -> {
                            if (!isHudVisible) {
                                isHudVisible = true
                                performSeek(-VideoPlayerManager.DEFAULT_SEEK_STEP_MS)
                                true
                            } else {
                                false
                            }
                        }
                        Key.DirectionRight -> {
                            if (!isHudVisible) {
                                isHudVisible = true
                                performSeek(+VideoPlayerManager.DEFAULT_SEEK_STEP_MS)
                                true
                            } else {
                                false
                            }
                        }
                        Key.DirectionUp, Key.DirectionDown, Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            if (!isHudVisible) {
                                isHudVisible = true
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        // 1. Media3 ExoPlayer Video Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    keepScreenOn = true
                    resizeMode = currentResizeMode
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    applySubtitleStyling(this, userSettings)
                }
            },
            update = { playerView ->
                if (playerView.player !== exoPlayer) {
                    playerView.player = exoPlayer
                }
                playerView.resizeMode = currentResizeMode
                applySubtitleStyling(playerView, userSettings)
            },
            modifier = Modifier.fillMaxSize()
        )

        // Styled .ASS Fansub Overlay
        if (assCues.isNotEmpty()) {
            com.darknet.animetv.player.subtitles.AssSubtitleOverlay(
                cues = assCues,
                currentPositionMs = (currentPositionMs + subtitleOffsetMs).coerceAtLeast(0L),
                fontSizeMultiplier = when (userSettings.subtitleFontSize) {
                    "18sp" -> 0.8f
                    "32sp" -> 1.3f
                    "40sp" -> 1.6f
                    else -> 1.0f
                }
            )
        }

        // 2. Center Buffering / Stream Resolution Spinner
        if (streamError == null && (isLoadingStreams || isBuffering)) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xD908090E), RoundedCornerShape(20.dp))
                    .border(1.dp, TvAccentPurple.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 28.dp, vertical = 20.dp)
            ) {
                TvTanukiLoadingIndicator(
                    statusText = when {
                        isLoadingStreams && audioMode == "DUB" -> "Tuning English Dub Stream..."
                        isLoadingStreams -> "Tuning Subtitled Stream..."
                        else -> "Buffering ${anime.title}..."
                    },
                    subtitleText = "Episode ${episode.number} • $audioMode Audio",
                    isCompact = true
                )
            }
        }

        // 2b. Stream Unavailable Overlay (no demo footage substitution)
        streamError?.let { message ->
            val retryFocus = remember { FocusRequester() }
            LaunchedEffect(message) {
                try { retryFocus.requestFocus() } catch (_: Exception) {}
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(560.dp)
                    .background(Color(0xF0090A12), RoundedCornerShape(20.dp))
                    .border(1.5.dp, TvPrimaryRed.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                    .padding(28.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Stream unavailable", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text(message, color = TvTextSecondary, fontSize = 14.sp)
                    Text(
                        "${anime.title} • Episode ${episode.number} • $audioMode",
                        color = TvAccentPurple, fontSize = 12.sp, fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { streamError = null; streamRetryTick++ },
                            shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
                            colors = ButtonDefaults.colors(
                                containerColor = TvPrimaryRed, focusedContainerColor = Color.White,
                                contentColor = Color.White, focusedContentColor = Color.Black
                            ),
                            modifier = Modifier.focusRequester(retryFocus)
                        ) { Text("↻ Retry", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                        Button(
                            onClick = {
                                streamError = null
                                audioMode = if (audioMode == "DUB") "SUB" else "DUB"
                            },
                            shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
                            colors = ButtonDefaults.colors(
                                containerColor = Color(0x33FFFFFF), focusedContainerColor = Color.White,
                                contentColor = Color.White, focusedContentColor = Color.Black
                            )
                        ) { Text(if (audioMode == "DUB") "Try SUB" else "Try DUB", fontSize = 13.sp) }
                        Button(
                            onClick = { persistCurrentProgress(); onBack() },
                            shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
                            colors = ButtonDefaults.colors(
                                containerColor = Color(0x33FFFFFF), focusedContainerColor = Color.White,
                                contentColor = Color.White, focusedContentColor = Color.Black
                            )
                        ) { Text("Back", fontSize = 13.sp) }
                    }
                }
            }
        }

        // 3. Center ±10s Fast Seek Pill Indicator
        AnimatedVisibility(
            visible = seekPillText != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .background(Color(0xD90C0E18), RoundedCornerShape(28.dp))
                    .border(2.dp, TvAccentPurple, RoundedCornerShape(28.dp))
                    .padding(horizontal = 28.dp, vertical = 14.dp)
            ) {
                Text(
                    text = seekPillText ?: "",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // 4. Auto-Resume / Failover Notification Pill (Top Center)
        val notificationBanner = resumeBannerText ?: failoverBannerText
        AnimatedVisibility(
            visible = notificationBanner != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 96.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(Color(0xE6141622), RoundedCornerShape(20.dp))
                    .border(
                        1.5.dp,
                        if (resumeBannerText != null) TvSuccessGreen else TvGoldStar,
                        RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "●",
                        color = if (resumeBannerText != null) TvSuccessGreen else TvGoldStar,
                        fontSize = 14.sp
                    )
                    Text(
                        text = notificationBanner ?: "",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "(${anime.title} • EP ${episode.number})",
                        color = TvTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // 4a. Floating AniSkip Pill (Bottom-Right)
        AnimatedVisibility(
            visible = activeSkipInterval != null && !isHudVisible && !isNextEpisodeCountdownActive,
            enter = fadeIn() + slideInHorizontally { it },
            exit = fadeOut() + slideOutHorizontally { it },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 60.dp, end = 56.dp)
        ) {
            activeSkipInterval?.let { interval ->
                Button(
                    onClick = {
                        exoPlayer.seekTo(interval.endMs)
                        currentPositionMs = interval.endMs
                        seekPillText = "⏩ Skipped ${interval.type.name} (+${interval.durationMs / 1000}s)"
                        seekPillTick++
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = Color(0xE6141622),
                        focusedContainerColor = TvPrimaryRed,
                        contentColor = Color.White,
                        focusedContentColor = Color.White
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(24.dp)),
                    modifier = Modifier.tvFocusableItem()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "⏭ Skip ${if (interval.isOpening) "Opening" else if (interval.isEnding) "Ending" else "Recap"}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "(${interval.durationMs / 1000}s)",
                            color = TvTextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // 4b. Next Episode Auto-Play Countdown Card (Bottom-Right)
        AnimatedVisibility(
            visible = isNextEpisodeCountdownActive && !isNextEpisodeCardDismissed && nextEpisode != null,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 54.dp, end = 56.dp)
        ) {
            nextEpisode?.let { next ->
                Surface(
                    colors = SurfaceDefaults.colors(
                        containerColor = Color(0xF20F121C)
                    ),
                    shape = RoundedCornerShape(18.dp),
                    border = Border(
                        border = androidx.compose.foundation.BorderStroke(2.dp, TvAccentPurple)
                    ),
                    modifier = Modifier.width(380.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "NEXT EPISODE IN ${nextEpisodeCountdownSeconds}s",
                                color = TvAccentPurple,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Ep ${next.number}",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        }
                        Text(
                            text = next.title.ifBlank { "Episode ${next.number}" },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        LinearProgressIndicator(
                            progress = { (10 - nextEpisodeCountdownSeconds) / 10f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                            color = TvPrimaryRed,
                            trackColor = Color(0x33FFFFFF)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    isNextEpisodeCountdownActive = false
                                    persistCurrentProgress()
                                    onSelectEpisode(next)
                                },
                                colors = ButtonDefaults.colors(
                                    containerColor = TvPrimaryRed,
                                    focusedContainerColor = Color.White,
                                    contentColor = Color.White,
                                    focusedContentColor = Color.Black
                                ),
                                shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(playNowFocusRequester)
                                    .tvFocusableItem(shape = RoundedCornerShape(10.dp))
                            ) {
                                Text("▶ Play Now", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Button(
                                onClick = {
                                    isNextEpisodeCountdownActive = false
                                    isNextEpisodeCardDismissed = true
                                },
                                colors = ButtonDefaults.colors(
                                    containerColor = Color(0x33FFFFFF),
                                    focusedContainerColor = Color.White,
                                    contentColor = Color.White,
                                    focusedContentColor = Color.Black
                                ),
                                shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
                                modifier = Modifier
                                    .weight(0.8f)
                                    .tvFocusableItem(shape = RoundedCornerShape(10.dp))
                            ) {
                                Text("✕ Dismiss", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        // 5. TV Remote HUD Overlay
        AnimatedVisibility(
            visible = isHudVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            TvPlayerHud(
                animeTitle = anime.title,
                episodeNumber = episode.number,
                episodeTitle = episode.title,
                activeSource = activeSource,
                availableSources = availableSources,
                selectedQuality = selectedQuality,
                activeSubtitle = activeSubtitle,
                availableSubtitles = availableSubtitles,
                audioMode = audioMode,
                currentPositionMs = currentPositionMs,
                bufferedPositionMs = bufferedPositionMs,
                durationMs = durationMs,
                isPlaying = isPlaying,
                hasPrevEpisode = prevEpisode != null,
                hasNextEpisode = nextEpisode != null,
                activeDialog = activeDialog,
                playbackSpeed = playbackSpeed,
                resizeModeName = resizeModeName,
                audioPresetName = activeAudioPreset,
                introSkipSeconds = userSettings.introSkipSeconds,
                sleepTimerManager = sleepTimerManager,
                onSelectSleepMode = { mode ->
                    sleepTimerManager.setTimer(mode)
                    seekPillText = if (mode == SleepTimerManager.SleepTimerMode.OFF) {
                        "Sleep Timer: Off"
                    } else {
                        "Sleep Timer set: ${mode.label}"
                    }
                    seekPillTick++
                },
                subtitleOffsetMs = subtitleOffsetMs,
                onSelectSubtitleOffset = { offset ->
                    subtitleOffsetMs = offset
                    storage.saveUserSettings(userSettings.copy(subtitleOffsetMs = offset))
                    seekPillText = "Subtitle Sync: ${if (offset >= 0) "+${offset}ms" else "${offset}ms"}"
                    seekPillTick++
                },
                onPlayPauseToggle = { togglePlayPause() },
                onSeekRewind = { performSeek(-VideoPlayerManager.DEFAULT_SEEK_STEP_MS) },
                onSeekForward = { performSeek(+VideoPlayerManager.DEFAULT_SEEK_STEP_MS) },
                onSeekTo = { targetMs ->
                    val clamped = targetMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
                    exoPlayer.seekTo(clamped)
                    currentPositionMs = clamped
                    seekPillText = "Seek: ${VideoPlayerManager.formatDuration(clamped)}"
                    seekPillTick++
                    interactionTick++
                },
                onPrevEpisode = {
                    prevEpisode?.let { prev ->
                        persistCurrentProgress()
                        onSelectEpisode(prev)
                    }
                },
                onNextEpisode = {
                    nextEpisode?.let { next ->
                        persistCurrentProgress()
                        onSelectEpisode(next)
                    }
                },
                onOpenDialog = { dialog ->
                    activeDialog = dialog
                    interactionTick++
                },
                onCloseDialog = {
                    activeDialog = PlayerDialogType.NONE
                    interactionTick++
                },
                onSelectQuality = { quality ->
                    if (VideoPlayerManager.AVAILABLE_QUALITIES.contains(quality)) {
                        selectedQuality = quality
                        VideoPlayerManager.applyQualityTrackSelection(exoPlayer, quality)
                    }
                },
                onSelectSubtitle = { subtitle ->
                    activeSubtitle = subtitle
                },
                onToggleAudioMode = {
                    persistCurrentProgress()
                    audioMode = if (audioMode == "SUB") "DUB" else "SUB"
                },
                onSelectServer = { server ->
                    val idx = availableSources.indexOf(server)
                    if (idx >= 0) {
                        persistCurrentProgress()
                        activeSourceIndex = idx
                    }
                },
                onSelectSpeed = { speed ->
                    playbackSpeed = speed
                    exoPlayer.playbackParameters = androidx.media3.common.PlaybackParameters(speed)
                    seekPillText = "Speed: ${speed}x"
                    seekPillTick++
                },
                onSelectResizeMode = { mode ->
                    resizeModeName = mode
                    seekPillText = "Aspect: $mode"
                    seekPillTick++
                },
                onSelectAudioPreset = { preset ->
                    activeAudioPreset = preset
                    audioEffectsManager.applyPreset(preset)
                    seekPillText = "🔊 Audio: $preset"
                    seekPillTick++
                    storage.saveUserSettings(userSettings.copy(audioPreset = preset))
                },
                onSkipIntro = {
                    val targetMs = userSettings.introSkipSeconds * 1000L
                    if (targetMs > currentPositionMs) {
                        exoPlayer.seekTo(targetMs)
                        currentPositionMs = targetMs
                        seekPillText = "⏩ Skipped Intro (+${userSettings.introSkipSeconds}s)"
                        seekPillTick++
                    }
                },
                onEnterPictureInPicture = {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        val activity = context as? android.app.Activity
                        activity?.let {
                            val params = android.app.PictureInPictureParams.Builder()
                                .setAspectRatio(android.util.Rational(16, 9))
                                .build()
                            it.enterPictureInPictureMode(params)
                            isHudVisible = false
                        }
                    }
                },
                onOpenExternalPlayer = {
                    activeSource?.let { src ->
                        launchExternalPlayer(
                            context = context,
                            streamUrl = src.url,
                            title = "${anime.title} - Ep ${episode.number}: ${episode.title}",
                            headers = src.headers
                        )
                    }
                },
                onBack = {
                    persistCurrentProgress()
                    onBack()
                }
            )
        }
    }
}

private fun applySubtitleStyling(playerView: PlayerView, settings: UserSettings) {
    playerView.subtitleView?.apply {
        val textColor = when (settings.subtitleTextColor) {
            "Yellow" -> android.graphics.Color.YELLOW
            "Cyan" -> android.graphics.Color.CYAN
            "Green" -> android.graphics.Color.GREEN
            else -> android.graphics.Color.WHITE
        }
        val bgColor = when (settings.subtitleBackground) {
            "None" -> android.graphics.Color.TRANSPARENT
            "Solid" -> android.graphics.Color.argb(220, 10, 10, 14)
            else -> android.graphics.Color.argb(128, 10, 10, 14) // Translucent 50%
        }
        val captionStyle = CaptionStyleCompat(
            textColor,
            bgColor,
            android.graphics.Color.TRANSPARENT,
            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
            android.graphics.Color.BLACK,
            null
        )
        setStyle(captionStyle)

        val fractionalSize = when (settings.subtitleFontSize) {
            "18sp" -> 0.040f
            "32sp" -> 0.065f
            "40sp" -> 0.080f
            else -> 0.053f // 24sp default
        }
        setFractionalTextSize(fractionalSize)
    }
}

private fun launchExternalPlayer(
    context: Context,
    streamUrl: String,
    title: String,
    headers: Map<String, String>? = null
) {
    try {
        val uri = Uri.parse(streamUrl)
        val mimeType = if (streamUrl.contains(".m3u8", ignoreCase = true)) {
            "application/x-mpegURL"
        } else {
            "video/*"
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            putExtra("title", title)
            putExtra("display_name", title)
            if (!headers.isNullOrEmpty()) {
                val headersBundle = android.os.Bundle()
                headers.forEach { (k, v) -> headersBundle.putString(k, v) }
                putExtra("android.media.intent.extra.HTTP_HEADERS", headersBundle)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "Open in External Player").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "No external player found: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}
