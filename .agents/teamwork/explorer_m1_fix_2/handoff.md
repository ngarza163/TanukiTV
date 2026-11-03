# Handoff Report — Explorer M1 Fix 2: Timestamp Preservation & Watch History Semantics

## 1. Observation

### 1.1 Model Definition in `Anime.kt`
- **File**: `app/src/main/java/com/darknet/animetv/data/model/Anime.kt` (lines 48–61)
- **Code**:
  ```kotlin
  data class WatchProgress(
      val animeId: String,
      val animeTitle: String,
      val posterUrl: String,
      val episodeId: String,
      val episodeNumber: Int,
      val episodeTitle: String,
      val positionMs: Long,
      val durationMs: Long,
      val lastWatchedTimestamp: Long = System.currentTimeMillis()
  ) {
      val progressPercentage: Float
          get() = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
  }
  ```
- **Observation**: `WatchProgress` defines `lastWatchedTimestamp: Long` with a default parameter of `System.currentTimeMillis()`. The caller has the option to pass an explicit timestamp (e.g., imported history, offline synced session, or historical records).

### 1.2 Unconditional Timestamp Overwrite in `saveWatchProgress`
- **File**: `app/src/main/java/com/darknet/animetv/data/storage/AppDatabase.kt` (lines 118–132)
- **Code**:
  ```kotlin
  fun saveWatchProgress(progress: WatchProgress) {
      val db = dbHelper.writableDatabase
      val values = ContentValues().apply {
          put("anime_id", progress.animeId)
          put("anime_title", progress.animeTitle)
          put("poster_url", progress.posterUrl)
          put("episode_id", progress.episodeId)
          put("episode_number", progress.episodeNumber)
          put("episode_title", progress.episodeTitle)
          put("position_ms", progress.positionMs)
          put("duration_ms", progress.durationMs)
          put("updated_at", System.currentTimeMillis())
      }
      db.insertWithOnConflict("watch_history", null, values, SQLiteDatabase.CONFLICT_REPLACE)
  }
  ```
- **Observation**: Line 129 unconditionally overwrites `"updated_at"` with `System.currentTimeMillis()`. The property `progress.lastWatchedTimestamp` is completely ignored and discarded.

### 1.3 Empirical Test Execution & Challenger Finding
- **File**: `app/src/test/java/com/darknet/animetv/storage/AppStorageEmpiricalTest.kt` (lines 174–205)
- **Execution Command**: `.\gradlew.bat testDebugUnitTest --rerun-tasks --info`
- **Verbatim Output**:
  ```
  AppStorageEmpiricalTest > testLastWatchedTimestampPreservationHypothesis STANDARD_OUT
      Empirical Check - customTimestamp: 123456789000 vs actualStored: 1793075944076 (Preserved=false)
  ```
- **Challenger M1-2 Report (`challenger_m1_2/handoff.md`)**:
  - Finding 3 explicitly documents this defect:
    `saveWatchProgress discards progress.lastWatchedTimestamp, unconditionally overwriting it with System.currentTimeMillis(). Any historical sync or imported playback progress loses its true timestamp.`

### 1.4 Cursor Reading in `getWatchProgress` and `getAllWatchHistory`
- **File**: `app/src/main/java/com/darknet/animetv/data/storage/AppDatabase.kt` (lines 145–160, 175–192)
- **Code**:
  ```kotlin
  // In getWatchProgress (lines 156-157):
  lastWatchedTimestamp = it.getLong(it.getColumnIndexOrThrow("updated_at"))

  // In getAllWatchHistory (lines 173-188):
  val cursor = db.query("watch_history", null, null, null, null, null, "updated_at DESC")
  ...
  lastWatchedTimestamp = it.getLong(it.getColumnIndexOrThrow("updated_at"))
  ```
