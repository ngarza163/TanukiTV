# Dispatch: reviewer_m2_1 (Scraper & Extractor Correctness Reviewer)

## Mission
Perform independent review of Milestone 2 (Modular Streaming Scraper Engine & Extractors) for AnimeTV Android TV.

## Authoritative Inputs
- ORIGINAL_REQUEST.md: `c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\ORIGINAL_REQUEST.md` (MUST READ FIRST)
- PROJECT.md: `c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\orchestrator_1\PROJECT.md`
- Worker M2 Handoff: `c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\worker_m2\handoff.md`

## Scope of Review
Review the implementation of all video extractors and scrapers:
- `app/src/main/java/com/darknet/animetv/data/source/extractor/` (MegaCloud, StreamWish, Doodstream, Vidplay, Mp4Upload, DirectStream, Registry)
- `app/src/main/java/com/darknet/animetv/data/source/` (Aniwatch, Anikoto, Miruro, ReAnime, Kaa, Wco, AnimeFlv, AniListCatalogSource, DynamicHtmlSource)
- Unit tests: `app/src/test/java/com/darknet/animetv/source/`

## Requirements
1. Verify correctness, completeness, robustness, and interface conformance.
2. Run build and tests via `.\gradlew.bat testDebugUnitTest --tests "com.darknet.animetv.source.*"` and `.\gradlew.bat assembleDebug`.
3. Verify that 16:9 banner extraction is properly handled for Android TV leanback requirements.
4. Verify anti-hotlinking headers (Referer, Origin, User-Agent) are correctly assigned.
5. Deliver structured `handoff.md` with explicit verdict: `APPROVE` or `REQUEST_CHANGES`.

## 2026-10-27T05:16:36Z
You are reviewer_m2_1 (Scraper & Extractor Correctness Reviewer) for AnimeTV Android TV rebuild.
Your working directory is c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\reviewer_m2_1.
Read your instructions in c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\reviewer_m2_1\DISPATCH.md.
MANDATORY: You MUST read ORIGINAL_REQUEST.md at c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\ORIGINAL_REQUEST.md before starting.
Also read PROJECT.md at c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\orchestrator_1\PROJECT.md and worker_m2 handoff at c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\worker_m2\handoff.md.
Verify all extractors and scrapers, run .\gradlew.bat testDebugUnitTest --tests "com.darknet.animetv.source.*" and .\gradlew.bat assembleDebug, and write your handoff.md with an explicit verdict (APPROVE or REQUEST_CHANGES). Then send a message back to parent.
