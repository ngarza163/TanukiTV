# Dispatch: Worker M1 Iteration 2 (Remediation)
Target: c:\Users\ngarz\Documents\antigravity\blissful-planck
Working Directory: c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\worker_m1_gen2
User Request: c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\ORIGINAL_REQUEST.md
Explorer Reports:
- Explorer 1 (Null Safety): c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\explorer_m1_fix_1\handoff.md
- Explorer 2 (Timestamp): c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\explorer_m1_fix_2\handoff.md
- Explorer 3 (Genres & Closeable): c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\explorer_m1_fix_3\handoff.md

Tasks:
1. Apply null-coalescing fallbacks (`?: ""`, `?: "Ongoing"`, `?: "HTML_SCRAPER"`) to all cursor string reads in `app/src/main/java/com/darknet/animetv/data/storage/AppDatabase.kt`.
2. In `saveWatchProgress`, preserve `progress.lastWatchedTimestamp` if > 0.
3. In `AppStorage`, implement `Closeable` and add `close()` delegating to `dbHelper.close()`.
4. In `addFavorite` / `getFavorite` / `getFavorites`, use JSON array serialization via `org.json.JSONArray` for genres with backwards-compatible comma fallback.
5. In `app/src/test/java/com/darknet/animetv/storage/AppStorageEmpiricalTest.kt`, update assertions from expecting bug exceptions to asserting that defensive fallbacks and exact preservation work, and add `storage.close()` in `tearDown()`.
6. Run `.\gradlew.bat testDebugUnitTest` and `.\gradlew.bat assembleDebug` to confirm all 21 tests pass with 0 errors and 0 warnings.
7. Write handoff report to `c:\Users\ngarz\Documents\antigravity\blissful-planck\.agents\teamwork\worker_m1_gen2\handoff.md`.
