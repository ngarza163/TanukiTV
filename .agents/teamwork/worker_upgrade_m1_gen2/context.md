# Worker Assignment: Milestone 1 Remediation (Iteration 2)

## Mission
Remediate the Milestone 1 defects and integrity violations identified by the Forensic Auditor, Reviewers, Challengers, and Fix Explorers.
Reference documents:
- `c:\Users\ngarz\Documents\antigravity\blissful-planck\PROJECT.md`
- `c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\ORIGINAL_REQUEST.md` (specifically `## 2026-10-28T04:24:53Z`)
- Forensic Auditor Report: `c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\auditor_upgrade_m1\handoff.md`
- Fix Explorer 1 Report: `c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\explorer_upgrade_m1_fix_1\handoff.md`
- Fix Explorer 2 Report: `c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\explorer_upgrade_m1_fix_2\handoff.md`
- Fix Explorer 3 Report: `c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\explorer_upgrade_m1_fix_3\handoff.md`

## Required Code Fixes
1. **`app/src/main/java/com/darknet/animetv/data/repository/AnimeRepository.kt`**:
   - **`isDemoFallbackUrl(url: String)`**: Add `TearsOfSteel`, `commondatastorage.googleapis.com`, `gtv-videos-bucket`, `devstreaming-cdn.apple.com`.
   - **Exclude pure metadata sources**: Ensure `AniListCatalogSource` and `JikanSource` are never added to `prioritizedSources` for stream failover (remove lines appending them as "last resort").
   - **Stream failover audio filtering**: In the loop over `prioritizedSources`, if `preferredAudio == "DUB"`, filter `streams` for DUB audio (`isDubStream(it)`). If DUB streams exist, collect them and `break`. If the source only provides SUB streams, do NOT `break`; continue failover to subsequent sources or emergency DUB fallback.
   - **Explicit return of sorted streams**: Explicitly return the sorted list from `withContext(Dispatchers.IO)` (do not rely on in-place sorting).
   - **Remove hardcoded 4-show switch**: Ensure `getCharacterCast` uses live AniList query + clean dynamic fallback so unknown anime receive valid structured characters rather than the hardcoded 4-show placeholder strings.
2. **`app/src/main/java/com/darknet/animetv/data/source/JikanSource.kt`**:
   - `getEpisodeStreams(...)` must return `emptyList()` (pure metadata provider).
3. **`app/src/main/java/com/darknet/animetv/data/source/AnimeFlvSource.kt`**:
   - Remove lines 263-280 injecting `TearsOfSteel.mp4` / `test-streams.mux.dev` fallback streams on empty scrape.
4. **`app/src/main/java/com/darknet/animetv/data/source/WcoSource.kt`**:
   - In `getDubbedAnime()`: ensure `dubbedEpisodesCount` is populated (e.g. `totalEpisodes.takeIf { it > 0 } ?: 1`).
5. **Verification**:
   - Run `.\gradlew.bat testDebugUnitTest` and ensure 100% of tests pass, specifically `DubbedExperienceTest > testEpisodeStreamsAudioPreferencePriority`.
   - Run `.\gradlew.bat assembleDebug` and ensure exit code 0.
   - Document raw command output in `handoff.md`.
