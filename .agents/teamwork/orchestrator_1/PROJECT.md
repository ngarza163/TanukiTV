# Project: AnimeTV Native Android TV Rebuild

## Architecture
AnimeTV is rebuilt as a modern, native Android TV streaming application adhering to Android TV Leanback design principles, Jetpack Compose for TV (`androidx.tv:tv-material` and `androidx.tv:tv-foundation`), AndroidX Media3 ExoPlayer, and SQLite-backed local persistence.

### Package & Module Boundaries
```
com.darknet.animetv/
├── AnimeTvApplication.kt       # Application class, dependency container & storage initialization [DONE]
├── MainActivity.kt              # Leanback launcher activity hosting Compose TV Navigation [DONE]
├── data/
│   ├── model/                  # Data models (Anime, AnimeDetail, Episode, VideoSource, etc.) [DONE]
│   ├── source/                 # Scraper engine & source extractors
│   │   ├── AnimeSource.kt      # Core source interface contract
│   │   ├── AniwatchSource.kt   # Aniwatch / HiAnime extractor
│   │   ├── AnikotoSource.kt    # Anikoto extractor
│   │   ├── MiruroSource.kt     # Miruro API extractor (fast JSON & HLS)
│   │   ├── ReAnimeSource.kt    # ReAnime extractor
│   │   ├── KaaSource.kt        # KickAssAnime extractor (AES-CBC decryption)
│   │   ├── WcoSource.kt        # Western Cartoons & Anime dub extractor
│   │   ├── AnimeFlvSource.kt   # AnimeFLV Jsoup extractor
│   │   ├── AniListCatalogSource.kt # AniList GraphQL 16:9 banner metadata provider
│   │   ├── JikanSource.kt      # Jikan v4 REST fallback provider
│   │   ├── DynamicHtmlSource.kt# Declarative JSON DSL custom source engine
│   │   └── extractor/          # Video server extractors (MegaCloud, StreamWish, etc.)
│   ├── repository/             # AnimeRepository with multi-source failover & health monitor
│   └── storage/                # AppStorage & SQLite schemas (history, favorites, sources) [DONE]
├── player/                     # Media3 ExoPlayer integration & TV remote HUD
│   ├── TvPlayerScreen.kt       # TV Video player composable with PlayerView
│   ├── TvPlayerHud.kt          # Leanback overlay with ±10s seek pills and controls
│   └── VideoPlayerManager.kt   # ExoPlayer lifecycle, track selector & progress sync
└── ui/
    ├── theme/                  # TV Color, Typography, Shape & focus styling [DONE]
    ├── components/             # Reusable TV focusable cards, banners, chips
    │   ├── FocusModifiers.kt   # tvFocusableItem (scale 1.08x, glow, border, z-index)
    │   ├── TvAnimeCard.kt      # 140x210dp focus-scaled media card
    │   └── TvHeroBanner.kt     # High-res 16:9 hero banner carousel
    └── screens/                # Top-level TV screens
        ├── HomeScreen.kt       # Carousel & horizontal shelves (Continue Watching, Trending, etc.)
        ├── DetailsScreen.kt    # Backdrop, metadata, favorite button & episode shelf
        ├── SearchScreen.kt     # Category chips + 5-column responsive grid
        └── SourcesScreen.kt    # Source toggle, latency tester, and custom source creator
```

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---|---|---|---|
| F1 | Build Toolchain & Dependencies | Fix tv-material:1.0.0, Kotlin 1.9.24, Android SDK 34, AGP 8.5.2 | M1 [DONE] | Survey E1 |
| F2 | Core App & Leanback Activity | AnimeTvApplication, MainActivity, AndroidManifest TV leanback intent | M1 [DONE] | Survey E1 |
| F3 | SQLite Local Persistence | AppStorage schemas for Watch History, Watchlist/Favorites, Sources | M1 [DONE] | Survey E1/E3 |
| F4 | Modular Scraper Architecture | AnimeSource interface with search, catalog, details, and streams | M2 [DONE] | Survey E2 |
| F5 | 7 Target Scraper Providers | Aniwatch, Anikoto, Miruro, ReAnime, KAA, WCO, AnimeFLV | M2 [DONE] | ORIGINAL_REQUEST R2 |
| F6 | Video Hosting Extractors | MegaCloud, StreamWish, Doodstream, Vidplay, Mp4Upload, Direct | M2 [DONE] | Survey E2 |
| F7 | Catalog Metadata Providers | AniList GraphQL (16:9 banners) + Jikan v4 REST fallback | M2 [DONE] | ORIGINAL_REQUEST R2 |
| F8 | Custom Source JSON DSL | Declarative selector DSL allowing user-added custom sources | M2 [DONE] | ORIGINAL_REQUEST R2 |
| F9 | Multi-Source Failover Engine | Dynamic failover chain, connection tester, and latency probe | M2 [DONE] | ORIGINAL_REQUEST R2 |
| F10 | TV Remote Focus & Interaction | tvFocusableItem modifier (1.08x scale, border ring, z-index elevation) | M3 | ORIGINAL_REQUEST R1 |
| F11 | Dynamic Hero Banner Carousel | 16:9 backdrop, synopsis, rating, play/resume and watchlist actions | M3 | ORIGINAL_REQUEST R1 |
| F12 | Horizontal Shelves & Leanback | TvLazyRow shelves: Trending, Popular, Airing, Continue Watching | M3 | ORIGINAL_REQUEST R1 |
| F13 | TV Search Screen | Remote category chips + 5-column responsive grid navigation | M3 | ORIGINAL_REQUEST R1 |
| F14 | Anime Details & Episode Picker | Backdrop, metadata chips, favorite toggle, episode card shelf | M3 | ORIGINAL_REQUEST R1 |
| F15 | TV Sources Management Screen | Toggle sources, run live connection tests, reorder, add source | M3 | ORIGINAL_REQUEST R2 |
| F16 | Media3 ExoPlayer Engine | AndroidX Media3 ExoPlayer with OkHttp header injection (HLS/MP4) | M4 | ORIGINAL_REQUEST R3 |
| F17 | TV Remote-Optimized HUD | 5-second auto-hide timer, D-pad key event capture (onPreviewKeyEvent) | M4 | ORIGINAL_REQUEST R3 |
| F18 | TV Playback & Seek Controls | Play/Pause, ±10s fast seek with center-screen pill, prev/next episode | M4 | ORIGINAL_REQUEST R3 |
| F19 | Track Selection Dialogs | Video quality (1080p, 720p, 480p, Auto), subtitle toggle, audio sub/dub | M4 | ORIGINAL_REQUEST R3 |
| F20 | Auto Resume & Progress Tracking | Periodic progress persistence to SQLite & auto-resume on return | M4 | ORIGINAL_REQUEST R3/R4 |
| F21 | Dual-Track E2E Test Suite | 4-tier requirement-driven opaque-box test suite (Tiers 1-4) | E2E Track | Dual Track |
| F22 | Adversarial Hardening & Audit | Tier 5 white-box stress testing and forensic integrity verification | M5 | Project Pattern |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|---|---|---|---|
| E2E | E2E Testing Track | Requirement-driven test suite (Tiers 1-4, test runner, TEST_READY.md) | none | IN_PROGRESS |
| M1 | Core Foundation & Build Setup | Fix dependencies, build config, Application, MainActivity, SQLite persistence | none | **DONE** |
| M2 | Scraper Engine & Sources | 7 scrapers, video extractors, AniList catalog, custom source DSL, failover | M1 | **DONE** |
| M3 | Compose TV UI & Navigation | TV Material 3 design system, D-pad focus, Carousel, Shelves, Details, Search, Sources UI | M1, M2 | PLANNED |
| M4 | Media3 ExoPlayer & TV HUD | ExoPlayer integration, auto-hide HUD, ±10s seek pill, track selection, progress sync | M1, M2, M3 | PLANNED |
| M5 | E2E Verification & Hardening | Pass 100% of E2E test suite (Tiers 1-4), Tier 5 adversarial testing, forensic audit | M4, E2E | PLANNED |

