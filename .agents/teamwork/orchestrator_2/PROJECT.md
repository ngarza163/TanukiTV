# Project: TanukiTV Android TV Production Upgrade

## Architecture
TanukiTV (`com.darknet.animetv`) is a native 10-foot Android TV streaming application built with Jetpack Compose for TV (`androidx.tv.compose`), Media3 ExoPlayer, and Kotlin Coroutines/Flow.
The production upgrade elevates TanukiTV from a demo prototype to a production-grade living-room streaming service across four major subsystems:
1. **Live Data Integration & Catalog Layer**: Pure metadata catalog querying the AniList GraphQL API in real time (`AiringSchedule`, `characters(sort: [ROLE, RELEVANCE])`, public `User` and `MediaListCollection` sync), delegating video playback to active multi-source scrapers (`KaaSource`, `AniwatchSource`, `MiruroSource`, `ConsumetSource`, `AnikotoSource`, `ReAnimeSource`, `WcoSource`).
2. **First-Class Dubbed Experience**: End-to-end support for English Dubbed anime and episodes across models (`Anime.hasDub`, `Episode.audioTrack`, `VideoSource.audioTrack`), repository query `getDubbedAnime()`, Home Screen Dubbed shelf, Search Screen Dubbed filter chip, and interactive Details & Player SUB/DUB toggle pills.
3. **Procedural TV Remote Audio & Screen Animations**: Zero-asset procedural PCM wave synthesizer (`TvSoundManager` via `AudioTrack`) providing <5ms latency sound effects for remote navigation, integrated with `UserSettings` and `SettingsScreen`; smooth `AnimatedContent` tab and drill-down transitions; multi-pass soft focus glow.
4. **Animated Tanuki Mascot Loading Component**: High-fidelity Compose Canvas mascot (`TvTanukiLoadingIndicator`) featuring bobbing, tail wagging, eye blinking, glowing leaf aura, pulsing orbital ring, and contextual subtitles, replacing generic `CircularProgressIndicator` across all 5 major screens.

