package com.darknet.animetv.ui.screens

import android.os.Build
import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.darknet.animetv.BuildConfig
import com.darknet.animetv.audio.TvSoundManager
import com.darknet.animetv.data.model.AniListUser
import com.darknet.animetv.data.model.CustomSource
import com.darknet.animetv.data.model.StreamBenchmarkResult
import com.darknet.animetv.data.model.UserSettings
import com.darknet.animetv.data.repository.AniListSyncManager
import com.darknet.animetv.data.repository.AnimeRepository
import com.darknet.animetv.data.source.SourceHealthResult
import com.darknet.animetv.data.storage.AppStorage
import com.darknet.animetv.ui.components.tvFocusableItem
import com.darknet.animetv.update.UpdateInfo
import com.darknet.animetv.update.UpdateManager
import com.darknet.animetv.ui.theme.TvAccentPurple
import com.darknet.animetv.ui.theme.TvCardBorder
import com.darknet.animetv.ui.theme.TvDarkBackground
import com.darknet.animetv.ui.theme.TvGoldStar
import com.darknet.animetv.ui.theme.TvPrimaryRed
import com.darknet.animetv.ui.theme.TvSuccessGreen
import com.darknet.animetv.ui.theme.TvSurfaceColor
import com.darknet.animetv.ui.theme.TvSurfaceFocusedColor
import com.darknet.animetv.ui.theme.TvTextPrimary
import com.darknet.animetv.ui.theme.TvTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class SettingsCategory(val title: String, val icon: String, val subtitle: String) {
    PLAYBACK("Playback & Video", "\uD83C\uDFAC", "Quality, Audio Sub/Dub, Auto-play, Intro Skip"),
    ACCOUNT("Account & Sync", "\uD83D\uDD17", "AniList & MyAnimeList linking, scrobble"),
    APPEARANCE("Appearance & TV Focus", "\uD83C\uDFA8", "Remote Focus scale, high-contrast borders, Theme"),
    SOURCES("Catalogs & Scrapers", "\uD83D\uDCE1", "Catalog provider, failover engine, scraper settings"),
    STORAGE("Data & Storage", "\uD83D\uDCBE", "Watch history, watchlist bookmarks, reset data"),
    ABOUT("About & Updates", "\u2139\uFE0F", "GitHub OTA updates, version, device info")
}

