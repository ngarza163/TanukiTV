package com.darknet.animetv.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.darknet.animetv.audio.TvSoundManager
import com.darknet.animetv.data.model.Anime
import com.darknet.animetv.data.model.AnimeDetail
import com.darknet.animetv.data.model.CharacterCast
import com.darknet.animetv.data.model.Episode
import com.darknet.animetv.data.model.FranchiseRelation
import com.darknet.animetv.data.model.FranchiseRelationType
import com.darknet.animetv.data.model.WatchProgress
import com.darknet.animetv.data.repository.AnimeRepository
import com.darknet.animetv.data.storage.AppStorage
import com.darknet.animetv.ui.components.CategoryChip
import com.darknet.animetv.ui.components.TvAnimeCard
import com.darknet.animetv.ui.components.TvTanukiLoadingIndicator
import com.darknet.animetv.ui.components.tvFocusableItem
import com.darknet.animetv.ui.theme.TvAccentPurple
import com.darknet.animetv.ui.theme.TvBrandCyan
import com.darknet.animetv.ui.theme.TvCardBorder
import com.darknet.animetv.ui.theme.TvDarkBackground
import com.darknet.animetv.ui.theme.TvGoldStar
import com.darknet.animetv.ui.theme.TvPrimaryRed
import com.darknet.animetv.ui.theme.TvSuccessGreen
import com.darknet.animetv.ui.theme.TvSurfaceColor
import com.darknet.animetv.ui.theme.TvTextSecondary

