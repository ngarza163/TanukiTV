# Adversarial Challenge Report — Milestone 1 (AniListSyncManager & Stream Failover)

**Agent**: `challenger_upgrade_m1_2` (Archetype: challenger, Roles: critic, specialist)  
**Parent / Caller**: `5530bcff-e1f6-4c64-9cf0-a70de03c988f` (`orchestrator_2`)  
**Date**: 2026-10-28T05:26:00Z  
**Directory**: `c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\challenger_upgrade_m1_2\`  
**Verdict**: **REJECT**  

---

## Challenge Summary

- **Target Under Challenge**: `AniListSyncManager` (public username authentication, watchlist sync, demo sync guarding, JSON parsing resilience) and `DefaultAnimeRepository` stream resolution failover across active streaming providers.
- **Overall Risk Assessment**: **CRITICAL**
- **Verdict**: **REJECT**
- **Key Finding**: While `AniListSyncManager` successfully isolates demo sync from real accounts and demonstrates strong input validation against GraphQL injection, `DefaultAnimeRepository.getEpisodeStreams` contains a critical stream resolution failover defect: `AnimeFlvSource` fallback URL `https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4` (`audioTrack = "SUB"`) is omitted from `isDemoFallbackUrl()`. When a user requests English Dubbed streams (`preferredAudio = "DUB"`), this Subtitled stream bypasses the filter, prematurely halts the multi-source failover loop, and is returned to the player, causing `DubbedExperienceTest.testEpisodeStreamsAudioPreferencePriority` to fail with `java.lang.AssertionError: Primary stream when preferredAudio=DUB should be DUB`.

---

## 1. Observation

### 1.1 `AniListSyncManager` Implementation Analysis
1. **Username Validation (`AniListSyncManager.kt:116-157`)**:
   ```kotlin
   val trimmed = username.trim()
   if (trimmed.isBlank()) {
       return@withContext Result.failure(IllegalArgumentException("Username cannot be blank"))
   }
   ```
   - Input trimming and instant rejection of blank/whitespace inputs (`""`, `"   "`, `"\t"`, `"\n"`) prevents unnecessary HTTP calls.
   - GraphQL variables are parameterized in JSON:
     ```kotlin
     val variables = JsonObject().apply { addProperty("name", trimmed) }
     val payload = JsonObject().apply {
         addProperty("query", query)
         add("variables", variables)
     }
     ```
     This strictly prevents GraphQL query injection syntax attacks when malicious strings (`User"WithQuotes`, `User' OR '1'='1`, `}{ User(id: 1) { id } } #`, `<script>alert(1)</script>`) are entered.
   - On successful username login (line 186): `storage.saveAniListToken(null)` explicitly wipes any existing token (including any previously set demo token).

2. **Guarding Against `performDemoSync` on Real Accounts (`AniListSyncManager.kt:202-213`)**:
   ```kotlin
   val token = storage.getAniListToken()
   val user = storage.getAniListUser()

   if (token?.startsWith("demo_") == true) {
       // Explicit demo token in test or offline simulation
       return@withContext performDemoSync(storage)
   }

   if (user == null && token.isNullOrBlank()) {
       return@withContext SyncResult(false, 0, "No AniList account linked.")
   }
   ```
   - For real accounts logged in by username: `token` is `null`. The check `token?.startsWith("demo_")` evaluates to `false`.
   - For real accounts whose username begins with `"demo_"` (e.g. `username = "demo_slayer"`): `token` is `null`, so `token?.startsWith("demo_")` is `false`.
   - In offline/network error scenarios, lines 365-367 catch exceptions and return `SyncResult(false, 0, "Sync Error: ${e.message}")` without falling back to `performDemoSync`. Fake demo anime (IDs 16498, 21, 101922, 153518) are never injected into real accounts.

3. **`MediaListCollection` Parsing Robustness (`AniListSyncManager.kt:282-364`)**:
   - `root.getAsJsonObject("data")?.getAsJsonObject("MediaListCollection")?.getAsJsonArray("lists") ?: JsonArray()`: Safely returns empty `JsonArray` when AniList user profile is private or when 0 lists exist.
   - `titles?.get("english")?.takeIf { !it.isJsonNull }?.asString ?: romajiTitle ?: "Anime #$mediaId"`: Safely falls back if titles are null.
   - Scores default to 8.0 if `averageScore` and `entry.score` are null.
   - Total episodes default to 12 if `media.episodes` is null.
   - Watch progress is only seeded when `progress > 0`.

