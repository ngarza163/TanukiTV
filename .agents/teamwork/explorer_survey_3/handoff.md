# Handoff Report: TV UI, Remote Navigation, Media3 Player HUD & Local Persistence Survey

**Agent**: Explorer 3 (`explorer_survey_3`)  
**Mission**: Deep investigation and architectural specification for R1 (Native Android TV Compose UI), R3 (Media3 ExoPlayer Video Playback HUD), and R4 (Local Persistence) for the AnimeTV Android TV rebuild.  
**Target Project**: `c:\Users\ngarz\Documents\antigravity\blissful-planck`  
**Date**: 2026-10-27  

---

## 1. Observation

### 1.1 Legacy Repository vs Target Rebuild Architecture
- **Legacy Implementation (`animetv-src/`)**:
  - `animetv-src/app/src/main/res/layout/activity_main.xml` (lines 18-38): Uses a `FrameLayout` (`@+id/video_layout`) and an invisible `WebView` (`@+id/webview`) hosting an embedded HTML5/Cordova web application.
  - `animetv-src/app/src/main/java/com/amarullz/androidtv/animetvjmto/AnimeView.java` (lines 1-100, 2456 total lines): A 2,456-line monolithic WebView wrapper mixing legacy `ExoMedia` / `ExoPlayer` video playback, `JavascriptInterface` methods (`AnimeApi.java`), and SharedPreferences storage.
  - `animetv-src/app/src/main/java/com/amarullz/androidtv/animetvjmto/AddSourceActivity.kt` (lines 12-44, 46-99): A standard phone-oriented `AppCompatActivity` with a custom `SourceDatabaseHelper` extending `SQLiteOpenHelper`, storing sources in raw SQLite table `sources` (`id`, `name`, `baseUrl`, `animeListSelector`, `episodeSelector`, `streamUrlSelector`).
- **Target Modern Rebuild (`app/`)**:
  - `app/build.gradle` (lines 7-15, 34-45): Targets Android SDK 34 (`minSdk 26`, `targetSdk 34`), Java 17, Kotlin 1.9.24, with Compose compiler extension `1.5.14` and opt-ins for `androidx.tv.material3.ExperimentalTvMaterial3Api` and `androidx.compose.material3.ExperimentalMaterial3Api`.
  - `app/build.gradle` (lines 68-87): Already includes:
    - `androidx.compose:compose-bom:2024.06.00`
    - `androidx.tv:tv-foundation:1.0.0-alpha11`
    - `androidx.tv:tv-material:1.0.0-alpha11`
    - `androidx.media3:media3-exoplayer:1.3.1`
    - `androidx.media3:media3-ui:1.3.1`
    - `androidx.media3:media3-exoplayer-hls:1.3.1`
    - `androidx.media3:media3-datasource-okhttp:1.3.1`
    - `io.coil-kt:coil-compose:2.6.0`
  - `app/src/main/AndroidManifest.xml` (lines 8-36): Explicitly requests TV features:
    ```xml
    <uses-feature android:name="android.hardware.touchscreen" android:required="false" />
    <uses-feature android:name="android.software.leanback" android:required="true" />
    ```
    Declares `MainActivity` with `android.intent.category.LEANBACK_LAUNCHER`.
  - `app/src/main/java/com/darknet/animetv/ui/` (`components/`, `screens/`, `theme/`) and `app/src/main/java/com/darknet/animetv/player/`: Currently completely empty directories awaiting implementation.
  - `app/src/main/java/com/darknet/animetv/data/storage/AppDatabase.kt` (lines 11-112, 114-311): Contains an initial `AppDatabaseHelper` extending `SQLiteOpenHelper` ("animetv_v2.db") with tables `watch_history`, `favorites`, and `sources`, plus an `AppStorage` wrapper providing synchronous CRUD helper methods. Room SQLite (`androidx.room`) is not yet added to `app/build.gradle`.

---

## 2. Logic Chain

