# Handoff Report: R2 — Full Dubbed Anime & Dubbed Episodes Experience

## Summary
Survey and architecture investigation for R2 (Full Dubbed Anime & Dubbed Episodes Experience) covering model definitions (`Anime`, `Episode`, `VideoSource`), repository contracts (`AnimeRepository`, `DefaultAnimeRepository`, `AnimeSource`), catalog/streaming sources (`AniListCatalogSource`, `WcoSource`, `AniwatchSource`, `KaaSource`), and TV UI screens (`HomeScreen`, `SearchScreen`, `DetailsScreen`, `TvPlayerScreen`, `TvAnimeCard`).

---

## 1. Observation

### 1.1 Model Definitions (`app/src/main/java/com/darknet/animetv/data/model/Anime.kt`)
- `Anime` (lines 3–15):
  ```kotlin
  data class Anime(
      val id: String,
      val title: String,
      val posterUrl: String = "",
      val backdropUrl: String = "",
      val score: Double = 0.0,
      val year: Int = 0,
      val status: String = "Ongoing",
      val genres: List<String> = emptyList(),
      val synopsis: String = "",
      val totalEpisodes: Int = 0,
      val sourceId: String = "default"
  )
  ```
  *Currently lacks dub metadata fields (`hasDub`, `dubbedEpisodesCount`).*

- `Episode` (lines 24–31):
  ```kotlin
  data class Episode(
      val id: String,
      val animeId: String,
      val number: Int,
      val title: String,
      val thumbnail: String = "",
      val releaseDate: String = ""
  )
  ```
  *Currently lacks dub metadata fields (`hasDub: Boolean`, `audioTrack: String`).*

- `VideoSource` (lines 33–40):
  ```kotlin
  data class VideoSource(
      val serverName: String,
      val url: String,
      val quality: String = "1080p",
      val isHls: Boolean = url.contains(".m3u8", ignoreCase = true),
      val headers: Map<String, String> = emptyMap(),
      val subtitles: List<SubtitleTrack> = emptyList()
  )
  ```
  *Currently lacks `audioTrack: String = "SUB"` ("SUB" | "DUB").*

### 1.2 Repository & Sources
- `AnimeRepository` (`app/src/main/java/com/darknet/animetv/data/repository/AnimeRepository.kt`, lines 38–54):
  Currently declares `getTrending()`, `getPopular()`, `getRecentEpisodes()`, `search()`, `getAnimeDetail()`, `getEpisodeStreams()`, `testAllSources()`, `getAiringSchedule()`, `getFranchiseRelations()`, `getRecommendations()`, `benchmarkSource()`, `benchmarkAllSources()`, `getCharacterCast()`. It does not yet declare `getDubbedAnime()`.
- `DefaultAnimeRepository` (lines 262–358):
  `getEpisodeStreams` accepts `(animeTitle: String, episodeNumber: Int, preferredSourceId: String? = null)`. When searching sources, it searches for `animeTitle`.
- `AnimeSource` (`app/src/main/java/com/darknet/animetv/data/source/AnimeSource.kt`, lines 11–28):
  Declares `getTrending()`, `getPopular()`, `getRecentEpisodes()`, `search()`, `getAnimeDetail()`, `getEpisodeStreams()`, `testConnection()`.
- `WcoSource` (`app/src/main/java/com/darknet/animetv/data/source/WcoSource.kt`):
  Line 36: `scrapeAnimeCatalog("$baseUrl/dubbed-anime-list")`
  Line 206: `VideoSource(..., quality = "720p (Dub)", ...)`
  *WCO is inherently an English-dubbed provider.*
- `AniwatchSource` (`app/src/main/java/com/darknet/animetv/data/source/AniwatchSource.kt`):
  Lines 285–287:
  ```kotlin
  val dataHash = item.attr("data-hash")
  val srvName = item.attr("data-server-name").ifEmpty { item.text().trim().ifEmpty { "Aniwatch" } }
  val srvType = item.attr("data-type").uppercase().ifEmpty { "SUB" }
  ```
  *Aniwatch already inspects `data-type` on OtakuThemes server buttons which returns `"SUB"` or `"DUB"`.*
- `KaaSource` (`app/src/main/java/com/darknet/animetv/data/source/KaaSource.kt`):
  Line 125: `val locales = mutableListOf("ja-JP", "en-US")`
  Supports English audio via `en-US` locale.