---

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| F1 | Extended Dub Models | Add `hasDub`, `dubbedEpisodesCount` to `Anime`; `hasDub`, `audioTrack` to `Episode`; `audioTrack` to `VideoSource` | M1 | ORIGINAL_REQUEST R2 |
| F2 | Live Airing Schedule GraphQL | Query AniList GraphQL `airingSchedules` in `AniListCatalogSource` and `DefaultAnimeRepository` with offline fallback | M1 | ORIGINAL_REQUEST R1 |
| F3 | Live Character & Seiyuu Cast GraphQL | Query AniList GraphQL `characters` (with Japanese & English VAs) in `AniListCatalogSource` and `DefaultAnimeRepository` | M1 | ORIGINAL_REQUEST R1 |
| F4 | Pure Metadata Stream Separation | Ensure `AniListCatalogSource.getEpisodeStreams` returns `emptyList()` and `DefaultAnimeRepository` routes streams to scrapers | M1 | ORIGINAL_REQUEST R1 |
| F5 | Dubbed Anime Repository Query | Implement `AnimeRepository.getDubbedAnime()` aggregating multi-source dub catalogs with offline curated fallback | M1 | ORIGINAL_REQUEST R2 |
| F6 | Stream Audio Track Priority | Support `preferredAudio: String` ("SUB" \| "DUB") in `getEpisodeStreams` prioritizing matching audio sources | M1 | ORIGINAL_REQUEST R1, R2 |
| F7 | Production AniList Account Sync | Add `loginWithUsername` and unauthenticated public `MediaListCollection` sync in `AniListSyncManager` (no fake demo anime injection) | M1 | ORIGINAL_REQUEST R1 |
| F8 | Home Screen Dubbed Shelf | Add `"🎙️ Dubbed Anime & Episodes"` horizontal shelf on `HomeScreen` with `DUB` badges on cards | M2 | ORIGINAL_REQUEST R2 |
| F9 | Search Screen Dubbed Filter | Add `"🎙️ Dubbed"` filter chip to `SearchScreen` filtering catalog to dubbed titles | M2 | ORIGINAL_REQUEST R2 |
| F10 | Details SUB / DUB Audio Toggle | Add focusable `[ SUB ]  [ 🎙️ DUB ]` audio toggle pill above episode shelf in `DetailsScreen` | M2 | ORIGINAL_REQUEST R2 |
| F11 | Player Audio Mode Propagation | Propagate selected `audioTrack` from Details to `TvPlayerScreen` requesting preferred audio streams | M2 | ORIGINAL_REQUEST R2 |
| F12 | Animated Tanuki Mascot Loader | Create `TvTanukiLoadingIndicator` Canvas composable with bobbing, tail wag, blinking, aura, pulsing ring, and subtitles | M2 | ORIGINAL_REQUEST R4 |
| F13 | Screen Loading Spinner Replacement | Replace `CircularProgressIndicator` with `TvTanukiLoadingIndicator` in Home, Search, Schedule, Details, and TvPlayer | M2 | ORIGINAL_REQUEST R4 |
| F14 | Procedural TV Sound Synthesizer | Implement `TvSoundManager` using in-memory 16-bit PCM `AudioTrack` (Focus tick, Select, Tab switch, Back, Watchlist add) | M3 | ORIGINAL_REQUEST R3 |
| F15 | Universal Sound Hooks | Hook `TvSoundManager` into `FocusModifiers.tvFocusableItem` and navigation action handlers | M3 | ORIGINAL_REQUEST R3 |
| F16 | UI Sound Effects Setting | Add `uiSoundEffects` toggle to `UserSettings`, `AppDatabase`, and `SettingsScreen` | M3 | ORIGINAL_REQUEST R3 |
| F17 | Screen Transitions & Focus Glow | Add `AnimatedContent` tab crossfade/slide, Details vertical drill-down, and soft focus glow | M3 | ORIGINAL_REQUEST R3 |
| F18 | AniList Username Input Dialog | Add public AniList username / token link dialog in `SettingsScreen` and `WatchlistScreen` | M3 | ORIGINAL_REQUEST R1 |
| F19 | Comprehensive Unit Test Suite | Write unit tests for models, repository dub queries, live schedule, character cast, sound manager, and UI state | M4 | ORIGINAL_REQUEST Acceptance |
| F20 | Build & Live Emulator Verification | Verify `.\gradlew.bat testDebugUnitTest assembleDebug` and live emulator D-pad navigation on `emulator-5554` | M4 | ORIGINAL_REQUEST Acceptance |

---

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | Data Models, Live AniList & Dub Repositories | F1, F2, F3, F4, F5, F6, F7 | none | IN_PROGRESS |
| M2 | UI Dubbed Shelves, Filters, Audio Selectors & Mascot Loader | F8, F9, F10, F11, F12, F13 | M1 | PLANNED |
| M3 | TV Sound Effects, Procedural Synthesizer, Animations & Polish | F14, F15, F16, F17, F18 | M1, M2 | PLANNED |
| M4 | Full Test Suite Integration, Verification & Live TV Emulator Acceptance | F19, F20 | M1, M2, M3 | PLANNED |

---

## Interface Contracts

### 1. Data Models ↔ Repository & UI (`Anime.kt`)
```kotlin
data class Anime(
    // ... existing fields ...
    val hasDub: Boolean = false,
    val dubbedEpisodesCount: Int = 0
)

data class Episode(
    // ... existing fields ...
    val hasDub: Boolean = false,
    val audioTrack: String = "SUB" // "SUB" | "DUB"
)

data class VideoSource(
    // ... existing fields ...
    val audioTrack: String = "SUB" // "SUB" | "DUB"
)
```

### 2. Repository Contract (`AnimeRepository.kt`)
```kotlin
interface AnimeRepository {
    // ... existing methods ...
    suspend fun getDubbedAnime(): List<Anime>
    suspend fun getEpisodeStreams(
        animeTitle: String,
        episodeNumber: Int,
        preferredSourceId: String? = null,
        preferredAudio: String = "SUB"
    ): List<VideoSource>
}
```

