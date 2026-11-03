# Milestone 1 Remediation & Test Robustness Strategy Report

**Agent**: `explorer_upgrade_m1_fix_3` (Archetype: explorer, Roles: investigation, synthesis)  
**Parent**: `5530bcff-e1f6-4c64-9cf0-a70de03c988f` (`orchestrator_2`)  
**Date**: 2026-10-28T05:31:00Z  
**Working Directory**: `c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\explorer_upgrade_m1_fix_3`  
**Target Milestone**: Milestone 1 (F1-F7 Remediation)  

---

## Executive Summary
This investigation confirms the **INTEGRITY VIOLATION** reported by `auditor_upgrade_m1` and `reviewer_upgrade_m1_2`: `DubbedExperienceTest > testEpisodeStreamsAudioPreferencePriority` fails empirically with `AssertionError: Primary stream when preferredAudio=DUB should be DUB`.

The failure stems from a confluence of four distinct defects:
1. **Demo Stream Leakage in Fallback Filter**: `DefaultAnimeRepository.isDemoFallbackUrl()` fails to filter `TearsOfSteel.mp4` and Google Cloud sample bucket URLs (`commondatastorage.googleapis.com/gtv-videos-bucket/sample/`), allowing demo streams to leak through as production streams.
2. **Catalog Metadata Stream Contamination**: `JikanSource.kt` (a pure AniList/Jikan catalog metadata source) returns hardcoded demo `VideoSource` entries in `getEpisodeStreams()`, violating the pure metadata separation required by R1.
3. **Improper Catalog Inclusion in Streaming Prioritization**: `DefaultAnimeRepository.getEpisodeStreams()` appends catalog sources (`AniListCatalogSource`, `JikanSource`) to `prioritizedSources` as a "last resort", querying catalog metadata providers for video streams.
4. **Premature Failover Termination for DUB Requests**: In `DefaultAnimeRepository.getEpisodeStreams()`, the failover loop executes `break` immediately when `streams.isNotEmpty()`. When `preferredAudio == "DUB"`, a source returning exclusively `SUB` streams prematurely halts failover, preventing the repository from querying subsequent DUB sources or falling back to the emergency DUB stream.

Below is the complete evidence chain, root-cause logic, and exact drop-in fix strategy for all affected source and test files.

---

## 1. Observation

### 1.1 Empirical Test Failure Reproduction
Direct execution of the unit test suite reproduces the exact failure observed by the auditor and reviewer:

- **Command**:
  ```powershell
  .\gradlew.bat testDebugUnitTest --tests "com.darknet.animetv.dub.DubbedExperienceTest" "-Dkotlin.incremental=false"
  ```
- **Verbatim Tool Output**:
  ```
  > Task :app:testDebugUnitTest

  DubbedExperienceTest > testEpisodeStreamsAudioPreferencePriority FAILED
      java.lang.AssertionError at DubbedExperienceTest.kt:76

  12 tests completed, 1 failed

  > Task :app:testDebugUnitTest FAILED

  FAILURE: Build failed with an exception.
  * What went wrong:
  Execution failed for task ':app:testDebugUnitTest'.
  > There were failing tests. See the report at: file:///C:/Users/ngarz/Documents/antigravity/blissful-planck/app/build/reports/tests/testDebugUnitTest/index.html
  ```
- **XML Report (`app/build/test-results/testDebugUnitTest/TEST-com.darknet.animetv.dub.DubbedExperienceTest.xml`)**:
  ```xml
  <testcase name="testEpisodeStreamsAudioPreferencePriority" classname="com.darknet.animetv.dub.DubbedExperienceTest" time="5.995">
    <failure message="java.lang.AssertionError: Primary stream when preferredAudio=DUB should be DUB" type="java.lang.AssertionError">
  java.lang.AssertionError: Primary stream when preferredAudio=DUB should be DUB
      at org.junit.Assert.fail(Assert.java:89)
      at org.junit.Assert.assertTrue(Assert.java:42)
      at com.darknet.animetv.dub.DubbedExperienceTest$testEpisodeStreamsAudioPreferencePriority$1.invokeSuspend(DubbedExperienceTest.kt:83)
      at com.darknet.animetv.dub.DubbedExperienceTest.testEpisodeStreamsAudioPreferencePriority(DubbedExperienceTest.kt:76)
    </failure>
  </testcase>
  ```

