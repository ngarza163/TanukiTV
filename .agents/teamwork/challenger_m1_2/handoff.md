# Handoff Report — Challenger M1-2: Persistence & Data Integrity Stress Verification

## Verdict: REQUEST_CHANGES

---

## 1. Observation

Direct empirical observations and execution outputs from stress testing `AppStorage` and SQLite schema persistence:

1. **Null Column Crash Defect (`NullPointerException`)**:
   - Files: `app/src/main/java/com/darknet/animetv/data/storage/AppDatabase.kt` (lines 148–156, 265–273, 315–323, 345–354, 375–383)
   - Code:
     ```kotlin
     // In getWatchProgress:
     animeTitle = it.getString(it.getColumnIndexOrThrow("anime_title")),
     posterUrl = it.getString(it.getColumnIndexOrThrow("poster_url")),
     episodeId = it.getString(it.getColumnIndexOrThrow("episode_id")),
     episodeTitle = it.getString(it.getColumnIndexOrThrow("episode_title")),

     // In getFavorite:
     title = it.getString(it.getColumnIndexOrThrow("title")),
     posterUrl = it.getString(it.getColumnIndexOrThrow("poster_url")),
     backdropUrl = it.getString(it.getColumnIndexOrThrow("backdrop_url")),
     status = it.getString(it.getColumnIndexOrThrow("status")),
     synopsis = it.getString(it.getColumnIndexOrThrow("synopsis"))
     ```
   - Schema in `AppDatabaseHelper.onCreate`:
     None of these columns (`anime_title`, `poster_url`, `backdrop_url`, `status`, `synopsis`, etc.) are declared with `NOT NULL` constraints in `CREATE TABLE watch_history` or `CREATE TABLE favorites` or `CREATE TABLE sources`.
   - Test execution command: `.\gradlew.bat testDebugUnitTest`
   - Verbatim test output from `AppStorageEmpiricalTest`:
     ```
     CONFIRMED BUG: NullPointerException when reading favorites row with NULL columns: getString(...) must not be null
     CONFIRMED BUG: NullPointerException when reading sources row with NULL columns: getString(...) must not be null
     CONFIRMED BUG: NullPointerException when reading watch_history row with NULL columns: getString(...) must not be null
     ```
   - In production, scraper responses or API catalog entries (e.g., Jikan, Consumet, AnimeFLV) often have null synopsis, poster, or backdrop URLs. When persisted and read, `AppStorage` throws an unhandled `NullPointerException` which crashes the application.

2. **Data Corruption via Delimiter Collision in Genres Serialization**:
   - File: `app/src/main/java/com/darknet/animetv/data/storage/AppDatabase.kt` lines 229, 271, 285
   - Code:
     ```kotlin
     // Serialization in addFavorite:
     put("genres", anime.genres.joinToString(","))

     // Deserialization in getFavorite / getFavorites:
     val genresStr = it.getString(it.getColumnIndexOrThrow("genres")) ?: ""
     genres = if (genresStr.isNotEmpty()) genresStr.split(",") else emptyList()
     ```
   - Test execution output:
     ```
     Original genres: [Sci-Fi, Mecha, Action, Romance, Comedy]
     Retrieved genres: [Sci-Fi,  Mecha, Action, Romance,  Comedy]
     Empirical Check - Comma in genre preserved exactly: false
     ```
   - Naive unescaped comma splitting corrupts genre values containing commas (e.g. `"Sci-Fi, Mecha"` turns into two malformed tokens `"Sci-Fi"` and `" Mecha"`).