### 2.1 Requirement R1: Native Android TV Compose UI Architecture
1. **Remote D-Pad Focus & Leanback Interaction Model**:
   - On Android TV, there is zero touch input (`touchscreen android:required="false"`). All navigation is driven by remote control DPAD keys (`DPAD_UP`, `DPAD_DOWN`, `DPAD_LEFT`, `DPAD_RIGHT`, `DPAD_CENTER`/`ENTER`, `BACK`).
   - If interactive elements do not visually indicate focus, the user experience breaks completely. Every focusable element must have a distinct focused state:
     - **Scale**: `1.08f` scale-up on focus using `animateFloatAsState`.
     - **Border / Focus Ring**: 2.5dp solid border in accent color (e.g. `#FF5722` or `#E50914`).
     - **Glow / Elevation**: 12dp to 16dp elevation shadow.
     - **Z-Index Layering**: When an item scales up in a `TvLazyRow`, it overlaps neighboring cards unless `Modifier.zIndex(if (isFocused) 1f else 0f)` is applied.
   - **Focus Restoration**: In horizontal shelves embedded in a vertical list, navigating down into row 3 and then navigating back up to row 2 must return focus to the previously selected card in row 2, rather than resetting to item 0. This requires `Modifier.focusRestorer()` (or `FocusRequester` state retention).
   - **Default Focus**: Upon screen launch, an initial element must be focused immediately via `LaunchedEffect(Unit) { focusRequester.requestFocus() }` to ensure the D-pad is not lost in a focus void.

2. **Screen Design & Hierarchy**:
   - **Home Screen**:
     - Dynamic Hero Banner (`Carousel` or auto-advancing banner) displaying top trending anime with high-resolution 16:9 backdrop, vertical and horizontal dark gradient overlays, title, synopsis snippet, score badge, and action buttons ("Play / Resume", "Details", "Add to Watchlist").
     - Horizontal Shelves (`TvLazyRow` inside `TvLazyColumn`):
       1. *Continue Watching*: Displayed dynamically when `watchHistory` has entries, with episode thumbnail, episode number, and progress bar overlay.
       2. *Trending Anime*: Sourced from active provider or Jikan catalog.
       3. *Popular Anime*: All-time popular anime.
       4. *Top Airing / Recent Episodes*: Newly released episodes today.
       5. *Genre Shelves*: Action, Shounen, Fantasy, Romance, Sci-Fi.
   - **TV Search Screen**:
     - Quick D-pad category filter chips at the top ("All", "Action", "Shounen", "Fantasy", "Sci-Fi", "Movies") allowing instant discovery without tedious remote typing.
     - Virtual keyboard / text input area triggering Android TV IME keyboard.
     - 5-column responsive grid (`TvLazyVerticalGrid(columns = TvGridCells.Fixed(5))`) displaying matching anime cards with seamless D-pad traversal from search input to grid cards.
   - **Anime Details Screen**:
     - Immersive backdrop background with gradient fade.
     - Split layout: Left poster card, Right metadata section (Title, Romaji title, Score ⭐ 8.8, Year, Status, Total Episodes, Studio, Genre chips, Synopsis).
     - Action buttons: "Play Next Episode" (focused by default), "Add to Watchlist / Favorite" (toggles Room DB state), "Select Source".
     - Interactive Episode Picker: Horizontal shelf of episode cards showing thumbnail, episode number, title, and watched progress badge.
   - **Sources Management Screen**:
     - Remote-friendly list of active sources.
     - Each source card shows: Name, Base URL, Type (`SCRAPER`, `CONSUMET`, `CUSTOM_HTML`), and status badge (`Active`, `Testing...`, `Success - 24 items`, `Failed - Timeout`).
     - Action triggers: "Test Connection", "Toggle Enable", "Move Priority Up/Down", "Add Custom Source".
     - "Add Custom Source" dialog with fields for Name, Base URL, Anime List Selector, Episode Selector, Stream URL Selector.

### 2.2 Requirement R3: Media3 ExoPlayer Video Playback & TV HUD Architecture
1. **ExoPlayer Configuration for Anime Streaming**:
   - Anime streaming hosts (VidStreaming, StreamWish, Mp4Upload, AnimeFLV) enforce strict HTTP headers (such as `User-Agent`, `Referer`, `Origin`). Standard media players fail with 403 Forbidden without header injection.
   - Using Media3 `DefaultHttpDataSource.Factory()` configured with custom headers (`setDefaultRequestProperties(headers)`) or `OkHttpDataSource.Factory(client)` ensures robust stream resolution for HLS (.m3u8) adaptive bitrate streams and progressive MP4 streams.
   - Compose integration: Embedding `PlayerView(context)` via `AndroidView` with `useController = false` disables mobile touch controls, allowing a 100% custom TV HUD overlay built in Compose.
