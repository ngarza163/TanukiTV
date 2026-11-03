package com.darknet.animetv.ui.components

import android.view.KeyEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
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
import coil.compose.AsyncImage
import com.darknet.animetv.data.model.Anime
import com.darknet.animetv.data.model.WatchProgress
import com.darknet.animetv.ui.theme.TvAccentPurple
import com.darknet.animetv.ui.theme.TvCardBorder
import com.darknet.animetv.ui.theme.TvDarkBackground
import com.darknet.animetv.ui.theme.TvGoldStar
import com.darknet.animetv.ui.theme.TvPrimaryRed
import com.darknet.animetv.ui.theme.TvSurfaceColor
import com.darknet.animetv.ui.theme.TvTextSecondary
import kotlinx.coroutines.delay

/**
 * High-definition 16:9 dynamic widescreen hero banner carousel for Android TV.
 * Displays backdrop banner image, title, score, airing status, synopsis,
 * action buttons ("Play Now", "Resume", "Watchlist", "Details"),
 * and supports automatic timed rotation and manual carousel navigation.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvHeroBanner(
    featuredAnimeList: List<Anime>,
    onPlayClick: (Anime) -> Unit,
    onDetailsClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
    watchProgressMap: Map<String, WatchProgress> = emptyMap(),
    watchlistIds: Set<String> = emptySet(),
    onToggleWatchlist: ((Anime) -> Unit)? = null,
    onNavigateUp: (() -> Unit)? = null,
    playFocusRequester: FocusRequester? = null,
    autoAdvanceSeconds: Long = 8L
) {
    if (featuredAnimeList.isEmpty()) return

    var currentIndex by remember { mutableIntStateOf(0) }
    var hasFocus by remember { mutableStateOf(false) }
    val playButtonRequester = playFocusRequester ?: remember { FocusRequester() }

    // Auto-advance rotation timer (pauses when user interacts with hero banner)
    LaunchedEffect(featuredAnimeList.size, currentIndex, hasFocus) {
        if (!hasFocus && featuredAnimeList.size > 1 && autoAdvanceSeconds > 0) {
            delay(autoAdvanceSeconds * 1000)
            currentIndex = (currentIndex + 1) % featuredAnimeList.size
        }
    }

    val activeAnime = featuredAnimeList.getOrNull(currentIndex) ?: featuredAnimeList.first()
    val watchProgress = watchProgressMap[activeAnime.id]
    val isInWatchlist = watchlistIds.contains(activeAnime.id)

    // Action button state resolution
    val actionLabel = when {
        watchProgress != null && watchProgress.progressPercentage < 0.90f -> "Resume"
        watchProgress != null && watchProgress.progressPercentage >= 0.90f -> "Watch Again"
        else -> "Play Now"
    }

    // High-contrast drop shadows for guaranteed legibility across any anime artwork
    val bannerTitleShadow = Shadow(
        color = Color(0xFF000000),
        offset = Offset(3f, 5f),
        blurRadius = 14f
    )
    val bannerTextShadow = Shadow(
        color = Color(0xFF000000),
        offset = Offset(2f, 3f),
        blurRadius = 10f
    )
    val bannerBadgeShadow = Shadow(
        color = Color(0xEE000000),
        offset = Offset(1.5f, 2f),
        blurRadius = 6f
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(380.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(TvDarkBackground)
    ) {
        // Crossfading Backdrop & Anime Text Information
        AnimatedContent(
            targetState = activeAnime,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "hero_banner_fade"
        ) { anime ->
            Box(modifier = Modifier.fillMaxSize()) {
                // 16:9 Backdrop Image
                val bannerUrl = anime.backdropUrl.ifBlank { anime.posterUrl }
                AsyncImage(
                    model = bannerUrl,
                    contentDescription = anime.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Resolution-independent proportional horizontal gradient (shades text area on left; characters on right remain clear)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    0.0f to TvDarkBackground.copy(alpha = 0.88f),
                                    0.38f to TvDarkBackground.copy(alpha = 0.65f),
                                    0.62f to TvDarkBackground.copy(alpha = 0.25f),
                                    0.78f to Color.Transparent,
                                    1.0f to Color.Transparent
                                )
                            )
                        )
                )

                // Bottom-to-top subtle gradient
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Transparent,
                                    0.60f to Color.Transparent,
                                    0.85f to TvDarkBackground.copy(alpha = 0.45f),
                                    1.0f to TvDarkBackground.copy(alpha = 0.80f)
                                )
                            )
                        )
                )

                // Hero Content Info (Left Column)
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth(0.60f)
                        .padding(start = 40.dp, top = 26.dp, end = 24.dp)
                ) {
                    // Metadata Badges Row (Score, Status, Year)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (anime.score > 0.0) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0xD9000000), RoundedCornerShape(8.dp))
                                    .border(1.dp, TvGoldStar.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "★",
                                        color = TvGoldStar,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        style = TextStyle(shadow = bannerBadgeShadow)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = String.format("%.1f", anime.score),
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        style = TextStyle(shadow = bannerBadgeShadow)
                                    )
                                }
                            }
                        }

                        // Airing Status
                        Box(
                            modifier = Modifier
                                .background(TvSurfaceColor, RoundedCornerShape(8.dp))
                                .border(1.dp, TvCardBorder, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = anime.status,
                                color = TvAccentPurple,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                style = TextStyle(shadow = bannerBadgeShadow)
                            )
                        }

                        // Year
                        if (anime.year > 0) {
                            Text(
                                text = "${anime.year}",
                                color = Color(0xFFF0F0F0),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                style = TextStyle(shadow = bannerTextShadow)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Title with heavy drop shadow
                    Text(
                        text = anime.title,
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 38.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(shadow = bannerTitleShadow)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Genres with drop shadow
                    if (anime.genres.isNotEmpty()) {
                        Text(
                            text = anime.genres.joinToString(" • "),
                            color = Color(0xFFE6E6FA),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(shadow = bannerTextShadow)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Synopsis with drop shadow
                    if (anime.synopsis.isNotBlank()) {
                        Text(
                            text = anime.synopsis,
                            color = Color(0xFFF5F5F5),
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(shadow = bannerTextShadow)
                        )
                    }
                }
            }
        }

        // Action Buttons Row (Mounted Stably Over Banner)
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 40.dp, bottom = 26.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { onPlayClick(activeAnime) },
                scale = ButtonDefaults.scale(focusedScale = 1.10f),
                border = ButtonDefaults.border(
                    border = Border(
                        border = BorderStroke(1.dp, Color.Transparent),
                        shape = RoundedCornerShape(12.dp)
                    ),
                    focusedBorder = Border(
                        border = BorderStroke(3.5.dp, Color.White),
                        shape = RoundedCornerShape(12.dp)
                    )
                ),
                colors = ButtonDefaults.colors(
                    containerColor = TvPrimaryRed,
                    focusedContainerColor = TvAccentPurple,
                    contentColor = Color.White,
                    focusedContentColor = Color.White
                ),
                shape = ButtonDefaults.shape(shape = RoundedCornerShape(12.dp)),
                modifier = Modifier
                    .focusRequester(playButtonRequester)
                    .onFocusChanged { hasFocus = it.hasFocus }
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                            keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP
                        ) {
                            if (onNavigateUp != null) {
                                onNavigateUp()
                                true
                            } else false
                        } else false
                    }
            ) {
                Text(
                    text = "▶ $actionLabel",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    style = TextStyle(
                        shadow = Shadow(
                            color = Color(0xCC000000),
                            offset = Offset(1f, 2f),
                            blurRadius = 4f
                        )
                    )
                )
            }

            Button(
                onClick = { onDetailsClick(activeAnime) },
                scale = ButtonDefaults.scale(focusedScale = 1.10f),
                border = ButtonDefaults.border(
                    border = Border(
                        border = BorderStroke(1.dp, Color.Transparent),
                        shape = RoundedCornerShape(12.dp)
                    ),
                    focusedBorder = Border(
                        border = BorderStroke(3.5.dp, Color.White),
                        shape = RoundedCornerShape(12.dp)
                    )
                ),
                colors = ButtonDefaults.colors(
                    containerColor = TvSurfaceColor,
                    focusedContainerColor = TvAccentPurple,
                    contentColor = Color.White,
                    focusedContentColor = Color.White
                ),
                shape = ButtonDefaults.shape(shape = RoundedCornerShape(12.dp)),
                modifier = Modifier
                    .onFocusChanged { hasFocus = it.hasFocus }
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                            keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP
                        ) {
                            if (onNavigateUp != null) {
                                onNavigateUp()
                                true
                            } else false
                        } else false
                    }
            ) {
                Text(
                    text = "Details",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    style = TextStyle(
                        shadow = Shadow(
                            color = Color(0xCC000000),
                            offset = Offset(1f, 2f),
                            blurRadius = 4f
                        )
                    )
                )
            }

            if (onToggleWatchlist != null) {
                Button(
                    onClick = { onToggleWatchlist(activeAnime) },
                    scale = ButtonDefaults.scale(focusedScale = 1.10f),
                    border = ButtonDefaults.border(
                        border = Border(
                            border = BorderStroke(1.dp, Color.Transparent),
                            shape = RoundedCornerShape(12.dp)
                        ),
                        focusedBorder = Border(
                            border = BorderStroke(3.5.dp, Color.White),
                            shape = RoundedCornerShape(12.dp)
                        )
                    ),
                    colors = ButtonDefaults.colors(
                        containerColor = if (isInWatchlist) TvAccentPurple.copy(alpha = 0.5f) else TvSurfaceColor,
                        focusedContainerColor = TvAccentPurple,
                        contentColor = Color.White,
                        focusedContentColor = Color.White
                    ),
                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(12.dp)),
                    modifier = Modifier
                        .onFocusChanged { hasFocus = it.hasFocus }
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP
                            ) {
                                if (onNavigateUp != null) {
                                    onNavigateUp()
                                    true
                                } else false
                            } else false
                        }
                ) {
                    Text(
                        text = if (isInWatchlist) "✓ In Watchlist" else "+ Watchlist",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        style = TextStyle(
                            shadow = Shadow(
                                color = Color(0xCC000000),
                                offset = Offset(1f, 2f),
                                blurRadius = 4f
                            )
                        )
                    )
                }
            }
        }

        // Carousel Dot Indicators (Bottom Right, non-focusable visual indicators)
        if (featuredAnimeList.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 36.dp, bottom = 26.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                featuredAnimeList.forEachIndexed { index, _ ->
                    val isSelected = index == currentIndex
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 10.dp else 7.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) TvPrimaryRed else Color(0x66FFFFFF))
                    )
                }
            }
        }
    }
}