3. **Silent Timestamp Loss in `saveWatchProgress`**:
   - File: `app/src/main/java/com/darknet/animetv/data/storage/AppDatabase.kt` line 129
   - Code:
     ```kotlin
     fun saveWatchProgress(progress: WatchProgress) {
         val db = dbHelper.writableDatabase
         val values = ContentValues().apply {
             ...
             put("updated_at", System.currentTimeMillis())
         }
         db.insertWithOnConflict("watch_history", null, values, SQLiteDatabase.CONFLICT_REPLACE)
     }
     ```
   - Model definition (`app/src/main/java/com/darknet/animetv/data/model/Anime.kt` line 57):
     `data class WatchProgress(..., val lastWatchedTimestamp: Long = System.currentTimeMillis())`
   - Test execution output:
     ```
     Empirical Check - customTimestamp: 123456789000 vs actualStored: 1793075538130 (Preserved=false)
     ```
   - `saveWatchProgress` discards `progress.lastWatchedTimestamp`, unconditionally overwriting it with `System.currentTimeMillis()`. Any historical sync or imported playback progress loses its true timestamp.

4. **Resource Leak / Unclosed Database Warning**:
   - File: `app/src/main/java/com/darknet/animetv/data/storage/AppDatabase.kt` line 114
   - Verbatim stack trace from `testDebugUnitTest`:
     ```
     System.logW: A resource was acquired at attached stack trace but never released. See java.io.Closeable for information on avoiding resource leaks.
     java.lang.Throwable: Explicit termination method 'close' not called
     	at dalvik.system.CloseGuard.open(CloseGuard.java)
     	at android.database.sqlite.SQLiteDatabase.open(SQLiteDatabase.java)
     	at com.darknet.animetv.data.storage.AppStorage.saveWatchProgress(AppDatabase.kt:119)
     ```
   - `AppStorage` creates `AppDatabaseHelper` but provides no `close()` method and never closes the underlying database helper.

5. **Empirical Robustness Verified**:
   - 21 test cases executed via Robolectric with 0 failures:
     - Empty strings and whitespace keys (`""`, `"   "`) in `anime_id`, `animeTitle`, `episodeTitle`, `posterUrl` persist without SQL syntax errors.
     - Extreme numeric boundaries (`Long.MAX_VALUE`, `Long.MIN_VALUE`, `Int.MAX_VALUE`, negative positions) persist and round-trip accurately.
     - Rapid sequential writes (200 consecutive updates for the same anime) accurately retain the latest episode and position.
     - Multi-threaded concurrent writes (8 threads x 25 writes = 200 concurrent transactions) completed with 0 errors or lock collisions.
     - Rapid toggling of favorites (102 consecutive toggles) maintained strict parity.
     - Unicode support (Japanese Kanji "葬送のフリーレン", Arabic, Cyrillic, Emojis "🔥⚔️", special quotes and punctuation) stored and retrieved with 100% fidelity.
     - SQL injection payloads (`'; DROP TABLE watch_history; --`) were safely parameterized by `ContentValues` with 0 vulnerability.
     - Default seeded sources (AnimeFLV, Consumet, Jikan) are seeded on first creation and can be toggled and deleted.

---

## 2. Logic Chain

1. From Observation 1: In Kotlin, non-nullable types like `String` generate implicit bytecode assertions (`Intrinsics.checkNotNullParameter`). Because the SQLite schema allows `NULL` across all non-key columns, any `NULL` column returned by SQLite cursor causes `cursor.getString(...)` to return `null`, immediately throwing `NullPointerException`.
2. From Observation 2: Because genres are joined using `joinToString(",")` and split using `split(",")`, any comma within a genre item or leading/trailing whitespace is corrupted upon retrieval.
3. From Observation 3: In `saveWatchProgress`, ignoring `progress.lastWatchedTimestamp` means the caller has no ability to specify when an episode was actually watched (e.g. syncing offline watch history or restoring backup records).
4. From Observation 4: Leaving `AppDatabaseHelper` unclosed triggers Android's `CloseGuard` warning on finalization and can lead to leaked SQLite connection handles.
5. While the storage layer is robust under concurrency, Unicode, and numeric extremes (Observation 5), the null-dereference crash defect (Observation 1) poses an immediate stability risk for the upcoming UI and scraper integration milestones.

---

## 3. Caveats

