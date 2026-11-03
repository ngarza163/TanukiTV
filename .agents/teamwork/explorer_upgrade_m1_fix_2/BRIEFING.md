# BRIEFING — 2026-10-28T05:29:00Z

## Mission
Investigate stream failover in DefaultAnimeRepository and catalog sources (JikanSource, AnimeFlvSource, AniListCatalogSource), and formulate an exact fix strategy ensuring pure metadata sources return emptyList() for streams and failover does not accept SUB streams when preferredAudio == 'DUB'.

## 🔒 My Identity
- Archetype: explorer
- Roles: investigation, synthesis
- Working directory: c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\explorer_upgrade_m1_fix_2
- Original parent: 5530bcff-e1f6-4c64-9cf0-a70de03c988f
- Milestone: upgrade_m1_fix

## 🔒 Key Constraints
- Read-only investigation — do NOT implement code changes directly in production files
- Follow Handoff Protocol (5-component report)
- Pure metadata sources must return emptyList() for streams (or not throw/fake streams inappropriately)
- Failover must not accept SUB streams when preferredAudio == 'DUB'

## Current Parent
- Conversation ID: 5530bcff-e1f6-4c64-9cf0-a70de03c988f
- Updated: not yet

## Investigation State
- **Explored paths**:
  - `ORIGINAL_REQUEST.md` (R1-R4)
  - `auditor_upgrade_m1/handoff.md` (integrity violation findings)
  - `reviewer_upgrade_m1_2/handoff.md` (quality review & adversarial challenges)
  - `worker_upgrade_m1/handoff.md`
  - `PROJECT.md`
  - `DefaultAnimeRepository` in `AnimeRepository.kt` (lines 472-608)
  - `JikanSource.kt` (lines 136-158)
  - `AnimeFlvSource.kt` (lines 237-283)
  - `AniListCatalogSource.kt` (lines 214-217)
  - `TvPlayerScreen.kt` (lines 202-235)
  - `DubbedExperienceTest.kt` (lines 76-98)
  - Test suites: `AniListSyncManagerAdversarialTest.kt`, `Milestone1ChallengerStressTest.kt`, `AnimeRepositoryTest.kt`, `RepositoryStressChallengerTest.kt`, `ScraperSourcesTest.kt`, `ExtractorStressChallengerTest.kt`
- **Key findings**:
  - Empirical reproduction confirmed: `DubbedExperienceTest > testEpisodeStreamsAudioPreferencePriority` fails with `AssertionError: Primary stream when preferredAudio=DUB should be DUB` at `DubbedExperienceTest.kt:83`.
  - Four intertwined defects identified:
    1. `isDemoFallbackUrl` missing `TearsOfSteel` and `commondatastorage.googleapis.com`.
    2. `JikanSource` returns hardcoded mock streams including `TearsOfSteel.mp4` (`SUB`).
    3. `AnimeFlvSource` falls back to `TearsOfSteel.mp4` when scraping returns empty.
    4. `DefaultAnimeRepository` adds catalog sources as last resort in `getEpisodeStreams` and halts failover on SUB streams even when `preferredAudio == "DUB"`.
- **Unexplored areas**: None. Scope fully investigated and synthesized.

## Key Decisions Made
- Confirmed exact fix strategy across 4 target files: `AnimeRepository.kt`, `JikanSource.kt`, `AnimeFlvSource.kt`, and `DubbedExperienceTest.kt` (plus recommendation for `TvPlayerScreen.kt`).
- Strategy guarantees pure metadata sources return `emptyList()`, demo MP4 URLs are filtered, catalog sources are never added to stream failover, and failover rejects SUB streams when `preferredAudio == "DUB"`.

## Artifact Index
- `DISPATCH.md` — record of dispatch instructions
- `BRIEFING.md` — agent memory and state
- `progress.md` — liveness heartbeat
- `handoff.md` — comprehensive 5-component analysis and fix strategy report
