# Handoff Report — challenger_m2_1: Scraper & Extractor Stress Challenger

## 1. Observation
1. **Target Implementation Files Inspected**:
   - `app/src/main/java/com/darknet/animetv/data/source/extractor/StreamWishExtractor.kt` (lines 85-181): Dean Edwards unpacker algorithm parsing `eval(function(p,a,c,k,e,d)...)` with `Matcher.quoteReplacement` and base-N radix decoding.
   - `app/src/main/java/com/darknet/animetv/data/source/extractor/MegaCloudExtractor.kt` (lines 40-92, 94-138): Stream ID extraction, `/embed-2/ajax/e-1/getSources` parsing, subtitle tracks filtering, and offline HLS failover fallback.
   - `app/src/main/java/com/darknet/animetv/data/source/extractor/VidplayExtractor.kt` (lines 40-60, 111-155): Video ID regex parsing, `mediainfo` JSON decoding, and direct regex master `.m3u8` HTML parser.
   - `app/src/main/java/com/darknet/animetv/data/source/extractor/DoodstreamExtractor.kt` (lines 54-85, 109-116): `/pass_md5/` token path extraction and direct signed stream URL generation.
   - `app/src/main/java/com/darknet/animetv/data/source/KaaSource.kt` (lines 160-174, 236-266): `decryptAesCbc` implementing `AES/CBC/PKCS7Padding` with fallback to `AES/CBC/PKCS5Padding` and hex-to-byte IV translation.
   - `app/src/main/java/com/darknet/animetv/data/source/AnimeFlvSource.kt` (lines 188-212, 318-344): Jsoup DOM parsing, episode list regex scraping (`var episodes = [...]`), and `var videos = {...};` script JSON extraction.
   - `app/src/main/java/com/darknet/animetv/data/source/WcoSource.kt` (lines 190-216, 267-285): Base64 embedded stream extraction (`atob(...)` and `b64(...)`) and iframe extraction.
   - `app/src/main/java/com/darknet/animetv/data/source/AnikotoSource.kt` (lines 64-124, 142-152): DOM card extraction, episode ID resolution, and iframe embedding.
   - `app/src/main/java/com/darknet/animetv/data/source/ReAnimeSource.kt` (lines 64-124, 133-154): AJAX server endpoint `/ajax/episode/servers?episodeId=...` and iframe extraction.
   - `app/src/main/java/com/darknet/animetv/data/source/DynamicHtmlSource.kt` (lines 75-113, 300-340): Declarative JSON DSL parser, composite attribute selector evaluator (`@attr`), and multi-value regex extractor.
   - `app/src/main/java/com/darknet/animetv/data/source/AniListCatalogSource.kt` (lines 261-304): `parseMediaNode` prioritizing 16:9 `bannerImage` over vertical cover art with fallback to `posterUrl` when banner is null.
   - `app/src/main/java/com/darknet/animetv/data/repository/AnimeRepository.kt` (lines 235-275): `DefaultAnimeRepository.getEpisodeStreams` multi-source failover priority pipeline with emergency fallback stream.

2. **Empirical Stress Test Implementation**:
   - File created: `app/src/test/java/com/darknet/animetv/source/ExtractorStressChallengerTest.kt`
   - Test count: 23 comprehensive adversarial tests spanning:
     - Dean Edwards JS unpacking with extreme radices (1, 37, 100), truncated function wrappers, regex metacharacters (`$`, `\1`, `&`, quotes) in dictionary tokens, and dictionary index out-of-bounds underflow/overflow.
     - MegaCloud stream ID edge cases, corrupted JSON (syntax errors, primitive sources, missing track files), and offline HLS stream failover.
     - Vidplay video ID edge cases, unquoted key JSON, and HTML stream regex fallback.
     - Doodstream unclosed quotes, malformed MD5 paths, and direct signed URL creation.
     - Mp4Upload malformed player scripts and complex whitespace regex matching.
     - Kaa AES-CBC decryption resilience against invalid key lengths (5 bytes, empty string), corrupted odd-length hex IVs, non-Base64 ciphertexts, short ciphertexts (<16 bytes), and PKCS7 padding corruption via mismatched secret keys (`BadPaddingException` handled cleanly).
     - AnimeFLV malformed `var videos` JSON syntax and missing episode scripts.
     - WCO invalid Base64 in `atob()`, non-media strings, and trimmed whitespace URLs.
     - DynamicHtmlSource malformed CSS selectors and invalid unclosed regex patterns (`[unclosed`).
     - AniList GraphQL missing `bannerImage` fallback to cover art (verifying Android TV 16:9 banner requirement R1/R2) and all-null title/score/genre handling.
     - DefaultAnimeRepository total provider failure resilience (all registered upstream sources throwing `IOException` and `RuntimeException`).

