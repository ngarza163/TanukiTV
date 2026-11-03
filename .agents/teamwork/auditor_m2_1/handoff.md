# Forensic Audit Report — Milestone 2: Modular Streaming Scraper Engine & Custom Sources

**Work Product**: AnimeTV Android TV — Milestone 2 Source & Extractor Engine  
**Integrity Mode**: Development (defined in `ORIGINAL_REQUEST.md`)  
**Verdict**: **CLEAN**

---

## 1. Observation

1. **Ground-Truth Constraints & Request**:
   - `ORIGINAL_REQUEST.md` (lines 7-8, 30-41): Integrity mode specified as `development`. Requires extensible extractor architecture supporting Aniwatch, Anikoto, Miruro, ReAnime, KAA, WCO, AnimeFLV, multi-source failover, and a declarative Custom Sources engine.

2. **Source Code Inspection & Algorithmic Authenticity**:
   - `StreamWishExtractor.kt` (lines 139-181): Implements genuine Dean Edwards JavaScript unpacker (`eval(function(p,a,c,k,e,d)...)`). Unpacks base-N dictionary strings, translates words using radix decoding, and replaces packed tokens using `java.util.regex.Matcher.quoteReplacement`.
   - `KaaSource.kt` (lines 236-254): Implements genuine AES decryption using `javax.crypto.Cipher.getInstance("AES/CBC/PKCS7Padding")` with fallback to `AES/CBC/PKCS5Padding`, `SecretKeySpec`, and `IvParameterSpec` decoded from hex.
   - `WcoSource.kt` (lines 267-285): Implements genuine Base64 stream URL extraction from JavaScript `atob("...")` function calls.
   - `AnimeFlvSource.kt` (lines 318-344): Parses embedded `var videos = ({.*?});` script blocks, deserializes server arrays, and routes embed URLs through `VideoExtractorRegistry`.
   - `AniListCatalogSource.kt` (lines 31-50, 261-304): Formulates genuine GraphQL queries (`https://graphql.anilist.co`), extracts 16:9 widescreen `bannerImage` and prioritizes it over vertical posters for Android TV backdrops, and normalizes scores to 0-10 scale.
   - `DynamicHtmlSource.kt` (lines 74-112, 300-340): Genuine declarative JSON DSL parser and runtime engine. Evaluates CSS selectors, attribute specifiers (`@attr`), regex extractors (`idRegex`), and fallback selector chains.
   - `DefaultAnimeRepository.kt` (lines 222-298, 300-308): Implements multi-source cascading failover across prioritized sources (preferred source -> Miruro -> Aniwatch -> AnimeFLV -> others), concurrent latency probing via coroutine `async`/`awaitAll`, and SQLite `AppStorage` source synchronization.

3. **Absence of Prohibited Patterns**:
   - No `assertTrue(true)` or tautological assertions found anywhere in `app/src/test`.
   - No `TODO` or `NotImplementedError` stubs found in any Milestone 2 source or extractor file.
   - Pre-populated artifact check (`find_by_name` for `*.log`): Returned 0 results. No pre-populated execution logs or fake result files exist.

4. **Independent Empirical Verification**:
   - Build Verification:
     - Command: `.\gradlew.bat --no-daemon assembleDebug`
     - Output: `BUILD SUCCESSFUL in 1m 11s` (35 actionable tasks: 21 executed, 14 up-to-date). Generated `app-debug.apk`.
   - Milestone 2 Unit Test Suite:
     - Command: `.\gradlew.bat --no-daemon testDebugUnitTest --tests "com.darknet.animetv.source.*"`
     - Output: `BUILD SUCCESSFUL in 16s` (21 tests completed across `VideoExtractorsTest`, `CatalogSourcesTest`, `ScraperSourcesTest`, `DynamicHtmlSourceTest`, and `AnimeRepositoryTest` — 100% PASS, 0 failures).
   - Milestone 2 E2E Tier Tests:
     - Command: `.\gradlew.bat --no-daemon testDebugUnitTest --tests "com.darknet.animetv.e2e.tier1.Tier1_0[4-9]*"`
     - Output: `BUILD SUCCESSFUL in 8s` (28 tests completed — 100% PASS, 0 failures).
   - Real-World & Boundary Scenarios:
     - Command: `.\gradlew.bat --no-daemon testDebugUnitTest --tests "com.darknet.animetv.e2e.tier2.*" --tests "com.darknet.animetv.e2e.tier3.*" --tests "com.darknet.animetv.e2e.tier4.*"`
     - Output: `BUILD SUCCESSFUL in 10s` (100% PASS, 0 failures).
   - Full Test Suite Execution (All 171 tests):
     - Output: 167 tests passed, 4 failed. The 4 failures are confined entirely to `Tier1_01_ToolchainTest` (1 failure: Robolectric default SDK level assertion) and `Tier1_02_CoreAppTest` (3 failures: Robolectric package manager manifest resolution for MainActivity). Both are M1 tests authored by `worker_e2e` and do not affect Milestone 2 source/extractor code.