/**
 * Android TV Anime Details View & Episode Picker featuring:
 * - 16:9 widescreen backdrop with legibility gradient overlays
 * - Metadata badges (Studio, Year, Score, Status, Genres)
 * - Watchlist/Favorite toggle button with SQLite persistence sync
 * - Interactive horizontal Episode shelf with watch progress (>90% marked watched)
 * - Automatic 50-episode chunking for long-running series
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DetailsScreen(
    anime: Anime,
    repository: AnimeRepository,
    storage: AppStorage,
    onPlayEpisode: (Anime, Episode) -> Unit,
    onBack: () -> Unit,
    onSelectAnime: ((Anime) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var detail by remember { mutableStateOf<AnimeDetail?>(null) }
    var franchiseRelations by remember { mutableStateOf<List<FranchiseRelation>>(emptyList()) }
    var recommendations by remember { mutableStateOf<List<Anime>>(emptyList()) }
    var characterCast by remember { mutableStateOf<List<CharacterCast>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isFavorite by remember { mutableStateOf(storage.isFavorite(anime.id)) }
    var watchProgress by remember { mutableStateOf<WatchProgress?>(null) }
    var selectedChunkIndex by remember { mutableIntStateOf(0) }
    var selectedAudioTrack by remember(anime.id) {
        val preferred = storage.getUserSettings().preferredAudio
        mutableStateOf(if (anime.hasDub && preferred.equals("DUB", ignoreCase = true)) "DUB" else "SUB")
    }
    val playButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            playButtonFocusRequester.requestFocus()
        } catch (_: Exception) {
        }
    }

    LaunchedEffect(anime.id) {
        isLoading = true
        try {
            isFavorite = storage.isFavorite(anime.id)
            watchProgress = storage.getWatchProgress(anime.id)
            franchiseRelations = repository.getFranchiseRelations(anime)
            recommendations = repository.getRecommendations(anime)
            characterCast = repository.getCharacterCast(anime.id, anime.title)

            val remoteDetail = repository.getAnimeDetail(anime.id)
            if (remoteDetail != null && remoteDetail.episodes.isNotEmpty()) {
                val rAnime = remoteDetail.anime
                val hasValidRemoteTitle = rAnime.title.isNotBlank() &&
                    !rAnime.title.equals(anime.id, ignoreCase = true) &&
                    !rAnime.title.matches(Regex("^[a-z]{2,4}-\\d+$", RegexOption.IGNORE_CASE))
                val mergedAnime = if (hasValidRemoteTitle) {
                    rAnime.copy(
                        posterUrl = rAnime.posterUrl.ifBlank { anime.posterUrl },
                        backdropUrl = rAnime.backdropUrl.ifBlank { anime.backdropUrl },
                        synopsis = rAnime.synopsis.ifBlank { anime.synopsis },
                        genres = rAnime.genres.ifEmpty { anime.genres },
                        year = if (rAnime.year > 0) rAnime.year else anime.year,
                        hasDub = rAnime.hasDub || anime.hasDub,
                        dubbedEpisodesCount = maxOf(rAnime.dubbedEpisodesCount, anime.dubbedEpisodesCount)
                    )
                } else {
                    anime
                }
                val count = maxOf(remoteDetail.episodes.size, anime.totalEpisodes, mergedAnime.totalEpisodes).coerceAtLeast(1)
                val finalEpisodes = if (remoteDetail.episodes.size >= count) {
                    remoteDetail.episodes
                } else {
                    (1..count).map { num ->
                        Episode(
                            id = "${anime.id}-ep-$num",
                            animeId = anime.id,
                            number = num,
                            title = "Episode $num",
                            thumbnail = mergedAnime.backdropUrl.ifBlank { mergedAnime.posterUrl },
                            hasDub = mergedAnime.hasDub
                        )
                    }
                }
                detail = remoteDetail.copy(
                    anime = mergedAnime,
                    episodes = finalEpisodes
                )
            } else {
                // Synthesize episodes if source does not provide episode list
                val count = if (anime.totalEpisodes > 0) anime.totalEpisodes else 12
                val syntheticEpisodes = (1..count).map { num ->
                    Episode(
                        id = "${anime.id}-ep-$num",
                        animeId = anime.id,
                        number = num,
                        title = "Episode $num",
                        thumbnail = anime.backdropUrl.ifBlank { anime.posterUrl }
                    )
                }
                detail = AnimeDetail(
                    anime = remoteDetail?.anime ?: anime,
                    episodes = syntheticEpisodes,
                    studio = remoteDetail?.studio ?: "Animation Studio",
                    trailerUrl = remoteDetail?.trailerUrl
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    val currentDetail = detail ?: AnimeDetail(anime = anime)
    val displayAnime = currentDetail.anime
    val episodes = currentDetail.episodes

    // Chunk episodes in groups of 50 for long-running anime
    val chunkSize = 50
    val episodeChunks = remember(episodes) {
        if (episodes.size > chunkSize) episodes.chunked(chunkSize) else listOf(episodes)
    }
    val currentChunkEpisodes = episodeChunks.getOrElse(selectedChunkIndex) { episodes }

    // Helper: is episode watched?
    fun isEpisodeWatched(ep: Episode): Boolean {
        val wp = watchProgress ?: return false
        return ep.number < wp.episodeNumber ||
                (ep.number == wp.episodeNumber && wp.progressPercentage >= 0.90f)
    }

    TvLazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(TvDarkBackground),
        contentPadding = PaddingValues(bottom = 60.dp)
    ) {
        // Hero Backdrop & Details Header
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(440.dp)
            ) {
                // Backdrop Image
                val backdrop = displayAnime.backdropUrl.ifBlank { displayAnime.posterUrl }
                AsyncImage(
                    model = backdrop,
                    contentDescription = displayAnime.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Horizontal Gradient (Left to Right)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    TvDarkBackground,
                                    TvDarkBackground.copy(alpha = 0.95f),
                                    TvDarkBackground.copy(alpha = 0.65f),
                                    Color.Transparent
                                ),
                                startX = 0f,
                                endX = 1300f
                            )
                        )
                )

                // Vertical Gradient (Bottom to Top)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    TvDarkBackground.copy(alpha = 0.6f),
                                    TvDarkBackground
                                ),
                                startY = 150f,
                                endY = 850f
                            )
                        )
                )

                // Navigation Header (Back Button)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp, vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onBack,
                        scale = ButtonDefaults.scale(focusedScale = 1.05f),
                        border = ButtonDefaults.border(
                            focusedBorder = Border(
                                border = BorderStroke(3.dp, Color.White),
                                shape = RoundedCornerShape(12.dp)
                            )
                        ),
                        colors = ButtonDefaults.colors(
                            containerColor = TvSurfaceColor,
                            focusedContainerColor = TvAccentPurple
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(12.dp))
                    ) {
                        Text(
                            text = "← Back",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }

                // Details Content
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(0.65f)
                        .padding(start = 48.dp, bottom = 24.dp)
                ) {
                    // Badges (Score, Studio, Status, Year)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (displayAnime.score > 0.0) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0xD9000000), RoundedCornerShape(8.dp))
                                    .border(1.dp, TvGoldStar.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "★", color = TvGoldStar, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = String.format("%.1f", displayAnime.score),
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (currentDetail.studio.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .background(TvSurfaceColor, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = currentDetail.studio,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .background(TvSurfaceColor, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = displayAnime.status,
                                color = TvAccentPurple,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (displayAnime.year > 0) {
                            Text(
                                text = "${displayAnime.year}",
                                color = TvTextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (displayAnime.hasDub) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0xDD1A1038), RoundedCornerShape(8.dp))
                                    .border(1.dp, TvBrandCyan.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "🎙️ SUB / DUB",
                                    color = TvBrandCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Title
                    Text(
                        text = displayAnime.title,
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 38.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Genres
                    if (displayAnime.genres.isNotEmpty()) {
                        Text(
                            text = displayAnime.genres.joinToString(" • "),
                            color = TvTextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Synopsis
                    if (displayAnime.synopsis.isNotBlank()) {
                        val cleanSynopsis = remember(displayAnime.synopsis) {
                            displayAnime.synopsis.replace(Regex("<[^>]*>"), "").trim()
                        }
                        Text(
                            text = cleanSynopsis,
                            color = Color(0xFFD4D4D4),
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Action Buttons Row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Play / Resume Button
                        val targetEpisode = if (watchProgress != null) {
                            episodes.find { it.number == watchProgress?.episodeNumber } ?: episodes.firstOrNull()
                        } else {
                            episodes.firstOrNull()
                        }

                        val audioLabel = if (selectedAudioTrack == "DUB") " (DUB)" else ""
                        val playButtonText = when {
                            watchProgress != null && watchProgress!!.progressPercentage < 0.90f ->
                                "Resume EP ${watchProgress!!.episodeNumber}$audioLabel"
                            watchProgress != null && watchProgress!!.progressPercentage >= 0.90f ->
                                "Watch EP ${watchProgress!!.episodeNumber} Again$audioLabel"
                            else -> "Play Episode 1$audioLabel"
                        }

                        Button(
                            onClick = {
                                if (targetEpisode != null) {
                                    onPlayEpisode(displayAnime, targetEpisode.copy(audioTrack = selectedAudioTrack))
                                }
                            },
                            scale = ButtonDefaults.scale(focusedScale = 1.05f),
                            border = ButtonDefaults.border(
                                focusedBorder = Border(
                                    border = BorderStroke(3.dp, Color.White),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            ),
                            colors = ButtonDefaults.colors(
                                containerColor = TvPrimaryRed,
                                focusedContainerColor = TvAccentPurple
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)),
                            modifier = Modifier
                                .focusRequester(playButtonFocusRequester)
                        ) {
                            Text(
                                text = "▶ $playButtonText",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        // Watchlist / Favorite Toggle Button
                        Button(
                            onClick = {
                                if (isFavorite) {
                                    storage.removeFavorite(displayAnime.id)
                                    isFavorite = false
                                } else {
                                    storage.addFavorite(displayAnime)
                                    isFavorite = true
                                    TvSoundManager.playWatchlistAdd()
                                }
                            },
                            scale = ButtonDefaults.scale(focusedScale = 1.05f),
                            border = ButtonDefaults.border(
                                focusedBorder = Border(
                                    border = BorderStroke(3.dp, Color.White),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            ),
                            colors = ButtonDefaults.colors(
                                containerColor = if (isFavorite) TvAccentPurple.copy(alpha = 0.6f) else TvSurfaceColor,
                                focusedContainerColor = TvAccentPurple
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(12.dp))
                        ) {
                            Text(
                                text = if (isFavorite) "✓ In Watchlist" else "+ Add to Watchlist",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Episode Shelf Header
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Episodes (${episodes.size})",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // SUB / DUB Audio Track Selector Pills
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategoryChip(
                            title = "🇯🇵 SUB",
                            isSelected = selectedAudioTrack == "SUB",
                            onClick = { selectedAudioTrack = "SUB" }
                        )
                        CategoryChip(
                            title = "🎙️ DUB",
                            isSelected = selectedAudioTrack == "DUB",
                            onClick = { selectedAudioTrack = "DUB" }
                        )
                    }
                }

                // Episode Chunk Selector (for >50 episodes)
                if (episodeChunks.size > 1) {
                    TvLazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(episodeChunks.indices.toList()) { index ->
                            val startEp = index * chunkSize + 1
                            val endEp = minOf((index + 1) * chunkSize, episodes.size)
                            CategoryChip(
                                title = "$startEp–$endEp",
                                isSelected = selectedChunkIndex == index,
                                onClick = { selectedChunkIndex = index }
                            )
                        }
                    }
                }
            }
        }

        // Horizontal Episode Picker Shelf
        item {
            if (isLoading && episodes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TvTanukiLoadingIndicator(
                        statusText = "Loading episodes & cast...",
                        isCompact = true
                    )
                }
            } else if (currentChunkEpisodes.isEmpty()) {
                Text(
                    text = "No episodes available",
                    color = TvTextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 48.dp, vertical = 16.dp)
                )
            } else {
                TvLazyRow(
                    contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(currentChunkEpisodes, key = { it.id }) { ep ->
                        val isWatched = isEpisodeWatched(ep)
                        val isCurrentlyWatching = watchProgress?.episodeNumber == ep.number

                        TvEpisodeCard(
                            episode = ep.copy(audioTrack = selectedAudioTrack),
                            isWatched = isWatched,
                            isCurrentlyWatching = isCurrentlyWatching,
                            watchProgress = if (isCurrentlyWatching) watchProgress else null,
                            fallbackThumbnail = displayAnime.backdropUrl.ifBlank { displayAnime.posterUrl },
                            onClick = { onPlayEpisode(displayAnime, ep.copy(audioTrack = selectedAudioTrack)) }
                        )
                    }
                }
            }
        }

        // Franchise Chronological Watch Order Timeline Shelf
        if (franchiseRelations.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Franchise Chronology & Watch Order",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .background(TvAccentPurple.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                .border(1.dp, TvAccentPurple.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${franchiseRelations.size} Entries",
                                color = TvAccentPurple,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Canonical chronological timeline: prequels, main story, sequels, and canon movies",
                        color = TvTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            item {
                TvLazyRow(
                    contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(franchiseRelations, key = { it.animeId + it.relationType.name }) { relation ->
                        val isCurrent = relation.animeId.equals(displayAnime.id, ignoreCase = true) ||
                                relation.title.equals(displayAnime.title, ignoreCase = true)

                        TvFranchiseCard(
                            relation = relation,
                            isCurrent = isCurrent,
                            fallbackPoster = displayAnime.posterUrl,
                            onClick = {
                                if (!isCurrent) {
                                    val targetAnime = Anime(
                                        id = relation.animeId,
                                        title = relation.title,
                                        posterUrl = relation.posterUrl.ifBlank { displayAnime.posterUrl },
                                        backdropUrl = displayAnime.backdropUrl,
                                        score = relation.score,
                                        year = relation.year,
                                        status = "Finished Airing"
                                    )
                                    onSelectAnime?.invoke(targetAnime)
                                }
                            }
                        )
                    }
                }
            }
        }

        // Character & Voice Actor (Seiyuu) Cast Shelf
        if (characterCast.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Characters & Voice Cast",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Featured main characters and Japanese voice talent (Seiyuu)",
                        color = TvTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            item {
                TvLazyRow(
                    contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(characterCast, key = { it.id }) { cast ->
                        TvCharacterCastCard(cast = cast)
                    }
                }
            }
        }

        // Smart Recommendations ("More Like This") Shelf
        if (recommendations.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "More Like This",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Recommended anime based on shared themes, genres, and viewer ratings",
                        color = TvTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            item {
                TvLazyRow(
                    contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(recommendations, key = { it.id }) { recAnime ->
                        TvAnimeCard(
                            anime = recAnime,
                            onClick = {
                                onSelectAnime?.invoke(recAnime)
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Focus-interactive TV card showcasing a character portrait and their Japanese voice actor (Seiyuu).
 */
