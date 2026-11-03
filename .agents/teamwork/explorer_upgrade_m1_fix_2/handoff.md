# Investigation & Remediation Strategy Report — Milestone 1 Stream Failover & Catalog Sources Fix

**Agent**: `explorer_upgrade_m1_fix_2` (Archetype: explorer, Roles: investigation, synthesis)  
**Parent**: `5530bcff-e1f6-4c64-9cf0-a70de03c988f` (`orchestrator_2`)  
**Working Directory**: `c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\explorer_upgrade_m1_fix_2`  
**Date**: 2026-10-28T05:30:00Z  

---

## Executive Summary
This investigation addresses the **INTEGRITY VIOLATION** flagged by `auditor_upgrade_m1` and `reviewer_upgrade_m1_2` where `DubbedExperienceTest > testEpisodeStreamsAudioPreferencePriority` fails with `AssertionError: Primary stream when preferredAudio=DUB should be DUB`.

The root cause is a compound defect:
1. `DefaultAnimeRepository.isDemoFallbackUrl()` fails to filter `TearsOfSteel.mp4` (and `commondatastorage.googleapis.com` sample MP4 URLs).
2. Pure metadata source `JikanSource` returns mock demo streams (`TearsOfSteel.mp4`, `x36xhzz.m3u8`, `img_bipbop_adv_example`) instead of `emptyList()`.
3. `AnimeFlvSource` falls back to `TearsOfSteel.mp4` when scraping returns empty.
4. `DefaultAnimeRepository.getEpisodeStreams()` appends catalog sources to `prioritizedSources` as a "last resort", and its failover loop immediately accepts `SUB` streams even when the caller specified `preferredAudio == "DUB"`, breaking early before checking subsequent sources or triggering the emergency `DUB` fallback.

Below is the verified evidence chain, architectural analysis, and exact code-level remediation plan.

---

## 1. Observation

### 1.1 Empirical Verification Failure
Executing the unit test command:
```powershell
.\gradlew.bat testDebugUnitTest --tests "com.darknet.animetv.dub.DubbedExperienceTest"
```
Direct tool output from task execution:
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
BUILD FAILED in 27s
```

From `app/build/test-results/testDebugUnitTest/TEST-com.darknet.animetv.dub.DubbedExperienceTest.xml`:
```xml
<testcase name="testEpisodeStreamsAudioPreferencePriority" classname="com.darknet.animetv.dub.DubbedExperienceTest" time="6.662">
  <failure message="java.lang.AssertionError: Primary stream when preferredAudio=DUB should be DUB" type="java.lang.AssertionError">
java.lang.AssertionError: Primary stream when preferredAudio=DUB should be DUB
	at org.junit.Assert.fail(Assert.java:89)
	at org.junit.Assert.assertTrue(Assert.java:42)
	at com.darknet.animetv.dub.DubbedExperienceTest$testEpisodeStreamsAudioPreferencePriority$1.invokeSuspend(DubbedExperienceTest.kt:83)
	at com.darknet.animetv.dub.DubbedExperienceTest.testEpisodeStreamsAudioPreferencePriority(DubbedExperienceTest.kt:76)
  </failure>