/**
 * Android TV Leanback Settings Screen.
 * 2-pane navigation designed specifically for 10-foot remote interaction.
 * Left: category list with D-pad navigation and escape to header.
 * Right: active setting option items with instant persistence into AppStorage.
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    storage: AppStorage,
    repository: AnimeRepository? = null,
    modifier: Modifier = Modifier,
    onNavigateUp: (() -> Unit)? = null
) {
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    var selectedCategory by remember { mutableStateOf(SettingsCategory.PLAYBACK) }
    var currentSettings by remember { mutableStateOf(storage.getUserSettings()) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }

    fun showFeedback(msg: String) {
        feedbackMessage = msg
    }
    var historyCount by remember { mutableIntStateOf(storage.getWatchHistoryCount()) }
    var watchlistCount by remember { mutableIntStateOf(storage.getWatchlistCount()) }
    var aniListUser by remember { mutableStateOf(storage.getAniListUser()) }
    var malUser by remember { mutableStateOf(storage.getMalUser()) }
    var malUsername by remember { mutableStateOf(storage.getMalUsername()) }

    // Streaming Sources & Benchmark State
    var sourcesList by remember { mutableStateOf(storage.getSources()) }
    var healthResults by remember { mutableStateOf<Map<String, SourceHealthResult>>(emptyMap()) }
    var benchmarkResults by remember { mutableStateOf<Map<String, StreamBenchmarkResult>>(emptyMap()) }
    var isTestingHealth by remember { mutableStateOf(false) }
    var isBenchmarking by remember { mutableStateOf(false) }
    var benchmarkProgress by remember { mutableStateOf<String?>(null) }
    var showAddSourceDialog by remember { mutableStateOf(false) }
    var newSourceName by remember { mutableStateOf("") }
    var newSourceUrl by remember { mutableStateOf("") }
    var showAniListLoginDialog by remember { mutableStateOf(false) }
    var aniListInputText by remember { mutableStateOf("") }
    var showMalLoginDialog by remember { mutableStateOf(false) }
    var malInputText by remember { mutableStateOf("") }

    fun refreshSources() {
        sourcesList = storage.getSources()
    }

    fun toggleSource(source: CustomSource) {
        val updated = source.copy(isEnabled = !source.isEnabled)
        storage.saveSource(updated)
        refreshSources()
        showFeedback("${source.name} ${if (updated.isEnabled) "enabled" else "disabled"}")
    }

    fun moveSource(fromIndex: Int, toIndex: Int) {
        if (fromIndex in sourcesList.indices && toIndex in sourcesList.indices) {
            val mutable = sourcesList.toMutableList()
            val item = mutable.removeAt(fromIndex)
            mutable.add(toIndex, item)
            sourcesList = mutable
            storage.saveSourcesOrder(mutable)
            showFeedback("Moved ${item.name} to #${toIndex + 1}")
        }
    }

    fun deleteSource(sourceId: Long) {
        storage.deleteSource(sourceId)
        refreshSources()
        showFeedback("Source deleted")
    }

    fun runHealthCheck() {
        if (isTestingHealth || repository == null) return
        coroutineScope.launch {
            isTestingHealth = true
            showFeedback("Testing source latencies...")
            try {
                healthResults = repository.testAllSources()
                showFeedback("Health test complete for ${healthResults.size} sources")
            } catch (e: Exception) {
                showFeedback("Health check failed: ${e.message}")
            } finally {
                isTestingHealth = false
            }
        }
    }

    fun runBenchmark() {
        if (isBenchmarking || repository == null) return
        coroutineScope.launch {
            isBenchmarking = true
            benchmarkProgress = "Starting stream throughput benchmark..."
            try {
                val results = repository.benchmarkAllSources { partial ->
                    benchmarkResults = benchmarkResults + (partial.sourceId.lowercase() to partial)
                    benchmarkProgress = "Benchmarked ${partial.sourceName}: ${partial.throughputMbps} Mbps"
                }
                benchmarkResults = results.associateBy { it.sourceId.lowercase() }
                benchmarkProgress = "Completed benchmark across ${results.size} sources"
                showFeedback("Benchmark complete (${results.size} sources)")
            } catch (e: Exception) {
                benchmarkProgress = "Benchmark error: ${e.message}"
                showFeedback("Benchmark failed: ${e.message}")
            } finally {
                isBenchmarking = false
            }
        }
    }

    fun autoRankSourcesBySpeed() {
        if (benchmarkResults.isEmpty()) return
        val ranked = sourcesList.sortedWith(
            compareByDescending<CustomSource> { src ->
                val bm = benchmarkResults[src.sourceType.lowercase()]
                    ?: benchmarkResults[src.name.lowercase()]
                    ?: benchmarkResults.values.firstOrNull { src.sourceType.contains(it.sourceId, ignoreCase = true) || src.name.contains(it.sourceName, ignoreCase = true) }
                bm?.isHealthy == true
            }.thenByDescending { src ->
                val bm = benchmarkResults[src.sourceType.lowercase()]
                    ?: benchmarkResults[src.name.lowercase()]
                    ?: benchmarkResults.values.firstOrNull { src.sourceType.contains(it.sourceId, ignoreCase = true) || src.name.contains(it.sourceName, ignoreCase = true) }
                bm?.throughputMbps ?: 0.0
            }.thenBy { src ->
                val bm = benchmarkResults[src.sourceType.lowercase()]
                    ?: benchmarkResults[src.name.lowercase()]
                    ?: benchmarkResults.values.firstOrNull { src.sourceType.contains(it.sourceId, ignoreCase = true) || src.name.contains(it.sourceName, ignoreCase = true) }
                bm?.pingMs ?: Long.MAX_VALUE
            }
        )
        sourcesList = ranked
        storage.saveSourcesOrder(ranked)
        showFeedback("⚡ Sources auto-ranked by throughput & ping!")
    }

    val context = LocalContext.current
    val updateManager = remember { UpdateManager() }
    var isCheckingUpdates by remember { mutableStateOf(false) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(0) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }

    fun checkAppUpdates() {
        if (isCheckingUpdates) return
        isCheckingUpdates = true
        showFeedback("Checking GitHub for updates...")
        coroutineScope.launch {
            try {
                val info = updateManager.checkForUpdates(BuildConfig.VERSION_NAME)
                updateInfo = info
                if (info.hasUpdate) {
                    showFeedback("New update v${info.latestVersion} available!")
                } else {
                    showFeedback("TanukiTV is up to date (v${info.currentVersion})")
                }
            } catch (e: Exception) {
                showFeedback("Update check failed: ${e.message}")
            } finally {
                isCheckingUpdates = false
            }
        }
    }

    fun downloadAndInstallUpdate(url: String) {
        if (isDownloadingUpdate) return
        isDownloadingUpdate = true
        downloadProgress = 0
        showFeedback("Downloading update package...")
        coroutineScope.launch {
            val result = updateManager.downloadAndInstallApk(context, url) { progress ->
                downloadProgress = progress
            }
            isDownloadingUpdate = false
            if (result.isSuccess) {
                showFeedback("Download complete! Launching package installer...")
            } else {
                showFeedback("Download failed: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    LaunchedEffect(feedbackMessage) {
        if (feedbackMessage != null) {
            delay(3000L)
            feedbackMessage = null
        }
    }

    fun updateSettings(newSettings: UserSettings, message: String) {
        currentSettings = newSettings
        storage.saveUserSettings(newSettings)
        showFeedback(message)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TvDarkBackground)
            .padding(horizontal = 48.dp, vertical = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Settings & Preferences",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Configure playback, remote focus aesthetics, streaming scrapers, and data storage",
                    color = TvTextSecondary,
                    fontSize = 13.sp
                )
            }

            AnimatedVisibility(
                visible = feedbackMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .background(TvSuccessGreen.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                        .border(1.dp, TvSuccessGreen, RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "\u2714 ${feedbackMessage ?: ""}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 2-Pane Split: Left Categories (280.dp) + Right Setting Options (Remaining)
        val rightPanelFirstItemFocusRequester = remember { FocusRequester() }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            // Left Category Rail
            Column(
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingsCategory.values().forEachIndexed { index, category ->
                    val isSelected = selectedCategory == category
                    var isFocused by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                    if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP && index == 0) {
                                        onNavigateUp?.invoke()
                                        true
                                    } else if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                                        val moved = try {
                                            rightPanelFirstItemFocusRequester.requestFocus()
                                            true
                                        } catch (_: Exception) {
                                            false
                                        }
                                        if (!moved) {
                                            focusManager.moveFocus(FocusDirection.Right)
                                        }
                                        true
                                    } else false
                                } else false
                            }
                            .tvFocusableItem(
                                onClick = { selectedCategory = category },
                                focusedScale = 1.05f,
                                borderWidth = 3.dp,
                                focusedBorderColor = Color.White,
                                shape = RoundedCornerShape(12.dp),
                                onFocusChanged = { focused ->
                                    isFocused = focused
                                    if (focused) {
                                        selectedCategory = category
                                    }
                                }
                            )
                            .background(
                                if (isSelected) TvPrimaryRed.copy(alpha = 0.85f) else TvSurfaceColor,
                                RoundedCornerShape(12.dp)
                            )
                            .border(
                                1.dp,
                                if (isSelected) TvPrimaryRed else TvCardBorder,
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = category.icon,
                                fontSize = 20.sp
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = category.title,
                                    color = if (isSelected || isFocused) Color.White else TvTextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = category.subtitle,
                                    color = if (isSelected) Color.White.copy(alpha = 0.8f) else TvTextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // Right Options Panel
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .background(TvSurfaceColor, RoundedCornerShape(16.dp))
                    .border(1.dp, TvCardBorder, RoundedCornerShape(16.dp))
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                val listState = rememberLazyListState()
                val scrollToTop: () -> Unit = { coroutineScope.launch { listState.scrollToItem(0, 0) } }

                LaunchedEffect(selectedCategory) {
                    listState.scrollToItem(0)
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp)
                ) {
                    when (selectedCategory) {
                        SettingsCategory.PLAYBACK -> {
                            item {
                                SettingOptionSelector(
                                    title = "Default Streaming Quality",
                                    description = "Preferred video resolution when initializing ExoPlayer streams",
                                    options = listOf("Auto", "1080p", "720p", "480p", "360p"),
                                    selectedOption = currentSettings.defaultQuality,
                                    onSelectOption = {
                                        updateSettings(currentSettings.copy(defaultQuality = it), "Quality set to $it")
                                    },
                                    isFirstItem = true,
                                    onNavigateUp = onNavigateUp,
                                    onFocusTopItem = scrollToTop
                                )
                            }
                            item {
                                SettingOptionSelector(
                                    title = "Preferred Audio Track",
                                    description = "Default soundtrack format for anime series with multi-audio availability",
                                    options = listOf("SUB", "DUB"),
                                    selectedOption = currentSettings.preferredAudio,
                                    onSelectOption = {
                                        updateSettings(currentSettings.copy(preferredAudio = it), "Audio mode set to $it")
                                    }
                                )
                            }
                            item {
                                SettingOptionSelector(
                                    title = "Preferred Subtitle Language",
                                    description = "Default subtitle track language automatically activated during playback",
                                    options = listOf("English", "Spanish", "Portuguese", "German", "Off"),
                                    selectedOption = currentSettings.preferredSubLanguage,
                                    onSelectOption = {
                                        updateSettings(currentSettings.copy(preferredSubLanguage = it), "Subtitles set to $it")
                                    }
                                )
                            }
                            item {
                                SettingToggleRow(
                                    title = "Auto-Play Next Episode",
                                    description = "Automatically advance to the next sequential episode when playback completes",
                                    isChecked = currentSettings.autoPlayNextEpisode,
                                    onToggle = {
                                        updateSettings(currentSettings.copy(autoPlayNextEpisode = it), if (it) "Auto-play enabled" else "Auto-play disabled")
                                    }
                                )
                            }
                            item {
                                SettingOptionSelector(
                                    title = "Auto-Skip Anime Intro",
                                    description = "Skip opening intro theme song duration when starting episodes",
                                    options = listOf("Off (0s)", "85s", "90s"),
                                    selectedOption = if (currentSettings.autoSkipIntro) "${currentSettings.introSkipSeconds}s" else "Off (0s)",
                                    onSelectOption = {
                                        val (skip, sec) = when (it) {
                                            "85s" -> true to 85
                                            "90s" -> true to 90
                                            else -> false to 0
                                        }
                                        updateSettings(currentSettings.copy(autoSkipIntro = skip, introSkipSeconds = sec), "Intro skip set to $it")
                                    }
                                )
                            }
                            item {
                                SettingOptionSelector(
                                    title = "Remote Seeking Step",
                                    description = "Jump duration applied on Left/Right D-pad remote button presses",
                                    options = listOf("5s", "10s", "15s", "30s"),
                                    selectedOption = "${currentSettings.seekStepSeconds}s",
                                    onSelectOption = {
                                        val sec = it.replace("s", "").toIntOrNull() ?: 10
                                        updateSettings(currentSettings.copy(seekStepSeconds = sec), "Seek step set to $it")
                                    }
                                )
                            }
                            item {
                                SettingOptionSelector(
                                    title = "Subtitle Font Size",
                                    description = "Adjust text size for subtitles and closed captions on TV",
                                    options = listOf("18sp", "24sp", "32sp", "40sp"),
                                    selectedOption = currentSettings.subtitleFontSize,
                                    onSelectOption = {
                                        updateSettings(currentSettings.copy(subtitleFontSize = it), "Subtitle font size set to $it")
                                    }
                                )
                            }
                            item {
                                SettingOptionSelector(
                                    title = "Subtitle Text Color",
                                    description = "Caption text rendering color against vibrant or dark scenes",
                                    options = listOf("White", "Yellow", "Cyan", "Green"),
                                    selectedOption = currentSettings.subtitleTextColor,
                                    onSelectOption = {
                                        updateSettings(currentSettings.copy(subtitleTextColor = it), "Subtitle text color set to $it")
                                    }
                                )
                            }
                            item {
                                SettingOptionSelector(
                                    title = "Subtitle Background Box",
                                    description = "Contrast background opacity behind subtitle text",
                                    options = listOf("None", "Translucent", "Solid"),
                                    selectedOption = currentSettings.subtitleBackground,
                                    onSelectOption = {
                                        updateSettings(currentSettings.copy(subtitleBackground = it), "Subtitle background set to $it")
                                    }
                                )
                            }
                            item {
                                SettingToggleRow(
                                    title = "External Video Player Hand-off",
                                    description = "Delegate video playback to external TV media players (VLC, Just Player, Nova)",
                                    isChecked = currentSettings.useExternalPlayer,
                                    onToggle = {
                                        updateSettings(currentSettings.copy(useExternalPlayer = it), if (it) "External player hand-off enabled" else "Internal ExoPlayer selected")
                                    }
                                )
                            }
                            item {
                                SettingOptionSelector(
                                    title = "Living Room Audio Enhancement",
                                    description = "Real-time equalizer profile optimized for TV speakers and soundbars",
                                    options = com.darknet.animetv.player.audio.AudioEffectsManager.AVAILABLE_PRESETS,
                                    selectedOption = currentSettings.audioPreset,
                                    onSelectOption = {
                                        updateSettings(currentSettings.copy(audioPreset = it), "Audio preset set to $it")
                                    }
                                )
                            }
                            item {
                                SettingToggleRow(
                                    title = "Surround Sound Passthrough",
                                    description = "Bitstream surround audio (AC-3, E-AC-3, DTS) directly to AV receivers",
                                    isChecked = currentSettings.audioPassthrough,
                                    onToggle = {
                                        updateSettings(currentSettings.copy(audioPassthrough = it), if (it) "Audio passthrough enabled" else "Audio passthrough disabled")
                                    }
                                )
                            }
                            item {
                                SettingOptionSelector(
                                    title = "Wi-Fi Buffer Optimizer",
                                    description = "Tune buffer-ahead duration in ExoPlayer for TV living-room Wi-Fi stability",
                                    options = com.darknet.animetv.player.VideoPlayerManager.AVAILABLE_BUFFER_MODES,
                                    selectedOption = currentSettings.bufferMode,
                                    onSelectOption = {
                                        updateSettings(currentSettings.copy(bufferMode = it), "Buffer set to $it")
                                    }
                                )
                            }
                            item {
                                SettingOptionSelector(
                                    title = "Max Video Bitrate Cap",
                                    description = "Cap maximum streaming bandwidth for low-bandwidth connections or data savers",
                                    options = com.darknet.animetv.player.VideoPlayerManager.AVAILABLE_BITRATE_CAPS,
                                    selectedOption = currentSettings.maxBitrateCap,
                                    onSelectOption = {
                                        updateSettings(currentSettings.copy(maxBitrateCap = it), "Bitrate capped at $it")
                                    }
                                )
                            }
                        }

                        SettingsCategory.ACCOUNT -> {
                            // AniList Account Status
                            item {
                                val aniBringIntoView = remember { BringIntoViewRequester() }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .bringIntoViewRequester(aniBringIntoView)
                                        .background(TvDarkBackground, RoundedCornerShape(12.dp))
                                        .border(1.dp, TvCardBorder, RoundedCornerShape(12.dp))
                                        .padding(18.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "AniList Account",
                                                    color = Color.White,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .background(
                                                            if (aniListUser != null) TvSuccessGreen.copy(alpha = 0.2f) else TvPrimaryRed.copy(alpha = 0.2f),
                                                            RoundedCornerShape(6.dp)
                                                        )
                                                        .border(
                                                            1.dp,
                                                            if (aniListUser != null) TvSuccessGreen else TvPrimaryRed,
                                                            RoundedCornerShape(6.dp)
                                                        )
                                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = if (aniListUser != null) "\u2714 Connected" else "Not Linked",
                                                        color = if (aniListUser != null) TvSuccessGreen else TvPrimaryRed,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = if (aniListUser != null)
                                                    "Logged in as ${aniListUser!!.name} \u2022 ${aniListUser!!.totalAnime} Anime in List \u2022 ${aniListUser!!.episodesWatched} Episodes Watched"
                                                else
                                                    "Pair your AniList account to enable two-way watchlist sync and automatic episode scrobbling",
                                                color = TvTextSecondary,
                                                fontSize = 12.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(16.dp))

                                        if (aniListUser != null) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                var isSyncFocused by remember { mutableStateOf(false) }
                                                Button(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            showFeedback("Synchronizing from AniList...")
                                                            val res = AniListSyncManager.syncAniListWatchlist(storage)
                                                            watchlistCount = storage.getWatchlistCount()
                                                            historyCount = storage.getWatchHistoryCount()
                                                            showFeedback(res.message)
                                                        }
                                                    },
                                                    modifier = Modifier
                                                        .focusRequester(rightPanelFirstItemFocusRequester)
                                                        .onPreviewKeyEvent { keyEvent ->
                                                            if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                                                if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                                                                    focusManager.moveFocus(FocusDirection.Left)
                                                                    true
                                                                } else if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP && onNavigateUp != null) {
                                                                    onNavigateUp()
                                                                    true
                                                                } else false
                                                            } else false
                                                        }
                                                        .tvFocusableItem(
                                                            onClick = {
                                                                coroutineScope.launch {
                                                                    showFeedback("Synchronizing from AniList...")
                                                                    val res = AniListSyncManager.syncAniListWatchlist(storage)
                                                                    watchlistCount = storage.getWatchlistCount()
                                                                    historyCount = storage.getWatchHistoryCount()
                                                                    showFeedback(res.message)
                                                                }
                                                            },
                                                            focusedScale = 1.08f,
                                                            borderWidth = 3.dp,
                                                            focusedBorderColor = Color.White,
                                                            shape = RoundedCornerShape(8.dp),
                                                            onFocusChanged = { focused ->
                                                                isSyncFocused = focused
                                                                if (focused) {
                                                                    scrollToTop()
                                                                    coroutineScope.launch { aniBringIntoView.bringIntoView() }
                                                                }
                                                            }
                                                        ),
                                                    colors = ButtonDefaults.colors(
                                                        containerColor = TvAccentPurple,
                                                        focusedContainerColor = TvPrimaryRed
                                                    ),
                                                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp))
                                                ) {
                                                    Text(
                                                        text = "\uD83D\uDD04 Sync Now",
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        maxLines = 1
                                                    )
                                                }

                                                var isDisconnectFocused by remember { mutableStateOf(false) }
                                                Button(
                                                    onClick = {
                                                        storage.clearAniListAuth()
                                                        aniListUser = null
                                                        showFeedback("AniList account unlinked")
                                                    },
                                                    modifier = Modifier
                                                        .onPreviewKeyEvent { keyEvent ->
                                                            if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                                                keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP && onNavigateUp != null
                                                            ) {
                                                                onNavigateUp()
                                                                true
                                                            } else false
                                                        }
                                                        .tvFocusableItem(
                                                            onClick = {
                                                                storage.clearAniListAuth()
                                                                aniListUser = null
                                                                showFeedback("AniList account unlinked")
                                                            },
                                                            focusedScale = 1.08f,
                                                            borderWidth = 3.dp,
                                                            focusedBorderColor = Color.White,
                                                            shape = RoundedCornerShape(8.dp),
                                                            onFocusChanged = { focused ->
                                                                isDisconnectFocused = focused
                                                                if (focused) {
                                                                    scrollToTop()
                                                                    coroutineScope.launch { aniBringIntoView.bringIntoView() }
                                                                }
                                                            }
                                                        ),
                                                    colors = ButtonDefaults.colors(
                                                        containerColor = TvSurfaceColor,
                                                        focusedContainerColor = TvPrimaryRed
                                                    ),
                                                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp))
                                                ) {
                                                    Text(
                                                        text = "Disconnect",
                                                        color = TvTextSecondary,
                                                        fontWeight = FontWeight.Medium,
                                                        fontSize = 13.sp,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        } else {
                                            var isConnectFocused by remember { mutableStateOf(false) }
                                            Button(
                                                onClick = { showAniListLoginDialog = true },
                                                modifier = Modifier
                                                    .focusRequester(rightPanelFirstItemFocusRequester)
                                                    .onPreviewKeyEvent { keyEvent ->
                                                        if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                                            if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                                                                focusManager.moveFocus(FocusDirection.Left)
                                                                true
                                                            } else if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP && onNavigateUp != null) {
                                                                onNavigateUp()
                                                                true
                                                            } else false
                                                        } else false
                                                    }
                                                    .tvFocusableItem(
                                                        onClick = { showAniListLoginDialog = true },
                                                        focusedScale = 1.08f,
                                                        borderWidth = 3.dp,
                                                        focusedBorderColor = Color.White,
                                                        shape = RoundedCornerShape(8.dp),
                                                        onFocusChanged = { focused ->
                                                            isConnectFocused = focused
                                                            if (focused) {
                                                                scrollToTop()
                                                                coroutineScope.launch { aniBringIntoView.bringIntoView() }
                                                            }
                                                        }
                                                    ),
                                                colors = ButtonDefaults.colors(
                                                    containerColor = TvPrimaryRed,
                                                    focusedContainerColor = TvAccentPurple
                                                ),
                                                shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp))
                                            ) {
                                                Text(
                                                    text = "\u26A1 Link AniList Account",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }

                                    if (aniListUser == null) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(TvSurfaceColor, RoundedCornerShape(8.dp))
                                                .padding(10.dp)
                                        ) {
                                            Text(
                                                text = "Authorize via Web: ${AniListSyncManager.OAUTH_AUTH_URL}",
                                                color = TvAccentPurple,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }

                            // Auto-Scrobble Toggle Row
                            item {
                                SettingToggleRow(
                                    title = "Auto-Scrobble Watched Episodes",
                                    description = "Automatically update episode progress and mark anime as completed on AniList when playback completes",
                                    isChecked = currentSettings.autoScrobble,
                                    onToggle = {
                                        updateSettings(currentSettings.copy(autoScrobble = it), if (it) "Auto-scrobbling active" else "Auto-scrobbling paused")
                                    }
                                )
                            }

                            // MyAnimeList (MAL) Account Status & Login
                            item {
                                val malBringIntoView = remember { BringIntoViewRequester() }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .bringIntoViewRequester(malBringIntoView)
                                        .background(TvDarkBackground, RoundedCornerShape(12.dp))
                                        .border(1.dp, TvCardBorder, RoundedCornerShape(12.dp))
                                        .padding(18.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "MyAnimeList Account",
                                                    color = Color.White,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .background(
                                                            if (malUser != null) TvSuccessGreen.copy(alpha = 0.2f) else TvPrimaryRed.copy(alpha = 0.2f),
                                                            RoundedCornerShape(6.dp)
                                                        )
                                                        .border(
                                                            1.dp,
                                                            if (malUser != null) TvSuccessGreen else TvPrimaryRed,
                                                            RoundedCornerShape(6.dp)
                                                        )
                                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = if (malUser != null) "\u2714 Connected" else "Not Linked",
                                                        color = if (malUser != null) TvSuccessGreen else TvPrimaryRed,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = if (malUser != null)
                                                    "Logged in as ${malUser!!.name} \u2022 ${malUser!!.totalAnime} Anime in List \u2022 ${malUser!!.episodesWatched} Episodes Watched"
                                                else
                                                    "Log in with your MyAnimeList username or OAuth Access Token to import and sync your MAL anime library",
                                                color = TvTextSecondary,
                                                fontSize = 12.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(16.dp))

                                        if (malUser != null) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Button(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            showFeedback("Synchronizing from MyAnimeList...")
                                                            val res = AniListSyncManager.syncMalWatchlist(malUser!!.name, storage)
                                                            watchlistCount = storage.getWatchlistCount()
                                                            historyCount = storage.getWatchHistoryCount()
                                                            malUser = storage.getMalUser()
                                                            malUsername = storage.getMalUsername()
                                                            showFeedback(res.message)
                                                        }
                                                    },
                                                    modifier = Modifier
                                                        .onPreviewKeyEvent { keyEvent ->
                                                            if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                                                keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                                                            ) {
                                                                focusManager.moveFocus(FocusDirection.Left)
                                                                true
                                                            } else false
                                                        }
                                                        .tvFocusableItem(
                                                            onClick = {
                                                                coroutineScope.launch {
                                                                    showFeedback("Synchronizing from MyAnimeList...")
                                                                    val res = AniListSyncManager.syncMalWatchlist(malUser!!.name, storage)
                                                                    watchlistCount = storage.getWatchlistCount()
                                                                    historyCount = storage.getWatchHistoryCount()
                                                                    malUser = storage.getMalUser()
                                                                    malUsername = storage.getMalUsername()
                                                                    showFeedback(res.message)
                                                                }
                                                            },
                                                            focusedScale = 1.08f,
                                                            borderWidth = 3.dp,
                                                            focusedBorderColor = Color.White,
                                                            shape = RoundedCornerShape(8.dp),
                                                            onFocusChanged = { focused ->
                                                                if (focused) {
                                                                    coroutineScope.launch { malBringIntoView.bringIntoView() }
                                                                }
                                                            }
                                                        ),
                                                    colors = ButtonDefaults.colors(
                                                        containerColor = Color(0xFF2E51A2),
                                                        focusedContainerColor = TvAccentPurple
                                                    ),
                                                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp))
                                                ) {
                                                    Text(
                                                        text = "\uD83D\uDD04 Sync MAL",
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        maxLines = 1
                                                    )
                                                }

                                                Button(
                                                    onClick = {
                                                        storage.clearMalAuth()
                                                        malUser = null
                                                        malUsername = null
                                                        showFeedback("MyAnimeList account unlinked")
                                                    },
                                                    modifier = Modifier.tvFocusableItem(
                                                        onClick = {
                                                            storage.clearMalAuth()
                                                            malUser = null
                                                            malUsername = null
                                                            showFeedback("MyAnimeList account unlinked")
                                                        },
                                                        focusedScale = 1.08f,
                                                        borderWidth = 3.dp,
                                                        focusedBorderColor = Color.White,
                                                        shape = RoundedCornerShape(8.dp),
                                                        onFocusChanged = { focused ->
                                                            if (focused) {
                                                                coroutineScope.launch { malBringIntoView.bringIntoView() }
                                                            }
                                                        }
                                                    ),
                                                    colors = ButtonDefaults.colors(
                                                        containerColor = TvSurfaceColor,
                                                        focusedContainerColor = TvPrimaryRed
                                                    ),
                                                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp))
                                                ) {
                                                    Text(
                                                        text = "Disconnect",
                                                        color = TvTextSecondary,
                                                        fontWeight = FontWeight.Medium,
                                                        fontSize = 13.sp,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        } else {
                                            Button(
                                                onClick = { showMalLoginDialog = true },
                                                modifier = Modifier
                                                    .onPreviewKeyEvent { keyEvent ->
                                                        if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                                            keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                                                        ) {
                                                            focusManager.moveFocus(FocusDirection.Left)
                                                            true
                                                        } else false
                                                    }
                                                    .tvFocusableItem(
                                                        onClick = { showMalLoginDialog = true },
                                                        focusedScale = 1.08f,
                                                        borderWidth = 3.dp,
                                                        focusedBorderColor = Color.White,
                                                        shape = RoundedCornerShape(8.dp),
                                                        onFocusChanged = { focused ->
                                                            if (focused) {
                                                                coroutineScope.launch { malBringIntoView.bringIntoView() }
                                                            }
                                                        }
                                                    ),
                                                colors = ButtonDefaults.colors(
                                                    containerColor = Color(0xFF2E51A2),
                                                    focusedContainerColor = TvAccentPurple
                                                ),
                                                shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp))
                                            ) {
                                                Text(
                                                    text = "\uD83D\uDD17 Login MAL",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }

                                    if (malUser == null) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(TvSurfaceColor, RoundedCornerShape(8.dp))
                                                .padding(10.dp)
                                        ) {
                                            Text(
                                                text = "Supports MyAnimeList Username or OAuth2 Bearer Token (${AniListSyncManager.MAL_OAUTH_AUTH_URL})",
                                                color = TvAccentPurple,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        SettingsCategory.APPEARANCE -> {
                            item {
                                SettingOptionSelector(
                                    title = "TV Remote Focus Border Color",
                                    description = "High-contrast outline color applied around focused cards and buttons",
                                    options = listOf("White", "Crimson Red", "Gold", "Neon Purple", "Cyan"),
                                    selectedOption = currentSettings.focusRingColor,
                                    onSelectOption = {
                                        updateSettings(currentSettings.copy(focusRingColor = it), "Focus border color set to $it")
                                    },
                                    isFirstItem = true,
                                    onNavigateUp = onNavigateUp,
                                    onFocusTopItem = scrollToTop
                                )
                            }
                            item {
                                SettingOptionSelector(
                                    title = "Remote Focus Zoom Scale",
                                    description = "Scale factor applied when hovering or focusing interactive TV elements",
                                    options = listOf("1.05x", "1.08x", "1.12x", "1.15x"),
                                    selectedOption = "${currentSettings.focusScale}x",
                                    onSelectOption = {
                                        val scale = it.replace("x", "").toFloatOrNull() ?: 1.08f
                                        updateSettings(currentSettings.copy(focusScale = scale), "Focus scale set to $it")
                                    }
                                )
                            }
                            item {
                                SettingToggleRow(
                                    title = "Hardware Video Acceleration",
                                    description = "Use MediaCodec GPU hardware decoder for smooth 60fps playback",
                                    isChecked = currentSettings.hardwareAcceleration,
                                    onToggle = {
                                        updateSettings(currentSettings.copy(hardwareAcceleration = it), if (it) "Hardware acceleration enabled" else "Software decoding selected")
                                    }
                                )
                            }
                            item {
                                SettingToggleRow(
                                    title = "UI Sound Effects",
                                    description = "Play subtle acoustic feedback on D-pad focus ticks, remote selections, and watchlist actions",
                                    isChecked = currentSettings.uiSoundEffects,
                                    onToggle = {
                                        TvSoundManager.setEnabled(it)
                                        updateSettings(currentSettings.copy(uiSoundEffects = it), if (it) "UI Sound Effects enabled" else "UI Sound Effects muted")
                                    }
                                )
                            }
                        }

                        SettingsCategory.SOURCES -> {
                            item {
                                SettingOptionSelector(
                                    title = "Primary Catalog Provider",
                                    description = "Metadata source for seasonal anime listings, trending shelves, and search",
                                    options = listOf("AniList", "Jikan", "Direct"),
                                    selectedOption = currentSettings.primaryCatalog,
                                    onSelectOption = {
                                        updateSettings(currentSettings.copy(primaryCatalog = it), "Catalog set to $it")
                                    },
                                    isFirstItem = true,
                                    onNavigateUp = onNavigateUp,
                                    onFocusTopItem = scrollToTop
                                )
                            }
                            item {
                                SettingToggleRow(
                                    title = "Automatic Stream Failover",
                                    description = "Seamlessly retry alternate streaming servers if primary stream fails",
                                    isChecked = currentSettings.autoFailover,
                                    onToggle = {
                                        updateSettings(currentSettings.copy(autoFailover = it), if (it) "Failover enabled" else "Failover disabled")
                                    }
                                )
                            }
                            item {
                                // Sources Management Toolbar
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(TvDarkBackground, RoundedCornerShape(12.dp))
                                        .border(1.dp, TvCardBorder, RoundedCornerShape(12.dp))
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        text = "Scraper Actions & Benchmark",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Probe latency, measure CDN bandwidth, auto-rank priority, or add custom scrapers",
                                        color = TvTextSecondary,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(
                                            onClick = { runHealthCheck() },
                                            colors = ButtonDefaults.colors(
                                                containerColor = TvSurfaceColor,
                                                focusedContainerColor = TvAccentPurple
                                            ),
                                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                            modifier = Modifier.tvFocusableItem(
                                                onClick = { runHealthCheck() },
                                                focusedScale = 1.08f,
                                                borderWidth = 3.dp,
                                                focusedBorderColor = Color.White
                                            )
                                        ) {
                                            Text(
                                                text = if (isTestingHealth) "Testing..." else "⚡ Test Latency",
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                        }

                                        Button(
                                            onClick = { runBenchmark() },
                                            colors = ButtonDefaults.colors(
                                                containerColor = TvAccentPurple.copy(alpha = 0.85f),
                                                focusedContainerColor = TvPrimaryRed
                                            ),
                                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                            modifier = Modifier.tvFocusableItem(
                                                onClick = { runBenchmark() },
                                                focusedScale = 1.08f,
                                                borderWidth = 3.dp,
                                                focusedBorderColor = Color.White
                                            )
                                        ) {
                                            Text(
                                                text = if (isBenchmarking) "Benchmarking..." else "🚀 Benchmark Speed",
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                        }

                                        if (benchmarkResults.isNotEmpty()) {
                                            Button(
                                                onClick = { autoRankSourcesBySpeed() },
                                                colors = ButtonDefaults.colors(
                                                    containerColor = TvSuccessGreen,
                                                    focusedContainerColor = TvAccentPurple
                                                ),
                                                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                                modifier = Modifier.tvFocusableItem(
                                                    onClick = { autoRankSourcesBySpeed() },
                                                    focusedScale = 1.08f,
                                                    borderWidth = 3.dp,
                                                    focusedBorderColor = Color.White
                                                )
                                            ) {
                                                Text(
                                                    text = "⚡ Auto-Rank",
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1
                                                )
                                            }
                                        }

                                        Button(
                                            onClick = { showAddSourceDialog = true },
                                            colors = ButtonDefaults.colors(
                                                containerColor = TvPrimaryRed,
                                                focusedContainerColor = TvAccentPurple
                                            ),
                                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                            modifier = Modifier.tvFocusableItem(
                                                onClick = { showAddSourceDialog = true },
                                                focusedScale = 1.08f,
                                                borderWidth = 3.dp,
                                                focusedBorderColor = Color.White
                                            )
                                        ) {
                                            Text(
                                                text = "+ Add Source",
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                coroutineScope.launch {
                                                    showFeedback("Checking Community Extensions...")
                                                    val res = com.darknet.animetv.data.source.extension.CloudExtensionManager.syncRepository(
                                                        com.darknet.animetv.data.source.extension.CloudExtensionManager.DEFAULT_COMMUNITY_REPO_URL,
                                                        storage
                                                    )
                                                    if (res.isSuccess) {
                                                        showFeedback("Installed ${res.getOrNull()} Cloud Extensions")
                                                    } else {
                                                        val sample = com.darknet.animetv.data.source.extension.CloudExtensionManager.parseRepositoryJson(
                                                            com.darknet.animetv.data.source.extension.CloudExtensionManager.getSampleRepositoryJson()
                                                        )
                                                        sample.extensions.forEach { ext ->
                                                            com.darknet.animetv.data.source.extension.CloudExtensionManager.installExtension(ext, storage)
                                                        }
                                                        showFeedback("Loaded ${sample.extensions.size} Extensions")
                                                    }
                                                    refreshSources()
                                                }
                                            },
                                            colors = ButtonDefaults.colors(
                                                containerColor = Color(0xFF2C324B),
                                                focusedContainerColor = TvAccentPurple
                                            ),
                                            shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                            modifier = Modifier.tvFocusableItem(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        showFeedback("Checking Community Extensions...")
                                                        val res = com.darknet.animetv.data.source.extension.CloudExtensionManager.syncRepository(
                                                            com.darknet.animetv.data.source.extension.CloudExtensionManager.DEFAULT_COMMUNITY_REPO_URL,
                                                            storage
                                                        )
                                                        if (res.isSuccess) {
                                                            showFeedback("Installed ${res.getOrNull()} Cloud Extensions")
                                                        } else {
                                                            val sample = com.darknet.animetv.data.source.extension.CloudExtensionManager.parseRepositoryJson(
                                                                com.darknet.animetv.data.source.extension.CloudExtensionManager.getSampleRepositoryJson()
                                                            )
                                                            sample.extensions.forEach { ext ->
                                                                com.darknet.animetv.data.source.extension.CloudExtensionManager.installExtension(ext, storage)
                                                            }
                                                            showFeedback("Loaded ${sample.extensions.size} Extensions")
                                                        }
                                                        refreshSources()
                                                    }
                                                },
                                                focusedScale = 1.08f,
                                                borderWidth = 3.dp,
                                                focusedBorderColor = Color.White
                                            )
                                        ) {
                                            Text(
                                                text = "⚡ Sync Cloud",
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                        }
                                    }

                                    if (benchmarkProgress != null) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = benchmarkProgress ?: "",
                                            color = TvAccentPurple,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                            item {
                                Text(
                                    text = "Streaming Sources & Priority Order (${sourcesList.size})",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                                )
                            }
                            itemsIndexed(sourcesList, key = { _, s -> s.id }) { index, source ->
                                val health = healthResults.entries.firstOrNull {
                                    source.sourceType.equals(it.key, ignoreCase = true) ||
                                        source.name.contains(it.key, ignoreCase = true) ||
                                        it.key.contains(source.name, ignoreCase = true)
                                }?.value

                                val benchmark = benchmarkResults[source.sourceType.lowercase()]
                                    ?: benchmarkResults[source.name.lowercase()]
                                    ?: benchmarkResults.values.firstOrNull {
                                        source.sourceType.contains(it.sourceId, ignoreCase = true) ||
                                            source.name.contains(it.sourceName, ignoreCase = true)
                                    }

                                SettingSourceItemCard(
                                    rank = index + 1,
                                    source = source,
                                    health = health,
                                    benchmark = benchmark,
                                    canMoveUp = index > 0,
                                    canMoveDown = index < sourcesList.size - 1,
                                    onToggle = { toggleSource(source) },
                                    onMoveUp = { moveSource(index, index - 1) },
                                    onMoveDown = { moveSource(index, index + 1) },
                                    onDelete = { deleteSource(source.id) }
                                )
                            }
                        }

                        SettingsCategory.STORAGE -> {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    StorageMetricCard(
                                        title = "Watch History",
                                        value = "$historyCount Episodes",
                                        subtitle = "Saved watch timestamps & progress",
                                        modifier = Modifier.weight(1f)
                                    )
                                    StorageMetricCard(
                                        title = "My Watchlist",
                                        value = "$watchlistCount Anime",
                                        subtitle = "Bookmarked series & movies",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            item {
                                SettingActionRow(
                                    title = "Clear Watch History",
                                    description = "Erase all saved episode progress and Continue Watching shelves",
                                    buttonText = "\uD83D\uDDD1\uFE0F Clear History",
                                    buttonColor = TvPrimaryRed,
                                    onClick = {
                                        storage.clearWatchHistory()
                                        historyCount = 0
                                        showFeedback("Watch history erased")
                                    },
                                    isFirstItem = true,
                                    onNavigateUp = onNavigateUp,
                                    onFocusTopItem = scrollToTop
                                )
                            }
                            item {
                                SettingActionRow(
                                    title = "Clear Watchlist Bookmarks",
                                    description = "Remove all bookmarked anime from My Watchlist",
                                    buttonText = "\u2B50 Clear Watchlist",
                                    buttonColor = TvPrimaryRed,
                                    onClick = {
                                        storage.clearWatchlist()
                                        watchlistCount = 0
                                        showFeedback("Watchlist erased")
                                    }
                                )
                            }
                            item {
                                SettingActionRow(
                                    title = "Reset All Settings to Defaults",
                                    description = "Restore factory default video playback, focus, and scraper settings",
                                    buttonText = "\uD83D\uDD04 Reset Defaults",
                                    buttonColor = TvAccentPurple,
                                    onClick = {
                                        storage.resetSettings()
                                        currentSettings = storage.getUserSettings()
                                        showFeedback("Settings reset to defaults")
                                    }
                                )
                            }
                        }

                        SettingsCategory.ABOUT -> {
                            item {
                                SettingInfoCard(
                                    title = "TanukiTV Application",
                                    value = "Version ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                                    description = "GitHub OTA Repository: github.com/${UpdateManager.DEFAULT_GITHUB_REPO} \u2022 Package: ${BuildConfig.APPLICATION_ID}"
                                )
                            }
                            item {
                                SettingActionRow(
                                    title = "Check for App Updates (GitHub OTA)",
                                    description = "Check github.com/${UpdateManager.DEFAULT_GITHUB_REPO}/releases for new APK updates and install directly on your TV",
                                    buttonText = if (isCheckingUpdates) "Checking..." else if (isDownloadingUpdate) "Downloading ${downloadProgress}%" else "\uD83D\uDD04 Check Now",
                                    buttonColor = TvAccentPurple,
                                    onClick = { checkAppUpdates() },
                                    isFirstItem = true,
                                    onNavigateUp = onNavigateUp,
                                    onFocusTopItem = scrollToTop,
                                    buttonFocusRequester = rightPanelFirstItemFocusRequester
                                )
                            }
                            if (updateInfo != null) {
                                item {
                                    if (updateInfo!!.hasUpdate) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(TvDarkBackground, RoundedCornerShape(12.dp))
                                                .border(2.dp, TvSuccessGreen, RoundedCornerShape(12.dp))
                                                .padding(16.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "\uD83C\uDF89 New Update Available: v${updateInfo!!.latestVersion}",
                                                        color = TvSuccessGreen,
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = updateInfo!!.releaseTitle,
                                                        color = Color.White,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = updateInfo!!.releaseNotes,
                                                        color = TvTextSecondary,
                                                        fontSize = 12.sp,
                                                        maxLines = 4
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = "Package Size: ${updateInfo!!.formattedSize()}",
                                                        color = TvAccentPurple,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(16.dp))
                                                Button(
                                                    onClick = { downloadAndInstallUpdate(updateInfo!!.apkDownloadUrl) },
                                                    modifier = Modifier.tvFocusableItem(
                                                        onClick = { downloadAndInstallUpdate(updateInfo!!.apkDownloadUrl) },
                                                        focusedScale = 1.08f,
                                                        borderWidth = 3.dp,
                                                        focusedBorderColor = Color.White,
                                                        shape = RoundedCornerShape(8.dp)
                                                    ),
                                                    colors = ButtonDefaults.colors(
                                                        containerColor = TvSuccessGreen,
                                                        focusedContainerColor = TvPrimaryRed
                                                    ),
                                                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp))
                                                ) {
                                                    Text(
                                                        text = if (isDownloadingUpdate) "\u2B07\uFE0F ${downloadProgress}%" else "\u2B07\uFE0F Install Update",
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        SettingInfoCard(
                                            title = "Update Status",
                                            value = "TanukiTV is Up to Date",
                                            description = updateInfo!!.releaseNotes
                                        )
                                    }
                                }
                            }
                            item {
                                SettingInfoCard(
                                    title = "Target Form Factor & Navigation",
                                    value = "10-Foot Leanback Remote Experience",
                                    description = "Engineered exclusively for D-pad navigation \u2022 No touchscreen required"
                                )
                            }
                            item {
                                SettingInfoCard(
                                    title = "Video Engine",
                                    value = "AndroidX Media3 ExoPlayer",
                                    description = "Adaptive HLS/DASH/MP4 streaming \u2022 Subtitle Styler \u2022 External Player Hand-off"
                                )
                            }
                            item {
                                SettingInfoCard(
                                    title = "Running Device Info",
                                    value = "${Build.MANUFACTURER.uppercase()} ${Build.MODEL}",
                                    description = "Android SDK ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE}) \u2022 TV Landscape"
                                )
                            }
                        }
                    }
                }
            }
        }

        // Add Custom Source Dialog
        if (showAddSourceDialog) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { showAddSourceDialog = false }) {
                Box(
                    modifier = Modifier
                        .width(580.dp)
                        .background(TvSurfaceColor, RoundedCornerShape(16.dp))
                        .border(2.dp, TvCardBorder, RoundedCornerShape(16.dp))
                        .padding(28.dp)
                ) {
                    Column {
                        Text(
                            text = "Add Custom Streaming Source",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Source Name", color = TvTextSecondary, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        BasicTextField(
                            value = newSourceName,
                            onValueChange = { newSourceName = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TvDarkBackground, RoundedCornerShape(8.dp))
                                .border(1.dp, TvCardBorder, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Base URL (e.g. https://my-anime-stream.org)", color = TvTextSecondary, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        BasicTextField(
                            value = newSourceUrl,
                            onValueChange = { newSourceUrl = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TvDarkBackground, RoundedCornerShape(8.dp))
                                .border(1.dp, TvCardBorder, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { showAddSourceDialog = false },
                                colors = ButtonDefaults.colors(containerColor = TvSurfaceColor, focusedContainerColor = TvAccentPurple),
                                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                modifier = Modifier.tvFocusableItem(onClick = { showAddSourceDialog = false })
                            ) {
                                Text("Cancel", color = Color.White, fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = {
                                    if (newSourceName.isNotBlank() && newSourceUrl.isNotBlank()) {
                                        val created = CustomSource(
                                            name = newSourceName.trim(),
                                            baseUrl = newSourceUrl.trim(),
                                            isEnabled = true,
                                            sourceType = "HTML_SCRAPER"
                                        )
                                        storage.insertSource(created)
                                        refreshSources()
                                        newSourceName = ""
                                        newSourceUrl = ""
                                        showAddSourceDialog = false
                                        showFeedback("Custom source added successfully")
                                    } else {
                                        showFeedback("Please fill out name and URL")
                                    }
                                },
                                colors = ButtonDefaults.colors(containerColor = TvPrimaryRed, focusedContainerColor = TvAccentPurple),
                                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                modifier = Modifier.tvFocusableItem(onClick = {
                                    if (newSourceName.isNotBlank() && newSourceUrl.isNotBlank()) {
                                        val created = CustomSource(
                                            name = newSourceName.trim(),
                                            baseUrl = newSourceUrl.trim(),
                                            isEnabled = true,
                                            sourceType = "HTML_SCRAPER"
                                        )
                                        storage.insertSource(created)
                                        refreshSources()
                                        newSourceName = ""
                                        newSourceUrl = ""
                                        showAddSourceDialog = false
                                        showFeedback("Custom source added successfully")
                                    } else {
                                        showFeedback("Please fill out name and URL")
                                    }
                                })
                            ) {
                                Text("Save Source", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Link AniList Account Dialog
        if (showAniListLoginDialog) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { showAniListLoginDialog = false }) {
                Box(
                    modifier = Modifier
                        .width(560.dp)
                        .background(TvSurfaceColor, RoundedCornerShape(16.dp))
                        .border(2.dp, TvAccentPurple, RoundedCornerShape(16.dp))
                        .padding(28.dp)
                ) {
                    Column {
                        Text(
                            text = "Link AniList Account",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Enter your public AniList username or paste a Personal OAuth Token to sync your real anime library.",
                            color = TvTextSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Text("AniList Username or OAuth Access Token", color = TvTextSecondary, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        BasicTextField(
                            value = aniListInputText,
                            onValueChange = { aniListInputText = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TvDarkBackground, RoundedCornerShape(8.dp))
                                .border(1.dp, TvCardBorder, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { showAniListLoginDialog = false },
                                colors = ButtonDefaults.colors(containerColor = TvSurfaceColor, focusedContainerColor = TvAccentPurple),
                                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp))
                            ) {
                                Text("Cancel", color = Color.White, fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = {
                                    val input = aniListInputText.trim()
                                    if (input.isNotBlank()) {
                                        showAniListLoginDialog = false
                                        coroutineScope.launch {
                                            showFeedback("Linking AniList account...")
                                            val linkedUser = if (input.length > 40) {
                                                AniListSyncManager.loginWithToken(input, storage).getOrNull()
                                            } else {
                                                AniListSyncManager.loginWithUsername(input, storage).getOrNull()
                                            }
                                            if (linkedUser != null) {
                                                aniListUser = linkedUser
                                                val syncRes = AniListSyncManager.syncAniListWatchlist(storage)
                                                watchlistCount = storage.getWatchlistCount()
                                                historyCount = storage.getWatchHistoryCount()
                                                aniListInputText = ""
                                                showFeedback(syncRes.message)
                                            } else {
                                                showFeedback("AniList user '$input' not found")
                                            }
                                        }
                                    } else {
                                        showFeedback("Please enter your AniList username")
                                    }
                                },
                                colors = ButtonDefaults.colors(containerColor = TvPrimaryRed, focusedContainerColor = TvAccentPurple),
                                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp))
                            ) {
                                Text("Connect & Sync", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Link MyAnimeList (MAL) Account Dialog
        if (showMalLoginDialog) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { showMalLoginDialog = false }) {
                Box(
                    modifier = Modifier
                        .width(560.dp)
                        .background(TvSurfaceColor, RoundedCornerShape(16.dp))
                        .border(2.dp, Color(0xFF2E51A2), RoundedCornerShape(16.dp))
                        .padding(28.dp)
                ) {
                    Column {
                        Text(
                            text = "Login with MyAnimeList (MAL)",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Enter your MyAnimeList username or paste a MAL OAuth2 Access Token to link your account and sync your anime library.",
                            color = TvTextSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Text("MyAnimeList Username or OAuth2 Access Token", color = TvTextSecondary, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        BasicTextField(
                            value = malInputText,
                            onValueChange = { malInputText = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TvDarkBackground, RoundedCornerShape(8.dp))
                                .border(1.dp, TvCardBorder, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { showMalLoginDialog = false },
                                colors = ButtonDefaults.colors(containerColor = TvSurfaceColor, focusedContainerColor = TvAccentPurple),
                                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp))
                            ) {
                                Text("Cancel", color = Color.White, fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = {
                                    val input = malInputText.trim()
                                    if (input.isNotBlank()) {
                                        showMalLoginDialog = false
                                        coroutineScope.launch {
                                            showFeedback("Linking MyAnimeList account...")
                                            val linkedMal = if (input.length > 40) {
                                                AniListSyncManager.loginMalWithToken(input, storage).getOrNull()
                                            } else {
                                                AniListSyncManager.loginMalWithUsername(input, storage).getOrNull()
                                            }
                                            val targetName = linkedMal?.name ?: input
                                            val syncRes = AniListSyncManager.syncMalWatchlist(targetName, storage)
                                            malUser = storage.getMalUser()
                                            malUsername = storage.getMalUsername()
                                            watchlistCount = storage.getWatchlistCount()
                                            historyCount = storage.getWatchHistoryCount()
                                            if (linkedMal != null || syncRes.isSuccess) {
                                                malInputText = ""
                                                showFeedback(syncRes.message)
                                            } else {
                                                showFeedback("MyAnimeList user '$input' not found")
                                            }
                                        }
                                    } else {
                                        showFeedback("Please enter your MyAnimeList username")
                                    }
                                },
                                colors = ButtonDefaults.colors(containerColor = Color(0xFF2E51A2), focusedContainerColor = TvAccentPurple),
                                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp))
                            ) {
                                Text("Login & Sync", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Interactive Horizontal Option Selector for TV Remote navigation.
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingOptionSelector(
    title: String,
    description: String,
    options: List<String>,
    selectedOption: String,
    onSelectOption: (String) -> Unit,
    modifier: Modifier = Modifier,
    isFirstItem: Boolean = false,
    onNavigateUp: (() -> Unit)? = null,
    onFocusTopItem: (() -> Unit)? = null
) {
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .background(TvDarkBackground, RoundedCornerShape(12.dp))
            .border(1.dp, TvCardBorder, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = description,
            color = TvTextSecondary,
            fontSize = 12.sp,
            maxLines = 2
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { optIndex, option ->
                val isSelected = selectedOption.equals(option, ignoreCase = true)
                var isFocused by remember { mutableStateOf(false) }

                Button(
                    onClick = { onSelectOption(option) },
                    modifier = Modifier
                        .tvFocusableItem(
                            onClick = { onSelectOption(option) },
                            focusedScale = 1.08f,
                            borderWidth = 3.dp,
                            focusedBorderColor = Color.White,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                if (optIndex == 0 && keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                                    focusManager.moveFocus(FocusDirection.Left)
                                    true
                                } else if (isFirstItem && onNavigateUp != null && keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                                    onNavigateUp()
                                    true
                                } else false
                            } else false
                        }
                        .onFocusChanged {
                            isFocused = it.isFocused
                            if (it.isFocused) {
                                if (isFirstItem && onFocusTopItem != null) {
                                    onFocusTopItem()
                                }
                                coroutineScope.launch {
                                    bringIntoViewRequester.bringIntoView()
                                }
                            }
                        },
                    colors = ButtonDefaults.colors(
                        containerColor = if (isSelected) TvPrimaryRed else TvSurfaceColor,
                        focusedContainerColor = TvAccentPurple
                    ),
                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp))
                ) {
                    Text(
                        text = option,
                        color = if (isFocused || isSelected) Color.White else TvTextSecondary,
                        fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Setting Toggle Row with ON/OFF TV button.
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingToggleRow(
    title: String,
    description: String,
    isChecked: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isFirstItem: Boolean = false,
    onNavigateUp: (() -> Unit)? = null,
    onFocusTopItem: (() -> Unit)? = null
) {
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .background(TvDarkBackground, RoundedCornerShape(12.dp))
            .border(1.dp, TvCardBorder, RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                color = TvTextSecondary,
                fontSize = 12.sp,
                maxLines = 2
            )
        }
        Spacer(modifier = Modifier.width(16.dp))

        var isFocused by remember { mutableStateOf(false) }
        Button(
            onClick = { onToggle(!isChecked) },
            modifier = Modifier
                .tvFocusableItem(
                    onClick = { onToggle(!isChecked) },
                    focusedScale = 1.08f,
                    borderWidth = 3.dp,
                    focusedBorderColor = Color.White,
                    shape = RoundedCornerShape(8.dp)
                )
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                        if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                            focusManager.moveFocus(FocusDirection.Left)
                            true
                        } else if (isFirstItem && onNavigateUp != null && keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                            onNavigateUp()
                            true
                        } else false
                    } else false
                }
                .onFocusChanged {
                    isFocused = it.isFocused
                    if (it.isFocused) {
                        if (isFirstItem && onFocusTopItem != null) {
                            onFocusTopItem()
                        }
                        coroutineScope.launch {
                            bringIntoViewRequester.bringIntoView()
                        }
                    }
                },
            colors = ButtonDefaults.colors(
                containerColor = if (isChecked) TvSuccessGreen else TvSurfaceColor,
                focusedContainerColor = TvAccentPurple
            ),
            shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp))
        ) {
            Text(
                text = if (isChecked) "\u2714 ON" else "OFF",
                color = if (isFocused || isChecked) Color.White else TvTextSecondary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1
            )
        }
    }
}

/**
 * Setting Action Button Row (e.g. Clear Data, Reset).
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingActionRow(
    title: String,
    description: String,
    buttonText: String,
    buttonColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFirstItem: Boolean = false,
    onNavigateUp: (() -> Unit)? = null,
    onFocusTopItem: (() -> Unit)? = null,
    buttonFocusRequester: FocusRequester? = null
) {
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .background(TvDarkBackground, RoundedCornerShape(12.dp))
            .border(1.dp, TvCardBorder, RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                color = TvTextSecondary,
                fontSize = 12.sp,
                maxLines = 2
            )
        }
        Spacer(modifier = Modifier.width(16.dp))

        var isFocused by remember { mutableStateOf(false) }
        Button(
            onClick = onClick,
            modifier = (if (buttonFocusRequester != null) Modifier.focusRequester(buttonFocusRequester) else Modifier)
                .tvFocusableItem(
                    onClick = onClick,
                    focusedScale = 1.08f,
                    borderWidth = 3.dp,
                    focusedBorderColor = Color.White,
                    shape = RoundedCornerShape(8.dp)
                )
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                        if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                            focusManager.moveFocus(FocusDirection.Left)
                            true
                        } else if (isFirstItem && onNavigateUp != null && keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                            onNavigateUp()
                            true
                        } else false
                    } else false
                }
                .onFocusChanged {
                    isFocused = it.isFocused
                    if (it.isFocused) {
                        if (isFirstItem && onFocusTopItem != null) {
                            onFocusTopItem()
                        }
                        coroutineScope.launch {
                            bringIntoViewRequester.bringIntoView()
                        }
                    }
                },
            colors = ButtonDefaults.colors(
                containerColor = buttonColor,
                focusedContainerColor = TvAccentPurple
            ),
            shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp))
        ) {
            Text(
                text = buttonText,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1
            )
        }
    }
}

/**
 * Metric summary card for storage stats.
 */
