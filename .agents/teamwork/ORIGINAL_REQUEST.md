# Original User Request

## 2026-10-27T04:06:46Z

Rebuild AnimeTV into a modern, native Android TV streaming application using Jetpack Compose for TV, featuring a modular multi-source anime scraping engine, leanback D-pad remote navigation, and an improved Media3 ExoPlayer video streaming HUD.

Working directory: c:\Users\ngarz\Documents\antigravity\blissful-planck
Integrity mode: development

## Sources Reference
- Aniwatch: https://aniwatchtv.ro/
- Anikoto: https://anikototv.to/home
- Miruro: https://www.miruro.cx/
- ReAnime: https://reanime.to/home
- KAA: https://kaa.lt/
- WCO (Western Cartoons & Anime): https://www.wco.tv/
- AnimeFLV: https://www3.animeflv.net
- Catalog & Metadata: AniList / Jikan v4 API

## Requirements

### R1. Native Android TV Interface (Jetpack Compose for TV)
- Implement a TV-first user interface using `androidx.tv.compose` (TV Material 3 & TV Foundation).
- Feature a dynamic hero banner / carousel with featured anime, synopsis, score, and fast action triggers.
- Support horizontal shelves (Trending, Popular, Top Airing, Continue Watching, Genres).
- Complete remote control D-pad navigation support (smooth focus scales, glow/border focus rings, directional key handling).
- Provide TV Search with filter chips and a responsive results grid.
- Provide Anime Details view with backdrop, metadata tags, favorite toggle, and interactive episode picker.

### R2. Modular Streaming Scraper Engine & Custom Sources
- Implement an extensible extractor architecture supporting the requested sources:
  - Aniwatch (`https://aniwatchtv.ro/`)
  - Anikoto (`https://anikototv.to/home`)
  - Miruro (`https://www.miruro.cx/`)
  - ReAnime (`https://reanime.to/home`)
  - KAA (`https://kaa.lt/`)
  - WCO (`https://www.wco.tv/`)
  - AnimeFLV (`https://www3.animeflv.net`)
- Include fallback/failover logic across sources so if one site is down or protected, playback falls back gracefully.
- Provide a Sources Management screen allowing users to toggle, test, reorder, and add custom source URLs/selectors directly from the TV UI.

### R3. Advanced Media3 ExoPlayer Video Playback
- Implement video playback using AndroidX Media3 ExoPlayer with support for HLS (.m3u8) adaptive streaming and direct MP4 feeds.
- Include a TV remote-optimized overlay (HUD) that auto-hides during playback and reveals on D-pad input.
- TV playback controls: Play/Pause, 10s seek forward/rewind, next/previous episode, video quality selector (1080p, 720p, 480p, Auto), and subtitle track toggle.
- Automatic resume and progress tracking: persist timestamp and completed status in local storage.

### R4. Local Persistence & Modern Build Setup
- SQLite-backed storage for Watch History, Watchlist/Favorites, and Source Configurations.
- Clean Gradle build setup targeting Android SDK 34 (minSdk 26 for Android TV 8.0+) with Gradle 8.9 and Kotlin.

## Acceptance Criteria

### TV UI & Navigation
- [ ] App launches in Android TV leanback mode with full D-pad remote navigation without requiring touch input.
- [ ] All interactive elements (cards, buttons, rows, episode chips) display clear focus highlights and smooth focus transitions.

### Scraper & Streaming
- [ ] Source engine can query catalog/search and resolve playable video stream URLs from active providers.
- [ ] Sources screen allows testing connections to sources and displaying response status.

### Video Playback & HUD
- [ ] ExoPlayer launches video streams with audio/video sync and responsive remote control actions (play, pause, seek, quality selection).
- [ ] Playback progress is saved to local database and resumed when returning to an episode.


## 2026-10-28T04:24:53Z

