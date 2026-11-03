package com.darknet.animetv.player

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.darknet.animetv.data.model.SubtitleTrack
import com.darknet.animetv.data.model.VideoSource
import com.darknet.animetv.ui.components.tvFocusableItem
import com.darknet.animetv.ui.theme.TvAccentPurple
import com.darknet.animetv.ui.theme.TvCardBorder
import com.darknet.animetv.ui.theme.TvDarkBackground
import com.darknet.animetv.ui.theme.TvGoldStar
import com.darknet.animetv.ui.theme.TvPrimaryRed
import com.darknet.animetv.ui.theme.TvSuccessGreen
import com.darknet.animetv.ui.theme.TvSurfaceColor
import com.darknet.animetv.ui.theme.TvTextSecondary

enum class PlayerDialogType {
    NONE,
    QUALITY,
    SUBTITLES,
    SERVERS,
    PRO_CONTROLS,
    SLEEP_TIMER,
    SUBTITLE_SYNC
}

/**
 * Leanback TV Remote-Optimized Video Playback HUD Overlay.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvPlayerHud(
    animeTitle: String,
    episodeNumber: Int,
    episodeTitle: String,
    activeSource: VideoSource?,
    availableSources: List<VideoSource>,
    selectedQuality: String,
    activeSubtitle: SubtitleTrack?,
    availableSubtitles: List<SubtitleTrack>,
    audioMode: String,
    currentPositionMs: Long,
    bufferedPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    hasPrevEpisode: Boolean,
    hasNextEpisode: Boolean,
    activeDialog: PlayerDialogType,
    playbackSpeed: Float = 1.0f,
    resizeModeName: String = "Fit",
    audioPresetName: String = "Standard",
    introSkipSeconds: Int = 85,
    sleepTimerManager: SleepTimerManager? = null,
    onSelectSleepMode: (SleepTimerManager.SleepTimerMode) -> Unit = {},
    subtitleOffsetMs: Long = 0L,
    onSelectSubtitleOffset: (Long) -> Unit = {},
    onPlayPauseToggle: () -> Unit,
    onSeekRewind: () -> Unit,
    onSeekForward: () -> Unit,
    onPrevEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
    onOpenDialog: (PlayerDialogType) -> Unit,
    onCloseDialog: () -> Unit,
    onSelectQuality: (String) -> Unit,
    onSelectSubtitle: (SubtitleTrack?) -> Unit,
    onToggleAudioMode: () -> Unit,
    onSelectServer: (VideoSource) -> Unit,
    onSelectSpeed: (Float) -> Unit = {},
    onSelectResizeMode: (String) -> Unit = {},
    onSelectAudioPreset: (String) -> Unit = {},
    onSkipIntro: () -> Unit = {},
    onSeekTo: ((Long) -> Unit)? = null,
    onEnterPictureInPicture: () -> Unit = {},
    onOpenExternalPlayer: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playPauseFocusRequester = remember { FocusRequester() }
    val scrubberFocusRequester = remember { FocusRequester() }
    var isScrubberFocused by remember { mutableStateOf(false) }
    var scrubPositionMs by remember(currentPositionMs, isScrubberFocused) {
        mutableStateOf(currentPositionMs)
    }

    val effectivePositionMs = if (isScrubberFocused) scrubPositionMs else currentPositionMs
    val effectiveProgressFraction = if (durationMs > 0L) {
        (effectivePositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    LaunchedEffect(activeDialog) {
        if (activeDialog == PlayerDialogType.NONE) {
            try {
                playPauseFocusRequester.requestFocus()
            } catch (_: Exception) {
            }
        }
    }

    val progressFraction = if (durationMs > 0L) {
        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val bufferedFraction = if (durationMs > 0L) {
        (bufferedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xD908090E),
                        Color(0x3308090E),
                        Color(0x3308090E),
                        Color(0xEB08090E)
                    )
                )
            )
    ) {
        // --- TOP METADATA HEADER ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 48.dp, vertical = 28.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.colors(
                        containerColor = TvSurfaceColor,
                        focusedContainerColor = TvAccentPurple
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
                    border = ButtonDefaults.border(
                        focusedBorder = Border(
                            border = BorderStroke(3.dp, Color.White),
                            shape = RoundedCornerShape(10.dp)
                        )
                    ),
                    scale = ButtonDefaults.scale(focusedScale = 1.08f)
                ) {
                    Text(
                        text = "← Exit",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Column {
                    Text(
                        text = animeTitle,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Episode $episodeNumber • $episodeTitle",
                        color = TvTextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Stream Status Badges
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HudStatusBadge(
                    label = activeSource?.serverName ?: "Auto Server",
                    accentColor = TvAccentPurple
                )
                HudStatusBadge(
                    label = selectedQuality,
                    accentColor = TvGoldStar
                )
                HudStatusBadge(
                    label = audioMode,
                    accentColor = TvSuccessGreen
                )
            }
        }

        // --- BOTTOM TRANSPORT & SCRUB BAR ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 48.dp, vertical = 28.dp)
        ) {
            // Timestamps, Scrub Position & Progress Percentage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = VideoPlayerManager.formatDuration(effectivePositionMs),
                    color = if (isScrubberFocused) TvPrimaryRed else Color.White,
                    fontSize = if (isScrubberFocused) 16.sp else 14.sp,
                    fontWeight = FontWeight.Bold
                )
                if (isScrubberFocused) {
                    Box(
                        modifier = Modifier
                            .background(TvAccentPurple, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Scrubbing • Press [Enter] to Seek • ↓ for Controls",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Text(
                        text = "${(progressFraction * 100).toInt()}% Watched",
                        color = TvTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = VideoPlayerManager.formatDuration(durationMs),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // High-Contrast Interactive TV Scrub Bar with Focus & Scrubbing
            Surface(
                onClick = {
                    onSeekTo?.invoke(scrubPositionMs)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isScrubberFocused) 18.dp else 10.dp)
                    .focusRequester(scrubberFocusRequester)
                    .focusProperties {
                        down = playPauseFocusRequester
                    }
                    .onFocusChanged { focusState ->
                        isScrubberFocused = focusState.isFocused
                        if (focusState.isFocused) {
                            scrubPositionMs = currentPositionMs
                        }
                    }
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                            when (keyEvent.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_LEFT -> {
                                    val step = maxOf(5_000L, if (durationMs > 0) durationMs / 120 else 10_000L)
                                    scrubPositionMs = (scrubPositionMs - step).coerceAtLeast(0L)
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    val step = maxOf(5_000L, if (durationMs > 0) durationMs / 120 else 10_000L)
                                    scrubPositionMs = (scrubPositionMs + step).coerceAtMost(durationMs)
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                    onSeekTo?.invoke(scrubPositionMs)
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    try {
                                        playPauseFocusRequester.requestFocus()
                                    } catch (_: Exception) {}
                                    true
                                }
                                else -> false
                            }
                        } else false
                    },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isScrubberFocused) Color(0x66FFFFFF) else Color(0x4DFFFFFF),
                    focusedContainerColor = Color(0x66FFFFFF)
                ),
                border = ClickableSurfaceDefaults.border(
                    focusedBorder = Border(
                        border = BorderStroke(2.dp, Color.White),
                        shape = RoundedCornerShape(8.dp)
                    )
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Buffered Track
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(bufferedFraction)
                            .background(Color(0x80FFFFFF))
                    )
                    // Active Playback / Scrub Track
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(effectiveProgressFraction)
                            .background(TvPrimaryRed)
                    ) {
                        if (isScrubberFocused) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .align(Alignment.CenterEnd)
                                    .background(Color.White, CircleShape)
                                    .border(2.dp, TvPrimaryRed, CircleShape)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Transport & Track Selection Buttons Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusProperties {
                        up = scrubberFocusRequester
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val navigateToScrubber: () -> Unit = {
                    try {
                        scrubberFocusRequester.requestFocus()
                    } catch (_: Exception) {}
                }

                // Skip Intro Prompt Button if within intro range
                if (currentPositionMs in 1_000L..(introSkipSeconds * 1000L)) {
                    HudActionButton(
                        label = "⏩ Skip Intro (+${introSkipSeconds}s)",
                        onClick = onSkipIntro,
                        onNavigateUp = navigateToScrubber
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Left: Playback Transport Controls
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasPrevEpisode) {
                        HudActionButton(
                            label = "⏮ Prev Ep",
                            onClick = onPrevEpisode,
                            onNavigateUp = navigateToScrubber
                        )
                    }

                    HudActionButton(
                        label = "⏪ -10s",
                        onClick = onSeekRewind,
                        onNavigateUp = navigateToScrubber
                    )

                    // Primary Play/Pause Button
                    Button(
                        onClick = onPlayPauseToggle,
                        colors = ButtonDefaults.colors(
                            containerColor = TvPrimaryRed,
                            focusedContainerColor = TvAccentPurple
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)),
                        border = ButtonDefaults.border(
                            focusedBorder = Border(
                                border = BorderStroke(3.5.dp, Color.White),
                                shape = RoundedCornerShape(12.dp)
                            )
                        ),
                        scale = ButtonDefaults.scale(focusedScale = 1.12f),
                        modifier = Modifier
                            .focusRequester(playPauseFocusRequester)
                            .focusProperties {
                                up = scrubberFocusRequester
                            }
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                    keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP
                                ) {
                                    navigateToScrubber()
                                    true
                                } else false
                            }
                    ) {
                        Text(
                            text = if (isPlaying) "⏸ Pause" else "▶ Play",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }

                    HudActionButton(
                        label = "⏩ +10s",
                        onClick = onSeekForward,
                        onNavigateUp = navigateToScrubber
                    )

                    if (hasNextEpisode) {
                        HudActionButton(
                            label = "⏭ Next Ep",
                            onClick = onNextEpisode,
                            onNavigateUp = navigateToScrubber
                        )
                    }
                }

                // Right: Track & Server Selector Controls
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HudActionButton(
                        label = "Quality: $selectedQuality",
                        onClick = { onOpenDialog(PlayerDialogType.QUALITY) },
                        onNavigateUp = navigateToScrubber
                    )

                    HudActionButton(
                        label = "CC: ${activeSubtitle?.label ?: "Off"}",
                        onClick = { onOpenDialog(PlayerDialogType.SUBTITLES) },
                        onNavigateUp = navigateToScrubber
                    )

                    HudActionButton(
                        label = "Audio: $audioMode",
                        onClick = onToggleAudioMode,
                        onNavigateUp = navigateToScrubber
                    )

                    if (availableSources.size > 1) {
                        HudActionButton(
                            label = "Server (${availableSources.size})",
                            onClick = { onOpenDialog(PlayerDialogType.SERVERS) },
                            onNavigateUp = navigateToScrubber
                        )
                    }

                    HudActionButton(
                        label = "⚡ ${playbackSpeed}x",
                        onClick = { onOpenDialog(PlayerDialogType.PRO_CONTROLS) },
                        onNavigateUp = navigateToScrubber
                    )

                    HudActionButton(
                        label = "📐 $resizeModeName",
                        onClick = { onOpenDialog(PlayerDialogType.PRO_CONTROLS) },
                        onNavigateUp = navigateToScrubber
                    )

                    HudActionButton(
                        label = sleepTimerManager?.getFormattedBadgeText() ?: "⏰ Sleep: Off",
                        onClick = { onOpenDialog(PlayerDialogType.SLEEP_TIMER) },
                        onNavigateUp = navigateToScrubber
                    )

                    HudActionButton(
                        label = if (subtitleOffsetMs == 0L) "⏱ Sub Sync" else "⏱ Sub ${String.format(java.util.Locale.US, "%+.2fs", subtitleOffsetMs / 1000.0)}",
                        onClick = { onOpenDialog(PlayerDialogType.SUBTITLE_SYNC) },
                        onNavigateUp = navigateToScrubber
                    )

                    HudActionButton(
                        label = "🪟 PiP",
                        onClick = onEnterPictureInPicture,
                        onNavigateUp = navigateToScrubber
                    )

                    HudActionButton(
                        label = "\uD83D\uDCFA External",
                        onClick = onOpenExternalPlayer,
                        onNavigateUp = navigateToScrubber
                    )
                }
            }
        }

        // --- MODAL TRACK SELECTION DIALOGS ---
        when (activeDialog) {
            PlayerDialogType.PRO_CONTROLS -> {
                TvProControlsModal(
                    currentSpeed = playbackSpeed,
                    currentResizeMode = resizeModeName,
                    currentAudioPreset = audioPresetName,
                    currentSubtitleOffset = subtitleOffsetMs,
                    onSelectSpeed = { s ->
                        onSelectSpeed(s)
                        onCloseDialog()
                    },
                    onSelectResizeMode = { m ->
                        onSelectResizeMode(m)
                        onCloseDialog()
                    },
                    onSelectAudioPreset = { p ->
                        onSelectAudioPreset(p)
                        onCloseDialog()
                    },
                    onSelectSubtitleOffset = { offset ->
                        onSelectSubtitleOffset(offset)
                        onCloseDialog()
                    },
                    onDismiss = onCloseDialog,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            PlayerDialogType.QUALITY -> {
                TvTrackSelectorModal(
                    title = "Select Video Quality",
                    options = VideoPlayerManager.AVAILABLE_QUALITIES,
                    selectedOption = selectedQuality,
                    optionLabel = { it },
                    onSelect = { q ->
                        onSelectQuality(q)
                        onCloseDialog()
                    },
                    onDismiss = onCloseDialog,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            PlayerDialogType.SUBTITLES -> {
                val options: List<SubtitleTrack?> = listOf(null) + availableSubtitles
                TvTrackSelectorModal(
                    title = "Select Subtitles / Closed Captions",
                    options = options,
                    selectedOption = activeSubtitle,
                    optionLabel = { it?.label ?: "Off (Disabled)" },
                    onSelect = { track ->
                        onSelectSubtitle(track)
                        onCloseDialog()
                    },
                    onDismiss = onCloseDialog,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            PlayerDialogType.SERVERS -> {
                TvTrackSelectorModal(
                    title = "Switch Streaming Server",
                    options = availableSources,
                    selectedOption = activeSource,
                    optionLabel = { "${it.serverName} (${it.quality})" },
                    onSelect = { src ->
                        onSelectServer(src)
                        onCloseDialog()
                    },
                    onDismiss = onCloseDialog,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            PlayerDialogType.SLEEP_TIMER -> {
                TvTrackSelectorModal(
                    title = "⏰ TV Sleep Timer & Auto-Off",
                    options = SleepTimerManager.SleepTimerMode.values().toList(),
                    selectedOption = sleepTimerManager?.activeMode ?: SleepTimerManager.SleepTimerMode.OFF,
                    optionLabel = { it.label },
                    onSelect = { mode ->
                        onSelectSleepMode(mode)
                        onCloseDialog()
                    },
                    onDismiss = onCloseDialog,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            PlayerDialogType.SUBTITLE_SYNC -> {
                val offsets = listOf(-2000L, -1000L, -500L, -250L, 0L, 250L, 500L, 1000L, 2000L)
                TvTrackSelectorModal(
                    title = "⏱ Live Subtitle Audio-Sync Offset",
                    options = offsets,
                    selectedOption = subtitleOffsetMs,
                    optionLabel = {
                        when (it) {
                            0L -> "0.0s (Default / In Sync)"
                            else -> String.format(java.util.Locale.US, "%+.2f seconds", it / 1000.0)
                        }
                    },
                    onSelect = { offset ->
                        onSelectSubtitleOffset(offset)
                        onCloseDialog()
                    },
                    onDismiss = onCloseDialog,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            PlayerDialogType.NONE -> {}
        }
    }
}

@Composable
private fun HudStatusBadge(
    label: String,
    accentColor: Color
) {
    Box(
        modifier = Modifier
            .background(Color(0xCC141622), RoundedCornerShape(8.dp))
            .border(1.dp, accentColor.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HudActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateUp: (() -> Unit)? = null
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.colors(
            containerColor = TvSurfaceColor,
            focusedContainerColor = TvAccentPurple
        ),
        shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
        border = ButtonDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(3.dp, Color.White),
                shape = RoundedCornerShape(10.dp)
            )
        ),
        scale = ButtonDefaults.scale(focusedScale = 1.08f),
        modifier = modifier
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP &&
                    onNavigateUp != null
                ) {
                    onNavigateUp()
                    true
                } else false
            }
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun <T> TvTrackSelectorModal(
    title: String,
    options: List<T>,
    selectedOption: T?,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val firstOptionFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            firstOptionFocus.requestFocus()
        } catch (_: Exception) {
        }
    }

    Box(
        modifier = modifier
            .width(420.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(TvDarkBackground.copy(alpha = 0.96f))
            .border(2.dp, TvAccentPurple, RoundedCornerShape(16.dp))
            .padding(24.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.colors(
                        containerColor = TvSurfaceColor,
                        focusedContainerColor = TvPrimaryRed
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                    modifier = Modifier.tvFocusableItem(
                        onClick = onDismiss,
                        focusedScale = 1.08f,
                        borderWidth = 3.dp,
                        focusedBorderColor = Color.White
                    )
                ) {
                    Text("Close", color = Color.White, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            options.forEachIndexed { idx, option ->
                val isSelected = option == selectedOption
                val label = optionLabel(option)
                Button(
                    onClick = { onSelect(option) },
                    colors = ButtonDefaults.colors(
                        containerColor = if (isSelected) TvPrimaryRed.copy(alpha = 0.8f) else TvSurfaceColor,
                        focusedContainerColor = TvAccentPurple
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (idx == 0) Modifier.focusRequester(firstOptionFocus) else Modifier)
                        .tvFocusableItem(
                            onClick = { onSelect(option) },
                            focusedScale = 1.04f,
                            borderWidth = 3.dp,
                            focusedBorderColor = Color.White
                        )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                        if (isSelected) {
                            Text(
                                text = "✓ Active",
                                color = TvGoldStar,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * TV Pro Controls Modal for Playback Speed, Aspect Ratio, and Audio Sync Delay.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvProControlsModal(
    currentSpeed: Float,
    currentResizeMode: String,
    currentAudioPreset: String,
    currentSubtitleOffset: Long = 0L,
    onSelectSpeed: (Float) -> Unit,
    onSelectResizeMode: (String) -> Unit,
    onSelectAudioPreset: (String) -> Unit,
    onSelectSubtitleOffset: (Long) -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val firstFocus = remember { FocusRequester() }
    val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    val aspectModes = listOf("Fit", "Zoom", "Stretch", "4:3")
    val audioPresets = com.darknet.animetv.player.audio.AudioEffectsManager.AVAILABLE_PRESETS

    LaunchedEffect(Unit) {
        try {
            firstFocus.requestFocus()
        } catch (_: Exception) {}
    }

    Box(
        modifier = modifier
            .width(480.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(TvDarkBackground.copy(alpha = 0.98f))
            .border(2.dp, TvAccentPurple, RoundedCornerShape(16.dp))
            .padding(24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⚙ Player Pro Controls",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.colors(
                        containerColor = TvSurfaceColor,
                        focusedContainerColor = TvPrimaryRed
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                    modifier = Modifier.tvFocusableItem(
                        onClick = onDismiss,
                        focusedScale = 1.08f,
                        borderWidth = 3.dp,
                        focusedBorderColor = Color.White
                    )
                ) {
                    Text("Close", color = Color.White, fontSize = 12.sp)
                }
            }

            Text(
                text = "Playback Speed",
                color = TvGoldStar,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                speeds.forEachIndexed { idx, speed ->
                    val isSelected = speed == currentSpeed
                    Button(
                        onClick = { onSelectSpeed(speed) },
                        colors = ButtonDefaults.colors(
                            containerColor = if (isSelected) TvPrimaryRed else TvSurfaceColor,
                            focusedContainerColor = TvAccentPurple
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                        modifier = Modifier
                            .weight(1f)
                            .then(if (idx == 0) Modifier.focusRequester(firstFocus) else Modifier)
                            .tvFocusableItem(
                                onClick = { onSelectSpeed(speed) },
                                focusedScale = 1.08f,
                                borderWidth = 3.dp,
                                focusedBorderColor = Color.White
                            )
                    ) {
                        Text(
                            text = "${speed}x",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Aspect Ratio Mode",
                color = TvGoldStar,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                aspectModes.forEach { mode ->
                    val isSelected = mode == currentResizeMode
                    Button(
                        onClick = { onSelectResizeMode(mode) },
                        colors = ButtonDefaults.colors(
                            containerColor = if (isSelected) TvPrimaryRed else TvSurfaceColor,
                            focusedContainerColor = TvAccentPurple
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                        modifier = Modifier
                            .weight(1f)
                            .tvFocusableItem(
                                onClick = { onSelectResizeMode(mode) },
                                focusedScale = 1.08f,
                                borderWidth = 3.dp,
                                focusedBorderColor = Color.White
                            )
                    ) {
                        Text(
                            text = mode,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "🔊 Living Room Audio Enhancements",
                color = TvGoldStar,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                audioPresets.forEach { preset ->
                    val isSelected = preset == currentAudioPreset
                    Button(
                        onClick = { onSelectAudioPreset(preset) },
                        colors = ButtonDefaults.colors(
                            containerColor = if (isSelected) TvPrimaryRed else TvSurfaceColor,
                            focusedContainerColor = TvAccentPurple
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                        modifier = Modifier
                            .weight(1f)
                            .tvFocusableItem(
                                onClick = { onSelectAudioPreset(preset) },
                                focusedScale = 1.08f,
                                borderWidth = 3.dp,
                                focusedBorderColor = Color.White
                            )
                    ) {
                        Text(
                            text = preset,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "⏱ Subtitle Audio-Sync Offset",
                color = TvGoldStar,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val syncOptions = listOf(-1000L, -500L, 0L, 500L, 1000L)
                syncOptions.forEach { offset ->
                    val isSelected = offset == currentSubtitleOffset
                    Button(
                        onClick = { onSelectSubtitleOffset(offset) },
                        colors = ButtonDefaults.colors(
                            containerColor = if (isSelected) TvPrimaryRed else TvSurfaceColor,
                            focusedContainerColor = TvAccentPurple
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                        modifier = Modifier
                            .weight(1f)
                            .tvFocusableItem(
                                onClick = { onSelectSubtitleOffset(offset) },
                                focusedScale = 1.08f,
                                borderWidth = 3.dp,
                                focusedBorderColor = Color.White
                            )
                    ) {
                        Text(
                            text = when (offset) {
                                0L -> "0s (Sync)"
                                -1000L -> "-1.0s"
                                -500L -> "-0.5s"
                                500L -> "+0.5s"
                                1000L -> "+1.0s"
                                else -> "${offset}ms"
                            },
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
