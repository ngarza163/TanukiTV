# BRIEFING — 2026-10-27T04:14:30Z

## Mission
Investigate R2 (Modular Streaming Scraper Engine & Custom Sources) and catalog metadata APIs (AniList GraphQL / Jikan v4 REST), formulating technical specifications and interface contracts for the AnimeTV Android TV rebuild.

## 🔒 My Identity
- Archetype: explorer
- Roles: Scraper Engine Explorer, Streaming Architecture Investigator
- Working directory: c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\explorer_survey_2
- Original parent: ab6d63fe-9d63-4bee-ad2e-73a412c7f3eb
- Milestone: Survey & Architectural Design (M1)

## 🔒 Key Constraints
- Read-only investigation — do NOT implement
- Analyze the 7 target sources: Aniwatch, Anikoto, Miruro, ReAnime, KAA, WCO, AnimeFLV
- Analyze catalog/metadata sources: AniList GraphQL / Jikan v4 REST
- Formulate technical specifications and interface contracts for AnimeSource, video extractors, multi-source failover, custom source definition format, and connection/latency harness
- Document in handoff.md and maintain progress.md

## Current Parent
- Conversation ID: ab6d63fe-9d63-4bee-ad2e-73a412c7f3eb
- Updated: 2026-10-27T04:10:00Z

## Investigation State
- **Explored paths**: `animetv-src/tools/notes/new-source-notes.md`, `animetv-src/tools/utils/vrf.js`, `animetv-src/app/src/main/assets/view/m.js`, `animetv-src/app/src/main/java/.../AnimeView.java`, `AnimeDataSource.java`, `app/src/main/java/com/darknet/animetv/data/...`
- **Key findings**:
  - Miruro reverse-engineered API uses `mapper.miruro.tv` and `dio.miruro.tv` returning pre-parsed m3u8 streams and subtitles.
  - Aniwatch / HiAnime uses AJAX endpoints (`/ajax/v2/episode/list`, `/ajax/v2/episode/servers`, `/ajax/v2/episode/sources`) routing to MegaCloud / RapidCloud embeds.
  - KAA utilizes AES-CBC-PKCS7 decryption for its player config script.
  - WCO requires internal player embed resolution with strict `Referer: https://www.wco.tv/` headers.
  - AnimeFLV extracts from embedded JS variables (`var episodes` & `var videos`).
  - AniList GraphQL is superior to Jikan for Android TV due to 16:9 `bannerImage` hero art and higher rate limits (90/min vs Jikan's 3/sec).
  - Media3 ExoPlayer requires OkHttp DataSource with custom header interceptors (Referer/Origin spoofing) to play anti-hotlinking CDN streams.
- **Unexplored areas**: None remaining for specification. Ready to write handoff report.

## Key Decisions Made
- Formulated `AnimeSource` interface with streaming-first semantics.
- Designed modular `VideoExtractor` registry for MegaCloud, StreamWish, Doodstream, Vidplay, Mp4Upload, and direct feeds.
- Defined dynamic JSON DSL for custom user-configured sources.
- Designed multi-tier source failover chain with latency probe harness.

## Artifact Index
- DISPATCH.md — incoming dispatch instructions
- BRIEFING.md — persistent working memory
- progress.md — liveness heartbeat and milestone tracking
- handoff.md — final 5-component report
