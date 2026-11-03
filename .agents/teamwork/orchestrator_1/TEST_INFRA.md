# TEST_INFRA — AnimeTV Rebuild E2E Testing Infrastructure

## 1. Overview & Objectives
This document establishes the architecture, methodology, coverage mapping, and execution semantics for the comprehensive 4-Tier End-to-End (E2E) Test Suite for the **AnimeTV Native Android TV Rebuild**.

The test suite serves as the requirement-driven, opaque-box oracle across all 20 inventoried features (F1–F20) defined in `PROJECT.md` and `ORIGINAL_REQUEST.md`. It executes deterministically in host unit-test environments under Robolectric and JUnit 4 without requiring a physical TV device or emulator, enabling continuous verification across all implementation milestones.

---

## 2. Testing Methodology

The test suite is structured around four complementary software testing methodologies:

### 2.1 Category-Partition Methodology (Tier 1: Feature Coverage)
For each of the 20 features (F1–F20), the functional specification is partitioned into distinct input categories, parameter domains, and expected observable outputs. Each partition is covered by at least one dedicated test case, guaranteeing a minimum of **5 tests per feature (≥ 100 tests total in Tier 1)**:
- **Environment & Build Partitions**: Target SDK, Min SDK, Jetpack Compose TV compiler extension, class loading of Media3/OkHttp/Jsoup/Gson.
- **Application & Activity Partitions**: Manifest leanback declarations, leanback banner, hardware acceleration, touchscreen requirements, application-wide singleton lifecycle.
- **Persistence Partitions**: Watch progress upsert/read/order, watchlist favorites add/remove/check, custom sources CRUD and enabled filtering.
- **Scraper & Provider Partitions**: Search queries, trending/popular catalog feeds, episode detail extraction, stream link resolution.
- **TV UI & D-pad Partitions**: Remote directional events, focus scale amplification (1.08x), glow border styling, carousel timers, shelf progress indicators, 5-column search grid coordinates.
- **Media3 Player & HUD Partitions**: Play/Pause state transitions, ±10-second seek offset arithmetic, 5-second auto-hide timeout mechanics, track selection models (quality, subtitles, audio).

### 2.2 Boundary Value Analysis (BVA) (Tier 2: Boundary & Corner Cases)
Extreme, off-nominal, and boundary inputs are systematically exercised to verify robust error containment and graceful degradation:
- **HTTP / Network Failures**: HTTP 403 Forbidden, 429 Rate Limit Exceeded, 500 Internal Server Error, connection timeouts, DNS resolution failures.
- **Payload Anomalies**: Empty HTTP response bodies (0 bytes), truncated JSON, malformed HTML without target selectors, unicode and special escape sequences.
- **Numeric & Temporal Boundaries**: Position at 0 ms, duration at 0 ms, negative timestamps, position exceeding duration, extreme long integer values (`Long.MAX_VALUE`), seek past stream boundaries.
- **Storage Boundaries**: Empty string IDs, ultra-long anime titles (10,000+ characters), special SQL characters (`'`, `"`, `;`, `--`, `\u0000`), duplicate primary keys.

### 2.3 Pairwise & Cross-Feature Combinatorics (Tier 3: Interactions)
Cross-component data flows are validated to guarantee interoperability across architectural layers:
- **Search -> Details -> Stream -> Storage**: User executes TV search, opens anime details, resolves episode stream, and records watch progress in SQLite.
- **Source Failover -> Video Extractor -> Player**: Primary source returns 403/timeout, failover engine seamlessly triggers secondary provider, passes embed URL to video extractor, and supplies resolved HLS stream to player configuration.
- **Favorite Toggle -> Shelf Rendering -> Watchlist Removal**: Anime marked as favorite, reflects in Favorites shelf, persists across application restart, and removes cleanly.
- **Playback Seek -> Progress Snapshot -> Home Screen Resume**: User seeks 4 times during playback, player auto-saves progress, user returns to Home, Continue Watching shelf computes exact progress percentage.

### 2.4 Workload & Life-Cycle Testing (Tier 4: Real-World Workload Scenarios)
Simulates end-to-end user TV sessions and high-churn workflows:
- **Binge-Watching Workflow**: Consecutive multi-episode playback (Episodes 1 -> 2 -> 3), sequential progress tracking, auto-advancing episode pointers.
- **Stream & Audio Quality Switching**: User toggles stream qualities (1080p -> 720p -> 480p) and switches between Sub (Japanese) and Dub (English) audio tracks while preserving playback position.
- **Source Management & Prioritization**: User disables buggy source, reorders active sources, adds custom JSON DSL source, and tests connection latency across all configured sources.
- **Interrupted Session & Cold Boot Resumption**: App lifecycle suspension during video playback, DB persistence verification, cold application restart, and auto-resume from exact timestamp.

