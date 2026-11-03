package com.darknet.animetv.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.darknet.animetv.data.model.Anime
import com.darknet.animetv.data.repository.AnimeRepository
import com.darknet.animetv.ui.components.CategoryChip
import com.darknet.animetv.ui.components.TvAnimeCard
import com.darknet.animetv.ui.components.TvTanukiLoadingIndicator
import com.darknet.animetv.ui.components.tvFocusableItem
import com.darknet.animetv.ui.theme.TvAccentPurple
import com.darknet.animetv.ui.theme.TvCardBorder
import com.darknet.animetv.ui.theme.TvDarkBackground
import com.darknet.animetv.ui.theme.TvPrimaryRed
import com.darknet.animetv.ui.theme.TvSurfaceColor
import com.darknet.animetv.ui.theme.TvTextPrimary
import com.darknet.animetv.ui.theme.TvTextSecondary
import kotlinx.coroutines.launch

/**
 * Android TV Search & Catalog screen featuring:
 * - Remote-optimized search query input bar with click-to-edit (prevents IME focus trap on D-pad scroll)
 * - Horizontal genre / category filter chips
 * - Responsive 5-column grid with deterministic Up/Down D-pad focus routing
 * - Combined keyword and category filtering
 * - Zero-state empty search placeholder
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(
    repository: AnimeRepository,
    onAnimeClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
    initialQuery: String = "",
    onNavigateUp: (() -> Unit)? = null,
    searchBarFocusRequester: FocusRequester = remember { FocusRequester() }
) {
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var searchQuery by remember { mutableStateOf(initialQuery) }
    var isEditingSearch by remember { mutableStateOf(false) }
    var selectedGenre by remember { mutableStateOf("All") }
    var searchResults by remember { mutableStateOf<List<Anime>>(emptyList()) }
    var defaultCatalog by remember { mutableStateOf<List<Anime>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    val genres = remember {
        listOf(
            "All", "🎙️ Dubbed", "Action", "Adventure", "Comedy", "Drama", "Fantasy",
            "Sci-Fi", "Romance", "Sports", "Supernatural", "Mystery", "Slice of Life"
        )
    }

    val textFieldFocusRequester = remember { FocusRequester() }
    val voiceButtonFocusRequester = remember { FocusRequester() }
    val clearButtonFocusRequester = remember { FocusRequester() }
    val chipFocusRequesters = remember { List(genres.size) { FocusRequester() } }
    val firstGridItemFocusRequester = remember { FocusRequester() }
    val gridState = rememberLazyGridState()

    val selectedGenreIndex = remember(selectedGenre) {
        genres.indexOf(selectedGenre).coerceAtLeast(0)
    }

    fun focusSelectedChip(): Boolean {
        return try {
            chipFocusRequesters[selectedGenreIndex].requestFocus()
            true
        } catch (_: Exception) {
            try {
                chipFocusRequesters.first().requestFocus()
                true
            } catch (_: Exception) {
                focusManager.moveFocus(FocusDirection.Down)
            }
        }
    }

    BackHandler(enabled = isEditingSearch) {
        isEditingSearch = false
        keyboardController?.hide()
        try {
            searchBarFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    LaunchedEffect(isEditingSearch) {
        if (isEditingSearch) {
            try {
                textFieldFocusRequester.requestFocus()
                keyboardController?.show()
            } catch (_: Exception) {}
        } else {
            keyboardController?.hide()
        }
    }

    // Load initial catalog on launch
    LaunchedEffect(Unit) {
        isSearching = true
        try {
            val trending = repository.getTrending()
            val popular = repository.getPopular()
            val dubbed = repository.getDubbedAnime()
            defaultCatalog = (trending + popular + dubbed).distinctBy { it.id }
            searchResults = defaultCatalog
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isSearching = false
        }
    }

    fun performSearch(query: String) {
        coroutineScope.launch {
            isSearching = true
            try {
                if (query.isBlank()) {
                    searchResults = defaultCatalog
                } else {
                    val remoteResults = repository.search(query.trim())
                    searchResults = if (remoteResults.isNotEmpty()) {
                        remoteResults
                    } else {
                        // Fallback local search against default catalog
                        defaultCatalog.filter { it.title.contains(query, ignoreCase = true) }
                    }
                }
            } catch (e: Exception) {
                searchResults = defaultCatalog.filter { it.title.contains(query, ignoreCase = true) }
            } finally {
                isSearching = false
            }
        }
    }

    // Filter results by selected genre
    val filteredResults = remember(searchResults, selectedGenre) {
        when (selectedGenre) {
            "All" -> searchResults
            "🎙️ Dubbed" -> searchResults.filter { it.hasDub }
            else -> searchResults.filter { anime ->
                anime.genres.any { it.equals(selectedGenre, ignoreCase = true) }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TvDarkBackground)
    ) {
        // Top Search Bar Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Search Input Field (Click-to-edit container so D-pad traversal never gets trapped by IME)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .focusRequester(searchBarFocusRequester)
                    .focusProperties {
                        right = voiceButtonFocusRequester
                        down = chipFocusRequesters[selectedGenreIndex]
                    }
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                            when (keyEvent.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    if (isEditingSearch) {
                                        isEditingSearch = false
                                        keyboardController?.hide()
                                    }
                                    focusSelectedChip()
                                }
                                KeyEvent.KEYCODE_DPAD_UP -> {
                                    if (isEditingSearch) {
                                        isEditingSearch = false
                                        keyboardController?.hide()
                                    }
                                    if (onNavigateUp != null) {
                                        onNavigateUp()
                                        true
                                    } else {
                                        focusManager.moveFocus(FocusDirection.Up)
                                    }
                                }
                                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    if (!isEditingSearch) {
                                        try {
                                            voiceButtonFocusRequester.requestFocus()
                                            true
                                        } catch (_: Exception) {
                                            false
                                        }
                                    } else false
                                }
                                else -> false
                            }
                        } else false
                    }
                    .then(
                        if (!isEditingSearch) {
                            Modifier.tvFocusableItem(
                                onClick = { isEditingSearch = true },
                                focusedScale = 1.02f,
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = TvCardBorder,
                                borderWidth = 3.dp,
                                shape = RoundedCornerShape(12.dp)
                            )
                        } else {
                            Modifier.border(2.dp, TvPrimaryRed, RoundedCornerShape(12.dp))
                        }
                    )
                    .clip(RoundedCornerShape(12.dp))
                    .background(TvSurfaceColor)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (isEditingSearch) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Type anime title, character, or studio...",
                            color = TvTextSecondary,
                            fontSize = 15.sp
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            performSearch(it)
                        },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = TvTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(TvPrimaryRed),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                performSearch(searchQuery)
                                isEditingSearch = false
                                keyboardController?.hide()
                                try {
                                    searchBarFocusRequester.requestFocus()
                                } catch (_: Exception) {}
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(textFieldFocusRequester)
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "🔍",
                            fontSize = 15.sp
                        )
                        Text(
                            text = if (searchQuery.isEmpty()) {
                                "Search anime by title, character, or studio..."
                            } else {
                                searchQuery
                            },
                            color = if (searchQuery.isEmpty()) TvTextSecondary else TvTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = if (searchQuery.isEmpty()) FontWeight.Normal else FontWeight.Medium
                        )
                    }
                }
            }

            // Voice Search Action
            val speechLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                    if (!spoken.isNullOrBlank()) {
                        searchQuery = spoken
                        performSearch(spoken)
                    }
                }
            }

            fun launchVoiceSearch() {
                try {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak an anime title or genre...")
                    }
                    speechLauncher.launch(intent)
                } catch (_: Exception) {}
            }

            Button(
                onClick = { launchVoiceSearch() },
                scale = ButtonDefaults.scale(focusedScale = 1.08f),
                border = ButtonDefaults.border(
                    focusedBorder = Border(
                        border = BorderStroke(3.dp, Color.White),
                        shape = RoundedCornerShape(10.dp)
                    )
                ),
                colors = ButtonDefaults.colors(
                    containerColor = TvAccentPurple,
                    focusedContainerColor = TvPrimaryRed
                ),
                shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
                modifier = Modifier
                    .focusRequester(voiceButtonFocusRequester)
                    .focusProperties {
                        left = searchBarFocusRequester
                        down = chipFocusRequesters[selectedGenreIndex]
                        if (searchQuery.isNotEmpty()) {
                            right = clearButtonFocusRequester
                        }
                    }
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                            when (keyEvent.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_DOWN -> focusSelectedChip()
                                KeyEvent.KEYCODE_DPAD_UP -> {
                                    if (onNavigateUp != null) {
                                        onNavigateUp()
                                        true
                                    } else {
                                        focusManager.moveFocus(FocusDirection.Up)
                                    }
                                }
                                KeyEvent.KEYCODE_DPAD_LEFT -> {
                                    try {
                                        searchBarFocusRequester.requestFocus()
                                        true
                                    } catch (_: Exception) {
                                        false
                                    }
                                }
                                else -> false
                            }
                        } else false
                    }
            ) {
                Text("🎤 Voice", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            // Quick Clear / Search Action
            if (searchQuery.isNotEmpty()) {
                Button(
                    onClick = {
                        searchQuery = ""
                        performSearch("")
                        try {
                            searchBarFocusRequester.requestFocus()
                        } catch (_: Exception) {}
                    },
                    scale = ButtonDefaults.scale(focusedScale = 1.05f),
                    border = ButtonDefaults.border(
                        focusedBorder = Border(
                            border = BorderStroke(3.dp, Color.White),
                            shape = RoundedCornerShape(10.dp)
                        )
                    ),
                    colors = ButtonDefaults.colors(
                        containerColor = TvSurfaceColor,
                        focusedContainerColor = TvPrimaryRed
                    ),
                    shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
                    modifier = Modifier
                        .focusRequester(clearButtonFocusRequester)
                        .focusProperties {
                            left = voiceButtonFocusRequester
                            down = chipFocusRequesters[selectedGenreIndex]
                        }
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                when (keyEvent.nativeKeyEvent.keyCode) {
                                    KeyEvent.KEYCODE_DPAD_DOWN -> focusSelectedChip()
                                    KeyEvent.KEYCODE_DPAD_UP -> {
                                        if (onNavigateUp != null) {
                                            onNavigateUp()
                                            true
                                        } else {
                                            focusManager.moveFocus(FocusDirection.Up)
                                        }
                                    }
                                    else -> false
                                }
                            } else false
                        }
                ) {
                    Text("Clear", color = Color.White, fontSize = 14.sp)
                }
            }
        }

        // Horizontal Category / Genre Chips Row
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(genres) { index, genre ->
                CategoryChip(
                    title = genre,
                    isSelected = selectedGenre == genre,
                    onClick = { selectedGenre = genre },
                    modifier = Modifier
                        .focusRequester(chipFocusRequesters[index])
                        .focusProperties {
                            up = searchBarFocusRequester
                            if (filteredResults.isNotEmpty()) {
                                down = firstGridItemFocusRequester
                            }
                        }
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                when (keyEvent.nativeKeyEvent.keyCode) {
                                    KeyEvent.KEYCODE_DPAD_UP -> {
                                        try {
                                            searchBarFocusRequester.requestFocus()
                                            true
                                        } catch (_: Exception) {
                                            focusManager.moveFocus(FocusDirection.Up)
                                        }
                                    }
                                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                                        if (filteredResults.isNotEmpty()) {
                                            try {
                                                firstGridItemFocusRequester.requestFocus()
                                                true
                                            } catch (_: Exception) {
                                                focusManager.moveFocus(FocusDirection.Down)
                                            }
                                        } else true
                                    }
                                    else -> false
                                }
                            } else false
                        }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Grid Results Header / Status
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (searchQuery.isBlank()) {
                    if (selectedGenre == "All") "Browse Catalog" else "$selectedGenre Anime"
                } else {
                    "Results for \"$searchQuery\"${if (selectedGenre != "All") " in $selectedGenre" else ""}"
                },
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "${filteredResults.size} titles",
                color = TvTextSecondary,
                fontSize = 13.sp
            )
        }

        // Responsive 5-Column Grid with D-pad Boundary Resistance
        if (isSearching) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                TvTanukiLoadingIndicator(
                    statusText = "Searching the Tanuki archives...",
                    subtitleText = "Fetching dubbed & subtitled anime catalog"
                )
            }
        } else if (filteredResults.isEmpty()) {
            // Empty State Placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No Anime Found",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) {
                            "No anime matches \"$searchQuery\" in category \"$selectedGenre\". Try a different search."
                        } else {
                            "No anime available in category \"$selectedGenre\"."
                        },
                        color = TvTextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                state = gridState,
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                itemsIndexed(filteredResults, key = { _, anime -> anime.id }) { index, anime ->
                    TvAnimeCard(
                        anime = anime,
                        onClick = { onAnimeClick(anime) },
                        modifier = Modifier
                            .then(
                                if (index == 0) Modifier.focusRequester(firstGridItemFocusRequester)
                                else Modifier
                            )
                            .then(
                                if (index < 5) {
                                    Modifier
                                        .focusProperties {
                                            up = chipFocusRequesters[selectedGenreIndex]
                                        }
                                        .onPreviewKeyEvent { keyEvent ->
                                            if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                                keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP
                                            ) {
                                                focusSelectedChip()
                                            } else false
                                        }
                                } else Modifier
                            )
                    )
                }
            }
        }
    }
}