### 1.2 Multi-Source Stream Failover & Audio Priority Defect (`AnimeRepository.kt`)
1. **Stream Resolution Failover Loop (`AnimeRepository.kt:528-560`)**:
   ```kotlin
   for (source in prioritizedSources) {
       try {
           val streams = withTimeoutOrNull(10_000L) {
               val searchList = if (preferredAudio.equals("DUB", ignoreCase = true) && !animeTitle.contains("dub", ignoreCase = true)) {
                   val dubSearch = source.search("$animeTitle (Dub)")
                   if (dubSearch.isNotEmpty()) dubSearch else source.search(animeTitle)
               } else {
                   source.search(animeTitle)
               }
               val match = searchList.firstOrNull() ?: return@withTimeoutOrNull emptyList()
               val detail = source.getAnimeDetail(match.id) ?: return@withTimeoutOrNull emptyList()
               val targetEp = detail.episodes.find { it.number == episodeNumber }
                   ?: detail.episodes.getOrNull(episodeNumber - 1)
                   ?: detail.episodes.firstOrNull()
                   ?: return@withTimeoutOrNull emptyList()
               source.getEpisodeStreams(match.id, targetEp.id)
           }.orEmpty().filterNot { isDemoFallbackUrl(it.url) }

           if (streams.isNotEmpty()) {
               collectedStreams.addAll(streams)
               break
           }
       } catch (e: Exception) {
           e.printStackTrace()
       }
   }
   ```
2. **Demo Fallback URL Filter (`AnimeRepository.kt:604-608`)**:
   ```kotlin
   private fun isDemoFallbackUrl(url: String): Boolean {
       return url.contains("test-streams.mux.dev", ignoreCase = true) ||
           url.contains("BigBuckBunny", ignoreCase = true) ||
           url.contains("img_bipbop_adv_example", ignoreCase = true)
   }
   ```
3. **Stream Provider Leaking SUB Audio (`AnimeFlvSource.kt:272-279`)**:
   ```kotlin
   streams.add(
       VideoSource(
           serverName = "AnimeFLV Mega / StreamWish Mirror",
           url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
           quality = "720p",
           isHls = false
       )
   )
   ```
   - In `AnimeFlvSource`, the fallback stream uses `TearsOfSteel.mp4`.
   - `isDemoFallbackUrl("https://.../TearsOfSteel.mp4")` returns `false`!
   - Because `audioTrack` is not explicitly set in `AnimeFlvSource`, it defaults to `"SUB"`.
   - When `preferredAudio == "DUB"`, the loop reaches `AnimeFlvSource`, extracts `TearsOfSteel.mp4`, sees `streams.isNotEmpty() == true`, adds the SUB stream, and executes `break`!
   - The emergency DUB fallback stream (`TanukiTV Emergency Fallback (DUB HLS)`) is never reached.
   - The method returns the SUB stream to a user requesting English Dubbed audio.

4. **Empirical Test Verification Result**:
   - Command: `.\gradlew.bat testDebugUnitTest --tests "com.darknet.animetv.dub.DubbedExperienceTest"`
   - Result:
     ```
     DubbedExperienceTest > testEpisodeStreamsAudioPreferencePriority FAILED
         java.lang.AssertionError: Primary stream when preferredAudio=DUB should be DUB
         at org.junit.Assert.fail(Assert.java:89)
         at org.junit.Assert.assertTrue(Assert.java:42)
         at com.darknet.animetv.dub.DubbedExperienceTest$testEpisodeStreamsAudioPreferencePriority$1.invokeSuspend(DubbedExperienceTest.kt:83)
     ```

---

## 2. Logic Chain

1. **Premise 1 (Contract Requirement R2)**:
   Per `ORIGINAL_REQUEST.md` (lines 88-89 & 117): "selecting DUB filters/marks dubbed episodes and requests English-dubbed streams (`preferredAudio = "DUB"`) when launching playback" and "Unit tests cover ... SUB/DUB stream resolution."

2. **Premise 2 (Defect in Stream Resolution)**:
   In `DefaultAnimeRepository.getEpisodeStreams`, `isDemoFallbackUrl` fails to filter `https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4`. When scraping failover reaches `AnimeFlvSource`, `TearsOfSteel.mp4` (which has `audioTrack = "SUB"`) is treated as a valid stream, terminating the provider search loop early with `break`.