</testcase>
```

### 1.2 Inspection of Defective Code Points

#### Observation A: Missing `TearsOfSteel` in `isDemoFallbackUrl`
In `app/src/main/java/com/darknet/animetv/data/repository/AnimeRepository.kt` (lines 604-608):
```kotlin
private fun isDemoFallbackUrl(url: String): Boolean {
    return url.contains("test-streams.mux.dev", ignoreCase = true) ||
        url.contains("BigBuckBunny", ignoreCase = true) ||
        url.contains("img_bipbop_adv_example", ignoreCase = true)
}
```
`TearsOfSteel` and `commondatastorage.googleapis.com` are completely omitted.

#### Observation B: Catalog Source `JikanSource` Returns Mock Streams
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
Unlike `AniListCatalogSource` (which properly returns `emptyList()`), `JikanSource` returns fake streams, including `TearsOfSteel.mp4` with default `audioTrack = "SUB"`.

#### Observation C: `AnimeFlvSource` Injects `TearsOfSteel.mp4` on Empty Scrape
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
When live scraping returns no videos (or is offline/mocked), `AnimeFlvSource` injects `TearsOfSteel.mp4` with `audioTrack = "SUB"`.

#### Observation D: Catalog Sources Inappropriately Enqueued for Stream Extraction
In `app/src/main/java/com/darknet/animetv/data/repository/AnimeRepository.kt` (lines 520-525):
```kotlin
// 3. Any remaining catalog sources as last resort
active.forEach {
    if (!prioritizedSources.contains(it)) {
        prioritizedSources.add(it)
    }
}
```
While line 515 explicitly skipped `AniListCatalogSource` and `JikanSource`, lines 520-525 appended them anyway as "last resort" video sources.

#### Observation E: Failover Loop Halts Immediately on Incompatible `SUB` Streams
In `app/src/main/java/com/darknet/animetv/data/repository/AnimeRepository.kt` (lines 551-555):
```kotlin
if (streams.isNotEmpty()) {
    collectedStreams.addAll(streams)
    // Finish failover immediately once working stream(s) are resolved
    break
}
```
When `preferredAudio == "DUB"`, if any source returns streams that are solely `SUB`, the loop adds them to `collectedStreams` and issues a `break`. This aborts failover before querying subsequent sources that might offer DUB streams, and prevents the emergency DUB fallback at lines 563-584 from executing.

---

## 2. Logic Chain

1. **Step 1: Test Invalidation**:
   - `DubbedExperienceTest.testEpisodeStreamsAudioPreferencePriority` invokes `repository.getEpisodeStreams("Cowboy Bebop", 1, preferredAudio = "DUB")`.
   - The test asserts that `firstDubStream.audioTrack.equals("DUB", ignoreCase = true) || firstDubStream.serverName.contains("Dub", ignoreCase = true)`.
   - Because `firstDubStream.audioTrack == "SUB"`, the assertion throws `AssertionError`.

2. **Step 2: Stream Resolution Hijacking**:
   - In unit test execution, live external sites may be unreachable or return non-working scrapers.
   - Built-in sources `WcoSource`, `AniwatchSource`, `KaaSource`, etc., return fallback streams with `test-streams.mux.dev`, which are filtered out by `isDemoFallbackUrl()`.
   - However, when `AnimeFlvSource` or `JikanSource` are evaluated, they return `TearsOfSteel.mp4`.
   - `isDemoFallbackUrl(".../TearsOfSteel.mp4")` returns `false` (Observation A).
   - Therefore, `streams` retains the `TearsOfSteel.mp4` stream.

3. **Step 3: Premature Termination of Failover Loop**:
   - Because `streams.isNotEmpty()` is true, the loop unconditionally executes `collectedStreams.addAll(streams)` and `break` (Observation E).
   - The loop never verifies whether `streams` satisfies the requested `preferredAudio == "DUB"`.
   - Because `collectedStreams` is not empty, lines 563-584 (`Emergency Fallback (DUB)`) are bypassed.
   - `collectedStreams` contains only `SUB` audio tracks.
   - Sorting in lines 586-593 cannot promote a `DUB` track because none exists in `collectedStreams`.
   - Result: `dubStreams.first()` has `audioTrack = "SUB"`, directly causing the assertion failure.

4. **Step 4: Architectural Violation of Pure Metadata Separation**:
   - Requirement R1 (`ORIGINAL_REQUEST.md` line 81) specifies: *"Ensure AniListCatalogSource acts purely as a metadata/catalog provider (returning emptyList() for getEpisodeStreams) and DefaultAnimeRepository.getEpisodeStreams resolves real SUB and DUB streams from active streaming providers..."*
   - `JikanSource` is also a catalog metadata provider (`sourceName = "AniList / Jikan Catalog"`). Returning mock demo MP4 video streams violates the pure metadata contract.
   - Furthermore, `DefaultAnimeRepository` should never enqueue catalog sources into `prioritizedSources` for stream resolution.

---

## 3. Caveats

- **Network Dependency**: External streaming sites (`wco.tv`, `aniwatchtv.ro`, `kaa.lt`, `animeflv.net`) frequently rotate domains, update Cloudflare protections, or experience downtime. The repository must be completely resilient offline and in test sandboxes without leaking demo streams or returning incorrect audio tracks.
- **Multi-Track Sources**: When a source returns both `SUB` and `DUB` tracks, the source is valid for both preferences, and sorting must place the requested audio track at index 0. When a source provides *only* `SUB` tracks, it must be rejected during `preferredAudio == "DUB"` failover.

---

## 4. Conclusion & Exact Remediation Strategy

To remediate all integrity violations and defects cleanly, the implementer must apply the following specific changes across four files:

### 4.1 Target File 1: `AnimeRepository.kt` (`com.darknet.animetv.data.repository`)

#### Change 1: Comprehensive Demo URL Filtering
Update `isDemoFallbackUrl` to recognize `TearsOfSteel` and Google sample video storage:
```kotlin
private fun isDemoFallbackUrl(url: String): Boolean {
    return url.contains("test-streams.mux.dev", ignoreCase = true) ||
        url.contains("BigBuckBunny", ignoreCase = true) ||
        url.contains("img_bipbop_adv_example", ignoreCase = true) ||
        url.contains("TearsOfSteel", ignoreCase = true) ||
        url.contains("commondatastorage.googleapis.com", ignoreCase = true)
}
```

#### Change 2: Exclude Pure Metadata Sources from Stream Failover
Ensure pure metadata sources (`AniListCatalogSource`, `JikanSource`) are never added to `prioritizedSources`:
- Remove lines 520-525:
  ```kotlin
  // REMOVE:
  // 3. Any remaining catalog sources as last resort
  // active.forEach { if (!prioritizedSources.contains(it)) prioritizedSources.add(it) }
  ```
- Define helper:
  ```kotlin
  private fun isPureMetadataSource(source: AnimeSource): Boolean {
      return source is AniListCatalogSource || source is JikanSource
  }
  ```
  Ensure step 1 (`preferredSourceId`) and step 2 (`active streaming sources`) use `!isPureMetadataSource(source)`.

#### Change 3: Audio Preference Matching in Failover Loop
Define helper:
```kotlin
private fun isDubStream(stream: VideoSource): Boolean {
    return stream.audioTrack.equals("DUB", ignoreCase = true) ||
        stream.serverName.contains("Dub", ignoreCase = true) ||
        stream.quality.contains("Dub", ignoreCase = true)
}
```
Update the stream acceptance check in the failover loop (around lines 551-555):
```kotlin
if (streams.isNotEmpty()) {
    if (preferredAudio.equals("DUB", ignoreCase = true)) {
        val dubStreams = streams.filter { isDubStream(it) }
        if (dubStreams.isNotEmpty()) {
            collectedStreams.addAll(dubStreams)
            break
        }
        // If this provider returned only SUB streams, reject them during DUB failover and continue loop
    } else {
        collectedStreams.addAll(streams)
        break
    }
}
```

#### Change 4: Explicit Return of Sorted Streams
Ensure the sorted result is returned explicitly:
```kotlin
return@withContext if (preferredAudio.equals("DUB", ignoreCase = true)) {
    collectedStreams.sortedWith(
        compareByDescending<VideoSource> { isDubStream(it) }
            .thenByDescending { it.isHls }
    )
} else {
    collectedStreams.sortedWith(
        compareByDescending<VideoSource> { !isDubStream(it) }
            .thenByDescending { it.isHls }
    )
}
```

---

### 4.2 Target File 2: `JikanSource.kt` (`com.darknet.animetv.data.source`)

Update `getEpisodeStreams` (lines 136-158) to return `emptyList()` as a pure metadata catalog provider:
```kotlin
override suspend fun getEpisodeStreams(animeId: String, episodeId: String): List<VideoSource> {
    return emptyList()
}
```

---

### 4.3 Target File 3: `AnimeFlvSource.kt` (`com.darknet.animetv.data.source`)

Remove lines 263-280 in `getEpisodeStreams` that inject demo fallbacks when scraping yields no streams:
```kotlin
// In AnimeFlvSource.kt:
override suspend fun getEpisodeStreams(animeId: String, episodeId: String): List<VideoSource> = withContext(Dispatchers.IO) {
    val streams = mutableListOf<VideoSource>()
    try {
        val cleanEpId = episodeId.removePrefix("aflv-")
        val verUrl = if (cleanEpId.startsWith("http")) cleanEpId else "$baseUrl/ver/$cleanEpId"

        val req = Request.Builder()
            .url(verUrl)
            .header("User-Agent", userAgent)
            .header("Referer", "$baseUrl/")
            .build()

        client.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) {
                val html = resp.body?.string() ?: ""
                val videoCodes = extractVideosJson(html)
                for (codeUrl in videoCodes) {
                    val resolved = extractorRegistry.extractFromUrl(codeUrl, referer = "$baseUrl/")
                    streams.addAll(resolved)
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    // Do NOT inject TearsOfSteel or test-streams.mux.dev fallbacks; return actual extracted streams (empty if none)
    streams
}
```

---

### 4.4 Target File 4: `TvPlayerScreen.kt` (`com.darknet.animetv.player`)

In `LaunchedEffect(anime.id, episode.id, audioMode)` (lines 202-211), pass `preferredAudio = audioMode`:
```kotlin
val resolved = repository.getEpisodeStreams(
    animeTitle = queryTitle,
    episodeNumber = episode.number,
    preferredSourceId = preferredSource,
    preferredAudio = audioMode
)
```

---

### 4.5 Target File 5: `DubbedExperienceTest.kt` (`com.darknet.animetv.dub`)

Add targeted unit tests certifying pure metadata separation and failover audio preference:
```kotlin
@Test
fun testJikanSourceReturnsEmptyEpisodeStreams() = runBlocking {
    val jikan = JikanSource()
    val streams = jikan.getEpisodeStreams("1", "1-ep-1")
    assertTrue("JikanSource must act purely as metadata and return empty streams", streams.isEmpty())
}

@Test
fun testAnimeFlvSourceReturnsEmptyWhenNoStreamsExtracted() = runBlocking {
    val aflv = AnimeFlvSource()
    val streams = aflv.getEpisodeStreams("aflv-test", "aflv-test-1")
    assertTrue("AnimeFlvSource must not leak demo URLs when scraping returns empty", streams.none { it.url.contains("TearsOfSteel") })
}

@Test
fun testFailoverDoesNotAcceptSubStreamsWhenPreferredAudioIsDub() = runBlocking {
    val subOnlySource = object : AnimeSource {
        override val sourceId: String = "sub-only"
        override val sourceName: String = "Sub Only Source"
        override var isEnabled: Boolean = true
        override suspend fun getTrending(): List<Anime> = emptyList()
        override suspend fun getPopular(): List<Anime> = emptyList()
        override suspend fun getRecentEpisodes(): List<Anime> = emptyList()
        override suspend fun search(query: String): List<Anime> = listOf(Anime(id = "sub-1", title = "Test Anime", sourceId = sourceId))
        override suspend fun getAnimeDetail(animeId: String): AnimeDetail = AnimeDetail(
            anime = Anime(id = animeId, title = "Test Anime", sourceId = sourceId),
            episodes = listOf(Episode(id = "ep-1", animeId = animeId, number = 1, title = "Ep 1"))
        )
        override suspend fun getEpisodeStreams(animeId: String, episodeId: String): List<VideoSource> = listOf(
            VideoSource(serverName = "Sub Only Server", url = "https://cdn.example.com/sub.mp4", audioTrack = "SUB")
        )
        override suspend fun testConnection(): SourceHealthResult = SourceHealthResult(sourceId, true)
    }

    val repository = DefaultAnimeRepository(customSourcesList = listOf(subOnlySource))
    val streams = repository.getEpisodeStreams("Test Anime", 1, preferredAudio = "DUB")

    // Failover must reject subOnlySource and fall back to emergency DUB stream
    assertTrue(streams.isNotEmpty())
    assertEquals("DUB", streams.first().audioTrack)
    assertTrue(streams.none { it.serverName == "Sub Only Server" })
}
```

---

## 5. Verification Method

### 5.1 Verification Commands
1. **Targeted Dubbed Unit Test Suite**:
   ```powershell
   .\gradlew.bat testDebugUnitTest --tests "com.darknet.animetv.dub.DubbedExperienceTest"
   ```
   *Expected Result*: 100% pass, 0 failures, exit code 0.

2. **Full Repository & Source Test Suites**:
   ```powershell
   .\gradlew.bat testDebugUnitTest --tests "com.darknet.animetv.source.*" --tests "com.darknet.animetv.sync.*" --tests "com.darknet.animetv.challenger.*"
   ```
   *Expected Result*: All stream resolution, mock source, and adversarial tests pass.

3. **Complete Project Test Suite & APK Build**:
   ```powershell
   .\gradlew.bat testDebugUnitTest assembleDebug
   ```
   *Expected Result*: All 374+ tests pass cleanly with 0 failures, followed by successful APK generation (`BUILD SUCCESSFUL`, exit code 0).

### 5.2 Invalidation Conditions
The fix is considered **INVALID** if:
- `repository.getEpisodeStreams("Any Anime", 1, preferredAudio = "DUB")` returns a primary stream with `audioTrack == "SUB"`.
- `JikanSource.getEpisodeStreams(...)` returns a non-empty list.
- `AnimeFlvSource.getEpisodeStreams(...)` returns `TearsOfSteel.mp4` when scraping fails.
- `DefaultAnimeRepository` terminates failover on a source returning strictly `SUB` streams when `preferredAudio == "DUB"`.
- Any existing test fails under `.\gradlew.bat testDebugUnitTest`.
