# Challenger 1 Assignment: Milestone 1 Verification

## Mission
Adversarially challenge and stress-test the work product of `worker_upgrade_m1` for Milestone 1 (F1-F7).
Authoritative request: `c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\ORIGINAL_REQUEST.md` (specifically `## 2026-10-28T04:24:53Z`).
Project plan: `c:\Users\ngarz\Documents\antigravity\blissful-planck\PROJECT.md`.
Worker handoff: `c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\worker_upgrade_m1\handoff.md`.

## Stress Testing Scope
1. Empirically verify `getDubbedAnime()`:
   - Does it return a non-empty, deduplicated list where `hasDub == true`?
   - Test sorting when `preferredAudio == "DUB"` vs `"SUB"` in `getEpisodeStreams()`.
2. Empirically verify `AniListCatalogSource.getEpisodeStreams()`:
   - Does it return strictly `emptyList()` with zero mock video URLs?
3. Empirically verify Airing Schedule & Character Cast under normal, malformed, and offline scenarios.
4. Execute test commands and write findings with clear verdict (`APPROVE` or `REJECT`) to `handoff.md`.
