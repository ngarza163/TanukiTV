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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
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
import com.darknet.animetv.data.model.AiringScheduleItem
import com.darknet.animetv.data.model.Anime
import com.darknet.animetv.data.repository.AnimeRepository
import com.darknet.animetv.ui.components.TvTanukiLoadingIndicator
import com.darknet.animetv.ui.components.tvFocusableItem
import com.darknet.animetv.ui.theme.TvAccentPurple
import com.darknet.animetv.ui.theme.TvCardBorder
import com.darknet.animetv.ui.theme.TvDarkBackground
import com.darknet.animetv.ui.theme.TvGoldStar
import com.darknet.animetv.ui.theme.TvPrimaryRed
import com.darknet.animetv.ui.theme.TvSuccessGreen
import com.darknet.animetv.ui.theme.TvSurfaceColor
import com.darknet.animetv.ui.theme.TvTextPrimary
import com.darknet.animetv.ui.theme.TvTextSecondary
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Android TV Leanback Airing Calendar & Weekly Simulcast Schedule.
 * 7-day day selector tabs with live countdown tags and 10-foot remote navigation.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ScheduleScreen(
    repository: AnimeRepository,
    modifier: Modifier = Modifier,
    onAnimeClick: (Anime) -> Unit = {},
    onNavigateUp: (() -> Unit)? = null,
    firstTabFocusRequester: FocusRequester? = null
) {
    val daysOfWeek = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    val initialDay = remember {
        when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "Monday"
            Calendar.TUESDAY -> "Tuesday"
            Calendar.WEDNESDAY -> "Wednesday"
            Calendar.THURSDAY -> "Thursday"
            Calendar.FRIDAY -> "Friday"
            Calendar.SATURDAY -> "Saturday"
            else -> "Sunday"
        }
    }

    var selectedDay by remember { mutableStateOf(initialDay) }
    var scheduleItems by remember { mutableStateOf<List<AiringScheduleItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val dayTabFocusRequesters = remember { List(daysOfWeek.size) { FocusRequester() } }
    val firstCardFocusRequester = remember { FocusRequester() }

    fun loadSchedule(day: String) {
        isLoading = true
        coroutineScope.launch {
            try {
                scheduleItems = repository.getAiringSchedule(day)
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(selectedDay) {
        loadSchedule(selectedDay)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TvDarkBackground)
            .padding(horizontal = 48.dp, vertical = 20.dp)
    ) {
        // Schedule Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "\uD83D\uDCC5 Airing Calendar & Simulcasts",
                    color = TvTextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Weekly anime broadcast timetable \u2022 Live countdown to Japan release",
                    color = TvTextSecondary,
                    fontSize = 13.sp
                )
            }

            // Current day indicator badge
            Box(
                modifier = Modifier
                    .background(TvSurfaceColor, RoundedCornerShape(12.dp))
                    .border(1.dp, TvCardBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Today: $initialDay",
                    color = TvAccentPurple,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Horizontal Day Selector Tabs for TV Remote
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            daysOfWeek.forEachIndexed { index, day ->
                val isSelected = selectedDay.equals(day, ignoreCase = true)
                val isToday = initialDay.equals(day, ignoreCase = true)
                var isFocused by remember { mutableStateOf(false) }

                Button(
                    onClick = { selectedDay = day },
                    scale = ButtonDefaults.scale(focusedScale = 1.08f),
                    border = ButtonDefaults.border(
                        focusedBorder = Border(
                            border = BorderStroke(3.dp, Color.White),
                            shape = RoundedCornerShape(10.dp)
                        )
                    ),
                    modifier = Modifier
                        .then(
                            if (index == 0 && firstTabFocusRequester != null) {
                                Modifier.focusRequester(firstTabFocusRequester)
                            } else {
                                Modifier.focusRequester(dayTabFocusRequesters[index])
                            }
                        )
                        .focusProperties {
                            if (onNavigateUp != null) {
                                up = FocusRequester.Cancel
                            }
                            if (scheduleItems.isNotEmpty()) {
                                down = firstCardFocusRequester
                            }
                        }
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                when (keyEvent.nativeKeyEvent.keyCode) {
                                    KeyEvent.KEYCODE_DPAD_UP -> {
                                        if (onNavigateUp != null) {
                                            onNavigateUp()
                                            true
                                        } else {
                                            focusManager.moveFocus(FocusDirection.Up)
                                        }
                                    }
                                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                                        if (scheduleItems.isNotEmpty()) {
                                            try {
                                                firstCardFocusRequester.requestFocus()
                                                true
                                            } catch (_: Exception) {
                                                focusManager.moveFocus(FocusDirection.Down)
                                            }
                                        } else {
                                            focusManager.moveFocus(FocusDirection.Down)
                                        }
                                    }
                                    else -> false
                                }
                            } else false
                        }
                        .onFocusChanged { isFocused = it.isFocused },
                    colors = ButtonDefaults.colors(
                        containerColor = if (isSelected) TvPrimaryRed else TvSurfaceColor,
                        focusedContainerColor = TvAccentPurple
                    ),
                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(10.dp))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = day,
                            color = if (isFocused || isSelected) Color.White else TvTextSecondary,
                            fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp
                        )
                        if (isToday) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(TvSuccessGreen, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "LIVE",
                                    color = Color.Black,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Schedule Content Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    TvTanukiLoadingIndicator(
                        statusText = "Loading $selectedDay Simulcast Schedule...",
                        subtitleText = "Syncing live AniList broadcast timetable"
                    )
                }
            } else if (scheduleItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No anime broadcasts scheduled for $selectedDay.",
                        color = TvTextSecondary,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    itemsIndexed(scheduleItems, key = { _, item -> item.id }) { index, item ->
                        AiringScheduleCard(
                            item = item,
                            onClick = { onAnimeClick(item.toAnime()) },
                            modifier = Modifier
                                .then(if (index == 0) Modifier.focusRequester(firstCardFocusRequester) else Modifier)
                                .then(
                                    if (index < 5) {
                                        val dayIdx = daysOfWeek.indexOfFirst { it.equals(selectedDay, ignoreCase = true) }.coerceAtLeast(0)
                                        Modifier
                                            .focusProperties {
                                                up = if (dayIdx == 0 && firstTabFocusRequester != null) {
                                                    firstTabFocusRequester
                                                } else {
                                                    dayTabFocusRequesters[dayIdx]
                                                }
                                            }
                                            .onPreviewKeyEvent { keyEvent ->
                                                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                                    keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP
                                                ) {
                                                    try {
                                                        if (dayIdx == 0 && firstTabFocusRequester != null) {
                                                            firstTabFocusRequester.requestFocus()
                                                        } else {
                                                            dayTabFocusRequesters[dayIdx].requestFocus()
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
        }
    }
}

/**
 * TV Leanback Schedule Card displaying poster, countdown badge, episode number, and title.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AiringScheduleCard(
    item: AiringScheduleItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .tvFocusableItem(
                onClick = onClick,
                focusedScale = 1.08f,
                borderWidth = 3.dp,
                focusedBorderColor = Color.White,
                shape = RoundedCornerShape(12.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .background(TvSurfaceColor, RoundedCornerShape(12.dp))
            .border(1.dp, if (isFocused) Color.White else TvCardBorder, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
    ) {
        // Poster with badges
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.70f)
                .background(Color(0xFF141622))
        ) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Bottom gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0x60000000),
                                Color(0xEE090A10)
                            ),
                            startY = 180f
                        )
                    )
            )

            // Top Left: Countdown Tag
            val isAired = item.timeUntilAiringSeconds <= 0L
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.TopStart)
                    .background(
                        if (isAired) TvSuccessGreen.copy(alpha = 0.9f) else TvAccentPurple.copy(alpha = 0.9f),
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = item.formatCountdown(),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Top Right: Episode Badge
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.TopEnd)
                    .background(Color(0xCC000000), RoundedCornerShape(6.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "EP ${item.episodeNumber}",
                    color = TvGoldStar,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            // Bottom Row inside image: Score
            if (item.score > 0.0) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "\u2B50 ${item.score}",
                        color = TvGoldStar,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Title and Genres
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = item.title,
                color = if (isFocused) Color.White else TvTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.genres.take(2).joinToString(" \u2022 ").ifBlank { "Simulcast" },
                color = TvTextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