@Composable
fun StorageMetricCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(TvDarkBackground, RoundedCornerShape(12.dp))
            .border(1.dp, TvCardBorder, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = title,
                color = TvTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = TvAccentPurple,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * Read-only information card.
 */
@Composable
fun SettingInfoCard(
    title: String,
    value: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(TvDarkBackground, RoundedCornerShape(12.dp))
            .border(1.dp, TvCardBorder, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text(
            text = title,
            color = TvTextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = description,
            color = Color(0xFF888EA8),
            fontSize = 12.sp
        )
    }
}

/**
 * Focus-interactive setting card representing a single streaming scraper source.
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingSourceItemCard(
    rank: Int,
    source: CustomSource,
    health: SourceHealthResult?,
    benchmark: StreamBenchmarkResult?,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .background(TvDarkBackground, RoundedCornerShape(12.dp))
            .border(1.dp, TvCardBorder, RoundedCornerShape(12.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Priority Rank Pill (#1, #2...)
        Box(
            modifier = Modifier
                .background(Color(0xFF252A3E), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "#$rank",
                color = TvAccentPurple,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Name, Base URL and Badges
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = source.name,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                if (health != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                if (health.isAvailable) TvSuccessGreen.copy(alpha = 0.2f) else TvPrimaryRed.copy(alpha = 0.2f),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (health.isAvailable) "🟢 ${health.latencyMs}ms" else "🔴 OFFLINE",
                            color = if (health.isAvailable) TvSuccessGreen else TvPrimaryRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (benchmark != null && benchmark.isHealthy) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(TvAccentPurple.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "⚡ ${benchmark.throughputMbps} Mbps",
                            color = TvAccentPurple,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${source.sourceType} • ${source.baseUrl}",
                color = TvTextSecondary,
                fontSize = 12.sp,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Action Buttons: Toggle, Move Up, Move Down, Delete
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Enable/Disable Toggle
            Button(
                onClick = onToggle,
                colors = ButtonDefaults.colors(
                    containerColor = if (source.isEnabled) TvSuccessGreen else TvSurfaceColor,
                    focusedContainerColor = TvAccentPurple
                ),
                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                modifier = Modifier
                    .tvFocusableItem(
                        onClick = onToggle,
                        focusedScale = 1.08f,
                        borderWidth = 3.dp,
                        focusedBorderColor = Color.White,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .onFocusChanged {
                        if (it.isFocused) {
                            coroutineScope.launch { bringIntoViewRequester.bringIntoView() }
                        }
                    }
            ) {
                Text(
                    text = if (source.isEnabled) "✔ ON" else "OFF",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Move Up
            if (canMoveUp) {
                Button(
                    onClick = onMoveUp,
                    colors = ButtonDefaults.colors(
                        containerColor = TvSurfaceColor,
                        focusedContainerColor = TvAccentPurple
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                    modifier = Modifier
                        .tvFocusableItem(
                            onClick = onMoveUp,
                            focusedScale = 1.08f,
                            borderWidth = 3.dp,
                            focusedBorderColor = Color.White,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .onFocusChanged {
                            if (it.isFocused) {
                                coroutineScope.launch { bringIntoViewRequester.bringIntoView() }
                            }
                        }
                ) {
                    Text("▲", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Move Down
            if (canMoveDown) {
                Button(
                    onClick = onMoveDown,
                    colors = ButtonDefaults.colors(
                        containerColor = TvSurfaceColor,
                        focusedContainerColor = TvAccentPurple
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                    modifier = Modifier
                        .tvFocusableItem(
                            onClick = onMoveDown,
                            focusedScale = 1.08f,
                            borderWidth = 3.dp,
                            focusedBorderColor = Color.White,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .onFocusChanged {
                            if (it.isFocused) {
                                coroutineScope.launch { bringIntoViewRequester.bringIntoView() }
                            }
                        }
                ) {
                    Text("▼", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Delete Custom Source
            if (source.id > 12) {
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.colors(
                        containerColor = TvPrimaryRed.copy(alpha = 0.5f),
                        focusedContainerColor = TvPrimaryRed
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                    modifier = Modifier
                        .tvFocusableItem(
                            onClick = onDelete,
                            focusedScale = 1.08f,
                            borderWidth = 3.dp,
                            focusedBorderColor = Color.White,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .onFocusChanged {
                            if (it.isFocused) {
                                coroutineScope.launch { bringIntoViewRequester.bringIntoView() }
                            }
                        }
                ) {
                    Text("🗑", color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}