- **Observation**:
  - `getAllWatchHistory` sorts by `"updated_at DESC"`. Overwriting timestamps with current time disrupts the chronological ordering of the user's watch history.
  - If a row in the database has `updated_at` as NULL or `<= 0` (e.g. from an unpopulated column, direct raw insert, or legacy migration), `it.getLong(...)` returns `0L`, which violates the expectation of a valid positive epoch timestamp.
  - Furthermore, `getWatchProgress` and `getAllWatchHistory` currently read string columns directly without null-coalescing (`?: ""`), triggering `NullPointerException` if external or legacy records contain null strings (Observation 1 of Challenger M1-2).

---

## 2. Logic Chain

1. **Root Cause**: `saveWatchProgress` hardcodes `System.currentTimeMillis()` at line 129 rather than inspecting `progress.lastWatchedTimestamp`.
2. **Impact on Data Fidelity**:
   - When importing watch history or syncing progress offline, the actual watch timestamp is permanently replaced with the synchronization wall-clock time.
   - Calling `storage.saveWatchProgress(progress)` followed by `storage.getWatchProgress(animeId)` yields an object where `original.lastWatchedTimestamp != retrieved.lastWatchedTimestamp` whenever a custom timestamp was supplied.
   - Because `getAllWatchHistory()` orders by `"updated_at DESC"`, batch-syncing older items pushes them to the top of the "Continue Watching" row on Android TV, displacing genuinely recently watched content.
3. **Boundary Condition (0 or negative)**:
   - When `WatchProgress` is constructed with an uninitialized or sentinel timestamp (`0L` or `<= 0L`), saving `0L` or a negative value into SQLite would break sorting and display "Jan 1, 1970" in UI badges.
   - Defaulting to `System.currentTimeMillis()` ONLY when `progress.lastWatchedTimestamp <= 0L` resolves both requirements: valid timestamps (> 0) are strictly preserved, while uninitialized/sentinel values are automatically converted to the current time.
4. **Retrieval Safety**:
   - In `getWatchProgress` and `getAllWatchHistory`, reading `updated_at` should likewise ensure that any legacy row with `0` or negative values defaults to `System.currentTimeMillis()`, guaranteeing that in-memory `WatchProgress` instances always contain a valid positive timestamp.
   - String fields should use `?: ""` to maintain null-safety consistency with the rest of the database layer.
5. **Test Alignment**:
   - In `AppStorageEmpiricalTest.kt`, line 203 currently asserted `actualTimestamp in beforeSave..afterSave` to confirm the bug existed. Once the fix is applied, that assertion will fail unless updated to assert `assertEquals(customTimestamp, actualTimestamp)`.

---

## 3. Caveats

- **Existing Stored Records**: Database rows previously written with the old `saveWatchProgress` will contain the timestamp when they were written. The fix will preserve them as positive numbers without altering their existing values.
- **Clock Drift**: `System.currentTimeMillis()` is wall-clock time (subject to device clock changes). This matches standard Android persistence practices for watch history.
- **No Caveats regarding Schema**: The SQLite table `watch_history` already defines `updated_at INTEGER`, which is an 8-byte signed integer capable of storing the full range of Kotlin `Long` values. No schema migration or `ALTER TABLE` is required.

---

## 4. Conclusion

### 4.1 Recommended Implementation in `AppDatabase.kt`

#### Change 1: `saveWatchProgress` (lines 118–132)
```kotlin
    fun saveWatchProgress(progress: WatchProgress) {
        val db = dbHelper.writableDatabase
        val timestamp = if (progress.lastWatchedTimestamp > 0L) {
            progress.lastWatchedTimestamp
        } else {
            System.currentTimeMillis()
        }
        val values = ContentValues().apply {
            put("anime_id", progress.animeId)
            put("anime_title", progress.animeTitle)
            put("poster_url", progress.posterUrl)
            put("episode_id", progress.episodeId)
            put("episode_number", progress.episodeNumber)
            put("episode_title", progress.episodeTitle)
            put("position_ms", progress.positionMs)
            put("duration_ms", progress.durationMs)
            put("updated_at", timestamp)
        }
        db.insertWithOnConflict("watch_history", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }
```