- **Test Infrastructure**: `app/build.gradle` required the addition of `testImplementation 'junit:junit:4.13.2'`, `testImplementation 'org.robolectric:robolectric:4.12.2'`, and `testImplementation 'androidx.test:core:1.6.1'` to allow JVM execution of Android SQLite tests.
- **SQLite Migrations**: `AppDatabaseHelper.onUpgrade` currently drops tables and recreates them (`DROP TABLE IF EXISTS ...`). Incremental migrations were not tested as the schema is currently at version 1.

---

## 4. Conclusion

**Verdict: REQUEST_CHANGES**

Worker M1 must address the following required items:

1. **Fix Null Dereference Hazards in `AppDatabase.kt`**:
   - Provide null-coalescing fallbacks for all string columns retrieved from cursors:
     ```kotlin
     // In WatchProgress:
     animeTitle = it.getString(it.getColumnIndexOrThrow("anime_title")) ?: "",
     posterUrl = it.getString(it.getColumnIndexOrThrow("poster_url")) ?: "",
     episodeId = it.getString(it.getColumnIndexOrThrow("episode_id")) ?: "",
     episodeTitle = it.getString(it.getColumnIndexOrThrow("episode_title")) ?: "",

     // In Anime (favorites):
     title = it.getString(it.getColumnIndexOrThrow("title")) ?: "",
     posterUrl = it.getString(it.getColumnIndexOrThrow("poster_url")) ?: "",
     backdropUrl = it.getString(it.getColumnIndexOrThrow("backdrop_url")) ?: "",
     status = it.getString(it.getColumnIndexOrThrow("status")) ?: "Ongoing",
     synopsis = it.getString(it.getColumnIndexOrThrow("synopsis")) ?: "",

     // In CustomSource:
     name = it.getString(it.getColumnIndexOrThrow("name")) ?: "",
     baseUrl = it.getString(it.getColumnIndexOrThrow("base_url")) ?: "",
     animeListSelector = it.getString(it.getColumnIndexOrThrow("anime_list_selector")) ?: "",
     episodeSelector = it.getString(it.getColumnIndexOrThrow("episode_selector")) ?: "",
     streamUrlSelector = it.getString(it.getColumnIndexOrThrow("stream_url_selector")) ?: "",
     sourceType = it.getString(it.getColumnIndexOrThrow("source_type")) ?: "HTML_SCRAPER"
     ```
2. **Preserve `lastWatchedTimestamp` in `saveWatchProgress`**:
   ```kotlin
   put("updated_at", if (progress.lastWatchedTimestamp > 0) progress.lastWatchedTimestamp else System.currentTimeMillis())
   ```
3. **Robust Genres List Serialization**:
   - Use JSON array serialization (via `org.json.JSONArray` or Gson) or escape commas rather than raw `joinToString(",")` / `split(",")`.
4. **Lifecycle Resource Clean Up**:
   - Implement `close()` in `AppStorage` delegating to `dbHelper.close()`.

---

## 5. Verification Method

### 5.1 Independent Verification Commands
Run the empirical test suite:
```powershell
.\gradlew.bat testDebugUnitTest --info
```
*Expected result*: `BUILD SUCCESSFUL` (21 tests in `AppStorageEmpiricalTest` pass).

Verify compilation and APK build:
```powershell
.\gradlew.bat compileDebugKotlin assembleDebug
```
*Expected result*: `BUILD SUCCESSFUL` with APK at `app/build/outputs/apk/debug/app-debug.apk`.

### 5.2 Files to Inspect
- `app/src/test/java/com/darknet/animetv/storage/AppStorageEmpiricalTest.kt`: Contains all 21 empirical challenge test cases.
- `app/build/test-results/testDebugUnitTest/TEST-com.darknet.animetv.storage.AppStorageEmpiricalTest.xml`: Output report of test execution.
- `app/src/main/java/com/darknet/animetv/data/storage/AppDatabase.kt`: Target implementation file requiring fixes.

### 5.3 Invalidation Conditions
- If any string column in `AppDatabase.kt` is read without a `?: ""` fallback, inserting a row with NULL fields from external sources will crash with `NullPointerException`.