### 1.3 UI Screens & Components
- `HomeScreen` (`app/src/main/java/com/darknet/animetv/ui/screens/HomeScreen.kt`):
  Lines 67–88: Loads `continueWatchingList`, `trendingList`, `popularList`, `recentList`.
  Lines 208–289: Renders 5 shelves: "Continue Watching", "Trending Now", "Popular Hits", "Recently Updated Episodes", "Top Rated Anime".
  *Currently lacks a dedicated `"🎙️ Dubbed Anime & Episodes"` shelf.*
- `TvAnimeCard` (`app/src/main/java/com/darknet/animetv/ui/components/TvAnimeCard.kt`):
  Lines 54–60: Accepts `badgeText: String? = null`.
  Lines 183–206: Renders `badgeText` in top-left position when non-null.
- `SearchScreen` (`app/src/main/java/com/darknet/animetv/ui/screens/SearchScreen.kt`):
  Lines 107–112: `genres` list currently contains:
  `listOf("All", "Action", "Adventure", "Comedy", "Drama", "Fantasy", "Sci-Fi", "Romance", "Sports", "Supernatural", "Mystery", "Slice of Life")`.
  *Currently lacks a `"🎙️ Dubbed"` filter chip.*
- `DetailsScreen` (`app/src/main/java/com/darknet/animetv/ui/screens/DetailsScreen.kt`):
  Lines 450–484: Episode shelf header displays `Text("Episodes (${episodes.size})")` and chunk chips for >50 episodes.
  Lines 370–413: Play/Resume button invokes `onPlayEpisode(displayAnime, targetEpisode)`.
  Lines 519–521: Episode cards invoke `onPlayEpisode(displayAnime, ep)`.
  *Currently lacks an interactive SUB / DUB toggle pill.*
- `MainActivity` (`app/src/main/java/com/darknet/animetv/MainActivity.kt`):
  Lines 93–98: `PlaybackSession(anime, episode, allEpisodes, initialSeekMs)`.
  Lines 175–224: `startPlayback(anime, episode, episodesList, initialSeekMs)`.
  *Does not yet carry preferred audio track from Details to Player.*
- `TvPlayerScreen` (`app/src/main/java/com/darknet/animetv/player/TvPlayerScreen.kt`):
  Line 122: `var audioMode by remember { mutableStateOf(userSettings.preferredAudio) }`.
  Line 206: `val queryTitle = if (audioMode == "DUB") "${anime.title} (Dub)" else anime.title`.
  Lines 1099–1102: `onToggleAudioMode` toggles between `"SUB"` and `"DUB"` and invalidates streams.

### 1.4 Test Suite
- Executed `.\gradlew.bat testDebugUnitTest --tests com.darknet.animetv.ui.HomeScreenDataTest` via `run_command` (task-83):
  Completed successfully with exit code 0 in 9s.
- Existing tests in `app/src/test/java/com/darknet/animetv/ui/` (`HomeScreenDataTest`, `SearchScreenDataTest`, `DetailsScreenDataTest`, `TvAnimeCardTest`) test data transformation and view-model bindings.

---

## 2. Logic Chain

1. **Model Non-Breaking Extension**:
   - In `Anime.kt`:
     Adding `hasDub: Boolean = false` and `dubbedEpisodesCount: Int = 0` to `Anime` provides default values, ensuring all existing constructors in tests, repository methods, and storage continue to compile without changes.
     Adding `hasDub: Boolean = false` and `audioTrack: String = "SUB"` to `Episode` provides default values.
     Adding `audioTrack: String = "SUB"` to `VideoSource` provides default values.
2. **Repository Contract Expansion**:
   - In `AnimeSource.kt`:
     Adding `suspend fun getDubbedAnime(): List<Anime> = emptyList()` to `AnimeSource` allows any existing source to compile without forcing immediate implementation, while permitting sources with dub catalogs (`WcoSource`, `AniwatchSource`, `KaaSource`, `AniListCatalogSource`) to override it.
   - In `AnimeRepository.kt`:
     Declaring `suspend fun getDubbedAnime(): List<Anime>` and implementing it in `DefaultAnimeRepository`:
     a. Primary live query: AniList licensed catalog + WCO (`$baseUrl/dubbed-anime-list`) + Aniwatch (`$baseUrl/dubbed-anime`) + KAA (`en-US`).
     b. Marking items with `hasDub = true` and `dubbedEpisodesCount = totalEpisodes`.
     c. Fallback mechanism: When offline or during network dropouts, returns a rich curated offline catalog of 12 iconic English-dubbed anime (Cowboy Bebop, Fullmetal Alchemist: Brotherhood, Death Note, Attack on Titan, Jujutsu Kaisen, Dragon Ball Z, Cyberpunk: Edgerunners, etc.) with valid scores, poster URLs, and backdrop images.
   - In `getEpisodeStreams`:
     Add optional `preferredAudio: String = "SUB"` parameter. When `preferredAudio == "DUB"`, prioritize streams where `audioTrack == "DUB"` or `serverName.contains("Dub", ignoreCase = true)`.