3. **Execution Results**:
   - Command: `.\gradlew.bat testDebugUnitTest --tests "com.darknet.animetv.source.*" --rerun-tasks`
     - Result: `BUILD SUCCESSFUL in 45s (23 actionable tasks: 23 executed)`
     - Total tests executed: 66 tests across 7 test classes.
     - Failures: 0. Ignored: 0. Success rate: **100%**.
     - Test class breakdown:
       - `ExtractorStressChallengerTest`: 23 tests, 0 failures, 100% pass rate.
       - `RepositoryStressChallengerTest`: 17 tests, 0 failures, 100% pass rate.
       - `VideoExtractorsTest`: 8 tests, 0 failures, 100% pass rate.
       - `ScraperSourcesTest`: 7 tests, 0 failures, 100% pass rate.
       - `AnimeRepositoryTest`: 5 tests, 0 failures, 100% pass rate.
       - `CatalogSourcesTest`: 3 tests, 0 failures, 100% pass rate.
       - `DynamicHtmlSourceTest`: 3 tests, 0 failures, 100% pass rate.
   - Command: `.\gradlew.bat assembleDebug`
     - Result: `BUILD SUCCESSFUL in 8s (35 actionable tasks: 1 executed, 34 up-to-date)`
     - Generated APK: `app/build/outputs/apk/debug/app-debug.apk`.

---

## 2. Logic Chain
1. **Adversarial Input Hardening**:
   - In real-world web scraping, third-party video hosts frequently rotate obfuscation scripts, return Cloudflare challenge HTML instead of JSON, or serve empty/malformed tokens.
   - Observation 1 & 2 show that `StreamWishExtractor`, `MegaCloudExtractor`, `VidplayExtractor`, and `KaaSource` wrap all network calls, JSON parsing, regex group extractions, and AES decryptions in defensive `try/catch` blocks.
   - When fed intentionally corrupted Base64 or wrong encryption keys, `KaaSource.decryptAesCbc` caught `InvalidKeyException`, `BadPaddingException`, `IllegalBlockSizeException`, and `IllegalArgumentException`, safely returning `""` without crashing the JVM runtime.
   - When fed non-JSON HTML (e.g., Cloudflare 502 Bad Gateway), `MegaCloudExtractor` and `VidplayExtractor` cleanly caught `JsonSyntaxException` and fell back to constructed HLS stream URLs.

2. **16:9 Landscape Banner Verification (Android TV Requirement R1/R2)**:
   - Android TV Leanback hero carousels require 16:9 widescreen banners to avoid distorted or pixelated posters.
   - `testAniListMissingBannerFallbackToCover` verified that when `bannerImage` is `null` or missing, `AniListCatalogSource.parseMediaNode` automatically sets `backdropUrl` to `posterUrl` (`coverImage.extraLarge`), ensuring that the TV UI always receives non-null artwork without throwing `NullPointerException`.

3. **Upstream Network Failover Guarantee**:
   - `testDefaultAnimeRepositoryAllSourcesFailingResilience` proved that when all upstream scrapers fail simultaneously due to network timeouts, 403 Cloudflare blocks, or host unreachability, `DefaultAnimeRepository` does not rethrow unhandled exceptions; it supplies a reliable fallback stream to guarantee that ExoPlayer initialization never fails with a crash.

---

## 3. Caveats
1. **Robolectric Android Environment**: Tests utilize Robolectric to shadow `android.util.Base64` and SQLite storage during JVM unit testing.
2. **Dynamic Live Web Shifting**: Third-party domains (e.g. `kaa.lt`, `aniwatchtv.ro`, `streamwish.to`) may update Cloudflare Turnstile captchas or rotate top-level domains in live production. The failover architecture and declarative JSON DSL mitigate this by allowing runtime domain updates in `AppStorage`.

---

## 4. Conclusion
**VERDICT: APPROVE**

Milestone M2 (Scraper Engine, Video Extractors, AniList Catalog, and Repository Failover) has successfully passed all empirical adversarial challenges:
- 23 dedicated stress test cases added in `app/src/test/java/com/darknet/animetv/source/ExtractorStressChallengerTest.kt`.
- 100% pass rate across 66 unit tests in `com.darknet.animetv.source.*`.
- Verified zero crashes, zero uncaught exceptions, and clean fallback mechanisms under malformed JS unpacker payloads, corrupted AES keys/padding, invalid JSON, and complete upstream source downtime.
- Verified successful APK compilation via `.\gradlew.bat assembleDebug`.

Milestone M2 is hardened, robust, and approved to proceed to Milestone M3 (Compose TV UI & Navigation).

---

## 5. Verification Method
To independently verify the empirical challenger test results:

1. **Execute All Source & Challenger Tests**:
   ```powershell
   .\gradlew.bat testDebugUnitTest --tests "com.darknet.animetv.source.*" --rerun-tasks
   ```
   *Expected Result*: `BUILD SUCCESSFUL`, 66 passed tests (0 failed, 0 ignored).

2. **Inspect Test Report**:
   - Open `app/build/reports/tests/testDebugUnitTest/index.html` in a web browser.
   - Verify `ExtractorStressChallengerTest` reports 23/23 successful tests.

3. **Verify Debug APK Assembly**:
   ```powershell
   .\gradlew.bat assembleDebug
   ```
   *Expected Result*: `BUILD SUCCESSFUL`, producing `app/build/outputs/apk/debug/app-debug.apk`.
