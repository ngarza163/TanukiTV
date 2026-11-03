# Dispatch: challenger_m3_2 (Screen State & Episode Chunking Challenger)

## Mission
Empirically stress-test and challenge Milestone 3 (Screens, Episode Chunking, Sources UI & Custom DSL) for AnimeTV Android TV.

## Authoritative Inputs
- ORIGINAL_REQUEST.md: `c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\ORIGINAL_REQUEST.md` (MUST READ FIRST)
- PROJECT.md: `c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\orchestrator_1\PROJECT.md`
- Worker M3 Handoff: `c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\worker_m3\handoff.md`

## Scope & Target
- Target: `app/src/main/java/com/darknet/animetv/ui/screens/` (DetailsScreen, HomeScreen, SourcesScreen)
- Challenge adversarial scenarios:
  1. Massive episode lists: 1000+ episodes (chunking logic into 50-episode blocks, out-of-bounds tab index, empty episode lists)
  2. Watched status computation: boundary at 89.9% (unwatched) vs 90.0% (watched), 0 duration videos, past episodes in series
  3. Continue watching shelf: empty history, 100 entries, progress ratio clamping (0.0f to 1.0f)
  4. Sources screen custom DSL input: invalid URIs, empty names, malformed JSON injection
  5. Screen transitions and back-stack navigation under rapid tab switching

## Requirements
1. Author an adversarial stress test suite in `app/src/test/java/com/darknet/animetv/ui/` (e.g. `TvScreensStressChallengerTest.kt`).
2. Run tests via `.\gradlew.bat testDebugUnitTest --tests "com.darknet.animetv.ui.*"`.
3. Deliver structured `handoff.md` with explicit verdict: `APPROVE` or `REQUEST_CHANGES`.


## 2026-10-27T05:48:11Z
You are challenger_m3_2 (Screen State & Episode Chunking Challenger) for AnimeTV Android TV rebuild.
Your working directory is c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\challenger_m3_2.
Read your instructions in c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\challenger_m3_2\DISPATCH.md.
MANDATORY: You MUST read ORIGINAL_REQUEST.md at c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\ORIGINAL_REQUEST.md before starting.
Also read PROJECT.md at c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\orchestrator_1\PROJECT.md and worker_m3 handoff at c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\worker_m3\handoff.md.
Author an adversarial stress test suite (e.g., TvScreensStressChallengerTest.kt) challenging 1000+ episode chunking, 89.9% vs 90.0% watched threshold, 0 duration, empty/100 continue watching entries, and invalid custom DSL JSON inputs in SourcesScreen. Execute with .\gradlew.bat testDebugUnitTest --tests "com.darknet.animetv.ui.*", write handoff.md with verdict (APPROVE or REQUEST_CHANGES), and send a message back to parent.
