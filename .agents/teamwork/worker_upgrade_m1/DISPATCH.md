## 2026-10-28T04:39:02Z
You are worker_upgrade_m1.
Your working directory is: c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\worker_upgrade_m1
You MUST read the authoritative request at:
c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\ORIGINAL_REQUEST.md
and read your assignment context at:
c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\worker_upgrade_m1\context.md
and read the project plan at:
c:\Users\ngarz\Documents\antigravity\blissful-planck\PROJECT.md

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Your task:
Implement Milestone 1 (F1-F7):
1. F1: Models (Anime.kt) - add hasDub, dubbedEpisodesCount to Anime; hasDub, audioTrack to Episode; audioTrack to VideoSource.
2. F2: Live Airing Schedule GraphQL in AniListCatalogSource.kt and DefaultAnimeRepository.kt (with graceful fallback).
3. F3: Live Character & Seiyuu Cast GraphQL in AniListCatalogSource.kt and DefaultAnimeRepository.kt (with graceful fallback).
4. F4: Ensure AniListCatalogSource.getEpisodeStreams() returns emptyList().
5. F5 & F6: getDubbedAnime() in AnimeSource, WcoSource, AniwatchSource, AnimeRepository, and DefaultAnimeRepository; preferredAudio stream prioritization.
6. F7: Production AniList Account Sync (loginWithUsername, public MediaListCollection sync without fake demo entries) in AniListSyncManager.kt.
7. Unit tests: Create/update unit tests in app/src/test/java/com/darknet/animetv/ to verify getDubbedAnime(), live schedule, live character cast, and public account sync.
8. Run build and tests:
   .\gradlew.bat testDebugUnitTest
   .\gradlew.bat assembleDebug
   Verify exit code 0 and all tests pass.

Write your complete report to:
c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\worker_upgrade_m1\handoff.md
Update progress.md as you work. Send a message to orchestrator_2 when finished.