Elevate the TanukiTV Android TV application (`com.darknet.animetv`) from a demo prototype to a production-grade, premium 10-foot living-room streaming experience, featuring live API data across all screens, a comprehensive Dubbed Anime & Dubbed Episodes experience, subtle UI animations and remote navigation sound effects, and a custom animated Tanuki mascot across all loading states.

Working directory: c:\Users\ngarz\Documents\antigravity\blissful-planck
Integrity mode: development

## Requirements

### R1. Production Readiness & Live Data Integration (Out of Demo Mode)
Replace demo-only and hardcoded fallback flows with live API queries and production-grade state management:
- **Live Airing Schedule**: Query real weekly airing schedule data from the AniList GraphQL API (`AiringSchedule` query filtered by airing time window / day of week) in `AniListCatalogSource` and `DefaultAnimeRepository.getAiringSchedule`, falling back gracefully only if the network is unreachable.
- **Live Character & Seiyuu Cast**: Query real character and voice actor (`characters(sort: [ROLE, RELEVANCE])` with Japanese & English voice actors) data from AniList GraphQL in `AniListCatalogSource` and `DefaultAnimeRepository.getCharacterCast`, removing the hardcoded 4-show switch statement.
- **Production AniList Account Sync**: Support linking an AniList account by public username or personal access token in `SettingsScreen` / `WatchlistScreen` via `AniListSyncManager` (fetching real public `User` and `MediaListCollection` data from AniList GraphQL without requiring simulated `performDemoSync` fake anime entries).
- **Stream Resolution**: Ensure `AniListCatalogSource` acts purely as a metadata/catalog provider (returning `emptyList()` for `getEpisodeStreams`) and `DefaultAnimeRepository.getEpisodeStreams` resolves real SUB and DUB streams from active streaming providers (`KaaSource`, `AniwatchSource`, `MiruroSource`, `ConsumetSource`, `AnikotoSource`, `ReAnimeSource`, `WcoSource`).

### R2. Full Dubbed Anime & Dubbed Episodes Experience
Deliver a first-class English Dubbed experience across the app:
- **Model & Repository Support**: Extend `Anime`, `Episode`, and `VideoSource` models and `AnimeRepository` with dub metadata (`hasDub`, `dubbedEpisodesCount`, `audioTrack = "SUB" | "DUB"`) and a `getDubbedAnime()` query that fetches and aggregates English-dubbed anime series and recently dubbed episodes from active providers (e.g., AniList English-licensed/dubbed catalog + `AniwatchSource` / `KaaSource` / `WcoSource` dub catalogs).
- **Home Screen Dubbed Shelf**: Add a dedicated `"🎙️ Dubbed Anime & Episodes"` horizontal shelf on `HomeScreen` featuring `DUB` badges on cards.
- **Catalog & Search Dubbed Filter**: Add a `"🎙️ Dubbed"` filter chip / toggle in `SearchScreen` so users can filter the catalog and search results exclusively to dubbed anime and episodes.
- **Details & Player SUB / DUB Episode Audio Selector**: Add an interactive `SUB` / `DUB` audio track toggle pill on `DetailsScreen` (above the episode shelf) and in `TvPlayerScreen` so selecting `DUB` filters/marks dubbed episodes and requests English-dubbed streams (`preferredAudio = "DUB"`) when launching playback.

### R3. Subtle Premium UI Animations & TV Remote Sound Effects
Elevate every interaction across the 10-foot interface:
- **Synthesized Low-Latency TV Sound Effects (`TvSoundManager`)**: Create a zero-asset-dependency, low-latency procedural audio synthesizer (`AudioTrack` / `ToneGenerator` / PCM wave synthesizer) that plays subtle, pleasant living-room UI sounds for:
  - D-pad Focus Tick (soft high-tech micro-click when focus moves between cards/buttons)
  - Primary Action / Select (warm harmonic chord tap on `DPAD_CENTER` / `ENTER`)
  - Navigation / Tab Switch (subtle whoosh/chime when switching top-bar tabs or opening Details)
  - Back / Dismiss (soft descending tone)
  - Watchlist Add / Favorite (bright confirmation chime)
