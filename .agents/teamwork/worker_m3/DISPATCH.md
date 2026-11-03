# Dispatch: worker_m3 (Jetpack Compose for TV UI & D-pad Navigation Worker)

## Mission
Implement Milestone 3 (Jetpack Compose for TV UI & D-pad Remote Navigation) for AnimeTV Android TV rebuild.

## Authoritative Inputs
- ORIGINAL_REQUEST.md: `c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\ORIGINAL_REQUEST.md` (MUST READ FIRST)
- PROJECT.md: `c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\orchestrator_1\PROJECT.md`
- TEST_READY.md: `c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\orchestrator_1\TEST_READY.md`

## Mandatory Integrity Warning
> DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

## Write Ownership
You have exclusive write ownership of:
- `app/src/main/java/com/darknet/animetv/ui/` (all subpackages: theme, components, screens)
- `app/src/main/java/com/darknet/animetv/MainActivity.kt` (TV Leanback Navigation & screen host)
- `app/src/test/java/com/darknet/animetv/ui/` (unit and component tests)
- `app/src/main/java/com/darknet/animetv/data/source/extractor/Mp4UploadExtractor.kt` (line 79: allow optional port in direct URL regex e.g. `:(\d+)?`)

## Detailed Requirements (Features F10–F15)
1. **F10: TV Remote Focus & Interaction**:
   - In `app/src/main/java/com/darknet/animetv/ui/components/FocusModifiers.kt`: Implement `tvFocusableItem` modifier with 1.08x focus scale animation, focus border ring (`TvAccentPurple` / `TvPrimaryRed`), z-index elevation, and D-pad click listener.
2. **F11: Dynamic Hero Banner Carousel**:
   - In `app/src/main/java/com/darknet/animetv/ui/components/TvHeroBanner.kt`: High-definition 16:9 widescreen backdrop (`AniListCatalogSource` bannerImage), romaji/english title, score badge, airing status, synopsis, and action buttons ("Play Now", "Resume", "Watchlist").
3. **F12: Horizontal Shelves & Leanback Navigation**:
   - In `app/src/main/java/com/darknet/animetv/ui/components/TvAnimeCard.kt`: 140x210dp focus-scaled media card with poster, title, score, and optional watch progress bar overlay.
   - In `app/src/main/java/com/darknet/animetv/ui/screens/HomeScreen.kt`: Combine Hero Carousel with horizontal shelves (`TvLazyRow` within `TvLazyColumn`) for Continue Watching (with progress bar), Trending Anime, Popular Anime, Recently Updated, and Top Rated.
4. **F13: TV Search Screen**:
   - In `app/src/main/java/com/darknet/animetv/ui/screens/SearchScreen.kt`: Remote-friendly search bar, category/genre chips filter row (`CategoryChip.kt`), and responsive 5-column grid with D-pad boundary resistance.
5. **F14: Anime Details & Episode Picker**:
   - In `app/src/main/java/com/darknet/animetv/ui/screens/DetailsScreen.kt`: 16:9 backdrop with gradient overlay, detailed synopsis, metadata chips, Favorite/Watchlist toggle button syncing with `AppStorage`, and horizontal episode picker shelf with watch progress status (>90% marked watched).
6. **F15: TV Sources Management Screen**:
   - In `app/src/main/java/com/darknet/animetv/ui/screens/SourcesScreen.kt`: View all built-in and custom sources, toggle enabled/disabled states, run live latency health probes (`AnimeRepository.testAllSources()`), and add custom sources via JSON DSL dialog/input.
7. **MainActivity Integration**:
   - Wire top navigation bar tabs ("Home", "Catalog/Search", "Watchlist", "Sources") and drill-down to "Details" screen.
8. **Testing & Verification**:
   - Authored unit/component tests in `app/src/test/java/com/darknet/animetv/ui/` verifying D-pad navigation, focus scaling, shelf data binding, and screen transitions.
   - Run: `.\gradlew.bat testDebugUnitTest` and `.\gradlew.bat assembleDebug`.
   - Deliver structured `handoff.md` with full verification output.


## 2026-10-27T05:30:37Z
[Message] timestamp=2026-10-27T05:30:37Z sender=ab6d63fe-9d63-4bee-ad2e-73a412c7f3eb priority=MESSAGE_PRIORITY_HIGH content=You are worker_m3 (Jetpack Compose for TV UI & D-pad Navigation Worker) for AnimeTV Android TV rebuild.
Your working directory is c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\worker_m3.
Read your instructions in c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\worker_m3\DISPATCH.md.
MANDATORY: You MUST read ORIGINAL_REQUEST.md at c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\ORIGINAL_REQUEST.md before starting.
Also read PROJECT.md at c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\orchestrator_1\PROJECT.md and TEST_READY.md at c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\orchestrator_1\TEST_READY.md.

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Implement Features F10–F15:
- FocusModifiers.kt (tvFocusableItem: 1.08x scale, glow ring, z-index)
- TvAnimeCard.kt (140x210dp focus card)
- TvHeroBanner.kt (16:9 widescreen hero carousel)
- HomeScreen.kt (TvLazyRow shelves: Continue Watching with progress, Trending, Popular, Top Rated)
- DetailsScreen.kt (Backdrop, metadata, favorite button, episode shelf with progress)
- SearchScreen.kt (Remote chips + 5-column grid)
- SourcesScreen.kt (Source toggles, live latency ping, custom JSON DSL source adder)
- MainActivity.kt screen integration
- Fix minor Mp4UploadExtractor.kt port regex on line 79 (:(\\d+)?)
- Write unit tests in app/src/test/java/com/darknet/animetv/ui/
- Verify with .\gradlew.bat testDebugUnitTest and .\gradlew.bat assembleDebug
- Write handoff.md and send message back to parent orchestrator.
