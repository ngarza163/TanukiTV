# Dispatch: challenger_m2_2 (Failover & Concurrency Challenger)

## Mission
Empirically stress-test and challenge Milestone 2 (Failover Chain, Latency Probing & DSL) for AnimeTV Android TV.

## Authoritative Inputs
- ORIGINAL_REQUEST.md: `c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\ORIGINAL_REQUEST.md` (MUST READ FIRST)
- PROJECT.md: `c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\orchestrator_1\PROJECT.md`
- Worker M2 Handoff: `c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\worker_m2\handoff.md`

## Scope & Target
- Target: `app/src/main/java/com/darknet/animetv/data/repository/AnimeRepository.kt` and `app/src/main/java/com/darknet/animetv/data/source/DynamicHtmlSource.kt`
- Challenge adversarial scenarios:
  1. Cascading source failure (all sources throwing SocketTimeoutException or 503) -> verify repository returns emergency fallback without crashing.
  2. Highly concurrent requests to `getEpisodeStreams` and `testAllSources` -> verify thread-safety and no deadlock.
  3. Malformed JSON DSL in `DynamicHtmlSource` (missing fields, invalid CSS selectors, invalid regex pattern).
  4. Empty search results / zero episode edge cases.

## Requirements
1. Write and execute an empirical stress test suite in `app/src/test/java/com/darknet/animetv/source/` (e.g. `RepositoryStressChallengerTest.kt`).
2. Run tests via `.\gradlew.bat testDebugUnitTest` and verify behavior.
3. Deliver structured `handoff.md` with explicit verdict: `APPROVE` or `REQUEST_CHANGES`.


## 2026-10-27T05:16:36Z
You are challenger_m2_2 (Failover & Concurrency Challenger) for AnimeTV Android TV rebuild.
Your working directory is c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\challenger_m2_2.
Read your instructions in c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\challenger_m2_2\DISPATCH.md.
MANDATORY: You MUST read ORIGINAL_REQUEST.md at c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\ORIGINAL_REQUEST.md before starting.
Also read PROJECT.md at c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\orchestrator_1\PROJECT.md and worker_m2 handoff at c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\worker_m2\handoff.md.
Write an empirical stress test suite (e.g., RepositoryStressChallengerTest.kt) challenging cascading failure, concurrency, malformed JSON DSL, and latency probes. Execute with .\gradlew.bat testDebugUnitTest, write handoff.md with verdict (APPROVE or REQUEST_CHANGES), and send a message back to parent.
