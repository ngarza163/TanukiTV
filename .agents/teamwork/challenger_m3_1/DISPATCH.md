# Dispatch: challenger_m3_1 (D-pad Focus & Remote Navigation Challenger)

## Mission
Empirically stress-test and challenge Milestone 3 (D-pad Remote Focus, Card Scaling & Grid Navigation) for AnimeTV Android TV.

## Authoritative Inputs
- ORIGINAL_REQUEST.md: `c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\ORIGINAL_REQUEST.md` (MUST READ FIRST)
- PROJECT.md: `c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\orchestrator_1\PROJECT.md`
- Worker M3 Handoff: `c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\worker_m3\handoff.md`

## Scope & Target
- Target: `app/src/main/java/com/darknet/animetv/ui/components/` (FocusModifiers, TvAnimeCard, TvHeroBanner, CategoryChip) and `SearchScreen.kt` 5-column grid
- Challenge adversarial scenarios:
  1. D-pad boundary resistance (navigating LEFT at column 0, UP at top row, RIGHT at column 4)
  2. Rapid key events / debounce (100 rapid D-pad key presses)
  3. Action button resolution under invalid/extreme watch progress (progress = 0, negative, > 100%, Long.MAX_VALUE)
  4. Null / empty anime card titles, 0.0 scores, missing images
  5. Scale clamping and z-index elevation under focused vs unfocused states

## Requirements
1. Author an adversarial stress test suite in `app/src/test/java/com/darknet/animetv/ui/` (e.g. `TvFocusStressChallengerTest.kt`).
2. Run tests via `.\gradlew.bat testDebugUnitTest --tests "com.darknet.animetv.ui.*"`.
3. Deliver structured `handoff.md` with explicit verdict: `APPROVE` or `REQUEST_CHANGES`.

## 2026-10-27T05:48:11Z
You are challenger_m3_1 (D-pad Focus & Remote Navigation Challenger) for AnimeTV Android TV rebuild.
Your working directory is c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\challenger_m3_1.
Read your instructions in c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\challenger_m3_1\DISPATCH.md.
MANDATORY: You MUST read ORIGINAL_REQUEST.md at c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\ORIGINAL_REQUEST.md before starting.
Also read PROJECT.md at c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\orchestrator_1\PROJECT.md and worker_m3 handoff at c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\worker_m3\handoff.md.
Author an adversarial stress test suite (e.g., TvFocusStressChallengerTest.kt) challenging D-pad boundary resistance, rapid keypress debounce, action button resolution under extreme watch progress, null titles/images, and focus scale/z-index. Execute with .\gradlew.bat testDebugUnitTest --tests "com.darknet.animetv.ui.*", write handoff.md with verdict (APPROVE or REQUEST_CHANGES), and send a message back to parent.
