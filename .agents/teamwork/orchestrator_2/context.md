# Orchestrator Context — Elevate TanukiTV to Production TV Experience

## Authoritative Request
See `c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\ORIGINAL_REQUEST.md` (specifically under `## 2026-10-28T04:24:53Z`).

## Core Scope
1. **R1. Production Readiness & Live Data Integration (Out of Demo Mode)**:
   - Live Airing Schedule from AniList GraphQL in `AniListCatalogSource` and `DefaultAnimeRepository.getAiringSchedule`
   - Live Character & Seiyuu Cast (`characters(sort: [ROLE, RELEVANCE])` with JP & EN VAs) from AniList GraphQL in `AniListCatalogSource` and `DefaultAnimeRepository.getCharacterCast`
   - Production AniList Account Sync via public username or PAT in `SettingsScreen` / `WatchlistScreen` via `AniListSyncManager` (querying real GraphQL `User` & `MediaListCollection`)
   - Stream Resolution: `AniListCatalogSource` purely metadata; `DefaultAnimeRepository.getEpisodeStreams` resolves SUB and DUB streams from active providers (`KaaSource`, `AniwatchSource`, `MiruroSource`, `ConsumetSource`, `AnikotoSource`, `ReAnimeSource`, `WcoSource`)
2. **R2. Full Dubbed Anime & Dubbed Episodes Experience**:
   - Model & Repository support: `Anime`, `Episode`, `VideoSource` with dub metadata (`hasDub`, `dubbedEpisodesCount`, `audioTrack = "SUB" | "DUB"`) and `getDubbedAnime()` query
   - Home Screen Dubbed Shelf: `"🎙️ Dubbed Anime & Episodes"` with `DUB` badges
   - Catalog & Search Dubbed Filter: `"🎙️ Dubbed"` filter chip / toggle in `SearchScreen`
   - Details & Player SUB / DUB Episode Audio Selector: interactive toggle pill on `DetailsScreen` and in `TvPlayerScreen` requesting `preferredAudio = "DUB"`
3. **R3. Subtle Premium UI Animations & TV Remote Sound Effects**:
   - Zero-asset procedural TV Sound Effects (`TvSoundManager`) using `AudioTrack` / `ToneGenerator` / PCM wave synthesizer for D-pad focus tick, primary action / select, tab switch / navigation, back / dismiss, and watchlist add
   - Settings integration: `"UI Sound Effects"` toggle in `UserSettings`, `AppStorage`, and `SettingsScreen`
   - Screen & card animations: `AnimatedContent` crossfade + slide transitions for tabs & details; animated focus glow, glassmorphic badges, staggered shelf entrance
4. **R4. Custom Animated Tanuki Loading Component (`TvTanukiLoadingIndicator`)**:
   - Replace generic `CircularProgressIndicator` across `HomeScreen`, `SearchScreen`, `ScheduleScreen`, `DetailsScreen`, and `TvPlayerScreen`
   - Canvas / multi-layered vector drawing + `rememberInfiniteTransition` featuring bobbing animation, wagging tail, blinking eyes, glowing magical leaf/lantern aura, pulsing orbital ring, and animated contextual status subtitles
5. **Verification**:
   - `.\gradlew.bat testDebugUnitTest assembleDebug`
   - Unit tests for all new repository/sound/dub functions
   - Live emulator `emulator-5554` testing via adb and screenshots