3. **Premise 3 (Direct Test Breakage)**:
   The worker's unit test `DubbedExperienceTest.testEpisodeStreamsAudioPreferencePriority` asserts that when `preferredAudio = "DUB"`, `firstDubStream.audioTrack.equals("DUB", ignoreCase = true) || firstDubStream.serverName.contains("Dub", ignoreCase = true)`. Because of Premise 2, this assertion fails with `AssertionError`.

4. **Premise 4 (Worker Handoff Discrepancy)**:
   `worker_upgrade_m1` claimed in `handoff.md` (lines 57-63 and 123-128) that `.\gradlew.bat testDebugUnitTest` passed with 0 failures across all 374+ tests. Direct empirical verification proves this claim is false.

5. **Inference & Conclusion**:
   Because the worker's work product fails the required unit tests and contains a confirmed failure mode in English Dub stream prioritization, the work product cannot be approved. Verdict: **REJECT**.

---

## 3. Challenges & Attack Scenarios

### [Critical] Challenge 1: Stream Resolution Audio Leak Breaks DUB Audio Selection
- **Assumption Challenged**: Multi-source failover always returns streams matching `preferredAudio` ("DUB" vs "SUB").
- **Attack Scenario**: User selects "DUB" playback on an anime title where primary scrapers (`Wco`, `Aniwatch`, `Kaa`) are temporarily unresponsive or return demo fallback URLs. The failover loop falls through to `AnimeFlvSource`, which returns `TearsOfSteel.mp4` (`audioTrack = "SUB"`).
- **Blast Radius**: User requesting English Dubbed audio is served an un-dubbed stream; `DubbedExperienceTest.testEpisodeStreamsAudioPreferencePriority` fails.
- **Mitigation**:
  1. Add `url.contains("TearsOfSteel", ignoreCase = true)` to `DefaultAnimeRepository.isDemoFallbackUrl`.
  2. In `DefaultAnimeRepository.getEpisodeStreams`, when `preferredAudio == "DUB"`, filter or skip sources that only provide SUB audio until all sources are exhausted.

### [Low] Challenge 2: AniList Public Rate Limiting (90 requests/minute)
- **Assumption Challenged**: Public GraphQL queries for User and MediaListCollection never fail or get throttled.
- **Attack Scenario**: Rapid repetitive synchronization by a user on a TV device exceeding AniList's 90 req/min limit.
- **Blast Radius**: AniList returns HTTP 429.
- **Mitigation Already in Place**: `AniListSyncManager` handles HTTP error codes gracefully, catching non-200 responses and returning `SyncResult(false, 0, "AniList lookup failed: HTTP 429")` without corrupting local data or crashing.

---

## 4. Stress Test Results

| Scenario | Expected Behavior | Actual Behavior | Result |
|---|---|---|---|
| `loginWithUsername` with blank strings (`""`, `"   "`, `"\t"`, `"\n"`) | Fails with `IllegalArgumentException("Username cannot be blank")` without HTTP call | Throws `IllegalArgumentException` caught as `Result.failure` | **PASS** |
| `loginWithUsername` with injection strings (`User"WithQuotes`, `User' OR '1'='1`, `}{ User(id: 1) { id } } #`, `<script>alert(1)</script>`) | Encoded into JSON variables; fails safely on HTTP/GraphQL error without crashing | Handled safely, JSON variables prevent syntax errors, no crashes | **PASS** |
| `loginWithUsername` whitespace trimming (`"   RealAnimeFan   "`) | Leading/trailing whitespace trimmed in GraphQL request | Variables contain trimmed username | **PASS** |
| Real username account sync offline/failure | Never calls `performDemoSync`; does NOT inject fake demo anime (id 16498) | `performDemoSync` not called, favorites contain 0 demo items | **PASS** |
| Real username starting with `"demo_"` (`"demo_slayer"`) | Never calls `performDemoSync` (token is null) | `performDemoSync` not called | **PASS** |
| Unauthenticated sync (`user = null`, `token = null`) | Fails with `"No AniList account linked."` | Returns failure message, 0 synced, 0 favorites | **PASS** |
| Explicit demo token (`"demo_token_123"`) | Triggers `performDemoSync`, populates 4 demo anime | Successfully populates 4 demo anime | **PASS** |
| Switching demo account to username account | Clears demo token (`token = null`), prevents subsequent demo sync | Demo token cleared, subsequent sync does not call demo sync | **PASS** |
| `MediaListCollection` with 0 lists (`lists: []`) | Returns `isSuccess = true`, 0 synced | Returns `isSuccess = true`, 0 synced | **PASS** |
| `MediaListCollection` with 0 entries in list | Returns `isSuccess = true`, 0 synced | Returns `isSuccess = true`, 0 synced | **PASS** |
| `MediaListCollection` null profile (private list) | Safely handles null collection without NPE | Safely returns `isSuccess = true`, 0 synced | **PASS** |
| `MediaListCollection` null titles, scores, images, episodes, progress | Resilient fallbacks: Romaji fallback, `"Anime #$mediaId"`, default score 8.0, 12 eps | All nulls fall back correctly, no NPEs | **PASS** |
| Stream failover when Provider 1 throws exception | Recovers and falls over to Provider 2 | Recovers and extracts streams from Provider 2 | **PASS** |
| Stream failover when Provider 1 returns empty list | Recovers and falls over to Provider 2 | Recovers and extracts streams from Provider 2 | **PASS** |
| Stream failover when all providers fail | Returns TanukiTV Emergency Fallback matching `preferredAudio` | Returns emergency fallback | **PASS** |
| Stream resolution when `preferredAudio = "DUB"` across built-in sources | Returns DUB audio stream as primary stream | **Returns `AnimeFlvSource` SUB stream (`TearsOfSteel.mp4`) instead of DUB stream** | **FAIL** |
| Full unit test suite `DubbedExperienceTest` | All 12 unit tests pass | `testEpisodeStreamsAudioPreferencePriority` fails with `AssertionError` | **FAIL** |

