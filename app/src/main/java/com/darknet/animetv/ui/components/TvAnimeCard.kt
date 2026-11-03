package com.darknet.animetv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.darknet.animetv.data.model.Anime
import com.darknet.animetv.data.model.WatchProgress
import com.darknet.animetv.ui.theme.TvCardBorder
import com.darknet.animetv.ui.theme.TvGoldStar
import com.darknet.animetv.ui.theme.TvPrimaryRed
import com.darknet.animetv.ui.theme.TvSurfaceColor

/**
 * High-Visibility Android TV 140x210dp focus-scaled media card.
 * Features:
 * - 140x210dp dimensions (standard 2:3 poster ratio)
 * - 1.14x D-pad focus scaling with high-contrast bright white 3.5dp border ring
 * - Centered floating [▶ VIEW] action badge for unmistakable focus recognition
 * - Expanded bottom title banner with bold white text, red accent line, and status
 * - Subtle unfocused dimming (0.85f alpha) so the active card commands focus
 * - Score badge with glowing accent ring when focused
 * - Watch progress bar overlay for Continue Watching shelf
 */
@Composable
fun TvAnimeCard(
    anime: Anime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progressPercentage: Float? = null,
    badgeText: String? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .width(140.dp)
            .height(210.dp)
            .tvFocusableItem(
                onClick = onClick,
                focusedScale = 1.14f,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = TvCardBorder.copy(alpha = 0.5f),
                borderWidth = 3.5.dp,
                shape = RoundedCornerShape(12.dp),
                onFocusChanged = { isFocused = it }
            )
            .clip(RoundedCornerShape(12.dp))
            .background(TvSurfaceColor)
    ) {
        // Poster Image (dimmed slightly when unfocused, 100% bright when focused)
        AsyncImage(
            model = anime.posterUrl.ifBlank { anime.backdropUrl },
            contentDescription = anime.title,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = if (isFocused) 1.0f else 0.85f
                },
            contentScale = ContentScale.Crop
        )

        // Bottom Shadow / Gradient for readable title
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(if (isFocused) 0.70f else 0.60f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = if (isFocused) {
                            listOf(
                                Color.Transparent,
                                Color(0xCC05060A),
                                Color(0xFA05060A)
                            )
                        } else {
                            listOf(
                                Color.Transparent,
                                Color(0x8805060A),
                                Color(0xEB0A0B10)
                            )
                        }
                    )
                )
        )

        // Centered Action Indicator Pill (Appears on D-pad hover)
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
                        text = "VIEW",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        // Score Badge (Top Right)
        if (anime.score > 0.0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(
                        if (isFocused) Color(0xF005060A) else Color(0xD9000000),
                        RoundedCornerShape(6.dp)
                    )
                    .border(
                        width = if (isFocused) 1.dp else 0.dp,
                        color = if (isFocused) TvGoldStar.copy(alpha = 0.8f) else Color.Transparent,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "★",
                        color = TvGoldStar,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = String.format("%.1f", anime.score),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Custom Badge or Dub Badge (Top Left, e.g. Status, Episode, or 🎙️ DUB)
        val effectiveBadgeText = when {
            !badgeText.isNullOrBlank() -> badgeText
            anime.hasDub -> "🎙️ DUB"
            else -> null
        }
        if (!effectiveBadgeText.isNullOrBlank()) {
            val isDubBadge = badgeText.isNullOrBlank() && anime.hasDub
            val badgeBgColor = if (isDubBadge) {
                if (isFocused) com.darknet.animetv.ui.theme.TvAccentPurple else com.darknet.animetv.ui.theme.TvAccentPurple.copy(alpha = 0.90f)
            } else {
                if (isFocused) TvPrimaryRed else TvPrimaryRed.copy(alpha = 0.85f)
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(
                        badgeBgColor,
                        RoundedCornerShape(6.dp)
                    )
                    .border(
                        width = if (isFocused) 1.dp else 0.dp,
                        color = if (isFocused) Color.White.copy(alpha = 0.8f) else Color.Transparent,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = effectiveBadgeText,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // Anime Title & Info at Bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(
                    horizontal = 8.dp,
                    vertical = if (progressPercentage != null) 10.dp else 8.dp
                )
        ) {
            if (isFocused) {
                // Focus accent line above title
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.35f)
                        .height(2.dp)
                        .background(TvPrimaryRed)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            Text(
                text = anime.title,
                color = if (isFocused) Color.White else Color(0xFFDCDCDC),
                fontWeight = if (isFocused) FontWeight.ExtraBold else FontWeight.Medium,
                fontSize = if (isFocused) 12.5.sp else 11.5.sp,
                maxLines = if (isFocused) 3 else 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = if (isFocused) 16.sp else 14.sp
            )

            if (isFocused) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = if (!anime.status.isNullOrBlank() && anime.status != "UNKNOWN") {
                        anime.status.uppercase()
                    } else {
                        "PRESS OK TO OPEN"
                    },
                    color = TvGoldStar,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    maxLines = 1
                )
            }
        }

        // Watch Progress Bar Overlay (for Continue Watching)
        if (progressPercentage != null && progressPercentage > 0f) {
            val clamped = progressPercentage.coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(4.dp)
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

/**
 * Overload for WatchProgress card in Continue Watching shelf.
 */
@Composable
fun TvContinueWatchingCard(
    progress: WatchProgress,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sampleAnime = remember(progress) {
        Anime(
            id = progress.animeId,
            title = progress.animeTitle,
            posterUrl = progress.posterUrl,
            status = "EP ${progress.episodeNumber}"
        )
    }

    TvAnimeCard(
        anime = sampleAnime,
        onClick = onClick,
        modifier = modifier,
        progressPercentage = progress.progressPercentage,
        badgeText = "EP ${progress.episodeNumber}"
    )
}
