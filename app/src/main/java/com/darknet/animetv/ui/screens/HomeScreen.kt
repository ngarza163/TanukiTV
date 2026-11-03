package com.darknet.animetv.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import com.darknet.animetv.audio.TvSoundManager
import com.darknet.animetv.data.model.Anime
import com.darknet.animetv.data.model.WatchProgress
import com.darknet.animetv.data.repository.AnimeRepository
import com.darknet.animetv.data.storage.AppStorage
import com.darknet.animetv.ui.components.TvAnimeCard
import com.darknet.animetv.ui.components.TvContinueWatchingCard
import com.darknet.animetv.ui.components.TvHeroBanner
import com.darknet.animetv.ui.components.TvTanukiLoadingIndicator
import com.darknet.animetv.ui.theme.TvAccentPurple
import com.darknet.animetv.ui.theme.TvCardBorder
import com.darknet.animetv.ui.theme.TvDarkBackground
import com.darknet.animetv.ui.theme.TvPrimaryRed
import com.darknet.animetv.ui.theme.TvSurfaceColor
import com.darknet.animetv.ui.theme.TvTextSecondary

/**
 * Android TV Home Screen featuring:
 * - Dynamic 16:9 Hero Banner Carousel at top
 * - Horizontal Leanback Shelves (TvLazyRow within TvLazyColumn):
 *   1. Continue Watching (with progress bar)
 *   2. Trending Anime
 *   3. Popular Anime
 *   4. Recently Updated
 *   5. Top Rated
 */