---

## 3. Feature Inventory Mapping (F1 – F20)

| ID | Feature Name | Tier 1 Tests | Tier 2 Boundaries | Tier 3 Pairwise Path | Tier 4 Workload Scenario |
|---|---|---|---|---|---|
| **F1** | Build Toolchain & Dependencies | `Tier1_01_ToolchainTest` (5 tests) | Missing dep fallback | Toolchain classloader -> App launch | Complete app classpath integrity |
| **F2** | Core App & Leanback Activity | `Tier1_02_CoreAppTest` (5 tests) | Null bundle, multi-recreate | App startup -> DB init -> Theme load | Cold start -> Home transition |
| **F3** | SQLite Local Persistence | `Tier1_03_StorageTest` (5 tests) | SQL injection, empty strings | Storage update -> Shelf sync | Multi-session history churn |
| **F4** | Modular Scraper Architecture | `Tier1_04_ScraperArchTest` (5 tests) | Network timeout, null results | Scraper -> Repository aggregation | Provider switching during search |
| **F5** | 7 Target Scraper Providers | `Tier1_05_ProvidersTest` (5 tests) | Blocked anti-bot, 403 response | Provider scrape -> Extractor parse | Multi-provider catalog crawl |
| **F6** | Video Hosting Extractors | `Tier1_06_VideoExtractorsTest` (5 tests) | Dead stream link, cipher error | Embed URL -> Direct HLS stream | Multi-server stream selection |
| **F7** | Catalog Metadata Providers | `Tier1_07_CatalogMetadataTest` (5 tests) | Missing banner, null genres | AniList 16:9 banner -> Hero carousel | Catalog pagination & fallback |
| **F8** | Custom Source JSON DSL | `Tier1_08_CustomSourceDslTest` (5 tests) | Malformed JSON, missing tag | Custom source DSL -> Live parser | User adds & executes custom source |
| **F9** | Multi-Source Failover Engine | `Tier1_09_FailoverEngineTest` (5 tests) | All sources down, 500 error | Failover engine -> Fallback source | Live failover during binge session |
| **F10** | TV Remote Focus & Interaction | `Tier1_10_RemoteFocusTest` (5 tests) | Rapid key burst, edge navigation | D-pad focus -> Item selection | Navigation across all top tabs |
| **F11** | Dynamic Hero Banner Carousel | `Tier1_11_HeroBannerTest` (5 tests) | Empty banner list, 0 score | Hero banner action -> Details view | Carousel auto-rotation & pause |
| **F12** | Horizontal Shelves & Leanback | `Tier1_12_HorizontalShelvesTest` (5 tests) | 0-item shelf, ultra-wide list | Shelf card click -> Stream start | Shelf scrolling & progress bar |
| **F13** | TV Search Screen | `Tier1_13_SearchScreenTest` (5 tests) | 0 results, special regex query | Search query -> Filter chip -> Grid | Real-time search query typing |
| **F14** | Anime Details & Episode Picker | `Tier1_14_DetailsPickerTest` (5 tests) | 0 episodes, 1000+ episodes | Details view -> Favorite toggle | Episode selection & mark watched |
| **F15** | TV Sources Management Screen | `Tier1_15_SourcesManagementTest` (5 tests) | Invalid URL, unreachable host | Source toggle -> DB persist | Reorder & test all source latencies |
| **F16** | Media3 ExoPlayer Engine | `Tier1_16_ExoPlayerEngineTest` (5 tests) | Corrupt stream, 0-byte chunk | Stream URL -> Media3 config | Continuous HLS stream playback |
| **F17** | TV Remote-Optimized HUD | `Tier1_17_PlayerHudTest` (5 tests) | Continuous D-pad spam | D-pad event -> HUD show -> 5s timer | Auto-hide timer reset on input |
| **F18** | TV Playback & Seek Controls | `Tier1_18_PlaybackSeekTest` (5 tests) | Seek past duration, seek < 0 | Seek button -> Position update | ±10s seeking during active play |
| **F19** | Track Selection Dialogs | `Tier1_19_TrackSelectionTest` (5 tests) | Missing subtitle track | Track picker -> Track change | Quality & Sub/Dub live toggle |
| **F20** | Auto Resume & Progress Tracking | `Tier1_20_AutoResumeTest` (5 tests) | 99% complete (auto-finish) | Progress tick -> SQLite commit | Resuming video at exact second |

---

## 4. Test Architecture & Runner Semantics