@Composable
fun TvCharacterCastCard(
    cast: CharacterCast,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .width(160.dp)
            .height(180.dp)
            .tvFocusableItem(
                onClick = {},
                focusedScale = 1.10f,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = TvCardBorder.copy(alpha = 0.5f),
                borderWidth = if (isFocused) 3.dp else 1.dp,
                shape = RoundedCornerShape(12.dp),
                onFocusChanged = { isFocused = it }
            )
            .clip(RoundedCornerShape(12.dp))
            .background(TvSurfaceColor)
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Character Portrait / Avatar
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF24283C))
                    .border(
                        width = if (isFocused) 2.dp else 1.dp,
                        color = if (isFocused) TvPrimaryRed else Color(0x44FFFFFF),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (cast.characterImageUrl.isNotBlank()) {
                    AsyncImage(
                        model = cast.characterImageUrl,
                        contentDescription = cast.characterName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = cast.characterName.take(1).uppercase(),
                        color = TvGoldStar,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Character Info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = cast.characterName,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))

                // Role Badge
                Box(
                    modifier = Modifier
                        .background(
                            if (cast.characterRole == "MAIN") TvPrimaryRed.copy(alpha = 0.3f) else TvAccentPurple.copy(alpha = 0.3f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = cast.characterRole,
                        color = if (cast.characterRole == "MAIN") TvPrimaryRed else TvAccentPurple,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Voice Actor (Seiyuu)
                if (cast.voiceActorName.isNotBlank()) {
                    Text(
                        text = "🎙 ${cast.voiceActorName}",
                        color = TvTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Focus-scaled TV Franchise timeline card displaying order index, relation type badge,
 * release year, format, and score.
 */
@Composable
fun TvFranchiseCard(
    relation: FranchiseRelation,
    isCurrent: Boolean,
    fallbackPoster: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val effectivePoster = relation.posterUrl.ifBlank { fallbackPoster }

    Box(
        modifier = modifier
            .width(220.dp)
            .height(130.dp)
            .tvFocusableItem(
                onClick = onClick,
                focusedScale = 1.12f,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = if (isCurrent) TvPrimaryRed else TvCardBorder.copy(alpha = 0.5f),
                borderWidth = if (isCurrent || isFocused) 3.5.dp else 1.5.dp,
                shape = RoundedCornerShape(12.dp),
                onFocusChanged = { isFocused = it }
            )
            .clip(RoundedCornerShape(12.dp))
            .background(TvSurfaceColor)
    ) {
        if (effectivePoster.isNotBlank()) {
            AsyncImage(
                model = effectivePoster,
                contentDescription = relation.title,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (isFocused) 0.95f else 0.70f },
                contentScale = ContentScale.Crop
            )
        }

        // Dark gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xB3000000),
                            Color(0x88000000),
                            Color(0xF50A0B10)
                        )
                    )
                )
        )

        // Top Row: Step Order Pill + Relation Type Badge
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .background(Color(0xE6000000), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "STEP ${relation.chronologicalOrder}",
                    color = TvGoldStar,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Box(
                modifier = Modifier
                    .background(Color(relation.relationType.badgeColorHex), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (isCurrent) "★ Watching Now" else relation.relationType.label,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Bottom Info: Title, Year, Format, Score
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Text(
                text = relation.title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 15.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${if (relation.year > 0) "${relation.year} • " else ""}${relation.format}",
                    color = TvTextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
                if (relation.score > 0.0) {
                    Text(
                        text = "★ ${String.format("%.1f", relation.score)}",
                        color = TvGoldStar,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Focus-scaled TV Episode card for the Details episode picker shelf.
 * Features:
 * - 210x126dp dimensions with 1.14x D-pad focus scaling
 * - 3.5dp high-contrast solid white focus border ring
 * - Centered floating [▶ PLAY EP X] action badge
 * - Subtle unfocused dimming (0.82f alpha) for instant visual hierarchy
 * - Focus accent line above episode title and highlighted status badges
 * - Watch progress bar overlay with high-contrast indicator
 */
@Composable
fun TvEpisodeCard(
    episode: Episode,
    isWatched: Boolean,
    isCurrentlyWatching: Boolean,
    watchProgress: WatchProgress?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fallbackThumbnail: String = ""
) {
    var isFocused by remember { mutableStateOf(false) }
    val effectiveThumbnail = episode.thumbnail.ifBlank { fallbackThumbnail }

    Box(
        modifier = modifier
            .width(210.dp)
            .height(126.dp)
            .tvFocusableItem(
                onClick = onClick,
                focusedScale = 1.14f,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = if (isCurrentlyWatching) TvPrimaryRed.copy(alpha = 0.6f) else TvCardBorder.copy(alpha = 0.5f),
                borderWidth = 3.5.dp,
                shape = RoundedCornerShape(12.dp),
                onFocusChanged = { isFocused = it }
            )
            .clip(RoundedCornerShape(12.dp))
            .background(TvSurfaceColor)
    ) {
        // Thumbnail Image (dimmed when unfocused, 100% bright when focused)
        if (effectiveThumbnail.isNotBlank()) {
            AsyncImage(
                model = effectiveThumbnail,
                contentDescription = episode.title,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = if (isFocused) 1.0f else 0.82f
                    },
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = if (isFocused) 1.0f else 0.82f
                    }
                    .background(Color(0xFF1A1D28)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "EP ${episode.number}",
                    color = Color(0x66FFFFFF),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Gradient Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = if (isFocused) {
                            listOf(
                                Color(0x15000000),
                                Color(0x9905060A),
                                Color(0xFA05060A)
                            )
                        } else {
                            listOf(
                                Color(0x22000000),
                                Color(0x88000000),
                                Color(0xEE0A0B10)
                            )
                        }
                    )
                )
        )

        // Centered Floating Action Pill (Appears on D-pad hover)
        if (isFocused) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xE60A0B10), RoundedCornerShape(16.dp))
                    .border(1.5.dp, TvPrimaryRed, RoundedCornerShape(16.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "▶",
                        color = TvPrimaryRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "PLAY EP ${episode.number}",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        // Top Left: Audio Track Badge (DUB / SUB)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
                .background(
                    if (episode.audioTrack.equals("DUB", ignoreCase = true)) Color(0xE61A1038) else Color(0xCC090A10),
                    RoundedCornerShape(6.dp)
                )
                .border(
                    width = 1.dp,
                    color = if (episode.audioTrack.equals("DUB", ignoreCase = true)) TvBrandCyan.copy(alpha = 0.85f) else Color(0x44FFFFFF),
                    shape = RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = if (episode.audioTrack.equals("DUB", ignoreCase = true)) "🎙️ DUB" else "SUB",
                color = if (episode.audioTrack.equals("DUB", ignoreCase = true)) TvBrandCyan else TvTextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        // Watched Pill / In Progress Pill (Top Right)
        if (isWatched) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(TvSuccessGreen.copy(alpha = 0.95f), RoundedCornerShape(6.dp))
                    .border(
                        width = if (isFocused) 1.dp else 0.dp,
                        color = if (isFocused) Color.White.copy(alpha = 0.8f) else Color.Transparent,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "✓ Watched",
                    color = Color.Black,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        } else if (isCurrentlyWatching) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(TvPrimaryRed, RoundedCornerShape(6.dp))
                    .border(
                        width = if (isFocused) 1.dp else 0.dp,
                        color = if (isFocused) Color.White.copy(alpha = 0.8f) else Color.Transparent,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "In Progress",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // Episode Info (Bottom)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(
                    horizontal = 10.dp,
                    vertical = if (watchProgress != null && watchProgress.progressPercentage > 0f) 10.dp else 8.dp
                )
        ) {
            if (isFocused) {
                // Focus accent line above episode title
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.30f)
                        .height(2.dp)
                        .background(TvPrimaryRed)
                )
                Spacer(modifier = Modifier.height(3.dp))
            }

            Text(
                text = "EP ${episode.number}",
                color = if (isFocused) TvGoldStar else Color(0xFFC0C0C0),
                fontSize = 11.sp,
                fontWeight = if (isFocused) FontWeight.ExtraBold else FontWeight.Bold
            )
            Text(
                text = episode.title,
                color = Color.White,
                fontSize = if (isFocused) 13.5.sp else 12.5.sp,
                fontWeight = if (isFocused) FontWeight.ExtraBold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Progress Bar (if in progress)
        if (watchProgress != null && watchProgress.progressPercentage > 0f) {
            val clamped = watchProgress.progressPercentage.coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.5.dp)
                    .background(Color(0x66FFFFFF))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(clamped)
                        .background(TvPrimaryRed)
                )
            }
        }
    }
}