### 1.2 Inspection of `DubbedExperienceTest.kt`
In `app/src/test/java/com/darknet/animetv/dub/DubbedExperienceTest.kt` (lines 76-98):
```kotlin
    @Test
    fun testEpisodeStreamsAudioPreferencePriority() = runBlocking {
        val repository = DefaultAnimeRepository()

        // When preferredAudio is DUB, streams prioritized should be DUB
        val dubStreams = repository.getEpisodeStreams("Cowboy Bebop", 1, preferredAudio = "DUB")
        assertTrue(dubStreams.isNotEmpty())
        val firstDubStream = dubStreams.first()
        assertTrue(
            "Primary stream when preferredAudio=DUB should be DUB",
            firstDubStream.audioTrack.equals("DUB", ignoreCase = true) ||
                firstDubStream.serverName.contains("Dub", ignoreCase = true)
        )

        // When preferredAudio is SUB, streams prioritized should be SUB
        val subStreams = repository.getEpisodeStreams("Cowboy Bebop", 1, preferredAudio = "SUB")
        assertTrue(subStreams.isNotEmpty())
        val firstSubStream = subStreams.first()
        assertTrue(
            "Primary stream when preferredAudio=SUB should be SUB",
            firstSubStream.audioTrack.equals("SUB", ignoreCase = true) ||
                !firstSubStream.serverName.contains("Dub", ignoreCase = true)
        )
    }
```
`repository` is initialized via default constructor `DefaultAnimeRepository()`. This instantiates all 10 built-in sources (`AniListCatalogSource`, `KaaSource`, `AniwatchSource`, `MiruroSource`, `AnimeFlvSource`, `AnikotoSource`, `ReAnimeSource`, `WcoSource`, `JikanSource`, `ConsumetSource`).

### 1.3 Inspection of `DefaultAnimeRepository.kt` (`AnimeRepository.kt`)
In `app/src/main/java/com/darknet/animetv/data/repository/AnimeRepository.kt`:
1. **Catalog Sources Added to Video Stream Failover** (lines 520-525):
   ```kotlin
   // 3. Any remaining catalog sources as last resort
   active.forEach {
       if (!prioritizedSources.contains(it)) {
           prioritizedSources.add(it)
       }
   }
   ```
   Even though `AniListCatalogSource` and `JikanSource` were filtered at line 486 and line 515, line 521 unconditionally appends them to `prioritizedSources`.
2. **Premature Loop Termination** (lines 528-555):
   ```kotlin
   for (source in prioritizedSources) {
       try {
           val streams = withTimeoutOrNull(10_000L) {
               ...
               source.getEpisodeStreams(match.id, targetEp.id)
           }.orEmpty().filterNot { isDemoFallbackUrl(it.url) }

           if (streams.isNotEmpty()) {
               collectedStreams.addAll(streams)
               // Finish failover immediately once working stream(s) are resolved
               break
           }
       } catch (e: Exception) {
           e.printStackTrace()
       }
   }
   ```
   `break` occurs as soon as `streams.isNotEmpty()`, regardless of whether `preferredAudio == "DUB"` and whether any stream in `streams` actually has `audioTrack == "DUB"`.
3. **Incomplete Demo Fallback Filter** (lines 604-608):
   ```kotlin
   private fun isDemoFallbackUrl(url: String): Boolean {
       return url.contains("test-streams.mux.dev", ignoreCase = true) ||
           url.contains("BigBuckBunny", ignoreCase = true) ||
           url.contains("img_bipbop_adv_example", ignoreCase = true)
   }
   ```
   `TearsOfSteel.mp4` and `commondatastorage.googleapis.com` are NOT listed in `isDemoFallbackUrl()`.
4. **Non-Mutating Sort on Mutable List** (lines 586-602):
   ```kotlin
   if (preferredAudio.equals("DUB", ignoreCase = true)) {
       collectedStreams.sortedWith(
           compareByDescending<VideoSource> {
               it.audioTrack.equals("DUB", ignoreCase = true) ||
                   it.serverName.contains("Dub", ignoreCase = true) ||
                   it.quality.contains("Dub", ignoreCase = true)
           }.thenByDescending { it.isHls }
       )
   } else {
       collectedStreams.sortedWith(...)
   }
   ```
   `collectedStreams.sortedWith(...)` produces a new collection without modifying `collectedStreams`.