2. **TV Remote-Optimized HUD Overlay**:
   - **Auto-Hide Inactivity Timer**: When playback is active, HUD auto-hides after 5 seconds of inactivity.
   - **Remote D-Pad Interception**:
     - Using `Modifier.onPreviewKeyEvent`:
       - When HUD is hidden: Any DPAD key press (`UP`, `DOWN`, `LEFT`, `RIGHT`, `CENTER`) makes HUD visible and resets the 5-second auto-hide timer.
       - When HUD is visible:
         - `DPAD_LEFT`: Triggers 10-second fast seek backward (`player.seekTo((player.currentPosition - 10000).coerceAtLeast(0))`) and shows a center-screen `<< -10s` visual seek pill.
         - `DPAD_RIGHT`: Triggers 10-second fast seek forward (`player.seekTo((player.currentPosition + 10000).coerceAtMost(player.duration))`) and shows a `>> +10s` seek pill.
         - `BACK`: First click hides HUD; second click confirms exiting video player.
         - Any remote key press resets the 5s auto-hide countdown.
3. **Control Elements & Track Selection**:
   - **Top Header**: Anime Title, Episode Number & Title, Stream Server badge ("GogoStream 1080p"), Current Time clock.
   - **Bottom Scrub Bar**: Current timestamp (`12:45`), high-contrast TV progress bar with buffered indicator and accent thumb, Total duration (`24:10`).
   - **Control Buttons**:
     - Previous Episode (disabled if Episode 1).
     - Rewind 10s.
     - Play / Pause (primary centered focusable button).
     - Forward 10s.
     - Next Episode (advances to next episode in anime series).
     - Video Quality Selector: Opens modal dialog to choose resolution (1080p, 720p, 480p, 360p, Auto) via Media3 `TrackSelectionParameters`.
     - Subtitle Track Toggle: Enable/disable or switch subtitle language tracks.
     - Audio Track Selector: Switch between Sub (Japanese) and Dub (English).
     - Source Mirror Selector: Switch stream provider if buffering occurs.
4. **Progress Tracking & Auto-Resume**:
   - Coroutine ticker polling `player.currentPosition` every 1000ms during playback.
   - Automatically writes `WatchProgress` to database.
   - If `position >= duration * 0.90` (90% watched), sets `completed = true`.
   - On video launch: Queries database for existing progress. If `positionMs > 10_000` and `!completed`, automatically seeks to `positionMs` with a "Resumed from 14:22" toast/pill indicator.

### 2.3 Requirement R4: Local Persistence (Room SQLite) Architecture
1. **Room vs Raw SQLiteOpenHelper**:
   - Current `AppDatabaseHelper` in `AppDatabase.kt` uses legacy `SQLiteOpenHelper`.
   - Disadvantages of raw `SQLiteOpenHelper`:
     - Synchronous queries block the main thread unless manually dispatched to coroutines.
     - Manual `Cursor` column index parsing is error-prone.
     - No native reactive streams (`Flow`) for Compose UI state.
   - Advantages of Room SQLite:
     - Compile-time verification of SQL queries and schema integrity.
     - Direct `Flow<List<T>>` observation that updates Compose UI components instantly when records change.
     - Seamless coroutine suspend functions for inserts/updates/deletions.
2. **Schema & Entity Specifications**:
   - **`WatchHistoryEntity`**:
     ```kotlin
     @Entity(tableName = "watch_history", primaryKeys = ["animeId", "episodeId"])
     data class WatchHistoryEntity(
         val animeId: String,
         val episodeId: String,
         val title: String,
         val posterUrl: String,
         val episodeNumber: Int,
         val episodeTitle: String,
         val timestamp: Long,
         val duration: Long,
         val completed: Boolean,
         val lastWatchedDate: Long
     )
     ```
   - **`WatchlistEntity` (Favorites)**:
     ```kotlin
     @Entity(tableName = "watchlist")
     data class WatchlistEntity(
         @PrimaryKey val animeId: String,
         val title: String,
         val posterUrl: String,
         val backdropUrl: String = "",
         val rating: Double = 0.0,
         val status: String = "Ongoing",
         val genres: String = "",
         val synopsis: String = "",
         val addedAt: Long = System.currentTimeMillis()
     )
     ```
   - **`SourceConfigEntity`**:
     ```kotlin
     @Entity(tableName = "source_configs")
     data class SourceConfigEntity(
         @PrimaryKey val sourceId: String,
         val name: String,
         val baseUrl: String,
         val enabled: Boolean = true,
         val priority: Int = 0,
         val sourceType: String = "SCRAPER",
         val customConfig: String = "",
         val lastTestedAt: Long = 0L,
         val lastTestStatus: String = "UNTESTED"
     )
     ```

