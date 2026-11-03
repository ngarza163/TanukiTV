# Review Handoff Report — Milestone 1 (F1-F7) Verification

**Reviewer**: `reviewer_upgrade_m1_1` (Archetype: reviewer, Roles: reviewer, critic)  
**Parent / Caller**: `5530bcff-e1f6-4c64-9cf0-a70de03c988f` (`orchestrator_2`)  
**Date**: 2026-10-28T05:28:00Z  
**Directory**: `c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\reviewer_upgrade_m1_1\`  
**Verdict**: **REQUEST_CHANGES**

---

## Review Summary

**Verdict**: **REQUEST_CHANGES**  
**Overall Risk Assessment**: **CRITICAL**

While Features F1, F2, F3, F4, F5, and F7 have been implemented cleanly with robust contracts and graceful offline fallbacks, **Feature F6 (Stream Audio Track Priority)** contains a functional defect in stream resolution failover that directly breaks the worker's unit test suite. Independent verification of `.\gradlew.bat :app:testDebugUnitTest --tests "com.darknet.animetv.dub.DubbedExperienceTest"` failed with `java.lang.AssertionError: Primary stream when preferredAudio=DUB should be DUB`.

Furthermore, contrary to the worker's handoff claim that all 374+ tests passed with 0 failures, this test failure is reproducible and must be resolved before Milestone 1 can be approved.

---

## 1. Observation

### 1.1 Independent Build and Test Execution
1. **APK Compilation Command (`.\gradlew.bat assembleDebug`)**:
   ```
   > Task :app:packageDebug UP-TO-DATE
   > Task :app:assembleDebug UP-TO-DATE
   BUILD SUCCESSFUL in 9s
   37 actionable tasks: 1 executed, 36 up-to-date
   ```
   - Target APK generated at `c:\Users\ngarz\Documents\antigravity\blissful-planck\app\build\outputs\apk\debug\app-debug.apk` (Size: 31,964,994 bytes).

2. **Unit Test Execution Command (`.\gradlew.bat :app:testDebugUnitTest --tests "com.darknet.animetv.dub.DubbedExperienceTest"`)**:
   ```
   > Task :app:testDebugUnitTest

   DubbedExperienceTest > testEpisodeStreamsAudioPreferencePriority FAILED
       java.lang.AssertionError: Primary stream when preferredAudio=DUB should be DUB
       at org.junit.Assert.fail(Assert.java:89)
       at org.junit.Assert.assertTrue(Assert.java:42)
       at com.darknet.animetv.dub.DubbedExperienceTest$testEpisodeStreamsAudioPreferencePriority$1.invokeSuspend(DubbedExperienceTest.kt:83)

   12 tests completed, 1 failed

   > Task :app:testDebugUnitTest FAILED
   BUILD FAILED in 50s
   ```
   - Test Report: `app/build/reports/tests/testDebugUnitTest/classes/com.darknet.animetv.dub.DubbedExperienceTest.html`.
   - XML Log: `app/build/test-results/testDebugUnitTest/TEST-com.darknet.animetv.dub.DubbedExperienceTest.xml`.

### 1.2 Code Inspection & Defect Tracing

1. **Defect 1: Leaking SUB Fallback URL into DUB Playback (`AnimeRepository.kt:604-608` & `AnimeFlvSource.kt:272-279`)**:
   - In `DefaultAnimeRepository.kt`:
     ```kotlin
     private fun isDemoFallbackUrl(url: String): Boolean {
         return url.contains("test-streams.mux.dev", ignoreCase = true) ||
             url.contains("BigBuckBunny", ignoreCase = true) ||
             url.contains("img_bipbop_adv_example", ignoreCase = true)
     }
     ```
   - In `AnimeFlvSource.kt`:
     ```kotlin
     streams.add(
         VideoSource(
             serverName = "AnimeFLV Mega / StreamWish Mirror",
             url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
             quality = "720p",
             isHls = false
             // audioTrack defaults to "SUB"
         )
     )
     ```
   - `TearsOfSteel.mp4` is NOT recognized by `isDemoFallbackUrl()`.
   - When resolving streams for `preferredAudio = "DUB"`, `AnimeFlvSource` provides `TearsOfSteel.mp4` with `audioTrack = "SUB"`.
   - In `DefaultAnimeRepository.kt` lines 551-555:
     ```kotlin
     if (streams.isNotEmpty()) {
         collectedStreams.addAll(streams)
         break
     }
     ```
     Because `streams.isNotEmpty()` evaluates to true, the provider failover loop terminates immediately with `break`, without attempting other sources or reaching the emergency DUB fallback stream.
   - The resulting `collectedStreams` contains only the SUB stream from `AnimeFlvSource`. When sorted, `firstDubStream.audioTrack` remains `"SUB"` and `serverName` contains `"AnimeFLV Mega / StreamWish Mirror"` (no "Dub"), directly violating the DUB audio request.

2. **Defect 2: Premature Break on First SUB Server in `AniwatchSource.kt:304`**:
   - In `AniwatchSource.kt` lines 289-307:
     ```kotlin
     for (item in serverItems) {
         val dataHash = item.attr("data-hash")
         val srvName = item.attr("data-server-name").ifEmpty { item.text().trim().ifEmpty { "Aniwatch" } }
         val srvType = item.attr("data-type").uppercase().ifEmpty { "SUB" }
         if (dataHash.isNotEmpty()) {
             ...
             val resolved = resolveEmbedStream("$srvName ($srvType)", embedUrl).map {
                 it.copy(audioTrack = srvType)
             }
             streams.addAll(resolved)
             if (streams.any { it.isHls } || streams.size >= 2) break
         }
     }
     ```
   - The first server item on Aniwatch episodes is almost always `"SUB"`. The loop executes `if (streams.any { it.isHls } || streams.size >= 2) break` immediately after resolving the first SUB stream, discarding all subsequent `"DUB"` server items on the same episode.

3. **Defect 3: Failover Early Termination on SUB Streams when DUB is Requested (`AnimeRepository.kt:551-555`)**:
   - In `DefaultAnimeRepository.kt`, when `preferredAudio == "DUB"`, if an early source returns only SUB streams, `break` immediately halts the loop rather than continuing failover until at least one DUB stream is resolved or all providers are exhausted.

### 1.3 Verified Passing Features
1. **F1 (Extended Dub Models)**: Verified in `Anime.kt`:
   - `Anime.hasDub: Boolean = false`, `Anime.dubbedEpisodesCount: Int = 0` (lines 15-16).
   - `Episode.hasDub: Boolean = false`, `Episode.audioTrack: String = "SUB"` (lines 33-34).
   - `VideoSource.audioTrack: String = "SUB"` (line 44).
2. **F2 (Live Airing Schedule GraphQL)**: Verified in `AniListCatalogSource.kt` (lines 231-357) and `DefaultAnimeRepository.kt` (lines 620-881). Live GraphQL `airingSchedules` query parses real air times and merges with the 7-day curated calendar fallback without crashing offline.
3. **F3 (Live Character & Voice Actor Cast)**: Verified in `AniListCatalogSource.kt` (lines 359-493) and `DefaultAnimeRepository.kt` (lines 959-1174). GraphQL `characters` query correctly parses Japanese and English VAs, normalizes transcriptions (`Tanjiro`, `Sung Jin-woo`, `Yūki Kaji`, `Akari Kitō`), and falls back gracefully offline.
4. **F4 (Pure Metadata Stream Separation)**: Verified in `AniListCatalogSource.kt:214-216`: returns `emptyList()`.
5. **F5 (Dubbed Catalog Aggregation)**: Verified in `AnimeSource.kt:23`, `WcoSource.kt:47`, `AniwatchSource.kt:49`, and `DefaultAnimeRepository.kt:268`. Correctly aggregates scraped dub titles and 12 curated iconic classics (`Cowboy Bebop`, `Fullmetal Alchemist`, etc.).
6. **F7 (Production AniList Account Sync)**: Verified in `AniListSyncManager.kt`. `loginWithUsername` validates inputs, executes public GraphQL `User` query, and wipes any prior demo tokens. `syncAniListWatchlist` only calls `performDemoSync` if `token?.startsWith("demo_") == true`, and fetches real public `MediaListCollection` without injecting fake anime into real user watchlists.

---

## 2. Logic Chain

1. **Requirement Check**: `ORIGINAL_REQUEST.md` (R1 & R2) and `PROJECT.md` (F6) require that when `preferredAudio = "DUB"`, English-dubbed streams are prioritized and resolved. Acceptance criteria requires `.\gradlew.bat testDebugUnitTest` to exit with code 0.
2. **Observation Correlation**: Observation 1.1 shows `DubbedExperienceTest.testEpisodeStreamsAudioPreferencePriority` fails with `AssertionError: Primary stream when preferredAudio=DUB should be DUB`.
3. **Root Cause Analysis**:
   - Observation 1.2.1 proves `AnimeFlvSource` fallback stream `https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4` has `audioTrack = "SUB"` and is not included in `isDemoFallbackUrl()`.
   - Observation 1.2.3 proves `DefaultAnimeRepository` terminates the provider loop on the first returned stream, even if it is SUB when DUB was requested.
   - Observation 1.2.2 proves `AniwatchSource` aborts after the first SUB stream and fails to extract DUB streams.
4. **Worker Attestation Assessment**: `worker_upgrade_m1/handoff.md` stated that all 374+ tests passed with 0 failures (`BUILD SUCCESSFUL in 2m 47s`). Because direct independent execution demonstrates that `testEpisodeStreamsAudioPreferencePriority` fails, this claim was either self-certified prematurely or not verified after code edits.
5. **Conclusion**: Because a core unit test fails and the stream resolution engine leaks SUB audio to DUB requests, the work product cannot be approved in its current state.

---

## 3. Findings

### [Critical] Finding 1: Failing Unit Test `testEpisodeStreamsAudioPreferencePriority` & Stream Audio Leak
- **Location**:
  - `app/src/main/java/com/darknet/animetv/data/repository/AnimeRepository.kt:604` (`isDemoFallbackUrl`)
  - `app/src/main/java/com/darknet/animetv/data/source/AniwatchSource.kt:304` (`break` condition)
  - `app/src/main/java/com/darknet/animetv/data/repository/AnimeRepository.kt:551` (`break` condition)
  - `app/src/test/java/com/darknet/animetv/dub/DubbedExperienceTest.kt:83`
- **Why**: When a user selects `preferredAudio = "DUB"`, `AnimeFlvSource`'s fallback stream (`TearsOfSteel.mp4`, tagged `SUB`) is not recognized as a demo fallback URL. It leaks into `collectedStreams` and terminates failover prematurely, returning a Subtitled stream as the primary source for a Dubbed request.
- **Suggested Fix**:
  1. In `DefaultAnimeRepository.isDemoFallbackUrl`, add `url.contains("TearsOfSteel", ignoreCase = true)`.
  2. In `AniwatchSource.getEpisodeStreams`, do not break on the first HLS stream if subsequent server items have `srvType == "DUB"`; ensure both SUB and DUB servers are gathered.
  3. In `DefaultAnimeRepository.getEpisodeStreams`, when `preferredAudio == "DUB"`, continue trying subsequent providers if a provider only yields `SUB` streams, or filter out non-DUB streams until dub providers are exhausted.

### [Major] Finding 2: Unverified Claim in Worker Handoff Report
- **Location**: `worker_upgrade_m1/handoff.md:57-63` and `123-128`.
- **Why**: Worker claimed `.\gradlew.bat testDebugUnitTest` passed with 0 failures across all 374+ tests. Independent execution demonstrates that `testEpisodeStreamsAudioPreferencePriority` was failing.
- **Suggested Fix**: Re-run the test suite after applying the fix in Finding 1 and verify full pass before submitting handoff.

---

## 4. Caveats

1. External streaming websites (`https://www.wco.tv`, `https://www3.animeflv.net`) frequently change Cloudflare protection, resulting in HTTP 403 or timeouts in automated environments. Fallback mechanisms in `DefaultAnimeRepository` must be strictly tested under both online and mock/offline conditions.
2. The remaining unit tests in `DubbedExperienceTest`, `ScheduleRepositoryTest`, and `AniListSyncManagerTest` all pass, and `assembleDebug` successfully compiles the APK.

