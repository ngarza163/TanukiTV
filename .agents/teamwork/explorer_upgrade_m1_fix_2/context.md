# Fix Explorer Assignment: Milestone 1 Remediation

## Mission
Investigate and formulate a comprehensive fix strategy for the Milestone 1 failures and INTEGRITY VIOLATION.

## Mandatory Inputs to Read
1. Authoritative Request: `c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\ORIGINAL_REQUEST.md` (section `## 2026-10-28T04:24:53Z`).
2. Forensic Audit Report (Full Evidence): `c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\auditor_upgrade_m1\handoff.md`.
3. Reviewer 2 Report: `c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\reviewer_upgrade_m1_2\handoff.md`.
4. Worker Handoff: `c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\worker_upgrade_m1\handoff.md`.
5. Project Plan: `c:\Users\ngarz\Documents\antigravity\blissful-planck\PROJECT.md`.

## Integrity Violation & Failure Details
The Forensic Auditor reported INTEGRITY VIOLATION:
1. `DubbedExperienceTest > testEpisodeStreamsAudioPreferencePriority` fails with:
   `java.lang.AssertionError: Primary stream when preferredAudio=DUB should be DUB` at `DubbedExperienceTest.kt:83`.
2. Root cause 1: `DefaultAnimeRepository.isDemoFallbackUrl()` does not filter `TearsOfSteel.mp4` (returned by `AnimeFlvSource` on line 275 and `JikanSource`).
3. Root cause 2: `DefaultAnimeRepository.getEpisodeStreams()` accepts a `SUB` stream when `preferredAudio == "DUB"` and halts the failover loop early before checking remaining sources or falling back to emergency DUB streams.
4. Root cause 3: `JikanSource` returns mock demo streams and should return `emptyList()` for `getEpisodeStreams()`.

## Explorer Task
Formulate an exact, holistic fix strategy addressing all integrity violations and defects:
- What changes to make to `DefaultAnimeRepository.kt`, `AnimeFlvSource.kt`, `JikanSource.kt`, and `DubbedExperienceTest.kt`.
- How to ensure `getEpisodeStreams` when `preferredAudio == "DUB"` strictly requires DUB streams or continues failover.
- How to verify all 374+ unit tests and assembleDebug pass cleanly.

Do NOT implement any code changes. Write your complete analysis and recommended fix strategy to:
`handoff.md` in your working directory.