### 4.1 Framework & Test Runner
- **Test Runner**: `org.robolectric.RobolectricTestRunner`
- **Assertion Engine**: `org.junit.Assert` with deterministic value matching
- **Asynchronous Execution**: Kotlin Coroutines `kotlinx.coroutines.test.runTest` / `runBlocking`
- **Operating Context**: `androidx.test.core.app.ApplicationProvider.getApplicationContext()`

### 4.2 Directory & Package Organization
All E2E test files are co-located under `app/src/test/java/com/darknet/animetv/e2e/`:
```
app/src/test/java/com/darknet/animetv/e2e/
├── harness/                        # Test fixtures, mock servers, contracts & synthetic data
│   ├── E2eTestFixtures.kt          # Pre-built anime catalog, episodes, video streams & JSON
│   ├── MockScraperHarness.kt       # Deterministic provider implementations & failover simulation
│   └── TvNavigationSimulator.kt    # D-pad key event simulator and focus tracker
├── tier1/                          # Tier 1: Feature Coverage (F1–F20, ≥5 tests per feature)
│   ├── Tier1_01_ToolchainTest.kt
│   ├── Tier1_02_CoreAppTest.kt
│   ├── Tier1_03_StorageTest.kt
│   ├── Tier1_04_ScraperArchTest.kt
│   ├── Tier1_05_ProvidersTest.kt
│   ├── Tier1_06_VideoExtractorsTest.kt
│   ├── Tier1_07_CatalogMetadataTest.kt
│   ├── Tier1_08_CustomSourceDslTest.kt
│   ├── Tier1_09_FailoverEngineTest.kt
│   ├── Tier1_10_RemoteFocusTest.kt
│   ├── Tier1_11_HeroBannerTest.kt
│   ├── Tier1_12_HorizontalShelvesTest.kt
│   ├── Tier1_13_SearchScreenTest.kt
│   ├── Tier1_14_DetailsPickerTest.kt
│   ├── Tier1_15_SourcesManagementTest.kt
│   ├── Tier1_16_ExoPlayerEngineTest.kt
│   ├── Tier1_17_PlayerHudTest.kt
│   ├── Tier1_18_PlaybackSeekTest.kt
│   ├── Tier1_19_TrackSelectionTest.kt
│   └── Tier1_20_AutoResumeTest.kt
├── tier2/                          # Tier 2: Boundary & Corner Cases
│   └── Tier2_BoundaryAndCornerCasesTest.kt
├── tier3/                          # Tier 3: Cross-Feature Interactions
│   └── Tier3_CrossFeatureInteractionsTest.kt
└── tier4/                          # Tier 4: Real-World Workload Scenarios
    └── Tier4_RealWorldWorkloadScenariosTest.kt
```

### 4.3 Independence & Isolation Invariants
1. **Isolated SQLite Database**: Every test instantiates a dedicated or freshly cleared in-memory / temporary database (`animetv_test.db`), deleted in `@Before` and `@After`.
2. **Order Independence**: Zero static mutable state shared between tests. Tests can execute in parallel or random order.
3. **No External Network Dependency**: All HTTP responses, streaming payloads, and JSON schemas are resolved via local test doubles and fixtures, ensuring 100% offline determinism and sub-second execution speed.

---

## 5. Execution Semantics & Verification

### 5.1 Test Execution Command
The test suite is executed using Gradle via the standard project wrapper:
```powershell
.\gradlew.bat testDebugUnitTest
```
Or selectively by tier:
```powershell
.\gradlew.bat testDebugUnitTest --tests "com.darknet.animetv.e2e.*"
.\gradlew.bat testDebugUnitTest --tests "com.darknet.animetv.e2e.tier1.*"
.\gradlew.bat testDebugUnitTest --tests "com.darknet.animetv.e2e.tier2.*"
.\gradlew.bat testDebugUnitTest --tests "com.darknet.animetv.e2e.tier3.*"
.\gradlew.bat testDebugUnitTest --tests "com.darknet.animetv.e2e.tier4.*"
```

### 5.2 Pass / Fail Criteria
- **Pass Rate**: 100% passing tests (0 failures, 0 errors).
- **Execution Budget**: Entire unit and E2E suite completes in under 60 seconds on host machine.
- **Coverage**:
  - Tier 1: ≥ 100 test cases across features F1–F20 (5+ per feature).
  - Tier 2: ≥ 12 comprehensive boundary & edge test cases.
  - Tier 3: ≥ 6 multi-feature interaction test cases.
  - Tier 4: ≥ 4 real-world user workload session test cases.
  - Total E2E Tests: ≥ 122 automated test cases.