#### Change 2: `getWatchProgress` (lines 134–161)
```kotlin
    fun getWatchProgress(animeId: String): WatchProgress? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "watch_history",
            null,
            "anime_id = ?",
            arrayOf(animeId),
            null,
            null,
            null
        )
        cursor.use {
            if (it.moveToFirst()) {
                val updatedAt = it.getLong(it.getColumnIndexOrThrow("updated_at"))
                return WatchProgress(
                    animeId = it.getString(it.getColumnIndexOrThrow("anime_id")) ?: "",
                    animeTitle = it.getString(it.getColumnIndexOrThrow("anime_title")) ?: "",
                    posterUrl = it.getString(it.getColumnIndexOrThrow("poster_url")) ?: "",
                    episodeId = it.getString(it.getColumnIndexOrThrow("episode_id")) ?: "",
                    episodeNumber = it.getInt(it.getColumnIndexOrThrow("episode_number")),
                    episodeTitle = it.getString(it.getColumnIndexOrThrow("episode_title")) ?: "",
                    positionMs = it.getLong(it.getColumnIndexOrThrow("position_ms")),
                    durationMs = it.getLong(it.getColumnIndexOrThrow("duration_ms")),
                    lastWatchedTimestamp = if (updatedAt > 0L) updatedAt else System.currentTimeMillis()
                )
            }
        }
        return null
    }
```

#### Change 3: `getAllWatchHistory` (lines 163–193)
```kotlin
    fun getAllWatchHistory(): List<WatchProgress> {
        val list = mutableListOf<WatchProgress>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "watch_history",
            null,
            null,
            null,
            null,
            null,
            "updated_at DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                val updatedAt = it.getLong(it.getColumnIndexOrThrow("updated_at"))
                list.add(
                    WatchProgress(
                        animeId = it.getString(it.getColumnIndexOrThrow("anime_id")) ?: "",
                        animeTitle = it.getString(it.getColumnIndexOrThrow("anime_title")) ?: "",
                        posterUrl = it.getString(it.getColumnIndexOrThrow("poster_url")) ?: "",
                        episodeId = it.getString(it.getColumnIndexOrThrow("episode_id")) ?: "",
                        episodeNumber = it.getInt(it.getColumnIndexOrThrow("episode_number")),
                        episodeTitle = it.getString(it.getColumnIndexOrThrow("episode_title")) ?: "",
                        positionMs = it.getLong(it.getColumnIndexOrThrow("position_ms")),
                        durationMs = it.getLong(it.getColumnIndexOrThrow("duration_ms")),
                        lastWatchedTimestamp = if (updatedAt > 0L) updatedAt else System.currentTimeMillis()
                    )
                )
            }
        }
        return list
    }
```

### 4.2 Recommended Updates in `AppStorageEmpiricalTest.kt`