## Interface Contracts
### `AnimeSource` Contract (`data/source/AnimeSource.kt`)
```kotlin
interface AnimeSource {
    val sourceId: String
    val sourceName: String
    val baseUrl: String
    val isEnabled: Boolean

    suspend fun getTrending(page: Int = 1): Result<List<Anime>>
    suspend fun getPopular(page: Int = 1): Result<List<Anime>>
    suspend fun getRecentEpisodes(page: Int = 1): Result<List<Anime>>
    suspend fun search(query: String, page: Int = 1): Result<List<Anime>>
    suspend fun getAnimeDetail(animeId: String): Result<AnimeDetail>
    suspend fun getEpisodeStreams(animeId: String, episodeId: String): Result<List<VideoSource>>
    suspend fun testConnection(): SourceHealthResult
}
```

### `VideoExtractor` Contract (`data/source/extractor/VideoExtractor.kt`)
```kotlin
interface VideoExtractor {
    val serverName: String
    fun canHandle(url: String): Boolean
    suspend fun extract(embedUrl: String, referer: String? = null): List<VideoSource>
}
```

### `AnimeRepository` Contract (`data/repository/AnimeRepository.kt`)
```kotlin
interface AnimeRepository {
    fun getActiveSources(): List<AnimeSource>
    suspend fun getTrending(): List<Anime>
    suspend fun getPopular(): List<Anime>
    suspend fun getRecentEpisodes(): List<Anime>
    suspend fun search(query: String): List<Anime>
    suspend fun getAnimeDetail(animeId: String): AnimeDetail?
    suspend fun getEpisodeStreams(animeTitle: String, episodeNumber: Int, preferredSourceId: String? = null): List<VideoSource>
    suspend fun testAllSources(): Map<String, SourceHealthResult>
}
```

### `AppStorage` Persistence Contract (`data/storage/AppDatabase.kt`)
- `fun saveWatchProgress(progress: WatchProgress)`
- `fun getWatchProgress(animeId: String): WatchProgress?`
- `fun getAllWatchProgress(): List<WatchProgress>`
- `fun addFavorite(anime: Anime)`
- `fun removeFavorite(animeId: String)`
- `fun isFavorite(animeId: String): Boolean`
- `fun getAllFavorites(): List<Anime>`
- `fun saveSource(source: CustomSource)`
- `fun getSources(): List<CustomSource>`
- `fun getEnabledSources(): List<CustomSource>`
- `fun close()` (implements `java.io.Closeable`)