@Composable
fun HomeScreen(
    repository: AnimeRepository,
    storage: AppStorage,
    onAnimeClick: (Anime) -> Unit,
    onPlayClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
    onContinueWatchingClick: ((WatchProgress) -> Unit)? = null,
    onNavigateUp: (() -> Unit)? = null,
    heroPlayFocusRequester: androidx.compose.ui.focus.FocusRequester? = null
) {
    var trendingList by remember { mutableStateOf<List<Anime>>(emptyList()) }
    var dubbedList by remember { mutableStateOf<List<Anime>>(emptyList()) }
    var popularList by remember { mutableStateOf<List<Anime>>(emptyList()) }
    var recentList by remember { mutableStateOf<List<Anime>>(emptyList()) }
    var continueWatchingList by remember { mutableStateOf<List<WatchProgress>>(emptyList()) }
    var favoriteIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        isLoading = true
        try {
            continueWatchingList = storage.getAllWatchProgress().sortedByDescending { it.lastWatchedTimestamp }
            favoriteIds = storage.getAllFavorites().map { it.id }.toSet()

            coroutineScope {
                val recentDeferred = async { repository.getRecentEpisodes() }
                val trendingDeferred = async { repository.getTrending() }
                val dubbedDeferred = async { repository.getDubbedAnime() }
                val popularDeferred = async { repository.getPopular() }

                recentList = recentDeferred.await()
                trendingList = trendingDeferred.await()
                dubbedList = dubbedDeferred.await()
                popularList = popularDeferred.await()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    val topRatedList = remember(trendingList, popularList) {
        (trendingList + popularList)
            .distinctBy { it.id }
            .sortedByDescending { it.score }
            .take(15)
    }

    val featuredForHero = remember(trendingList) {
        if (trendingList.isNotEmpty()) trendingList.take(5) else emptyList()
    }

    val watchProgressMap = remember(continueWatchingList) {
        continueWatchingList.associateBy { it.animeId }
    }

    if (isLoading && trendingList.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            TvTanukiLoadingIndicator(
                statusText = "Summoning anime catalog...",
                subtitleText = "Tanuki is preparing your living-room sanctuary"
            )
        }
        return
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent),
        contentPadding = PaddingValues(bottom = 48.dp)
    ) {
        // Hero Banner Carousel
        if (featuredForHero.isNotEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 48.dp, vertical = 16.dp)
                        .onFocusChanged { focusState ->
                            if (focusState.hasFocus) {
                                coroutineScope.launch {
                                    listState.animateScrollToItem(0)
                                }
                            }
                        }
                ) {
                    TvHeroBanner(
                        featuredAnimeList = featuredForHero,
                        onPlayClick = onPlayClick,
                        onDetailsClick = onAnimeClick,
                        watchProgressMap = watchProgressMap,
                        watchlistIds = favoriteIds,
                        onNavigateUp = {
                            coroutineScope.launch {
                                listState.scrollToItem(0)
                            }
                            onNavigateUp?.invoke()
                        },
                        playFocusRequester = heroPlayFocusRequester,
                        onToggleWatchlist = { anime ->
                            if (favoriteIds.contains(anime.id)) {
                                storage.removeFavorite(anime.id)
                                favoriteIds = favoriteIds - anime.id
                            } else {
                                storage.addFavorite(anime)
                                favoriteIds = favoriteIds + anime.id
                                TvSoundManager.playWatchlistAdd()
                            }
                        }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Shelf 1: Continue Watching
        if (continueWatchingList.isNotEmpty()) {
            item {
                ShelfSection(
                    title = "Continue Watching",
                    itemCount = continueWatchingList.size
                ) {
                    LazyRow(
                        modifier = Modifier.focusProperties {
                            if (heroPlayFocusRequester != null) {
                                up = heroPlayFocusRequester
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(continueWatchingList, key = { it.animeId }) { progress ->
                            TvContinueWatchingCard(
                                progress = progress,
                                onClick = {
                                    if (onContinueWatchingClick != null) {
                                        onContinueWatchingClick(progress)
                                    } else {
                                        onAnimeClick(
                                            Anime(
                                                id = progress.animeId,
                                                title = progress.animeTitle,
                                                posterUrl = progress.posterUrl
                                            )
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Shelf 2: New Releases — Most Recent Episodes (placed right before Trending)
        if (recentList.isNotEmpty()) {
            item {
                ShelfSection(
                    title = "✨ New Releases — Recent Episodes",
                    itemCount = recentList.size,
                    badgeLabel = "JUST AIRED"
                ) {
                    LazyRow(
                        modifier = if (continueWatchingList.isEmpty()) {
                            Modifier.focusProperties {
                                if (heroPlayFocusRequester != null) {
                                    up = heroPlayFocusRequester
                                }
                            }
                        } else Modifier,
                        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(recentList, key = { "recent-${it.id}" }) { anime ->
                            val epBadge = when {
                                anime.latestEpisode > 0 -> "NEW • EP ${anime.latestEpisode}"
                                anime.totalEpisodes > 0 -> "NEW • EP ${anime.totalEpisodes}"
                                else -> "NEW EPISODE"
                            }
                            TvAnimeCard(
                                anime = anime,
                                badgeText = epBadge,
                                onClick = { onAnimeClick(anime) }
                            )
                        }
                    }
                }
            }
        }

        // Shelf 3: Trending Anime
        if (trendingList.isNotEmpty()) {
            item {
                ShelfSection(title = "🔥 Trending Now", itemCount = trendingList.size) {
                    LazyRow(
                        modifier = if (continueWatchingList.isEmpty() && recentList.isEmpty()) {
                            Modifier.focusProperties {
                                if (heroPlayFocusRequester != null) {
                                    up = heroPlayFocusRequester
                                }
                            }
                        } else Modifier,
                        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(trendingList, key = { it.id }) { anime ->
                            TvAnimeCard(
                                anime = anime,
                                onClick = { onAnimeClick(anime) }
                            )
                        }
                    }
                }
            }
        }

        // Shelf 4: Dubbed Anime & Episodes
        if (dubbedList.isNotEmpty()) {
            item {
                ShelfSection(
                    title = "🎙️ Dubbed Anime & Episodes",
                    itemCount = dubbedList.size,
                    badgeLabel = "ENGLISH DUB"
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(dubbedList, key = { "dub-${it.id}" }) { anime ->
                            val dubBadge = if (anime.dubbedEpisodesCount > 0) {
                                "🎙️ DUB • ${anime.dubbedEpisodesCount} EPS"
                            } else {
                                "🎙️ DUB"
                            }
                            TvAnimeCard(
                                anime = anime.copy(hasDub = true),
                                badgeText = dubBadge,
                                onClick = { onAnimeClick(anime.copy(hasDub = true)) }
                            )
                        }
                    }
                }
            }
        }

        // Shelf 5: Popular Anime
        if (popularList.isNotEmpty()) {
            item {
                ShelfSection(title = "Popular Hits", itemCount = popularList.size) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(popularList, key = { it.id }) { anime ->
                            TvAnimeCard(
                                anime = anime,
                                onClick = { onAnimeClick(anime) }
                            )
                        }
                    }
                }
            }
        }

        // Shelf 6: Top Rated
        if (topRatedList.isNotEmpty()) {
            item {
                ShelfSection(title = "Top Rated Anime", itemCount = topRatedList.size) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(topRatedList, key = { it.id }) { anime ->
                            TvAnimeCard(
                                anime = anime,
                                onClick = { onAnimeClick(anime) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShelfSection(
    title: String,
    itemCount: Int,
    modifier: Modifier = Modifier,
    badgeLabel: String? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                if (!badgeLabel.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .background(TvAccentPurple.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                            .border(1.dp, TvAccentPurple.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = badgeLabel,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            if (itemCount > 0) {
                Box(
                    modifier = Modifier
                        .background(TvSurfaceColor.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
                        .border(1.dp, TvCardBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "$itemCount titles",
                        color = TvTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (itemCount == 0) {
            Text(
                text = "No anime in this shelf yet",
                color = TvTextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 48.dp, vertical = 12.dp)
            )
        } else {
            content()
        }
    }
}