---

## 5. Unchallenged Areas

- **OAuth 2.0 Web Flow**: Token retrieval via web browser redirect was not challenged because the requirement specified public username authentication and personal access token linking without requiring TV browser redirection.
- **Real-Time AniList API Live Latency**: Live AniList network calls depend on upstream AniList uptime and are subject to real-world internet connectivity outside of local test harnesses.

---

## 6. Caveats

- **No Caveats**: All findings and test failures were directly reproduced and confirmed against the codebase.

---

## 7. Conclusion

`worker_upgrade_m1` made commendable progress implementing public AniList username login, demo sync isolation, and extended dub models. However, the work product cannot be approved due to a verified failure in stream audio priority failover:
1. `DefaultAnimeRepository.isDemoFallbackUrl` fails to filter `AnimeFlvSource`'s `TearsOfSteel.mp4` stream, causing SUB streams to leak into DUB playback requests.
2. `DubbedExperienceTest.testEpisodeStreamsAudioPreferencePriority` fails with `java.lang.AssertionError`.
3. The claim in worker's handoff that all tests passed is factually contradicted.

**Verdict**: **REJECT**

### Required Action Items for Worker:
1. Update `DefaultAnimeRepository.isDemoFallbackUrl` (lines 604-608 of `AnimeRepository.kt`):
   ```kotlin
   private fun isDemoFallbackUrl(url: String): Boolean {
       return url.contains("test-streams.mux.dev", ignoreCase = true) ||
           url.contains("BigBuckBunny", ignoreCase = true) ||
           url.contains("img_bipbop_adv_example", ignoreCase = true) ||
           url.contains("TearsOfSteel", ignoreCase = true)
   }
   ```
2. In `DefaultAnimeRepository.getEpisodeStreams`, when `preferredAudio == "DUB"`, ensure that sources returning solely `"SUB"` audio do not terminate the failover search early if DUB providers are still available.
3. Re-run `.\gradlew.bat testDebugUnitTest` and verify that all 12 tests in `DubbedExperienceTest` pass with exit code 0.

---

## 8. Verification Method

To independently verify this finding:
1. Run the test command:
   ```powershell
   .\gradlew.bat testDebugUnitTest --tests "com.darknet.animetv.dub.DubbedExperienceTest.testEpisodeStreamsAudioPreferencePriority"
   ```
   - **Observed Result**: Fails with `java.lang.AssertionError: Primary stream when preferredAudio=DUB should be DUB`.
2. Inspect `app/src/main/java/com/darknet/animetv/data/repository/AnimeRepository.kt`:
   - Line 549: `.filterNot { isDemoFallbackUrl(it.url) }`
   - Line 604: `isDemoFallbackUrl` (missing `TearsOfSteel`).
3. Inspect `app/src/main/java/com/darknet/animetv/data/source/AnimeFlvSource.kt`:
   - Line 275: `url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"` with default `audioTrack = "SUB"`.