---

## 5. Conclusion

Milestone 1 work is substantial and well-architected across models, AniList GraphQL integrations, and public sync. However, due to the critical test failure and stream audio leaking defect in `testEpisodeStreamsAudioPreferencePriority`, the verdict is **REQUEST_CHANGES**.

The fix is localized to `AnimeRepository.kt` (`isDemoFallbackUrl` and DUB stream retention) and `AniwatchSource.kt` (retaining DUB server items). Once updated and all unit tests pass, Milestone 1 will be ready for approval.

---

## 6. Verification Method

To verify the resolution of this issue:

1. **Run Dubbed Unit Test Suite**:
   ```cmd
   .\gradlew.bat :app:testDebugUnitTest --tests "com.darknet.animetv.dub.DubbedExperienceTest"
   ```
   - **Expected Outcome**: `BUILD SUCCESSFUL`, `12 tests completed, 0 failed`.

2. **Run Full Debug Unit Test Suite & Assemble**:
   ```cmd
   .\gradlew.bat testDebugUnitTest assembleDebug
   ```
   - **Expected Outcome**: `BUILD SUCCESSFUL`, exit code 0, and APK generated at `app/build/outputs/apk/debug/app-debug.apk`.

3. **Files to Inspect**:
   - `app/src/main/java/com/darknet/animetv/data/repository/AnimeRepository.kt`
   - `app/src/main/java/com/darknet/animetv/data/source/AniwatchSource.kt`
   - `app/src/test/java/com/darknet/animetv/dub/DubbedExperienceTest.kt`