### 1.4 Inspection of `JikanSource.kt`
In `app/src/main/java/com/darknet/animetv/data/source/JikanSource.kt` (lines 136-158):
```kotlin
    override suspend fun getEpisodeStreams(animeId: String, episodeId: String): List<VideoSource> {
        // High quality multi-source fallbacks (including demo HLS stream + direct extraction)
        return listOf(
            VideoSource(
                serverName = "FastCDN Multi-Quality (HLS)",
                url = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                quality = "1080p",
                isHls = true
            ),
            VideoSource(
                serverName = "CloudStream Mirror (720p)",
                url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                quality = "720p",
                isHls = false
            ),
            VideoSource(
                serverName = "AnimeFLV Stream Server",
                url = "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8",
                quality = "1080p (HQ)",
                isHls = true
            )
        )
    }
```
`JikanSource` returns demo streams including `TearsOfSteel.mp4` with default `audioTrack = "SUB"`.

### 1.5 Inspection of `AnimeFlvSource.kt`
In `app/src/main/java/com/darknet/animetv/data/source/AnimeFlvSource.kt` (lines 263-280):
```kotlin
        if (streams.isEmpty()) {
            streams.add(
                VideoSource(
                    serverName = "AnimeFLV Stream Server (Primary)",
                    url = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                    quality = "1080p",
                    isHls = true
                )
            )
            streams.add(
                VideoSource(
                    serverName = "AnimeFLV Mega / StreamWish Mirror",
                    url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                    quality = "720p",
                    isHls = false
                )
            )
        }
```
`AnimeFlvSource` falls back to `TearsOfSteel.mp4` with default `audioTrack = "SUB"`.

---

## 2. Logic Chain

1. **Test Failure Origin**:
   - `DubbedExperienceTest.testEpisodeStreamsAudioPreferencePriority` requests `repository.getEpisodeStreams("Cowboy Bebop", 1, preferredAudio = "DUB")`.
   - The test asserts that `firstDubStream.audioTrack == "DUB"` or `serverName.contains("Dub")`.
   - The test fails because `firstDubStream` is a `SUB` stream with URL `https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4`.

2. **Why `TearsOfSteel.mp4` was selected**:
   - During failover, `WcoSource` and `AniwatchSource` (prioritized dub sources) either fail network queries or return `test-streams.mux.dev` fallback streams.
   - `isDemoFallbackUrl` successfully filters out `test-streams.mux.dev`.
   - The failover loop proceeds to `AnimeFlvSource` or `JikanSource`.
   - Both `AnimeFlvSource` and `JikanSource` return `TearsOfSteel.mp4`.
   - Because `isDemoFallbackUrl(".../TearsOfSteel.mp4")` returns `false`, `TearsOfSteel.mp4` is accepted into `streams`.

3. **Why the loop stopped prematurely**:
   - `streams.isNotEmpty()` is true (it contains the `TearsOfSteel.mp4` stream).
   - The repository immediately calls `break`.
   - Because `collectedStreams` is not empty, lines 563-584 (the emergency fallback that creates a DUB stream when `preferredAudio == "DUB"`) never execute.
   - `collectedStreams` contains only `SUB` streams.
   - Sorting cannot synthesize a DUB stream from an exclusively SUB list. The primary stream is `SUB`, causing `AssertionError`.

4. **Architectural Inconsistency**:
   - Requirement R1 explicitly mandates pure metadata separation: `AniListCatalogSource` must return `emptyList()` for `getEpisodeStreams()`.
   - `JikanSource` is also a pure catalog metadata provider (`AniList / Jikan Catalog`), but was erroneously left returning mock demo streams.
   - In `DefaultAnimeRepository`, adding catalog sources to `prioritizedSources` for video streaming violates R1.

---

## 3. Caveats

1. **Scraper Network Dependency in Unit Tests**: Running `DefaultAnimeRepository()` with live network sources in unit tests subjects tests to external network latency (up to 10s per source). While the fallback and filtering fixes make the default constructor pass, unit tests for audio preference should also verify behavior using mock sources (as demonstrated in `Milestone1ChallengerStressTest`) to guarantee 100% deterministic, zero-network execution.
2. **Gradle Daemon State on Windows**: Gradle daemon memory corruption was observed during initial test runs (`NoSuchFileException: dirty-sources.txt`). Running tests with `-Dkotlin.incremental=false` or executing `.\gradlew.bat clean` completely eliminates this build-system artifact.

---

## 4. Conclusion & Recommended Fix Strategy

### 4.1 Changes to `app/src/main/java/com/darknet/animetv/data/repository/AnimeRepository.kt`

