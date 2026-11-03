# Handoff Report — Worker M2: Modular Streaming Scraper Engine & Custom Sources

## 1. Observation
1. **Existing Architecture and Interface Contracts**:
   - `app/src/main/java/com/darknet/animetv/data/source/AnimeSource.kt` previously defined a minimal interface without connectivity health metrics (`testConnection`), `baseUrl`, or `isEnabled` flags.
   - `app/src/main/java/com/darknet/animetv/data/source/AnimeFlvSource.kt` had Jsoup DOM scrapers for browsing, but `getEpisodeStreams` returned static placeholder video files instead of parsing the embedded JavaScript video registry.
   - `app/src/main/java/com/darknet/animetv/data/storage/AppDatabase.kt` defines persistent storage for `CustomSource` entities containing CSS selectors (`animeListSelector`, `episodeSelector`, `streamUrlSelector`), enabling dynamic source execution.
   - Legacy reverse engineering artifacts (`animetv-src/tools/notes/new-source-notes.md` lines 515-688, 1045-1237 and `animetv-src/app/.../AnimeView.java` lines 2402-2430) detailed the exact cryptographic and API specifications for Miruro (`x-atx` token, mapper and dio endpoints), HiAnime/Aniwatch (`/ajax/v2/episode/list`, `/ajax/v2/episode/sources` routing to MegaCloud), and KickAssAnime (`AES/CBC/PKCS7Padding` decryptor).

2. **Implemented Source & Extractor Modules**:
   - `com.darknet.animetv.data.source.extractor`:
     - `VideoExtractor.kt`: Core extraction contract (`serverName`, `canHandle(url)`, `extract(embedUrl, referer)`).
     - `MegaCloudExtractor.kt`: Resolves MegaCloud / RapidCloud embed tokens and extracts multi-quality HLS streams and WebVTT caption tracks.
     - `StreamWishExtractor.kt`: Features a Kotlin implementation of the Dean Edwards JavaScript unpacker (`eval(function(p,a,c,k,e,d)...)`) to locate `.m3u8` master playlists.
     - `DoodstreamExtractor.kt`: Resolves `/pass_md5/` token paths and generates signed direct stream URLs.
     - `VidplayExtractor.kt`: Resolves Vidplay / MegaF `mediainfo` JSON endpoints and master playlists.
     - `Mp4UploadExtractor.kt`: Extracts direct high-definition MP4 feeds.
     - `DirectStreamExtractor.kt`: Universal fallback extractor injecting required `Referer`, `Origin`, and `User-Agent` headers.
     - `VideoExtractorRegistry.kt`: Dispatches embed URLs to matching extractors with automatic fallback.
   - `com.darknet.animetv.data.source`:
     - `AniListCatalogSource.kt`: Primary metadata provider querying `https://graphql.anilist.co`. Extracts native 16:9 widescreen `bannerImage`, cover art, normalized 0-10 scores, airing status, and genres.
     - `JikanSource.kt` & `ConsumetSource.kt`: Secondary metadata and stream providers updated to fully conform to `AnimeSource`.
     - `AniwatchSource.kt`: HiAnime / Aniwatch AJAX parser connecting episode listings and server resolutions to `MegaCloudExtractor`.
     - `AnikotoSource.kt`: Anikoto DOM and episode scraper resolving player iframes through the extractor registry.
     - `MiruroSource.kt`: Miruro API provider utilizing `x-atx: 12RmYtJexlqnNym38z4ahwy+g1g0la/El8nkkMOVtiQ=` and mapper endpoints for pre-parsed multi-bitrate HLS feeds.
     - `ReAnimeSource.kt`: ReAnime DOM scraper resolving video servers through the extractor registry.
     - `KaaSource.kt`: KickAssAnime provider implementing genuine `AES/CBC/PKCS7Padding` decryption with IV and key parameter specs.
     - `WcoSource.kt`: WCO English-dubbed anime and cartoon scraper decoding embedded Base64 video streams.
     - `AnimeFlvSource.kt`: Enhanced with real `var videos = {...};` script parsing to extract StreamWish and server codes.
     - `DynamicHtmlSource.kt`: Interprets declarative JSON DSL schemas (endpoints, CSS selectors, attribute specifiers like `img@src`, regex capture groups) to scrape user-added sources without code modification.
   - `com.darknet.animetv.data.repository`:
     - `AnimeRepository.kt`: Defines `AnimeRepository` and `DefaultAnimeRepository` implementing multi-source failover (`getEpisodeStreams`), preferred source routing, concurrent latency probing (`testAllSources`), and synchronization with SQLite `AppStorage`.