### 3. AniList Sync Manager (`AniListSyncManager.kt`)
```kotlin
object AniListSyncManager {
    suspend fun loginWithUsername(username: String, storage: AppStorage): Result<AniListUser>
    suspend fun loginWithToken(token: String, storage: AppStorage): Result<AniListUser>
    suspend fun syncAniListWatchlist(storage: AppStorage): Result<Int>
}
```

### 4. Audio Manager (`TvSoundManager.kt`)
```kotlin
object TvSoundManager {
    fun init(context: Context? = null)
    fun setEnabled(enabled: Boolean)
    fun isEnabled(): Boolean
    fun playFocusTick()
    fun playActionSelect()
    fun playTabSwitch()
    fun playBackDismiss()
    fun playWatchlistAdd()
    fun release()
}
```

### 5. Loading Mascot Component (`TvTanukiLoadingIndicator.kt`)
```kotlin
@Composable
fun TvTanukiLoadingIndicator(
    modifier: Modifier = Modifier,
    statusText: String = "Summoning anime catalog...",
    subtitleText: String? = null,
    isCompact: Boolean = false
)
```

---

## Code Layout
```
app/src/main/java/com/darknet/animetv/
├── data/
│   ├── model/
│   │   ├── Anime.kt                 [F1: Dub metadata fields]
│   │   └── UserSettings.kt          [F16: uiSoundEffects toggle]
│   ├── repository/
│   │   ├── AnimeRepository.kt       [F2, F3, F5, F6: Live schedule, cast, dubbed queries]
│   │   ├── DefaultAnimeRepository.kt[F2, F3, F5, F6: Multi-source & AniList integration]
│   │   ├── AniListSyncManager.kt    [F7: Public username & token sync]
│   │   └── AppDatabase.kt           [F16: Settings persistence]
│   └── source/
│       ├── AnimeSource.kt           [F5: getDubbedAnime contract]
│       ├── AniListCatalogSource.kt  [F2, F3, F4: Live GraphQL schedule/cast & emptyList streams]
│       ├── WcoSource.kt             [F5, F6: DUB audioTrack tagging]
│       └── AniwatchSource.kt        [F5, F6: SUB/DUB data-type extraction]
├── audio/
│   └── TvSoundManager.kt            [F14: Zero-asset procedural PCM wave synthesizer]
├── ui/
│   ├── components/
│   │   ├── TvTanukiLoadingIndicator.kt [F12: Custom animated Tanuki Canvas mascot]
│   │   ├── TvAnimeCard.kt           [F8: DUB badge rendering]
│   │   └── FocusModifiers.kt        [F15, F17: Universal focus sound hook & soft focus glow]
│   └── screens/
│       ├── HomeScreen.kt            [F8, F13: Dubbed shelf & mascot loader]
│       ├── SearchScreen.kt          [F9, F13: Dubbed filter chip & mascot loader]
│       ├── ScheduleScreen.kt        [F13: Mascot loader]
│       ├── DetailsScreen.kt         [F10, F11, F13: SUB/DUB toggle pill & mascot loader]
│       ├── SettingsScreen.kt        [F16, F18: UI sound toggle & AniList username input]
│       └── WatchlistScreen.kt       [F18: Production sync without demo injection]
├── player/
│   └── TvPlayerScreen.kt            [F11, F13: preferredAudio propagation & mascot loader]
└── MainActivity.kt                  [F11, F15, F17: Tab/drill-down animations & audio hooks]

app/src/test/java/com/darknet/animetv/
├── dub/
│   └── DubbedExperienceTest.kt      [F1, F5, F6: Dub models and repository query unit tests]
├── audio/
│   └── TvSoundManagerTest.kt        [F14, F16: Procedural PCM synthesis & settings toggle tests]
├── schedule/
│   └── LiveScheduleRepositoryTest.kt[F2: Live AniList GraphQL schedule & fallback tests]
├── data/
│   └── LiveCharacterCastTest.kt     [F3: Live AniList GraphQL character cast & fallback tests]
└── sync/
    └── AniListProductionSyncTest.kt [F7, F18: Public username sync & demo isolation tests]
```
