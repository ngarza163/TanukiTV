# Worker Assignment: Milestone 1 — Data Models, Live AniList & Dub Repositories

## Mission
Implement Milestone 1 (F1-F7) of the TanukiTV Production Upgrade.
Reference documents:
- `c:\Users\ngarz\Documents\antigravity\blissful-planck\PROJECT.md`
- `c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\ORIGINAL_REQUEST.md` (specifically `## 2026-10-28T04:24:53Z`)
- Explorer 1 Report: `c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\explorer_upgrade_survey_1\handoff.md`
- Explorer 2 Report: `c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\explorer_upgrade_survey_2\handoff.md`

## Scope of Implementation
1. **F1: Extended Dub Models (`app/src/main/java/com/darknet/animetv/data/model/Anime.kt`)**:
   - `Anime`: add `val hasDub: Boolean = false`, `val dubbedEpisodesCount: Int = 0`.
   - `Episode`: add `val hasDub: Boolean = false`, `val audioTrack: String = "SUB"`.
   - `VideoSource`: add `val audioTrack: String = "SUB"`.
2. **F2: Live Airing Schedule GraphQL (`AniListCatalogSource.kt` & `DefaultAnimeRepository.kt`)**:
   - Add `getAiringSchedule(dayOfWeek: String? = null): List<AiringScheduleItem>` in `AniListCatalogSource` using the GraphQL query from Explorer 1 report.
   - In `DefaultAnimeRepository.getAiringSchedule(dayOfWeek: String?)`: query live AniList catalog source. Fall back gracefully to curated schedule when offline or network fails.
3. **F3: Live Character & Seiyuu Cast GraphQL (`AniListCatalogSource.kt` & `DefaultAnimeRepository.kt`)**:
   - Add `getCharacterCast(animeId: String, animeTitle: String): List<CharacterCast>` in `AniListCatalogSource` querying `characters(sort: [ROLE, RELEVANCE])` with Japanese & English voice actors.
   - In `DefaultAnimeRepository.getCharacterCast`: query live AniList. Replace hardcoded 4-show switch with dynamic live query + graceful fallback map matching existing tests.
4. **F4: Pure Metadata Stream Separation (`AniListCatalogSource.kt`)**:
   - Ensure `AniListCatalogSource.getEpisodeStreams()` returns `emptyList()` (remove the fake test streams).
5. **F5 & F6: Dubbed Repository Query & Stream Audio Priority**:
   - In `AnimeSource.kt`: add `suspend fun getDubbedAnime(): List<Anime> = emptyList()`.
   - In `WcoSource.kt`: override `getDubbedAnime()` and mark scraped anime with `hasDub = true`. Set `audioTrack = "DUB"` on video sources.
   - In `AniwatchSource.kt`: override `getDubbedAnime()` and set `audioTrack = srvType` on video sources.
   - In `AnimeRepository.kt`: add `suspend fun getDubbedAnime(): List<Anime>` and update `getEpisodeStreams` with `preferredAudio: String = "SUB"`.
   - In `DefaultAnimeRepository`: implement `getDubbedAnime()` aggregating multi-source queries with rich curated offline fallback (Cowboy Bebop, FMA:B, Attack on Titan, Death Note, Cyberpunk, etc.). In `getEpisodeStreams`: when `preferredAudio == "DUB"`, prioritize sources with `audioTrack == "DUB"` or containing "Dub".
6. **F7: Production AniList Account Sync (`AniListSyncManager.kt`)**:
   - Add `loginWithUsername(username: String, storage: AppStorage): Result<AniListUser>` querying `User(name: $username)` without auth token.
   - In `syncAniListWatchlist`: query public `MediaListCollection` without auth header when unauthenticated. Only call `performDemoSync` if `token?.startsWith("demo_") == true`. Do NOT inject fake demo anime into real accounts.
7. **Unit Tests**:
   - Implement unit tests covering `getDubbedAnime()`, live schedule, live character cast, SUB/DUB streams, and public username sync.
   - Run `.\gradlew.bat testDebugUnitTest assembleDebug` and verify all tests pass with exit code 0.

Produce a detailed `handoff.md` with build and test verification outputs.