Update `testLastWatchedTimestampPreservationHypothesis` (lines 175–205):
```kotlin
    @Test
    fun testLastWatchedTimestampPreservationHypothesis() {
        val customTimestamp = 123456789000L // historical timestamp (year 1973)
        val progress = WatchProgress(
            animeId = "timestamp_test",
            animeTitle = "Timestamp Test",
            posterUrl = "url",
            episodeId = "ep1",
            episodeNumber = 1,
            episodeTitle = "Ep 1",
            positionMs = 1000L,
            durationMs = 2000L,
            lastWatchedTimestamp = customTimestamp
        )
        storage.saveWatchProgress(progress)

        val retrieved = storage.getWatchProgress("timestamp_test")
        assertNotNull(retrieved)
        val actualTimestamp = retrieved!!.lastWatchedTimestamp
        assertEquals("Custom lastWatchedTimestamp should be preserved across storage round-trip",
            customTimestamp, actualTimestamp)
    }

    @Test
    fun testLastWatchedTimestampDefaultWhenZeroOrNegative() {
        val before = System.currentTimeMillis()
        val zeroProgress = WatchProgress(
            animeId = "zero_ts",
            animeTitle = "Zero Timestamp",
            posterUrl = "url",
            episodeId = "ep1",
            episodeNumber = 1,
            episodeTitle = "Ep 1",
            positionMs = 100L,
            durationMs = 200L,
            lastWatchedTimestamp = 0L
        )
        storage.saveWatchProgress(zeroProgress)
        val after = System.currentTimeMillis()
        val retrievedZero = storage.getWatchProgress("zero_ts")
        assertNotNull(retrievedZero)
        assertTrue("When lastWatchedTimestamp is 0, should default to current time window",
            retrievedZero!!.lastWatchedTimestamp in before..after)

        val beforeNeg = System.currentTimeMillis()
        val negProgress = WatchProgress(
            animeId = "neg_ts",
            animeTitle = "Negative Timestamp",
            posterUrl = "url",
            episodeId = "ep1",
            episodeNumber = 1,
            episodeTitle = "Ep 1",
            positionMs = 100L,
            durationMs = 200L,
            lastWatchedTimestamp = -100L
        )
        storage.saveWatchProgress(negProgress)
        val afterNeg = System.currentTimeMillis()
        val retrievedNeg = storage.getWatchProgress("neg_ts")
        assertNotNull(retrievedNeg)
        assertTrue("When lastWatchedTimestamp is negative, should default to current time window",
            retrievedNeg!!.lastWatchedTimestamp in beforeNeg..afterNeg)
    }
```

### 4.3 Round-Trip Consistency Matrix

| Input `lastWatchedTimestamp` | `saveWatchProgress` Action | Stored in SQLite (`updated_at`) | `getWatchProgress` Value | Round-Trip Invariant |
|---|---|---|---|---|
| Historical `123456789000L` (> 0) | Preserved | `123456789000L` | `123456789000L` | `retrieved == original` (Strict parity) |
| Default `System.currentTimeMillis()` (> 0) | Preserved | Construction time `t0` | `t0` | `retrieved == original` (Strict parity) |
| `0L` (Not set / Sentinel) | Defaults to `System.currentTimeMillis()` | Save time `t_save` | `t_save` | Positivity guaranteed (`retrieved > 0L`) |
| Negative `-500L` (Invalid) | Defaults to `System.currentTimeMillis()` | Save time `t_save` | `t_save` | Positivity guaranteed (`retrieved > 0L`) |
| Legacy row with `NULL` or `0` | N/A | `NULL` / `0` | `System.currentTimeMillis()` | Positivity guaranteed (`retrieved > 0L`) |

---

## 5. Verification Method

### 5.1 Verification Commands
1. Run JVM Unit Test Suite:
   ```powershell
   .\gradlew.bat testDebugUnitTest --rerun-tasks --info
   ```
   *Expected Outcome*: `BUILD SUCCESSFUL`, `testLastWatchedTimestampPreservationHypothesis` passes with `customTimestamp == actualStored`, and all edge-case tests pass.

2. Run Kotlin Compilation & Assemble APK:
   ```powershell
   .\gradlew.bat compileDebugKotlin assembleDebug
   ```
   *Expected Outcome*: `BUILD SUCCESSFUL`, zero compiler warnings or errors on `WatchProgress` fields.

### 5.2 Files to Inspect
- `app/src/main/java/com/darknet/animetv/data/storage/AppDatabase.kt`: Verify lines 118–193 implement the timestamp conditional and cursor extraction.
- `app/src/test/java/com/darknet/animetv/storage/AppStorageEmpiricalTest.kt`: Verify line 203 asserts exact equality for preserved timestamp.

### 5.3 Invalidation Conditions
- If `saveWatchProgress` uses `if (progress.lastWatchedTimestamp != 0L)` instead of `> 0L`, negative timestamps will be persisted and cause reverse sorting errors.
- If `getWatchProgress` fails to check `updatedAt > 0L`, legacy rows with NULL will return `0L` (epoch 0).
- If the test in `AppStorageEmpiricalTest.kt` is not updated from `actualTimestamp in beforeSave..afterSave`, the test suite will fail once the bug is fixed.