#### Change A: Remove Catalog Sources from Streaming Failover
In `DefaultAnimeRepository.getEpisodeStreams()` (lines 520-525):
**Before**:
```kotlin
        // 3. Any remaining catalog sources as last resort
        active.forEach {
            if (!prioritizedSources.contains(it)) {
                prioritizedSources.add(it)
            }
        }
```
**After**:
```kotlin
        // Catalog sources (AniListCatalogSource, JikanSource) must NEVER be used for streaming extraction
```

#### Change B: Expand `isDemoFallbackUrl`
In `DefaultAnimeRepository` (lines 604-608):
**Before**:
```kotlin
    private fun isDemoFallbackUrl(url: String): Boolean {
        return url.contains("test-streams.mux.dev", ignoreCase = true) ||
            url.contains("BigBuckBunny", ignoreCase = true) ||
            url.contains("img_bipbop_adv_example", ignoreCase = true)
    }
```
**After**:
```kotlin
    private fun isDemoFallbackUrl(url: String): Boolean {
        return url.contains("test-streams.mux.dev", ignoreCase = true) ||
            url.contains("BigBuckBunny", ignoreCase = true) ||
            url.contains("img_bipbop_adv_example", ignoreCase = true) ||
            url.contains("TearsOfSteel", ignoreCase = true) ||
            url.contains("gtv-videos-bucket", ignoreCase = true) ||
            url.contains("commondatastorage.googleapis.com", ignoreCase = true)
    }
```

#### Change C: Enforce Audio Preference in Failover Loop
In `DefaultAnimeRepository.getEpisodeStreams()` (lines 527-560):
**Before**:
```kotlin
        // Multi-Source Failover Loop with per-provider timeout
        for (source in prioritizedSources) {
            try {
                val streams = withTimeoutOrNull(10_000L) {
                    ...
                    source.getEpisodeStreams(match.id, targetEp.id)
                }.orEmpty().filterNot { isDemoFallbackUrl(it.url) }

                if (streams.isNotEmpty()) {
                    collectedStreams.addAll(streams)
                    // Finish failover immediately once working stream(s) are resolved
                    break
                }
            } catch (e: Exception) {
                // Log and fail over to next provider
                e.printStackTrace()
            }
        }
```
**After**:
```kotlin
        val fallbackSubStreams = mutableListOf<VideoSource>()

        // Multi-Source Failover Loop with per-provider timeout
        for (source in prioritizedSources) {
            try {
                val streams = withTimeoutOrNull(10_000L) {
                    // a) Search anime title
                    val searchList = if (preferredAudio.equals("DUB", ignoreCase = true) && !animeTitle.contains("dub", ignoreCase = true)) {
                        val dubSearch = source.search("$animeTitle (Dub)")
                        if (dubSearch.isNotEmpty()) dubSearch else source.search(animeTitle)
                    } else {
                        source.search(animeTitle)
                    }
                    val match = searchList.firstOrNull() ?: return@withTimeoutOrNull emptyList()

                    // b) Get episode detail
                    val detail = source.getAnimeDetail(match.id) ?: return@withTimeoutOrNull emptyList()
                    val targetEp = detail.episodes.find { it.number == episodeNumber }
                        ?: detail.episodes.getOrNull(episodeNumber - 1)
                        ?: detail.episodes.firstOrNull()
                        ?: return@withTimeoutOrNull emptyList()

                    // c) Extract streams
                    source.getEpisodeStreams(match.id, targetEp.id)
                }.orEmpty().filterNot { isDemoFallbackUrl(it.url) }

                if (streams.isNotEmpty()) {
                    if (preferredAudio.equals("DUB", ignoreCase = true)) {
                        val hasDub = streams.any {
                            it.audioTrack.equals("DUB", ignoreCase = true) ||
                                it.serverName.contains("Dub", ignoreCase = true) ||
                                it.quality.contains("Dub", ignoreCase = true)
                        }
                        if (hasDub) {
                            collectedStreams.addAll(streams)
                            break
                        } else {
                            // Keep SUB streams as secondary fallback if no DUB source is found across all providers
                            if (fallbackSubStreams.isEmpty()) {
                                fallbackSubStreams.addAll(streams)
                            }
                        }
                    } else {
                        collectedStreams.addAll(streams)
                        break
                    }
                }
            } catch (e: Exception) {
                // Log and fail over to next provider
                e.printStackTrace()
            }
        }
```

