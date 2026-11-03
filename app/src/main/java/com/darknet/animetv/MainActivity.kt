package com.darknet.animetv

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import androidx.tv.foundation.lazy.grid.itemsIndexed
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.darknet.animetv.audio.TvSoundManager
import com.darknet.animetv.data.model.Anime
import com.darknet.animetv.data.model.Episode
import com.darknet.animetv.data.model.WatchProgress
import com.darknet.animetv.data.repository.AnimeRepository
import com.darknet.animetv.data.repository.DefaultAnimeRepository
import com.darknet.animetv.data.storage.AppStorage
import com.darknet.animetv.player.TvPlayerScreen
import com.darknet.animetv.ui.components.TvAnimeCard
import com.darknet.animetv.ui.components.tvFocusableItem
import com.darknet.animetv.ui.screens.DetailsScreen
import com.darknet.animetv.ui.screens.HomeScreen
import com.darknet.animetv.ui.screens.ScheduleScreen
import com.darknet.animetv.ui.screens.SearchScreen
import com.darknet.animetv.ui.screens.SettingsScreen
import com.darknet.animetv.ui.screens.SourcesScreen
import com.darknet.animetv.ui.screens.WatchlistScreen
import com.darknet.animetv.ui.theme.AnimeTvMaterialTheme
import com.darknet.animetv.ui.theme.TvAccentPurple
import com.darknet.animetv.ui.theme.TvBrandCyan
import com.darknet.animetv.ui.theme.TvBrandPurple
import com.darknet.animetv.ui.theme.TvBrandPurpleDark
import com.darknet.animetv.ui.theme.TvCardBorder
import com.darknet.animetv.ui.theme.TvDarkBackground
import com.darknet.animetv.ui.theme.TvPrimaryRed
import com.darknet.animetv.ui.theme.TvSurfaceColor
import com.darknet.animetv.ui.theme.TvSurfaceFocusedColor
import com.darknet.animetv.ui.theme.TvTextPrimary
import com.darknet.animetv.ui.theme.TvTextSecondary

data class PlaybackSession(
    val anime: Anime,
    val episode: Episode,
    val allEpisodes: List<Episode>,
    val initialSeekMs: Long = 0L
)