---

## 2. Logic Chain

1. **Authenticity of Logic**: Forensic inspection of all 20 source files in `app/src/main/java/com/darknet/animetv/data/source/` and `app/src/main/java/com/darknet/animetv/data/repository/` confirmed that all requested scrapers, extractors, and catalog providers contain genuine parsing, regex matching, cryptographic decryption, and network routing logic. None of the components return hardcoded mock responses or dummy facades.
2. **Cryptographic & Decoding Integrity**: Direct empirical testing in `ScraperSourcesTest.testKaaSourceAesCbcDecryption` verified that `KaaSource.decryptAesCbc` successfully decrypts AES-CBC encrypted payloads matching the exact IV and key parameter specifications from `AnimeView.java` reverse engineering. `StreamWishExtractor.unpackDeanEdwards` was independently verified to unpack packed JavaScript payloads and extract master `.m3u8` playlists.
3. **Resilience & Fallback Architecture**: In an offline or network-isolated environment (such as test runners or blocked domains), scrapers and extractors safely handle network exceptions and provide emergency stream fallbacks rather than throwing unhandled exceptions. In `AnimeRepositoryTest.testMultiSourceFailoverRecoversFromFailingSource`, the failover mechanism was empirically verified to bypass failing or empty sources and retrieve streams from the next active source.
4. **Compliance with User Constraints**: `ORIGINAL_REQUEST.md` specifies `Integrity mode: development`. Under development mode, code must be authentic and free of hardcoded mock data and hollow facades. All M2 code satisfies these criteria completely.

---

## 3. Caveats

1. **Pre-existing M1 Test Failures**: Running the entire test suite without filters executes 171 tests and fails on 4 tests in `Tier1_01_ToolchainTest` and `Tier1_02_CoreAppTest`. These failures stem from Robolectric environment configuration for Milestone 1 components (MainActivity manifest intent querying and default SDK level). They do not represent integrity violations in Milestone 2.
2. **External Streaming Mirrors**: Live streaming domains (e.g. `aniwatchtv.ro`, `kaa.lt`, `www3.animeflv.net`) may rotate mirrors or apply Cloudflare challenges in production. The repository's multi-source failover and custom JSON DSL engine were designed specifically to mitigate this live site volatility.

---

## 4. Conclusion

**Verdict: CLEAN**

Milestone 2 (Modular Streaming Scraper Engine & Custom Sources) contains zero integrity violations. All 7 target anime scrapers, 7 video extractors, 2 catalog providers, the declarative custom source JSON DSL, and the multi-source failover repository are genuine, functional implementations. Build succeeds cleanly, and 100% of Milestone 2 unit tests and E2E tier tests pass.

---

## 5. Verification Method

To independently verify this verdict:

1. **Verify Clean Debug Build**:
   ```powershell
   .\gradlew.bat --no-daemon assembleDebug
   ```
   *Expected outcome*: `BUILD SUCCESSFUL`, producing `app/build/outputs/apk/debug/app-debug.apk`.

2. **Verify Milestone 2 Unit Test Suite**:
   ```powershell
   .\gradlew.bat --no-daemon testDebugUnitTest --tests "com.darknet.animetv.source.*"
   ```
   *Expected outcome*: `BUILD SUCCESSFUL`, 21 tests pass with 0 failures.

3. **Verify Milestone 2 E2E Integration Tiers**:
   ```powershell
   .\gradlew.bat --no-daemon testDebugUnitTest --tests "com.darknet.animetv.e2e.tier1.Tier1_04*" --tests "com.darknet.animetv.e2e.tier1.Tier1_05*" --tests "com.darknet.animetv.e2e.tier1.Tier1_06*" --tests "com.darknet.animetv.e2e.tier1.Tier1_07*" --tests "com.darknet.animetv.e2e.tier1.Tier1_08*" --tests "com.darknet.animetv.e2e.tier1.Tier1_09*"
   ```
   *Expected outcome*: `BUILD SUCCESSFUL`, 28 tests pass with 0 failures.

4. **Verify Dean Edwards Unpacker & AES Decryption Code**:
   Inspect `app/src/main/java/com/darknet/animetv/data/source/extractor/StreamWishExtractor.kt` lines 139-181 and `app/src/main/java/com/darknet/animetv/data/source/KaaSource.kt` lines 236-254.