#### Change D: In-Place Sort and Explicit Return
In `DefaultAnimeRepository.getEpisodeStreams()` (lines 585-602):
**Before**:
```kotlin
        // Prioritize streams matching preferredAudio
        if (preferredAudio.equals("DUB", ignoreCase = true)) {
            collectedStreams.sortedWith(
                compareByDescending<VideoSource> {
                    it.audioTrack.equals("DUB", ignoreCase = true) ||
                        it.serverName.contains("Dub", ignoreCase = true) ||
                        it.quality.contains("Dub", ignoreCase = true)
                }.thenByDescending { it.isHls }
            )
        } else {
            collectedStreams.sortedWith(
                compareByDescending<VideoSource> {
                    it.audioTrack.equals("SUB", ignoreCase = true) &&
                        !it.serverName.contains("Dub", ignoreCase = true)
                }.thenByDescending { it.isHls }
            )
        }
```
**After**:
```kotlin
        // Prioritize streams matching preferredAudio
        if (preferredAudio.equals("DUB", ignoreCase = true)) {
            collectedStreams.sortWith(
                compareByDescending<VideoSource> {
                    it.audioTrack.equals("DUB", ignoreCase = true) ||
                        it.serverName.contains("Dub", ignoreCase = true) ||
                        it.quality.contains("Dub", ignoreCase = true)
                }.thenByDescending { it.isHls }
            )
        } else {
            collectedStreams.sortWith(
                compareByDescending<VideoSource> {
                    it.audioTrack.equals("SUB", ignoreCase = true) &&
                        !it.serverName.contains("Dub", ignoreCase = true)
                }.thenByDescending { it.isHls }
            )
        }
        collectedStreams
```

---

### 4.2 Changes to `app/src/main/java/com/darknet/animetv/data/source/JikanSource.kt`
In `JikanSource.kt` (lines 136-158):
**Before**:
```kotlin
    override suspend fun getEpisodeStreams(animeId: String, episodeId: String): List<VideoSource> {
        // High quality multi-source fallbacks (including demo HLS stream + direct extraction)
        return listOf(
            VideoSource(
                serverName = "FastCDN Multi-Quality (HLS)",
                url = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                quality = "1080p",
                isHls = true
            ),
            VideoSource(
                serverName = "CloudStream Mirror (720p)",
                url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                quality = "720p",
                isHls = false
            ),
            VideoSource(
                serverName = "AnimeFLV Stream Server",
                url = "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8",
                quality = "1080p (HQ)",
                isHls = true
            )
        )
    }
```
**After**:
```kotlin
    override suspend fun getEpisodeStreams(animeId: String, episodeId: String): List<VideoSource> {
        // Pure metadata catalog provider - delegates all playback stream resolution to scrapers
        return emptyList()
    }
```

---

### 4.3 Changes to `app/src/main/java/com/darknet/animetv/data/source/AnimeFlvSource.kt`
In `AnimeFlvSource.kt` (lines 263-281):
When stream extraction yields no streams, return `emptyList()` rather than synthesizing hardcoded demo streams, aligning it with all other production scrapers:
**Before**:
```kotlin
        if (streams.isEmpty()) {
            streams.add(
                VideoSource(
                    serverName = "AnimeFLV Stream Server (Primary)",
                    url = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                    quality = "1080p",
                    isHls = true
                )
            )
            streams.add(
                VideoSource(
                    serverName = "AnimeFLV Mega / StreamWish Mirror",
                    url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                    quality = "720p",
                    isHls = false
                )
            )
        }

        streams
```
**After**:
```kotlin
        streams
```

---

### 4.4 Enhancements to `app/src/test/java/com/darknet/animetv/dub/DubbedExperienceTest.kt`

1. **Add Pure Metadata Verification for JikanSource**:
   ```kotlin
   @Test
   fun testJikanCatalogSourceReturnsEmptyEpisodeStreams() = runBlocking {
       val jikanSource = JikanSource()
       val streams = jikanSource.getEpisodeStreams("1", "ep-1")
       assertTrue("JikanSource must act purely as catalog metadata and return empty streams", streams.isEmpty())
   }
   ```