3. **UI Integration**:
   - **HomeScreen (`HomeScreen.kt`)**:
     Query `dubbedList = repository.getDubbedAnime()` in `LaunchedEffect(Unit)` and display a dedicated horizontal shelf `"🎙️ Dubbed Anime & Episodes"` directly beneath `Trending Now` or `Popular Hits`. Each card in this shelf passes `badgeText = "DUB"` (or `showDubBadge = true`).
   - **TvAnimeCard (`TvAnimeCard.kt`)**:
     Support `effectiveBadge = badgeText ?: if (anime.hasDub) "DUB" else null`. When the badge is `"DUB"`, render with distinct styling (`TvAccentPurple` / `TvPrimaryRed` background, bold white typography).
   - **SearchScreen (`SearchScreen.kt`)**:
     Insert `"🎙️ Dubbed"` directly after `"All"` in `genres`: `listOf("All", "🎙️ Dubbed", "Action", ...)`.
     In `LaunchedEffect(Unit)`, fetch `dubbedCatalog = repository.getDubbedAnime()`.
     When `selectedGenre == "🎙️ Dubbed"`, display `dubbedCatalog` if the search query is blank; otherwise filter `searchResults` for `it.hasDub || it.title.contains("dub", ignoreCase = true)`.
   - **DetailsScreen (`DetailsScreen.kt`)**:
     Add `var selectedAudioTrack by remember { mutableStateOf(storage.getUserSettings().preferredAudio) }`.
     In the Episodes header (lines 450–484), render a focusable TV audio selector pill `[ SUB ]  [ 🎙️ DUB ]`.
     When `selectedAudioTrack == "DUB"`:
     - Mark displayed episodes with `audioTrack = "DUB"`.
     - When user triggers Play (either the main Resume/Play button or any episode chip), invoke `onPlayEpisode(displayAnime, targetEpisode.copy(audioTrack = selectedAudioTrack))`.
   - **MainActivity & TvPlayerScreen**:
     In `MainActivity.startPlayback`, pass `episode.audioTrack` into `PlaybackSession`.
     In `TvPlayerScreen`, initialize `audioMode` with `if (preferredAudio == "DUB" || episode.audioTrack == "DUB") "DUB" else userSettings.preferredAudio`.
     This ensures that selecting `DUB` on `DetailsScreen` launches the player in English Dub mode and queries English dub streams immediately.

---

## 3. Caveats

- **Network Dependency of External Scrapers**: Live endpoints for Aniwatch, WCO, and KAA can experience transient Cloudflare blocks or DNS latency. The `DefaultAnimeRepository.getDubbedAnime()` implementation must therefore strictly combine multi-source queries with the guaranteed curated offline fallback list so the UI shelf and search filter are never empty.
- **Anime Detail Episode Count**: Some anime only have partial dubs (e.g. 12 out of 24 episodes dubbed). When `dubbedEpisodesCount` is specified and > 0, the DetailsScreen can filter or badge the available dubbed episodes.

---

## 4. Conclusion & Concrete Implementation Recommendations

### 4.1 Target Files & Proposed Changes

#### File 1: `app/src/main/java/com/darknet/animetv/data/model/Anime.kt`
- Add to `Anime`:
  ```kotlin
  val hasDub: Boolean = false,
  val dubbedEpisodesCount: Int = 0
  ```
- Add to `Episode`:
  ```kotlin
  val hasDub: Boolean = false,
  val audioTrack: String = "SUB" // "SUB" | "DUB"
  ```
- Add to `VideoSource`:
  ```kotlin
  val audioTrack: String = "SUB" // "SUB" | "DUB"
  ```

#### File 2: `app/src/main/java/com/darknet/animetv/data/source/AnimeSource.kt`
- Add default method to `AnimeSource`:
  ```kotlin
  suspend fun getDubbedAnime(): List<Anime> = emptyList()
  ```