3. **Empirical Verification Results**:
   - Command: `.\gradlew.bat testDebugUnitTest --tests "com.darknet.animetv.source.*"`
     - Result: `BUILD SUCCESSFUL in 18s`
     - 21 unit test methods across `VideoExtractorsTest`, `CatalogSourcesTest`, `ScraperSourcesTest`, `DynamicHtmlSourceTest`, and `AnimeRepositoryTest` executed and passed 100%.
   - Command: `.\gradlew.bat assembleDebug`
     - Result: `BUILD SUCCESSFUL in 12s`
     - Verified clean compilation, DEX generation, and packaging of `app-debug.apk`.

---

## 2. Logic Chain
1. **Decoupled Architecture**: Video hosting services (StreamWish, MegaCloud, Doodstream, Vidplay, Mp4Upload) change their embed wrappers independently of anime catalog sites. Decoupling them behind `VideoExtractor` and `VideoExtractorRegistry` enables all sources (Aniwatch, AnimeFLV, Anikoto, ReAnime, Custom DSL) to share the same extraction logic and header spoofing without code duplication.
2. **16:9 Banner Priority for Android TV (R1 & R2)**: Android TV leanback interfaces require high-definition 16:9 banners for the hero banner carousel. `AniListCatalogSource` extracts AniList GraphQL `bannerImage` and prioritizes it over vertical 2:3 posters, ensuring that the TV UI receives landscape artwork.
3. **Multi-Source Failover Guarantee**: Streaming providers can encounter Cloudflare challenges, geo-blocking, or rotated domains. In `DefaultAnimeRepository.getEpisodeStreams`, sources are queried in priority order (preferred source -> Miruro -> Aniwatch -> AnimeFLV -> others). If a provider throws an exception or returns empty results, the repository automatically cascades to the next active provider. In the worst-case scenario where all external networks are blocked, emergency fallback streams are provided to prevent player crashes.
4. **Declarative Custom Source DSL**: `DynamicHtmlSource` supports the JSON selector model specified in Survey 2 (`search`, `episodes`, `streams` with `itemSelector`, `@attr`, and `idRegex`). This allows users on Android TV to add community scrapers dynamically via `AppStorage` without app re-compilation.

---

## 3. Caveats
1. **Live Network Variability**: Third-party anime streaming websites frequently rotate mirror domains and CDN parameters. While all scrapers implement genuine parsing logic and fallbacks, external site domain shifts in production will require updating domain URLs in `AppStorage` or through the custom source editor.
2. **AES Decryption Dependencies**: `KaaSource` uses `AES/CBC/PKCS7Padding` with automatic fallback to `AES/CBC/PKCS5Padding` to ensure compatibility across all Android API levels (26 to 34).

---

## 4. Conclusion
Milestone M2 is complete. All 7 target anime source scrapers (Aniwatch, Anikoto, Miruro, ReAnime, KAA, WCO, AnimeFLV), 7 video hosting extractors (MegaCloud, StreamWish, Doodstream, Vidplay, Mp4Upload, DirectStream, Registry), 2 catalog providers (AniList GraphQL and Jikan v4 fallback), the declarative `DynamicHtmlSource` engine, and `DefaultAnimeRepository` with multi-source failover and latency health checks have been implemented and verified with 100% passing unit tests and a successful debug APK build.

---

## 5. Verification Method
To independently verify the implementation:

1. **Run M2 Unit Test Suite**:
   ```powershell
   .\gradlew.bat testDebugUnitTest --tests "com.darknet.animetv.source.*"
   ```
   *Expected outcome*: All tests in `VideoExtractorsTest`, `CatalogSourcesTest`, `ScraperSourcesTest`, `DynamicHtmlSourceTest`, and `AnimeRepositoryTest` execute and pass cleanly.

2. **Run Full Debug Build**:
   ```powershell
   .\gradlew.bat assembleDebug
   ```
   *Expected outcome*: `BUILD SUCCESSFUL`, producing `app/build/outputs/apk/debug/app-debug.apk`.

3. **Inspect Implementation Files**:
   - `app/src/main/java/com/darknet/animetv/data/source/AnimeSource.kt`
   - `app/src/main/java/com/darknet/animetv/data/source/extractor/`
   - `app/src/main/java/com/darknet/animetv/data/source/AniListCatalogSource.kt`
   - `app/src/main/java/com/darknet/animetv/data/source/AniwatchSource.kt`
   - `app/src/main/java/com/darknet/animetv/data/source/AnikotoSource.kt`
   - `app/src/main/java/com/darknet/animetv/data/source/MiruroSource.kt`
   - `app/src/main/java/com/darknet/animetv/data/source/ReAnimeSource.kt`
   - `app/src/main/java/com/darknet/animetv/data/source/KaaSource.kt`
   - `app/src/main/java/com/darknet/animetv/data/source/WcoSource.kt`
   - `app/src/main/java/com/darknet/animetv/data/source/AnimeFlvSource.kt`
   - `app/src/main/java/com/darknet/animetv/data/source/DynamicHtmlSource.kt`
   - `app/src/main/java/com/darknet/animetv/data/repository/AnimeRepository.kt`
