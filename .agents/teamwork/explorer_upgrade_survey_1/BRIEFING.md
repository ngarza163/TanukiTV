# BRIEFING — 2026-10-28T04:36:00Z

## Mission
Investigate R1: Production Readiness & Live Data Integration (Out of Demo Mode) across AniListCatalogSource, DefaultAnimeRepository, AniListSyncManager, streaming providers, and test suite.

## 🔒 My Identity
- Archetype: explorer
- Roles: investigator, analyzer, synthesizer
- Working directory: c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\explorer_upgrade_survey_1
- Original parent: 5530bcff-e1f6-4c64-9cf0-a70de03c988f
- Milestone: survey

## 🔒 Key Constraints
- Read-only investigation — do NOT implement
- Produce comprehensive handoff.md with code snippets, exact lines, and implementation recommendations for R1

## Current Parent
- Conversation ID: 5530bcff-e1f6-4c64-9cf0-a70de03c988f
- Updated: 2026-10-28T04:28:47Z

## Investigation State
- **Explored paths**:
  - `app/src/main/java/com/darknet/animetv/data/source/AniListCatalogSource.kt`
  - `app/src/main/java/com/darknet/animetv/data/repository/AnimeRepository.kt`
  - `app/src/main/java/com/darknet/animetv/data/repository/AniListSyncManager.kt`
  - `app/src/main/java/com/darknet/animetv/data/model/Anime.kt`, `AniListModels.kt`, `AiringScheduleItem.kt`, `CharacterCast.kt`
  - `app/src/main/java/com/darknet/animetv/data/storage/AppDatabase.kt`
  - `app/src/main/java/com/darknet/animetv/ui/screens/SettingsScreen.kt`, `WatchlistScreen.kt`, `ScheduleScreen.kt`, `DetailsScreen.kt`
  - Streaming providers: `KaaSource.kt`, `AniwatchSource.kt`, `MiruroSource.kt`, `ConsumetSource.kt`, `AnikotoSource.kt`, `ReAnimeSource.kt`, `WcoSource.kt`
  - Unit tests in `app/src/test/java/com/darknet/animetv/` (63 test files passing)
- **Key findings**:
  - `AniListCatalogSource` has no airing schedule or character cast methods; returns fake test streams for `getEpisodeStreams` (lines 212-227).
  - `DefaultAnimeRepository` hardcodes 16 mock airing schedule items (lines 376-626) and a 4-show switch for character cast (lines 708-899).
  - `AniListSyncManager` triggers `performDemoSync` inserting 4 fake anime whenever token is missing or starts with `demo_` (lines 121-125); `SettingsScreen` only provides a demo quick connect button (lines 734-776).
  - Streaming providers already have SUB/DUB logic (Aniwatch `data-type`, WCO `dubbed-anime-list`, KAA `en-US`), but `VideoSource` and repository need audioTrack and preferredAudio propagation.
- **Unexplored areas**: None for R1 survey scope.

## Key Decisions Made
- Formulate complete replacement GraphQL queries and mapping logic for live airing schedule and live character cast.
- Formulate public username linking via GraphQL `User` and `MediaListCollection` without requiring authentication token.
- Detail the exact `emptyList()` fix in `AniListCatalogSource` and SUB/DUB routing in `DefaultAnimeRepository`.

## Artifact Index
- context.md — Assignment instructions
- DISPATCH.md — Incoming messages log
- BRIEFING.md — Persistent working memory
- progress.md — Liveness heartbeat and task tracker
- handoff.md — Final investigation report