2. **Add Isolated Audio Preference Priority Unit Test (Fast & Mocked)**:
   Add a test using `customSourcesList` containing mock DUB and SUB streams to guarantee stream priority logic is tested independently of live scraper timeouts:
   ```kotlin
   @Test
   fun testEpisodeStreamsAudioPreferencePriorityWithMocks() = runBlocking {
       val mockSource = object : AnimeSource {
           override val sourceId = "mock-stream"
           override val sourceName = "Mock Streaming Source"
           override var isEnabled = true
           override suspend fun getTrending() = emptyList<Anime>()
           override suspend fun getPopular() = emptyList<Anime>()
           override suspend fun getRecentEpisodes() = emptyList<Anime>()
           override suspend fun search(query: String) = listOf(Anime(id = "1", title = query))
           override suspend fun getAnimeDetail(animeId: String) = AnimeDetail(
               anime = Anime(id = animeId, title = "Test Anime"),
               episodes = listOf(Episode(id = "ep-1", animeId = animeId, number = 1, title = "Ep 1"))
           )
           override suspend fun getEpisodeStreams(animeId: String, episodeId: String) = listOf(
               VideoSource(serverName = "Sub Stream", url = "https://cdn.example.com/sub.m3u8", audioTrack = "SUB"),
               VideoSource(serverName = "Dub Stream", url = "https://cdn.example.com/dub.m3u8", audioTrack = "DUB")
           )
           override suspend fun testConnection() = SourceHealthResult(sourceId, true)
       }

       val repo = DefaultAnimeRepository(customSourcesList = listOf(mockSource))

       val dubStreams = repo.getEpisodeStreams("Test Anime", 1, preferredAudio = "DUB")
       assertEquals("DUB", dubStreams.first().audioTrack)

       val subStreams = repo.getEpisodeStreams("Test Anime", 1, preferredAudio = "SUB")
       assertEquals("SUB", subStreams.first().audioTrack)
   }
   ```
3. **Prevent Robolectric SQLite Resource Leak**:
   In `testAniListSyncManagerUnauthenticatedSyncDoesNotInjectDemo`:
   ```kotlin
   val app = RuntimeEnvironment.getApplication()
   val storage = AppStorage(app)
   try {
       storage.saveAniListToken(null)
       storage.saveAniListUser(null)
       val res = AniListSyncManager.syncAniListWatchlist(storage)
       assertFalse("Unauthenticated sync without account should fail gracefully", res.isSuccess)
       val favorites = storage.getFavorites()
       assertFalse("Real unauthenticated sync must not inject fake demo anime", favorites.any { it.title.contains("Attack on Titan Final Season") })
   } finally {
       storage.close()
   }
   ```

---

## 5. Verification Method

### 5.1 Step 1: Run Targeted Dubbed Test Suite
Verify that all tests in `DubbedExperienceTest` pass with 0 failures:
```powershell
.\gradlew.bat testDebugUnitTest --tests "com.darknet.animetv.dub.DubbedExperienceTest" "-Dkotlin.incremental=false"
```
**Expected Result**:
```
DubbedExperienceTest > testEpisodeStreamsAudioPreferencePriority PASSED
DubbedExperienceTest > testAnimeModelDubFieldsDefaultAndCustom PASSED
DubbedExperienceTest > testEpisodeModelAudioTrack PASSED
DubbedExperienceTest > testVideoSourceAudioTrack PASSED
DubbedExperienceTest > testDefaultAnimeRepositoryGetDubbedAnime PASSED
...
BUILD SUCCESSFUL (0 failures)
```

### 5.2 Step 2: Run Full Milestone 1 Challenger Stress Suite
Verify resilience under crashes, attacks, and extreme inputs:
```powershell
.\gradlew.bat testDebugUnitTest --tests "com.darknet.animetv.challenger.Milestone1ChallengerStressTest" "-Dkotlin.incremental=false"
```
**Expected Result**: All 8 stress tests pass with 0 failures.

### 5.3 Step 3: Run Full Regression Test Suite
Verify zero regressions across all 374+ unit tests:
```powershell
.\gradlew.bat testDebugUnitTest "-Dkotlin.incremental=false"
```
**Expected Result**: `BUILD SUCCESSFUL` with 0 failures across all test suites.

### 5.4 Step 4: Verify Full APK Packaging
Verify full Kotlin compilation, resource shrinking, and DEX linking:
```powershell
.\gradlew.bat assembleDebug
```
**Expected Result**: `BUILD SUCCESSFUL`, producing `app\build\outputs\apk\debug\app-debug.apk` with exit code 0.

### 5.5 Invalidation Conditions
The fix is considered INVALIDATED if:
1. `DubbedExperienceTest > testEpisodeStreamsAudioPreferencePriority` fails with any `AssertionError`.
2. Any test in `app/src/test/` fails under `testDebugUnitTest`.
3. `JikanSource.getEpisodeStreams()` returns a non-empty list.
4. `isDemoFallbackUrl` allows `TearsOfSteel.mp4` to bypass failover filtering.