#### File 3: `app/src/main/java/com/darknet/animetv/data/repository/AnimeRepository.kt`
- Add to `interface AnimeRepository`:
  ```kotlin
  suspend fun getDubbedAnime(): List<Anime>
  suspend fun getEpisodeStreams(
      animeTitle: String,
      episodeNumber: Int,
      preferredSourceId: String? = null,
      preferredAudio: String = "SUB"
  ): List<VideoSource>
  ```
- Implement in `DefaultAnimeRepository`:
  - `getDubbedAnime()`: Query AniList licensed catalog, WCO dubbed list, Aniwatch dubbed list, KAA. Fall back to curated list:
    - Cowboy Bebop (ID: `al-1`, hasDub: true, dubbedEpisodesCount: 26)
    - Fullmetal Alchemist: Brotherhood (ID: `al-5114`, hasDub: true, dubbedEpisodesCount: 64)
    - Attack on Titan (ID: `al-16498`, hasDub: true, dubbedEpisodesCount: 87)
    - Death Note (ID: `al-1535`, hasDub: true, dubbedEpisodesCount: 37)
    - Cyberpunk: Edgerunners (ID: `al-120377`, hasDub: true, dubbedEpisodesCount: 10)
    - Jujutsu Kaisen (ID: `al-113415`, hasDub: true, dubbedEpisodesCount: 47)
    - Dragon Ball Z (ID: `al-813`, hasDub: true, dubbedEpisodesCount: 291)
    - Naruto Shippuden (ID: `al-1735`, hasDub: true, dubbedEpisodesCount: 500)
    - My Hero Academia (ID: `al-21459`, hasDub: true, dubbedEpisodesCount: 138)
    - Solo Leveling (ID: `al-151807`, hasDub: true, dubbedEpisodesCount: 12)
    - Demon Slayer (ID: `al-101922`, hasDub: true, dubbedEpisodesCount: 55)
    - Bleach: TYBW (ID: `al-159322`, hasDub: true, dubbedEpisodesCount: 26)
  - In `getEpisodeStreams`: If `preferredAudio == "DUB"`, sort video sources so `it.audioTrack == "DUB"` or `it.serverName.contains("Dub", ignoreCase = true)` appears first.

#### File 4: `app/src/main/java/com/darknet/animetv/data/source/WcoSource.kt`
- Override `getDubbedAnime()`:
  ```kotlin
  override suspend fun getDubbedAnime(): List<Anime> = withContext(Dispatchers.IO) {
      scrapeAnimeCatalog("$baseUrl/dubbed-anime-list").map { it.copy(hasDub = true) }
  }
  ```
- In `getEpisodeStreams`: Set `audioTrack = "DUB"` on returned `VideoSource` instances.

#### File 5: `app/src/main/java/com/darknet/animetv/data/source/AniwatchSource.kt`
- Override `getDubbedAnime()`:
  ```kotlin
  override suspend fun getDubbedAnime(): List<Anime> = withContext(Dispatchers.IO) {
      val grid = scrapeAnimeGrid("$baseUrl/dubbed-anime").ifEmpty { scrapeAnimeGrid("$baseUrl/most-popular") }
      grid.map { it.copy(hasDub = true) }
  }
  ```
- In `getEpisodeStreams`: In lines 285–298, pass `audioTrack = srvType` to created `VideoSource`.

#### File 6: `app/src/main/java/com/darknet/animetv/ui/components/TvAnimeCard.kt`
- Compute `val effectiveBadge = badgeText ?: if (anime.hasDub) "DUB" else null`.
- If `effectiveBadge == "DUB"`, render with distinct styling (`TvAccentPurple` / `TvPrimaryRed` background, bold white typography, 1dp border).

#### File 7: `app/src/main/java/com/darknet/animetv/ui/screens/HomeScreen.kt`
- Add state `var dubbedList by remember { mutableStateOf<List<Anime>>(emptyList()) }`.
- Fetch `dubbedList = repository.getDubbedAnime()` in `LaunchedEffect(Unit)`.
- Render horizontal shelf:
  ```kotlin
  if (dubbedList.isNotEmpty()) {
      item {
          ShelfSection(title = "🎙️ Dubbed Anime & Episodes", itemCount = dubbedList.size) {
              LazyRow(
                  contentPadding = PaddingValues(horizontal = 48.dp, vertical = 18.dp),
                  horizontalArrangement = Arrangement.spacedBy(16.dp)
              ) {
                  items(dubbedList, key = { it.id }) { anime ->
                      TvAnimeCard(
                          anime = anime,
                          onClick = { onAnimeClick(anime) },
                          badgeText = "DUB"
                      )
                  }
              }
          }
      }
  }
  ```

