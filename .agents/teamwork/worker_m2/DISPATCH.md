# Dispatch: Worker M2 (Modular Streaming Scraper Engine & Custom Sources)
Target: c:\Users\ngarz\Documents\antigravity\blissful-planck
Working Directory: c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\worker_m2
User Request: c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\ORIGINAL_REQUEST.md
Master Architecture: c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\orchestrator_1\PROJECT.md
Survey 2 Specifications: c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\explorer_survey_2\handoff.md

Write Ownership:
- `app/src/main/java/com/darknet/animetv/data/source/`
- `app/src/main/java/com/darknet/animetv/data/repository/`
- `app/src/test/java/com/darknet/animetv/source/`

Tasks to implement:
1. Ensure all 7 target source extractors exist and implement `AnimeSource`:
   - `AniwatchSource.kt` (https://aniwatchtv.ro/ / HiAnime)
   - `AnikotoSource.kt` (https://anikototv.to/home)
   - `MiruroSource.kt` (https://www.miruro.cx/ & API mapper)
   - `ReAnimeSource.kt` (https://reanime.to/home)
   - `KaaSource.kt` (https://kaa.lt/ with AES decryption)
   - `WcoSource.kt` (https://www.wco.tv/)
   - `AnimeFlvSource.kt` (https://www3.animeflv.net - connect real scraping & StreamWish extractor)
2. Implement Video Extractors in `com.darknet.animetv.data.source.extractor`:
   - `VideoExtractor.kt` interface
   - `MegaCloudExtractor.kt`
   - `StreamWishExtractor.kt`
   - `DoodstreamExtractor.kt`
   - `VidplayExtractor.kt`
   - `Mp4UploadExtractor.kt`
   - `DirectStreamExtractor.kt`
3. Implement `AniListCatalogSource.kt` providing high-resolution 16:9 banners, trending, popular, and top airing anime via AniList GraphQL API, with `JikanSource.kt` as fallback.
4. Implement `DynamicHtmlSource.kt` supporting the declarative custom JSON DSL (allowing user-defined custom sources).
5. Implement `AnimeRepository.kt` in `com.darknet.animetv.data.repository`:
   - Multi-source failover chain (`getStreamWithFailover`).
   - Connection test & latency probe harness (`testAllSources`).
   - Integration with `AppStorage` to sync source enable/disable states.
   - Register in `AnimeTvApplication`.
6. Unit tests: Create comprehensive unit tests in `app/src/test/java/com/darknet/animetv/source/` testing catalog queries, stream extractors, failover resilience, and custom source parsing.
7. Verification: Run `.\gradlew.bat testDebugUnitTest` and `.\gradlew.bat assembleDebug`.
8. Write handoff report to `c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\worker_m2\handoff.md`.

## 2026-10-27T04:57:44Z
You are Worker M2 (Modular Streaming Scraper Engine & Custom Sources) for AnimeTV rebuild.
Your working directory is: c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\worker_m2
You MUST read ORIGINAL_REQUEST.md located at: c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\ORIGINAL_REQUEST.md
Also read:
- Master Architecture: c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\orchestrator_1\PROJECT.md
- Survey 2 Scraper Specifications: c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\explorer_survey_2\handoff.md
- Dispatch file: c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\worker_m2\DISPATCH.md

Write ownership:
You own exclusively:
- `app/src/main/java/com/darknet/animetv/data/source/`
- `app/src/main/java/com/darknet/animetv/data/repository/`
- `app/src/test/java/com/darknet/animetv/source/`

Tasks to implement:
1. Complete all 7 target source extractors implementing `AnimeSource`:
   - `AniwatchSource.kt` (https://aniwatchtv.ro/ / HiAnime AJAX + MegaCloud)
   - `AnikotoSource.kt` (https://anikototv.to/home)
   - `MiruroSource.kt` (https://www.miruro.cx/ & mapper API)
   - `ReAnimeSource.kt` (https://reanime.to/home)
   - `KaaSource.kt` (https://kaa.lt/ with AES-CBC decryption)
   - `WcoSource.kt` (https://www.wco.tv/)
   - `AnimeFlvSource.kt` (https://www3.animeflv.net - real Jsoup parsing & StreamWish/direct stream extraction)
2. Implement Video Extractors in `com.darknet.animetv.data.source.extractor`:
   - `VideoExtractor.kt` interface
   - `MegaCloudExtractor.kt`
   - `StreamWishExtractor.kt`
   - `DoodstreamExtractor.kt`
   - `VidplayExtractor.kt`
   - `Mp4UploadExtractor.kt`
   - `DirectStreamExtractor.kt`
3. Implement `AniListCatalogSource.kt` providing high-definition 16:9 banners, scores, and details via AniList GraphQL, with `JikanSource.kt` as fallback.
4. Implement `DynamicHtmlSource.kt` for user-defined custom JSON DSL sources.
5. Implement `AnimeRepository.kt` in `com.darknet.animetv.data.repository`:
   - Multi-source failover (`getStreamWithFailover`).
   - Connection test & latency probe (`testAllSources`).
   - Synchronization with `AppStorage`.
6. Unit tests: In `app/src/test/java/com/darknet/animetv/source/`, write tests verifying each extractor, catalog source, failover chain, and custom source parsing.
7. Verification: Run `.\gradlew.bat testDebugUnitTest` and `.\gradlew.bat assembleDebug`.
8. Write your handoff report to `c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\worker_m2\handoff.md`. Send a message when finished.