---

## 3. Caveats
1. **Compose for TV Library Alpha Status**:
   `androidx.tv:tv-foundation:1.0.0-alpha11` and `androidx.tv:tv-material:1.0.0-alpha11` require `@OptIn(ExperimentalTvMaterial3Api::class)`. This compiler flag is already properly configured in `app/build.gradle` (lines 41-45).
2. **Room KSP Configuration Coordination**:
   Enabling Room with KSP requires adding `id 'com.google.devtools.ksp' version '1.9.24-1.0.20'` to `build.gradle` and `ksp 'androidx.room:room-compiler:2.6.1'` to `app/build.gradle`. If KSP is pending in the build setup milestone, `AppDatabaseHelper` in `AppDatabase.kt` can serve as an immediate working bridge while adhering to the exact same schema contracts.
3. **ExoPlayer Surface vs TextureView on Low-End TV Hardware**:
   Certain budget Android TV boxes (Amlogic S905X chips, Android TV sticks) struggle with `TextureView` hardware video decoding. Using `SurfaceView` inside `PlayerView` provides maximum hardware acceleration and zero dropped frames.

---

## 4. Conclusion & Complete Architectural Blueprints

### 4.1 Component Blueprint: TV Focus & Visual Highlight Modifiers
```kotlin
package com.darknet.animetv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

@Composable
fun Modifier.tvFocusableItem(
    scaleOnFocus: Float = 1.08f,
    borderWidth: Dp = 2.5.dp,
    focusedBorderColor: Color = Color(0xFFFF5722),
    cornerRadius: Dp = 12.dp,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
): Modifier {
    val isFocused by interactionSource.collectIsFocusedAsState()
    val animatedScale by animateFloatAsState(
        targetValue = if (isFocused) scaleOnFocus else 1.0f,
        animationSpec = tween(durationMillis = 200),
        label = "tvScale"
    )

    return this
        .zIndex(if (isFocused) 10f else 1f)
        .scale(animatedScale)
        .border(
            width = if (isFocused) borderWidth else 0.dp,
            color = if (isFocused) focusedBorderColor else Color.Transparent,
            shape = RoundedCornerShape(cornerRadius)
        )
}
```

### 4.2 Component Blueprint: Media Card (`TvAnimeCard`)
```kotlin
package com.darknet.animetv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.darknet.animetv.data.model.Anime

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvAnimeCard(
    anime: Anime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    StandardCardContainer(
        imageCard = { interactionSource ->
            Card(
                onClick = onClick,
                interactionSource = interactionSource,
                modifier = Modifier
                    .width(140.dp)
                    .height(210.dp)
                    .tvFocusableItem(cornerRadius = 10.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = anime.posterUrl,
                        contentDescription = anime.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                    )
                    // Score badge
                    if (anime.score > 0.0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "⭐ ${anime.score}",
                                fontSize = 11.sp,
                                color = Color(0xFFFFD700),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        title = {
            Text(
                text = anime.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier.width(140.dp).padding(top = 4.dp)
            )
        },
        modifier = modifier
    )
}
```

