# Survey Assignment: Explorer 2 — Dubbed Anime & Dubbed Episodes Experience

## Mission
Investigate R2: Full Dubbed Anime & Dubbed Episodes Experience across Models, Repositories, and UI.
Authoritative request: `c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\ORIGINAL_REQUEST.md` (section `## 2026-10-28T04:24:53Z`).

## Scope of Investigation
1. Model & Repository Support:
   - Current definitions of `Anime`, `Episode`, and `VideoSource` in `app/src/main/java/com/darknet/animetv/model/`.
   - How to extend them with dub metadata: `hasDub: Boolean`, `dubbedEpisodesCount: Int`, `audioTrack: String` ("SUB" | "DUB").
   - Define `AnimeRepository.getDubbedAnime()` query, how it fetches/aggregates English-dubbed anime series and recently dubbed episodes from active providers (AniList English-licensed/dubbed catalog + `AniwatchSource` / `KaaSource` / `WcoSource` dub catalogs).
2. UI Integration:
   - `HomeScreen`: Add dedicated `"🎙️ Dubbed Anime & Episodes"` horizontal shelf featuring `DUB` badges on cards.
   - `SearchScreen`: Add `"🎙️ Dubbed"` filter chip / toggle to filter the catalog and search results exclusively to dubbed anime and episodes.
   - `DetailsScreen`: Add interactive `SUB` / `DUB` audio track toggle pill above the episode shelf.
   - `TvPlayerScreen`: Pass `preferredAudio = "DUB"` when launching playback and handle stream selection.
3. Unit tests:
   - Check existing tests for models, repository, and UI state mapping.

Produce a detailed `handoff.md` with file paths, line numbers, existing code snippets, required changes, and exact implementation recommendations.