@OptIn(ExperimentalTvMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private var initialDeepLinkUri by mutableStateOf<android.net.Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialDeepLinkUri = intent?.data

        val storage = AnimeTvApplication.appStorage
        val repository = DefaultAnimeRepository.getInstance(storage)

        setContent {
            AnimeTvMaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    TvMainLeanbackContainer(
                        storage = storage,
                        repository = repository,
                        deepLinkUri = initialDeepLinkUri
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        initialDeepLinkUri = intent.data
    }
}

/**
 * Main Leanback Container hosting top navigation tabs, drill-down Details navigation,
 * and full-screen Media3 ExoPlayer video playback.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvMainLeanbackContainer(
    storage: AppStorage,
    repository: AnimeRepository = DefaultAnimeRepository.getInstance(storage),
    deepLinkUri: android.net.Uri? = null,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val navTabs = listOf("Home", "Catalog & Search", "Schedule", "Watchlist", "Settings")
    val navTabFocusRequesters = remember { List(navTabs.size) { FocusRequester() } }
    val heroPlayFocusRequester = remember { FocusRequester() }
    val searchBarFocusRequester = remember { FocusRequester() }
    val scheduleFirstTabFocusRequester = remember { FocusRequester() }
    val watchlistFirstFilterFocusRequester = remember { FocusRequester() }
    var selectedNavTab by remember { mutableIntStateOf(0) }
    var selectedAnimeForDetails by remember { mutableStateOf<Anime?>(null) }
    var activePlaybackSession by remember { mutableStateOf<PlaybackSession?>(null) }
    var activeProfileState by remember { mutableStateOf(storage.getActiveProfile()) }
    var showProfileModal by remember { mutableStateOf(false) }

    val onNavigateToNavTab: () -> Unit = {
        try {
            navTabFocusRequesters.getOrNull(selectedNavTab)?.requestFocus()
        } catch (_: Exception) {}
    }

    LaunchedEffect(Unit) {
        TvSoundManager.init(context)
        TvSoundManager.setEnabled(storage.getUserSettings().uiSoundEffects)
        if (selectedAnimeForDetails == null && activePlaybackSession == null) {
            navTabFocusRequesters.firstOrNull()?.requestFocus()
        }
    }

    // Handle Android TV Back Key navigation from DetailsScreen
    BackHandler(enabled = selectedAnimeForDetails != null && activePlaybackSession == null) {
        TvSoundManager.playBackDismiss()
        selectedAnimeForDetails = null
    }

    // Handle Android TV Back Key on non-Home tabs -> navigate to Home tab before exiting the app
    BackHandler(
        enabled = selectedNavTab != 0 &&
            selectedAnimeForDetails == null &&
            activePlaybackSession == null &&
            !showProfileModal
    ) {
        TvSoundManager.playBackDismiss()
        selectedNavTab = 0
        try {
            navTabFocusRequesters.firstOrNull()?.requestFocus()
        } catch (_: Exception) {}
    }

    fun startPlayback(
        anime: Anime,
        episode: Episode,
        episodesList: List<Episode> = emptyList(),
        initialSeekMs: Long = 0L
    ) {
        val count = if (anime.totalEpisodes > 0) anime.totalEpisodes else maxOf(12, episode.number)
        val effectiveEpisodes = episodesList.ifEmpty {
            (1..count).map { num ->
                Episode(
                    id = "${anime.id}-ep-$num",
                    animeId = anime.id,
                    number = num,
                    title = "Episode $num",
                    thumbnail = anime.backdropUrl.ifBlank { anime.posterUrl }
                )
            }
        }
        val existingProgress = storage.getWatchProgress(anime.id)
        val initialPositionMs = if (initialSeekMs > 0L) {
            initialSeekMs
        } else if (existingProgress != null && existingProgress.episodeNumber == episode.number) {
            existingProgress.positionMs
        } else {
            1_000L
        }
        val initialDurationMs = if (existingProgress != null && existingProgress.durationMs > 0L) {
            existingProgress.durationMs
        } else {
            1_440_000L
        }
        storage.saveWatchProgress(
            WatchProgress(
                animeId = anime.id,
                animeTitle = anime.title,
                posterUrl = anime.posterUrl.ifBlank { anime.backdropUrl },
                episodeId = episode.id,
                episodeNumber = episode.number,
                episodeTitle = episode.title,
                positionMs = initialPositionMs,
                durationMs = initialDurationMs
            )
        )
        activePlaybackSession = PlaybackSession(
            anime = anime,
            episode = episode,
            allEpisodes = effectiveEpisodes,
            initialSeekMs = initialSeekMs
        )
    }

    // Handle incoming deep link URIs (from Android TV home channels or Watch Next cards)
    LaunchedEffect(deepLinkUri) {
        val uri = deepLinkUri ?: return@LaunchedEffect
        if (uri.scheme == "tanukitv" || uri.scheme == "animetv") {
            when (uri.host) {
                "home" -> {
                    selectedNavTab = 0
                    selectedAnimeForDetails = null
                    activePlaybackSession = null
                }
                "details" -> {
                    val animeId = uri.getQueryParameter("id")
                    val title = uri.getQueryParameter("title") ?: "Anime"
                    if (!animeId.isNullOrBlank()) {
                        activePlaybackSession = null
                        val detail = repository.getAnimeDetail(animeId)
                        selectedAnimeForDetails = detail?.anime ?: Anime(
                            id = animeId,
                            title = title,
                            posterUrl = "",
                            backdropUrl = "",
                            synopsis = ""
                        )
                    }
                }
                "player" -> {
                    val animeId = uri.getQueryParameter("animeId")
                    val epId = uri.getQueryParameter("episodeId")
                    val epNum = uri.getQueryParameter("episodeNum")?.toIntOrNull() ?: 1
                    val posMs = uri.getQueryParameter("positionMs")?.toLongOrNull() ?: 0L
                    val title = uri.getQueryParameter("title") ?: "Anime"
                    if (!animeId.isNullOrBlank()) {
                        val detail = repository.getAnimeDetail(animeId)
                        val anime = detail?.anime ?: Anime(
                            id = animeId,
                            title = title,
                            posterUrl = "",
                            backdropUrl = "",
                            synopsis = ""
                        )
                        val epList = detail?.episodes.orEmpty()
                        val targetEp = epList.find { it.number == epNum } ?: Episode(
                            id = epId ?: "$animeId-ep-$epNum",
                            animeId = animeId,
                            number = epNum,
                            title = "Episode $epNum",
                            thumbnail = anime.posterUrl
                        )
                        startPlayback(anime, targetEp, epList, initialSeekMs = posMs)
                    }
                }
            }
        }
    }

    // Publish / refresh Android TV Launcher Trending Channel on startup
    LaunchedEffect(Unit) {
        try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val trending = repository.getTrending()
                com.darknet.animetv.channel.TvChannelManager.publishTrendingChannel(context, trending)
            }
        } catch (_: Exception) {}
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TvDarkBackground)
    ) {
        if (activePlaybackSession != null) {
            val session = activePlaybackSession!!
            TvPlayerScreen(
                anime = session.anime,
                episode = session.episode,
                allEpisodes = session.allEpisodes,
                repository = repository,
                storage = storage,
                initialSeekMs = session.initialSeekMs,
                onSelectEpisode = { nextEpisode ->
                    activePlaybackSession = session.copy(episode = nextEpisode, initialSeekMs = 0L)
                },
                onBack = {
                    activePlaybackSession = null
                }
            )
        } else if (selectedAnimeForDetails != null) {
            // Drill-Down Anime Details Screen
            DetailsScreen(
                anime = selectedAnimeForDetails!!,
                repository = repository,
                storage = storage,
                onPlayEpisode = { anime, episode ->
                    startPlayback(anime, episode)
                },
                onSelectAnime = { targetAnime ->
                    selectedAnimeForDetails = targetAnime
                },
                onBack = { selectedAnimeForDetails = null }
            )
        } else {
            // Top-Level Screen Container with TV Navigation Header and Profile Background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusProperties { canFocus = !showProfileModal }
            ) {
                // Profile Custom Anime Background Scene (renders across main page)
                if (activeProfileState.bgImageUrl.isNotBlank()) {
                    AsyncImage(
                        model = com.darknet.animetv.ui.components.resolveImageModel(activeProfileState.bgImageUrl),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    // Atmospheric dark gradient overlay so UI text, hero banner, and shelves remain clear and legible
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        TvDarkBackground.copy(alpha = 0.65f),
                                        TvDarkBackground.copy(alpha = 0.82f),
                                        TvDarkBackground.copy(alpha = 0.94f)
                                    )
                                )
                            )
                    )
                }

                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // TV Leanback Header & Navigation Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 48.dp, end = 48.dp, top = 24.dp, bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Brand Logo
                        Image(
                            painter = painterResource(id = R.drawable.tanukitv_logo),
                            contentDescription = "TanukiTV",
                            modifier = Modifier.height(48.dp),
                            contentScale = ContentScale.Fit
                        )

                        // Leanback Navigation Bar Tabs
                        val focusManager = LocalFocusManager.current
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // User Profile Switcher Pill
                            Button(
                                onClick = { showProfileModal = true },
                                scale = ButtonDefaults.scale(focusedScale = 1.08f),
                                border = ButtonDefaults.border(
                                    focusedBorder = Border(
                                        border = BorderStroke(3.dp, TvBrandCyan),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                ),
                                colors = ButtonDefaults.colors(
                                    containerColor = Color(activeProfileState.avatarColor).copy(alpha = 0.25f),
                                    focusedContainerColor = TvAccentPurple
                                ),
                                shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)),
                                modifier = Modifier
                                    .focusProperties {
                                        when (selectedNavTab) {
                                            0 -> down = heroPlayFocusRequester
                                            1 -> down = searchBarFocusRequester
                                            2 -> down = scheduleFirstTabFocusRequester
                                            3 -> down = watchlistFirstFilterFocusRequester
                                        }
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (activeProfileState.avatarUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = com.darknet.animetv.ui.components.resolveImageModel(activeProfileState.avatarUrl),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Text(
                                            text = activeProfileState.avatarIcon.ifBlank { if (activeProfileState.isKidsMode) "👶" else "🦝" },
                                            fontSize = 14.sp
                                        )
                                    }
                                    Text(
                                        text = activeProfileState.name,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            navTabs.forEachIndexed { index, title ->
                                var isFocused by remember { mutableStateOf(false) }

                                Button(
                                    onClick = {
                                        if (selectedNavTab != index) {
                                            TvSoundManager.playTabSwitch()
                                            selectedNavTab = index
                                        }
                                    },
                                    scale = ButtonDefaults.scale(focusedScale = 1.08f),
                                    border = ButtonDefaults.border(
                                        focusedBorder = Border(
                                            border = BorderStroke(3.dp, TvBrandCyan),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    ),
                                    colors = ButtonDefaults.colors(
                                        containerColor = if (selectedNavTab == index) TvPrimaryRed else TvSurfaceColor,
                                        focusedContainerColor = if (selectedNavTab == index) TvBrandPurple else TvBrandPurpleDark
                                    ),
                                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(12.dp)),
                                    modifier = Modifier
                                        .focusRequester(navTabFocusRequesters[index])
                                        .focusProperties {
                                            when (selectedNavTab) {
                                                0 -> down = heroPlayFocusRequester
                                                1 -> down = searchBarFocusRequester
                                                2 -> down = scheduleFirstTabFocusRequester
                                                3 -> down = watchlistFirstFilterFocusRequester
                                            }
                                        }
                                        .onFocusChanged {
                                            if (it.isFocused && !isFocused) {
                                                TvSoundManager.playFocusTick()
                                            }
                                            isFocused = it.isFocused
                                        }
                                ) {
                                    Text(
                                        text = title,
                                        color = if (isFocused || selectedNavTab == index) Color.White else TvTextSecondary,
                                        fontWeight = if (isFocused || selectedNavTab == index) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Active Tab Content with smooth Crossfade transition
                Crossfade(
                    targetState = selectedNavTab,
                    animationSpec = tween(durationMillis = 220),
                    label = "tv_tab_crossfade",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) { activeTab ->
                    when (activeTab) {
                        0 -> HomeScreen(
                            repository = repository,
                            storage = storage,
                            onAnimeClick = { selectedAnimeForDetails = it },
                            onPlayClick = { anime ->
                                val saved = storage.getWatchProgress(anime.id)
                                val epNum = saved?.episodeNumber ?: 1
                                val epTitle = saved?.episodeTitle ?: "Episode $epNum"
                                val ep = Episode(
                                    id = saved?.episodeId ?: "${anime.id}-ep-$epNum",
                                    animeId = anime.id,
                                    number = epNum,
                                    title = epTitle,
                                    thumbnail = anime.backdropUrl.ifBlank { anime.posterUrl }
                                )
                                startPlayback(anime, ep)
                            },
                            onContinueWatchingClick = { progress ->
                                val anime = Anime(
                                    id = progress.animeId,
                                    title = progress.animeTitle,
                                    posterUrl = progress.posterUrl,
                                    backdropUrl = progress.posterUrl
                                )
                                val ep = Episode(
                                    id = progress.episodeId,
                                    animeId = progress.animeId,
                                    number = progress.episodeNumber,
                                    title = progress.episodeTitle,
                                    thumbnail = progress.posterUrl
                                )
                                startPlayback(anime, ep)
                            },
                            onNavigateUp = onNavigateToNavTab,
                            heroPlayFocusRequester = heroPlayFocusRequester
                        )
                        1 -> SearchScreen(
                            repository = repository,
                            onAnimeClick = { selectedAnimeForDetails = it },
                            onNavigateUp = onNavigateToNavTab,
                            searchBarFocusRequester = searchBarFocusRequester
                        )
                        2 -> ScheduleScreen(
                            repository = repository,
                            onAnimeClick = { selectedAnimeForDetails = it },
                            onNavigateUp = onNavigateToNavTab,
                            firstTabFocusRequester = scheduleFirstTabFocusRequester
                        )
                        3 -> WatchlistScreen(
                            storage = storage,
                            onAnimeClick = { selectedAnimeForDetails = it },
                            onExploreClick = {
                                TvSoundManager.playTabSwitch()
                                selectedNavTab = 1
                            },
                            onNavigateUp = onNavigateToNavTab,
                            firstFilterFocusRequester = watchlistFirstFilterFocusRequester
                        )
                        4 -> SettingsScreen(
                            storage = storage,
                            repository = repository,
                            onNavigateUp = onNavigateToNavTab
                        )
                    }
                }
            }
        }
    }

        // TV Profile Switcher Modal
        if (showProfileModal) {
            com.darknet.animetv.ui.components.TvProfileSwitcherModal(
                profiles = storage.getProfiles(),
                activeProfile = activeProfileState,
                userAccount = storage.getUserAccount(),
                onSelectProfile = { newProfile ->
                    storage.setActiveProfile(newProfile.id)
                    activeProfileState = newProfile
                    showProfileModal = false
                },
                onSaveProfile = { updated ->
                    storage.saveProfile(updated)
                    if (activeProfileState.id == updated.id) {
                        activeProfileState = updated
                    }
                },
                onDeleteProfile = { profileId ->
                    storage.deleteProfile(profileId)
                    activeProfileState = storage.getActiveProfile()
                },
                onSaveAccount = { account ->
                    storage.saveUserAccount(account)
                },
                onLogoutAccount = {
                    storage.clearUserAccount()
                },
                onDismiss = { showProfileModal = false },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