### 4.3 Component Blueprint: Media3 TV Player Screen & Remote HUD Overlay
```kotlin
package com.darknet.animetv.player

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.tv.material3.*
import com.darknet.animetv.data.model.VideoSource
import com.darknet.animetv.data.model.WatchProgress
import com.darknet.animetv.data.storage.AppStorage
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun TvPlayerScreen(
    videoSource: VideoSource,
    animeId: String,
    animeTitle: String,
    posterUrl: String,
    episodeId: String,
    episodeNumber: Int,
    episodeTitle: String,
    onBack: () -> Unit,
    onNextEpisode: (() -> Unit)? = null,
    onPrevEpisode: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val storage = remember { AppStorage(context) }

    var isHudVisible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var seekPillText by remember { mutableStateOf<String?>(null) }
    var interactionCounter by remember { mutableIntStateOf(0) }

    // Media3 ExoPlayer Instance
    val exoPlayer = remember {
        val httpDataSource = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AnimeTV/2.0")
            .setAllowCrossProtocolRedirects(true)

        videoSource.headers.forEach { (k, v) ->
            httpDataSource.setDefaultRequestProperties(mapOf(k to v))
        }

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpDataSource))
            .build().apply {
                setMediaItem(MediaItem.fromUri(videoSource.url))
                prepare()
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) {
                            durationMs = duration.coerceAtLeast(0L)
                            // Auto-resume from saved progress
                            val saved = storage.getWatchProgress(animeId)
                            if (saved != null && saved.episodeId == episodeId && saved.positionMs > 10_000 && !saved.completed) {
                                seekTo(saved.positionMs)
                            }
                        }
                    }
                })
            }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Save on exit
            if (durationMs > 0) {
                storage.saveWatchProgress(
                    WatchProgress(
                        animeId = animeId,
                        animeTitle = animeTitle,
                        posterUrl = posterUrl,
                        episodeId = episodeId,
                        episodeNumber = episodeNumber,
                        episodeTitle = episodeTitle,
                        positionMs = exoPlayer.currentPosition,
                        durationMs = durationMs,
                        completed = exoPlayer.currentPosition >= durationMs * 0.90
                    )
                )
            }
            exoPlayer.release()
        }
    }

    // Auto-hide HUD after 5s of inactivity
    LaunchedEffect(isHudVisible, interactionCounter, isPlaying) {
        if (isHudVisible && isPlaying) {
            delay(5000L)
            isHudVisible = false
        }
    }

    // Periodic Progress Saver & UI Sync
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            if (durationMs > 0 && currentPosMs % 5000L < 1000L) {
                storage.saveWatchProgress(
                    WatchProgress(
                        animeId = animeId,
                        animeTitle = animeTitle,
                        posterUrl = posterUrl,
                        episodeId = episodeId,
                        episodeNumber = episodeNumber,
                        episodeTitle = episodeTitle,
                        positionMs = currentPosMs,
                        durationMs = durationMs,
                        completed = currentPosMs >= durationMs * 0.90
                    )
                )
            }
            delay(1000L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    interactionCounter++
                    when (event.key) {
                        Key.Back -> {
                            if (isHudVisible) {
                                isHudVisible = false
                                true
                            } else {
                                onBack()
                                true
                            }
                        }
                        Key.DirectionLeft -> {
                            if (!isHudVisible) isHudVisible = true
                            val target = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                            exoPlayer.seekTo(target)
                            seekPillText = "<< -10s"
                            true
                        }
                        Key.DirectionRight -> {
                            if (!isHudVisible) isHudVisible = true
                            val target = (exoPlayer.currentPosition + 10_000L).coerceAtMost(durationMs)
                            exoPlayer.seekTo(target)
                            seekPillText = ">> +10s"
                            true
                        }
                        Key.DirectionUp, Key.DirectionDown, Key.DirectionCenter -> {
                            if (!isHudVisible) {
                                isHudVisible = true
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // Video Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = exoPlayer
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    keepScreenOn = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Center Seek Indicator Pill
        seekPillText?.let { text ->
            LaunchedEffect(text) {
                delay(1200L)
                seekPillText = null
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(text = text, color = Color.White, fontSize = 20.sp)
            }
        }

        // TV Remote HUD Overlay
        AnimatedVisibility(
            visible = isHudVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            TvPlayerHud(
                animeTitle = animeTitle,
                episodeTitle = "EP $episodeNumber - $episodeTitle",
                serverName = videoSource.serverName,
                quality = videoSource.quality,
                currentPosMs = currentPosMs,
                durationMs = durationMs,
                isPlaying = isPlaying,
                onPlayPause = {
                    if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                },
                onRewind10 = {
                    exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0L))
                },
                onForward10 = {
                    exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(durationMs))
                },
                onPrevEpisode = onPrevEpisode,
                onNextEpisode = onNextEpisode,
                onBack = onBack
            )
        }
    }
}
```

