## 2026-10-28T05:32:55Z
You are worker_upgrade_m1_gen2.
Your working directory is: c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\worker_upgrade_m1_gen2
You MUST read the authoritative request at:
c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\ORIGINAL_REQUEST.md
and read your assignment context at:
c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\worker_upgrade_m1_gen2\context.md
and read the project plan at:
c:\Users\ngarz\Documents\antigravity\blissful-planck\PROJECT.md

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Your task:
Remediate the Milestone 1 defects and integrity violations:
1. AnimeRepository.kt:
   - Update isDemoFallbackUrl to include TearsOfSteel, commondatastorage.googleapis.com, gtv-videos-bucket, devstreaming-cdn.apple.com.
   - Do NOT add pure metadata catalog sources (AniListCatalogSource, JikanSource) to prioritizedSources for streams.
   - In getEpisodeStreams failover loop, when preferredAudio == "DUB", filter for DUB streams; if source only has SUB streams, continue failover to subsequent sources or emergency DUB fallback.
   - Explicitly return the sorted list from withContext(Dispatchers.IO).
   - In getCharacterCast, remove the hardcoded 4-show switch and provide live AniList query with clean dynamic fallback.
2. JikanSource.kt:
   - getEpisodeStreams must return emptyList().
3. AnimeFlvSource.kt:
   - Remove lines injecting TearsOfSteel.mp4 / test-streams.mux.dev fallback streams on empty scrape.
4. WcoSource.kt:
   - In getDubbedAnime(), ensure dubbedEpisodesCount is populated.
5. Verification:
   Run:
   .\gradlew.bat testDebugUnitTest
   .\gradlew.bat assembleDebug
   Verify exit code 0 and all tests pass (especially DubbedExperienceTest).

Write your complete report to:
c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\worker_upgrade_m1_gen2\handoff.md
Send a message to orchestrator_2 when finished.
