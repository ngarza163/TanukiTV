# Survey Assignment: Explorer 1 — Live Data Integration & Backend Architecture

## Mission
Investigate R1: Production Readiness & Live Data Integration (Out of Demo Mode).
Authoritative request: `c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\ORIGINAL_REQUEST.md` (section `## 2026-10-28T04:24:53Z`).

## Scope of Investigation
1. `AniListCatalogSource` and `DefaultAnimeRepository`:
   - How `getAiringSchedule()` is currently implemented and where demo/fallback data is used.
   - How `getCharacterCast()` is currently implemented (identify the hardcoded 4-show switch statement).
   - GraphQL queries needed for AniList weekly AiringSchedule and characters/seiyuu cast (including Japanese & English voice actors, `characters(sort: [ROLE, RELEVANCE])`).
2. `AniListSyncManager`:
   - How account sync currently operates in `SettingsScreen` and `WatchlistScreen`.
   - Identify `performDemoSync` and how to query real GraphQL `User` and `MediaListCollection` by public username or personal access token.
3. Stream Resolution:
   - Ensure `AniListCatalogSource` returns `emptyList()` for `getEpisodeStreams`.
   - How `DefaultAnimeRepository.getEpisodeStreams` resolves SUB and DUB streams across active providers (`KaaSource`, `AniwatchSource`, `MiruroSource`, `ConsumetSource`, `AnikotoSource`, `ReAnimeSource`, `WcoSource`).
4. Unit tests:
   - Check existing tests in `app/src/test/java/com/darknet/animetv/` related to catalog, repository, and sync.

Produce a detailed `handoff.md` with file paths, line numbers, existing code snippets, required changes, and exact implementation recommendations.
