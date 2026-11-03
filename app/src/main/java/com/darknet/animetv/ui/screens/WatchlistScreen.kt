@file:OptIn(
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    androidx.tv.material3.ExperimentalTvMaterial3Api::class
)

package com.darknet.animetv.ui.screens

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.darknet.animetv.data.model.AniListUser
import com.darknet.animetv.data.model.Anime
import com.darknet.animetv.data.model.MalUser
import com.darknet.animetv.data.model.WatchProgress
import com.darknet.animetv.data.repository.AniListSyncManager
import com.darknet.animetv.data.storage.AppStorage
import com.darknet.animetv.ui.components.TvAnimeCard
import com.darknet.animetv.ui.components.tvFocusableItem
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

enum class WatchlistFilter(val label: String) {
    ALL("All"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Completed"),
    PLAN_TO_WATCH("Plan to Watch")
}

enum class WatchlistSort(val label: String) {
    RECENTLY_ADDED("Recently Added"),
    RATING("Highest Rated"),
    TITLE("Title (A-Z)")
}

/**
 * Full-featured Leanback Android TV Watchlist & Bookmarks Screen.
 * Includes category filtering (All, In Progress, Completed, Plan to Watch),
 * sorting, watch progress indicators, quick remove/resume actions,
 * and seamless D-pad escape navigation.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun WatchlistScreen(
    storage: AppStorage,
    onAnimeClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
    onExploreClick: (() -> Unit)? = null,
    onNavigateUp: (() -> Unit)? = null,
    firstFilterFocusRequester: androidx.compose.ui.focus.FocusRequester? = null
) {
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val firstCardFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val filterFocusRequesters = remember { List(WatchlistFilter.values().size) { androidx.compose.ui.focus.FocusRequester() } }
    var aniListUser by remember { mutableStateOf(storage.getAniListUser()) }
    var malUser by remember { mutableStateOf(storage.getMalUser()) }
    var showAniListLoginDialog by remember { mutableStateOf(false) }
    var aniListInputText by remember { mutableStateOf("") }
    var showMalLoginDialog by remember { mutableStateOf(false) }
    var malInputText by remember { mutableStateOf("") }
    var favorites by remember { mutableStateOf<List<Anime>>(emptyList()) }
    var watchProgressMap by remember { mutableStateOf<Map<String, WatchProgress>>(emptyMap()) }
    var selectedFilter by remember { mutableStateOf(WatchlistFilter.ALL) }
    var selectedSort by remember { mutableStateOf(WatchlistSort.RECENTLY_ADDED) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    fun refreshData() {
        favorites = storage.getAllFavorites()
        val allHistory = storage.getAllWatchHistory()
        watchProgressMap = allHistory.associateBy { it.animeId }
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    // Filter and Sort computation
    val filteredList = remember(favorites, watchProgressMap, selectedFilter, selectedSort) {
        val list = when (selectedFilter) {
            WatchlistFilter.ALL -> favorites
            WatchlistFilter.IN_PROGRESS -> favorites.filter { anime ->
                val progress = watchProgressMap[anime.id]
                progress != null && progress.positionMs > 0L && progress.progressPercentage < 0.92f
            }
            WatchlistFilter.COMPLETED -> favorites.filter { anime ->
                val progress = watchProgressMap[anime.id]
                progress != null && progress.progressPercentage >= 0.92f
            }
            WatchlistFilter.PLAN_TO_WATCH -> favorites.filter { anime ->
                val progress = watchProgressMap[anime.id]
                progress == null || progress.positionMs <= 0L
            }
        }

        when (selectedSort) {
            WatchlistSort.RECENTLY_ADDED -> list // Preserves DB added_at DESC order
            WatchlistSort.RATING -> list.sortedByDescending { it.score }
            WatchlistSort.TITLE -> list.sortedBy { it.title.lowercase() }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TvDarkBackground)
            .padding(horizontal = 48.dp, vertical = 16.dp)
    ) {
        // Top Header Row with Counts & Sort Switcher
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "My Watchlist",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Box(
                        modifier = Modifier
                            .background(TvPrimaryRed.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .border(1.dp, TvPrimaryRed.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${favorites.size} Saved",
                            color = TvPrimaryRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Track your favorite anime, active watch progress, and queue",
                    color = TvTextSecondary,
                    fontSize = 13.sp
                )
            }

            // Status message toast banner (e.g. "Removed from Watchlist")
            AnimatedVisibility(
                visible = statusMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .background(TvSurfaceColor, RoundedCornerShape(10.dp))
                        .border(1.dp, TvAccentPurple, RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = statusMessage ?: "",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Filter and Sort Bar with Remote Navigation Escape
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Filter Pills
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WatchlistFilter.values().forEachIndexed { filterIdx, filter ->
                    val isSelected = selectedFilter == filter
                    var isFocused by remember { mutableStateOf(false) }

                    val filterCount = when (filter) {
                        WatchlistFilter.ALL -> favorites.size
                        WatchlistFilter.IN_PROGRESS -> favorites.count {
                            val p = watchProgressMap[it.id]
                            p != null && p.positionMs > 0L && p.progressPercentage < 0.92f
                        }
                        WatchlistFilter.COMPLETED -> favorites.count {
                            val p = watchProgressMap[it.id]
                            p != null && p.progressPercentage >= 0.92f
                        }
                        WatchlistFilter.PLAN_TO_WATCH -> favorites.count {
                            val p = watchProgressMap[it.id]
                            p == null || p.positionMs <= 0L
                        }
                    }

                    Button(
                        onClick = { selectedFilter = filter },
                        scale = ButtonDefaults.scale(focusedScale = 1.08f),
                        border = ButtonDefaults.border(
                            focusedBorder = Border(
                                border = BorderStroke(3.dp, Color.White),
                                shape = RoundedCornerShape(10.dp)
                            )
                        ),
                        modifier = Modifier
                            .then(
                                if (filterIdx == 0 && firstFilterFocusRequester != null) {
                                    Modifier.focusRequester(firstFilterFocusRequester)
                                } else {
                                    Modifier.focusRequester(filterFocusRequesters[filterIdx])
                                }
                            )
                            .focusProperties {
                                if (onNavigateUp != null) {
                                    up = androidx.compose.ui.focus.FocusRequester.Cancel
                                }
                                if (filteredList.isNotEmpty()) {
                                    down = firstCardFocusRequester
                                }
                            }
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                    if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                                        onNavigateUp?.invoke()
                                        true
                                    } else if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                                        if (filteredList.isNotEmpty()) {
                                            try {
                                                firstCardFocusRequester.requestFocus()
                                                true
                                            } catch (_: Exception) {
                                                focusManager.moveFocus(FocusDirection.Down)
                                            }
                                        } else {
                                            focusManager.moveFocus(FocusDirection.Down)
                                        }
                                    } else false
                                } else false
                            }
                            .onFocusChanged { isFocused = it.isFocused },
                        colors = ButtonDefaults.colors(
                            containerColor = if (isSelected) TvPrimaryRed else TvSurfaceColor,
                            focusedContainerColor = TvAccentPurple
                        ),
                        shape = ButtonDefaults.shape(shape = RoundedCornerShape(10.dp))
                    ) {
                        Text(
                            text = "${filter.label} ($filterCount)",
                            color = if (isFocused || isSelected) Color.White else TvTextSecondary,
                            fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Action Buttons: AniList Sync, MyAnimeList Login/Sync & Sort Cycle Button
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                var isSyncFocused by remember { mutableStateOf(false) }
                Button(
                    onClick = {
                        if (aniListUser != null) {
                            coroutineScope.launch {
                                statusMessage = "Syncing from AniList..."
                                val res = AniListSyncManager.syncAniListWatchlist(storage)
                                aniListUser = storage.getAniListUser()
                                refreshData()
                                statusMessage = res.message
                            }
                        } else {
                            showAniListLoginDialog = true
                        }
                    },
                    scale = ButtonDefaults.scale(focusedScale = 1.08f),
                    border = ButtonDefaults.border(
                        focusedBorder = Border(
                            border = BorderStroke(3.dp, Color.White),
                            shape = RoundedCornerShape(10.dp)
                        )
                    ),
                    modifier = Modifier
                        .focusProperties {
                            if (onNavigateUp != null) {
                                up = androidx.compose.ui.focus.FocusRequester.Cancel
                            }
                            if (filteredList.isNotEmpty()) {
                                down = firstCardFocusRequester
                            }
                        }
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                                    onNavigateUp?.invoke()
                                    true
                                } else if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                                    if (filteredList.isNotEmpty()) {
                                        try {
                                            firstCardFocusRequester.requestFocus()
                                            true
                                        } catch (_: Exception) {
                                            focusManager.moveFocus(FocusDirection.Down)
                                        }
                                    } else false
                                } else false
                            } else false
                        }
                        .onFocusChanged { isSyncFocused = it.isFocused },
                    colors = ButtonDefaults.colors(
                        containerColor = if (aniListUser != null) TvAccentPurple else TvSurfaceColor,
                        focusedContainerColor = TvPrimaryRed
                    ),
                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(10.dp))
                ) {
                    Text(
                        text = if (aniListUser != null) "\uD83D\uDD04 ${aniListUser!!.name}" else "\uD83D\uDD17 Link AniList",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }

                // MyAnimeList (MAL) Login & Sync Button
                var isMalFocused by remember { mutableStateOf(false) }
                Button(
                    onClick = {
                        if (malUser != null) {
                            coroutineScope.launch {
                                statusMessage = "Syncing from MyAnimeList..."
                                val res = AniListSyncManager.syncMalWatchlist(malUser!!.name, storage)
                                malUser = storage.getMalUser()
                                refreshData()
                                statusMessage = res.message
                            }
                        } else {
                            showMalLoginDialog = true
                        }
                    },
                    scale = ButtonDefaults.scale(focusedScale = 1.08f),
                    border = ButtonDefaults.border(
                        focusedBorder = Border(
                            border = BorderStroke(3.dp, Color.White),
                            shape = RoundedCornerShape(10.dp)
                        )
                    ),
                    modifier = Modifier
                        .focusProperties {
                            if (onNavigateUp != null) {
                                up = androidx.compose.ui.focus.FocusRequester.Cancel
                            }
                            if (filteredList.isNotEmpty()) {
                                down = firstCardFocusRequester
                            }
                        }
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                                    onNavigateUp?.invoke()
                                    true
                                } else if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                                    if (filteredList.isNotEmpty()) {
                                        try {
                                            firstCardFocusRequester.requestFocus()
                                            true
                                        } catch (_: Exception) {
                                            focusManager.moveFocus(FocusDirection.Down)
                                        }
                                    } else false
                                } else false
                            } else false
                        }
                        .onFocusChanged { isMalFocused = it.isFocused },
                    colors = ButtonDefaults.colors(
                        containerColor = if (malUser != null) Color(0xFF2E51A2) else TvSurfaceColor,
                        focusedContainerColor = TvAccentPurple
                    ),
                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(10.dp))
                ) {
                    Text(
                        text = if (malUser != null) "\uD83D\uDD04 MAL: ${malUser!!.name}" else "\uD83D\uDD17 Login MAL",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }

                // Sort Cycle Button
                var isSortFocused by remember { mutableStateOf(false) }
                Button(
                    onClick = {
                        val nextOrdinal = (selectedSort.ordinal + 1) % WatchlistSort.values().size
                        selectedSort = WatchlistSort.values()[nextOrdinal]
                    },
                    scale = ButtonDefaults.scale(focusedScale = 1.08f),
                    border = ButtonDefaults.border(
                        focusedBorder = Border(
                            border = BorderStroke(3.dp, Color.White),
                            shape = RoundedCornerShape(10.dp)
                        )
                    ),
                    modifier = Modifier
                        .focusProperties {
                            if (onNavigateUp != null) {
                                up = androidx.compose.ui.focus.FocusRequester.Cancel
                            }
                            if (filteredList.isNotEmpty()) {
                                down = firstCardFocusRequester
                            }
                        }
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                                    onNavigateUp?.invoke()
                                    true
                                } else if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                                    if (filteredList.isNotEmpty()) {
                                        try {
                                            firstCardFocusRequester.requestFocus()
                                            true
                                        } catch (_: Exception) {
                                            focusManager.moveFocus(FocusDirection.Down)
                                        }
                                    } else false
                                } else false
                            } else false
                        }
                        .onFocusChanged { isSortFocused = it.isFocused },
                    colors = ButtonDefaults.colors(
                        containerColor = TvSurfaceColor,
                        focusedContainerColor = TvAccentPurple
                    ),
                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(10.dp))
                ) {
                    Text(
                        text = "Sort: ${selectedSort.label} \u25BC",
                        color = if (isSortFocused) Color.White else TvTextSecondary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Content Area: Grid or Empty State
        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .background(TvSurfaceColor, RoundedCornerShape(20.dp))
                            .border(1.dp, TvCardBorder, RoundedCornerShape(20.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "\uD83D\uDCD6",
                            fontSize = 42.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (favorites.isEmpty()) "Your Watchlist is Empty" else "No anime in ${selectedFilter.label}",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (favorites.isEmpty())
                            "Browse the catalog or search for anime and press '+ Watchlist' on any title to save it here."
                        else
                            "Try switching to the 'All' tab or explore more titles from the catalog.",
                        color = TvTextSecondary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    if (onExploreClick != null) {
                        var isExploreFocused by remember { mutableStateOf(false) }
                        Button(
                            onClick = onExploreClick,
                            scale = ButtonDefaults.scale(focusedScale = 1.08f),
                            border = ButtonDefaults.border(
                                focusedBorder = Border(
                                    border = BorderStroke(3.dp, Color.White),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            ),
                            modifier = Modifier
                                .onFocusChanged { isExploreFocused = it.isFocused },
                            colors = ButtonDefaults.colors(
                                containerColor = TvPrimaryRed,
                                focusedContainerColor = TvAccentPurple
                            ),
                            shape = ButtonDefaults.shape(shape = RoundedCornerShape(12.dp))
                        ) {
                            Text(
                                text = "\uD83D\uDD0D Explore Catalog & Search",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                itemsIndexed(filteredList, key = { _, anime -> anime.id }) { index, anime ->
                    val progress = watchProgressMap[anime.id]
                    WatchlistCard(
                        anime = anime,
                        progress = progress,
                        onClick = { onAnimeClick(anime) },
                        onRemove = {
                            storage.removeFavorite(anime.id)
                            statusMessage = "Removed '${anime.title}' from Watchlist"
                            refreshData()
                        },
                        modifier = Modifier
                            .then(if (index == 0) Modifier.focusRequester(firstCardFocusRequester) else Modifier)
                            .then(
                                if (index < 5) {
                                    val targetIdx = selectedFilter.ordinal.coerceIn(0, filterFocusRequesters.lastIndex)
                                    Modifier
                                        .focusProperties {
                                            up = if (targetIdx == 0 && firstFilterFocusRequester != null) {
                                                firstFilterFocusRequester
                                            } else {
                                                filterFocusRequesters[targetIdx]
                                            }
                                        }
                                        .onPreviewKeyEvent { keyEvent ->
                                            if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                                keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP
                                            ) {
                                                try {
                                                    if (targetIdx == 0 && firstFilterFocusRequester != null) {
                                                        firstFilterFocusRequester.requestFocus()
                                                    } else {
                                                        filterFocusRequesters[targetIdx].requestFocus()
                                                    }
                                                    true
                                                } catch (_: Exception) {
                                                    focusManager.moveFocus(FocusDirection.Up)
                                                }
                                            } else false
                                        }
                                } else Modifier
                            )
                    )
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
                            text = "Enter your public AniList username or paste a Personal OAuth Token to sync your anime library.",
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
                                            statusMessage = "Linking AniList account..."
                                            val linkedUser = if (input.length > 40) {
                                                AniListSyncManager.loginWithToken(input, storage).getOrNull()
                                            } else {
                                                AniListSyncManager.loginWithUsername(input, storage).getOrNull()
                                            }
                                            if (linkedUser != null) {
                                                aniListUser = linkedUser
                                                val syncRes = AniListSyncManager.syncAniListWatchlist(storage)
                                                refreshData()
                                                aniListInputText = ""
                                                statusMessage = syncRes.message
                                            } else {
                                                statusMessage = "AniList user '$input' not found"
                                            }
                                        }
                                    } else {
                                        statusMessage = "Please enter your AniList username"
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

        // Login with MyAnimeList (MAL) Dialog
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
                                            statusMessage = "Linking MyAnimeList account..."
                                            val linkedMal = if (input.length > 40) {
                                                AniListSyncManager.loginMalWithToken(input, storage).getOrNull()
                                            } else {
                                                AniListSyncManager.loginMalWithUsername(input, storage).getOrNull()
                                            }
                                            val targetName = linkedMal?.name ?: input
                                            val syncRes = AniListSyncManager.syncMalWatchlist(targetName, storage)
                                            malUser = storage.getMalUser()
                                            refreshData()
                                            if (linkedMal != null || syncRes.isSuccess) {
                                                malInputText = ""
                                                statusMessage = syncRes.message
                                            } else {
                                                statusMessage = "MyAnimeList user '$input' not found"
                                            }
                                        }
                                    } else {
                                        statusMessage = "Please enter your MyAnimeList username"
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
 * Dedicated Watchlist Card rendering cover image, title, progress bar,
 * status badge, and remote focus physics.
 */
@Composable
fun WatchlistCard(
    anime: Anime,
    progress: WatchProgress?,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .tvFocusableItem(
                onClick = onClick,
                focusedScale = 1.10f,
                borderWidth = 3.5.dp,
                focusedBorderColor = Color.White,
                shape = RoundedCornerShape(14.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .background(TvSurfaceColor, RoundedCornerShape(14.dp))
            .border(1.dp, if (isFocused) Color.White else TvCardBorder, RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Poster Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                TvAnimeCard(
                    anime = anime,
                    onClick = onClick,
                    modifier = Modifier.fillMaxSize()
                )

                // Progress Badge Overlay
                if (progress != null && progress.positionMs > 0L) {
                    val isFinished = progress.progressPercentage >= 0.92f
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(
                                if (isFinished) TvSuccessGreen else TvPrimaryRed,
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isFinished) "Completed" else "EP ${progress.episodeNumber}",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Title & Info Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = anime.title,
                    color = if (isFocused) Color.White else TvTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (anime.genres.isNotEmpty()) anime.genres.take(2).joinToString(" \u2022 ") else "Anime",
                        color = TvTextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (anime.score > 0.0) {
                        Text(
                            text = "\u2605 ${anime.score}",
                            color = TvGoldStar,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