- **Settings Integration**: Add a `"UI Sound Effects"` toggle in `UserSettings`, `AppStorage`, and `SettingsScreen` (under Appearance & TV Focus) enabled by default.
- **Premium Screen & Card Animations**:
  - Smooth `AnimatedContent` crossfade + subtle vertical/horizontal slide transitions when switching top navigation tabs in `MainActivity` and drilling down into `DetailsScreen`.
  - Subtle animated focus glow, glassmorphic badge refinements, and staggered entrance animations on shelves and grid cards.

### R4. Custom Animated Tanuki Loading Component (`TvTanukiLoadingIndicator`)
Replace all generic `CircularProgressIndicator` loading spinners across `HomeScreen`, `SearchScreen`, `ScheduleScreen`, `DetailsScreen`, and `TvPlayerScreen` with a custom, reusable `TvTanukiLoadingIndicator` composable:
- Render an animated Tanuki mascot (using Compose `Canvas` / multi-layered vector drawing + `rememberInfiniteTransition` or custom drawable animation) featuring a cute Tanuki character with a bobbing animation, wagging tail, blinking eyes, glowing magical leaf/lantern aura, pulsing orbital ring, and animated contextual status subtitle (e.g., `"Summoning anime catalog..."`, `"Fetching dubbed streams..."`).

## Verification Resources

- **Existing Unit Test Suite**: Run `.\gradlew.bat testDebugUnitTest` (located in `app/src/test/java/com/darknet/animetv/`).
- **Android Build & APK Packaging**: Run `.\gradlew.bat assembleDebug` to verify full Kotlin compilation and resource linking.
- **Live Android TV Emulator (`emulator-5554`)**: Use `$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe` to install `app\build\outputs\apk\debug\app-debug.apk`, navigate across screens via D-pad keyevents, and capture screenshots.

## Acceptance Criteria

### Build & Unit Tests
- [ ] `.\gradlew.bat testDebugUnitTest assembleDebug` exits with code 0 and zero compilation errors.
- [ ] Unit tests cover `getDubbedAnime()`, live `getAiringSchedule()`, live `getCharacterCast()`, `TvSoundManager` state/settings persistence, and `SUB`/`DUB` stream resolution.

### Dubbed Experience
- [ ] `HomeScreen` displays a `"🎙️ Dubbed Anime"` shelf with visible `DUB` badges on cards.
- [ ] `SearchScreen` includes a `"🎙️ Dubbed"` filter chip that filters the catalog grid to dubbed anime titles.
- [ ] `DetailsScreen` includes a focusable `SUB` / `DUB` selector pill in the Episodes header that toggles episode playback mode between Subtitled and English Dubbed streams.

### Live Production Data (Out of Demo)
- [ ] `AniListCatalogSource` and `DefaultAnimeRepository` fetch live airing schedule and character/voice actor cast data from `https://graphql.anilist.co`.
- [ ] `SettingsScreen` and `WatchlistScreen` allow linking any real AniList username (via public GraphQL `User` + `MediaListCollection` lookup) or token without forcing fake demo anime into the user's watchlist.

### Animated Tanuki Loader, Animations & Sound Effects
- [ ] `TvTanukiLoadingIndicator` replaces bare `CircularProgressIndicator` loading states in `HomeScreen`, `SearchScreen`, `ScheduleScreen`, `DetailsScreen`, and `TvPlayerScreen`.
- [ ] `TvSoundManager` plays subtle procedural UI sound effects on focus change and click across `tvFocusableItem` and top navigation buttons, controlled by `UserSettings.uiSoundEffects`.
- [ ] All D-pad navigation paths on `HomeScreen`, `SearchScreen`, `ScheduleScreen`, `WatchlistScreen`, `DetailsScreen`, and `SettingsScreen` remain smooth and free of focus traps on `emulator-5554`.