#### File 8: `app/src/main/java/com/darknet/animetv/ui/screens/SearchScreen.kt`
- Add `"🎙️ Dubbed"` to `genres` list.
- Fetch `dubbedCatalog = repository.getDubbedAnime()` in `LaunchedEffect(Unit)`.
- When `"🎙️ Dubbed"` is selected, display `dubbedCatalog` (or filter `searchResults` for `hasDub == true || title contains "(Dub)"`).
- Display `badgeText = "DUB"` on grid cards.

#### File 9: `app/src/main/java/com/darknet/animetv/ui/screens/DetailsScreen.kt`
- Add `var selectedAudioTrack by remember { mutableStateOf(storage.getUserSettings().preferredAudio) }`.
- In Episode Shelf Header (lines 450–484), render focusable TV toggle buttons `[ SUB ]  [ 🎙️ DUB ]`:
  ```kotlin
  Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
  ) {
      Button(
          onClick = { selectedAudioTrack = "SUB" },
          colors = ButtonDefaults.colors(
              containerColor = if (selectedAudioTrack == "SUB") TvPrimaryRed else TvSurfaceColor,
              focusedContainerColor = TvAccentPurple
          ),
          shape = ButtonDefaults.shape(RoundedCornerShape(8.dp))
      ) {
          Text("SUB", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
      }
      Button(
          onClick = { selectedAudioTrack = "DUB" },
          colors = ButtonDefaults.colors(
              containerColor = if (selectedAudioTrack == "DUB") TvAccentPurple else TvSurfaceColor,
              focusedContainerColor = TvPrimaryRed
          ),
          shape = ButtonDefaults.shape(RoundedCornerShape(8.dp))
      ) {
          Text("🎙️ DUB", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
      }
  }
  ```
- Propagate `targetEpisode.copy(audioTrack = selectedAudioTrack)` when invoking `onPlayEpisode`.

#### File 10: `app/src/main/java/com/darknet/animetv/MainActivity.kt`
- Update `PlaybackSession` and `startPlayback` to accept and store `preferredAudio: String = episode.audioTrack`.
- Pass `preferredAudio` into `TvPlayerScreen`.

#### File 11: `app/src/main/java/com/darknet/animetv/player/TvPlayerScreen.kt`
- Accept `preferredAudio: String = "SUB"` parameter.
- Initialize `audioMode` with `if (preferredAudio == "DUB" || episode.audioTrack == "DUB") "DUB" else userSettings.preferredAudio`.
- Pass `preferredAudio = audioMode` to `repository.getEpisodeStreams`.

---

## 5. Verification Method

### 5.1 Unit Tests to Add
Create `app/src/test/java/com/darknet/animetv/dub/DubbedExperienceTest.kt` with test methods:
1. `testAnimeModelDubFieldsDefaultAndCustom`: Verify `Anime` defaults `hasDub = false`, `dubbedEpisodesCount = 0` and properly retains set values.
2. `testEpisodeModelAudioTrack`: Verify `Episode` defaults `audioTrack = "SUB"` and allows `audioTrack = "DUB"`.
3. `testVideoSourceAudioTrack`: Verify `VideoSource` defaults `audioTrack = "SUB"` and allows `audioTrack = "DUB"`.
4. `testDefaultAnimeRepositoryGetDubbedAnime`: Verify `repository.getDubbedAnime()` returns non-empty list where every element has `hasDub == true`.
5. `testEpisodeStreamsAudioPreferencePriority`: Verify `getEpisodeStreams(..., preferredAudio = "DUB")` sorts DUB sources first.
6. `testSearchScreenDubbedFilterLogic`: Verify filtering sample catalog with `"🎙️ Dubbed"` selects only anime with `hasDub == true`.
7. `testDetailsScreenAudioTrackToggle`: Verify switching `selectedAudioTrack` from `"SUB"` to `"DUB"` correctly propagates `audioTrack = "DUB"` to `Episode`.

### 5.2 Build & Test Verification Commands
Run in PowerShell:
```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```
Verification passes when:
- Exit code is 0.
- Zero compilation errors.
- All existing tests and new dubbed tests pass.
