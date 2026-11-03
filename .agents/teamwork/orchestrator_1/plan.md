# Plan — AnimeTV Android TV Rebuild

## Mission
Rebuild AnimeTV into a modern native Android TV streaming application using Jetpack Compose for TV, modular multi-source anime scraping engine, leanback D-pad remote navigation, Media3 ExoPlayer HUD, and local SQLite persistence.

## Milestone Plan & Dependencies

```
[Phase 0: Survey] (Completed)
       │
       ▼
[Dual Track Launch]
 ├── E2E Testing Track (Independent, requirement-driven, Tiers 1-4)
 │     │
 │     └──> TEST_READY.md
 │
 └── Implementation Track
       ├── Milestone 1: Core Foundation & Build Setup
       │     │
       │     ▼
       ├── Milestone 2: Scraper Engine & Sources
       │     │
       │     ▼
       ├── Milestone 3: Compose TV UI & Navigation
       │     │
       │     ▼
       ├── Milestone 4: Media3 ExoPlayer & TV HUD
       │     │
       │     ▼
       └── Milestone 5: E2E Test Suite Validation & Adversarial Hardening
```

### Milestone E2E: E2E Testing Track
- **Role**: `teamwork_preview_test_writer` / `teamwork_preview_worker`
- **Output**:
  - `TEST_INFRA.md`
  - Complete 4-tier test suite in `app/src/test/java/com/darknet/animetv/e2e/`:
    - Tier 1: Feature Coverage (≥5 tests per feature across F1-F20)
    - Tier 2: Boundary & Corner Cases (empty responses, 403 Forbidden, malformed JSON, 0-duration video)
    - Tier 3: Cross-Feature Interactions (D-pad navigation to player, source failover to ExoPlayer, progress save on back key)
    - Tier 4: Real-World Scenarios (full browsing, searching, streaming, and resumption workflow)
  - Publishes `TEST_READY.md`.

### Milestone 1: Core Foundation & Build Setup
- **Objective**: Fix Gradle build blocker (`tv-material:1.0.0`), implement `AnimeTvApplication.kt`, `MainActivity.kt` with Android TV Leanback theme, verify SQLite schema in `AppDatabase.kt`.
- **Exit Criteria**: `.\gradlew.bat compileDebugKotlin` and `.\gradlew.bat assembleDebug` execute with BUILD SUCCESSFUL.

### Milestone 2: Scraper Engine & Sources Management
- **Objective**: Implement `AnimeSource` interface, 7 target source extractors (Aniwatch, Anikoto, Miruro, ReAnime, KAA, WCO, AnimeFLV), video extractors (MegaCloud, StreamWish, Doodstream, Vidplay, Mp4Upload, Direct), AniList GraphQL metadata provider, custom source JSON DSL (`DynamicHtmlSource`), and failover repository (`AnimeRepository`).
- **Exit Criteria**: Unit tests pass for all extractors, catalog parsing, stream resolution, and failover fallbacks.

### Milestone 3: Jetpack Compose for TV UI & D-pad Navigation
- **Objective**: Implement TV Material 3 design system, `tvFocusableItem` modifier, dynamic Hero Banner Carousel, Horizontal Shelves (`TvLazyRow`/`TvLazyColumn` including Continue Watching with progress bar), TV Search screen with category chips and 5-column grid, Anime Details with backdrop and episode picker, and Sources Management screen.
- **Exit Criteria**: UI composables compile and render with complete D-pad focus handling and smooth focus scaling.

### Milestone 4: Media3 ExoPlayer Video HUD & Controls
- **Objective**: Integrate AndroidX Media3 ExoPlayer with OkHttp header injection, remote-optimized Compose HUD overlay with 5s auto-hide timer, D-pad directional key interception (`onPreviewKeyEvent`), center-screen ±10s seek pill, playback controls (Play/Pause, prev/next episode), track selector dialogs (quality, subtitles, audio), and automatic progress tracking & resumption.
- **Exit Criteria**: Player screen compiles, handles all remote key events, accurately computes seek positions, and persists watch history to SQLite.

### Milestone 5: Dual-Track Acceptance & Adversarial Hardening
- **Objective**:
  - Phase 1: Run 100% of the E2E test suite published in `TEST_READY.md` (Tiers 1-4).
  - Phase 2: Dispatch `teamwork_preview_challenger` for Tier 5 adversarial stress testing and `teamwork_preview_auditor` for forensic integrity verification.
- **Exit Criteria**: All tests pass, zero regressions, clean audit verdict (CLEAN), verified TV leanback readiness.
