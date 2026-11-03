# Dispatch: challenger_m2_1 (Scraper & Extractor Stress Challenger)

## Mission
Empirically stress-test and challenge Milestone 2 (Video Extractors & Scrapers) for AnimeTV Android TV.

## Authoritative Inputs
- ORIGINAL_REQUEST.md: `c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\ORIGINAL_REQUEST.md` (MUST READ FIRST)
- PROJECT.md: `c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\orchestrator_1\PROJECT.md`
- Worker M2 Handoff: `c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\worker_m2\handoff.md`

## Scope & Target
- Target: `app/src/main/java/com/darknet/animetv/data/source/extractor/` and `app/src/main/java/com/darknet/animetv/data/source/`
- Challenge adversarial inputs:
  1. Corrupted/unpacked JS in StreamWish (`eval(function(...)`)
  2. Malformed token/URL responses in MegaCloud, Doodstream, Vidplay
  3. Kaa AES decryptor edge cases (invalid keys, short ciphertexts, PKCS7 padding corruption)
  4. Missing/malformed HTML elements in DOM scrapers (AnimeFLV, WCO, ReAnime, Anikoto)
  5. Missing 16:9 banner in AniList GraphQL responses (fallback to cover art)

## Requirements
1. Write and execute an adversarial stress test suite in `app/src/test/java/com/darknet/animetv/source/` (e.g. `ExtractorStressChallengerTest.kt`).
2. Run tests via `.\gradlew.bat testDebugUnitTest` and verify no unhandled crashes.
3. Deliver structured `handoff.md` with explicit verdict: `APPROVE` or `REQUEST_CHANGES`.


## 2026-10-27T05:16:36Z
[Message] timestamp=2026-10-27T05:16:36Z sender=ab6d63fe-9d63-4bee-ad2e-73a412c7f3eb priority=MESSAGE_PRIORITY_HIGH content=You are challenger_m2_1 (Scraper & Extractor Stress Challenger) for AnimeTV Android TV rebuild.
Your working directory is c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\challenger_m2_1.
Read your instructions in c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\challenger_m2_1\DISPATCH.md.
MANDATORY: You MUST read ORIGINAL_REQUEST.md at c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\ORIGINAL_REQUEST.md before starting.
Also read PROJECT.md at c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\orchestrator_1\PROJECT.md and worker_m2 handoff at c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\worker_m2\handoff.md.
Write an empirical stress test suite (e.g., ExtractorStressChallengerTest.kt) challenging edge cases (unpacker, malformed HTML, missing banner fallback, bad tokens, AES corruption). Execute with .\gradlew.bat testDebugUnitTest, write handoff.md with verdict (APPROVE or REQUEST_CHANGES), and send a message back to parent.