### 4.4 Persistence Specification: Room SQLite Schemas & Reactive DAOs
```kotlin
package com.darknet.animetv.data.storage.room

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "watch_history", primaryKeys = ["animeId", "episodeId"])
data class WatchHistoryEntity(
    val animeId: String,
    val episodeId: String,
    val title: String,
    val posterUrl: String,
    val episodeNumber: Int,
    val episodeTitle: String,
    val timestamp: Long,
    val duration: Long,
    val completed: Boolean,
    val lastWatchedDate: Long = System.currentTimeMillis()
)

@Dao
interface WatchHistoryDao {
    @Upsert
    suspend fun saveProgress(progress: WatchHistoryEntity)

    @Query("SELECT * FROM watch_history WHERE animeId = :animeId AND episodeId = :episodeId")
    suspend fun getProgress(animeId: String, episodeId: String): WatchHistoryEntity?

    @Query("SELECT * FROM watch_history WHERE completed = 0 AND timestamp > 10000 GROUP BY animeId ORDER BY lastWatchedDate DESC LIMIT 20")
    fun getContinueWatching(): Flow<List<WatchHistoryEntity>>

    @Query("DELETE FROM watch_history WHERE animeId = :animeId")
    suspend fun deleteForAnime(animeId: String)
}

@Entity(tableName = "watchlist")
data class WatchlistEntity(
    @PrimaryKey val animeId: String,
    val title: String,
    val posterUrl: String,
    val backdropUrl: String = "",
    val rating: Double = 0.0,
    val status: String = "Ongoing",
    val genres: String = "",
    val synopsis: String = "",
    val addedAt: Long = System.currentTimeMillis()
)

@Dao
interface WatchlistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: WatchlistEntity)

    @Query("DELETE FROM watchlist WHERE animeId = :animeId")
    suspend fun delete(animeId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE animeId = :animeId)")
    fun isFavorite(animeId: String): Flow<Boolean>

    @Query("SELECT * FROM watchlist ORDER BY addedAt DESC")
    fun getAllFavorites(): Flow<List<WatchlistEntity>>
}

@Entity(tableName = "source_configs")
data class SourceConfigEntity(
    @PrimaryKey val sourceId: String,
    val name: String,
    val baseUrl: String,
    val enabled: Boolean = true,
    val priority: Int = 0,
    val sourceType: String = "SCRAPER",
    val customConfig: String = "",
    val lastTestedAt: Long = 0L,
    val lastTestStatus: String = "UNTESTED"
)

@Dao
interface SourceConfigDao {
    @Query("SELECT * FROM source_configs ORDER BY priority ASC, name ASC")
    fun getAllSources(): Flow<List<SourceConfigEntity>>

    @Query("SELECT * FROM source_configs WHERE enabled = 1 ORDER BY priority ASC")
    suspend fun getEnabledSources(): List<SourceConfigEntity>

    @Upsert
    suspend fun upsert(source: SourceConfigEntity)

    @Query("UPDATE source_configs SET enabled = :enabled WHERE sourceId = :sourceId")
    suspend fun setEnabled(sourceId: String, enabled: Boolean)

    @Query("DELETE FROM source_configs WHERE sourceId = :sourceId")
    suspend fun delete(sourceId: String)
}
```

---

## 5. Verification Method

1. **Static Analysis & Dependencies Verification**:
   - Inspect `app/build.gradle` to verify the presence of:
     - `androidx.tv:tv-foundation:1.0.0-alpha11`
     - `androidx.tv:tv-material:1.0.0-alpha11`
     - `androidx.media3:media3-exoplayer:1.3.1`
     - `androidx.media3:media3-ui:1.3.1`
     - `androidx.media3:media3-exoplayer-hls:1.3.1`
     - `androidx.media3:media3-datasource-okhttp:1.3.1`
   - Command:
     ```pwsh
     ./gradlew app:dependencies --configuration releaseRuntimeClasspath
     ```
2. **Leanback Manifest Verification**:
   - Inspect `app/src/main/AndroidManifest.xml` to ensure:
     - `android.hardware.touchscreen` is marked `required="false"`.
     - `android.software.leanback` is marked `required="true"`.
     - Main activity has `android.intent.category.LEANBACK_LAUNCHER`.
3. **Compilation & Unit Test Validation**:
   - Run compilation command:
     ```pwsh
     ./gradlew assembleDebug
     ```
   - Verify that all `@OptIn(ExperimentalTvMaterial3Api::class)` and `@OptIn(UnstableApi::class)` annotations pass without compilation errors.
4. **Invalidation Conditions**:
   - If D-pad focus cannot navigate between `TvLazyRow` cards without losing active focus or crashing with `IllegalStateException`, focus restorer or focus requester logic requires adjustments.
   - If ExoPlayer fails to play streams due to 403 Forbidden errors, verify that `DefaultHttpDataSource.Factory` injects required `Referer` and `User-Agent` headers matching the target anime streaming provider.
