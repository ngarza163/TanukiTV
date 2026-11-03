# AnimeTV 2.0 — Modern Android TV Streaming App

A complete native rebuild of **AnimeTV** (`amarullz/AnimeTV` / `Darknet/AnimeTV`) crafted from the ground up with **Jetpack Compose for TV** (`androidx.tv.material3` & `androidx.tv.foundation`), **AndroidX Media3 ExoPlayer**, and a **Modular Multi-Source Scraper & Failover Engine**.

---

## ✨ Key Features

### 1. Native Android TV UI (`androidx.tv.compose`)
- **Leanback D-Pad Navigation**: Engineered specifically for 10-foot TV viewing with zero touch dependencies (`android.hardware.touchscreen` not required, `android.software.leanback` enabled).
- **Smooth Focus Physics**: Custom `tvFocusableItem` modifier with `1.08x` animated focus scaling, high-contrast focus borders, and dynamic `zIndex` elevation.
- **Dynamic 16:9 Hero Banner Carousel**: Auto-advancing featured anime showcase with dual legibility gradients, synopsis, studio/rating badges, and smart **Play / Resume / Watch Again** actions.
- **Horizontal Content Shelves**: Dedicated rows for *Continue Watching* (with progress bars), *Trending Now*, *Popular Hits*, *Recently Updated Episodes*, and *Top Rated Anime*.
- **TV Search & Genre Filtering**: Instant category filter chips paired with a 5-column responsive TV vertical grid.
- **Anime Details & Chunked Episode Picker**: Full synopsis, watchlist bookmarking, and 50-episode chunked tabs for effortless navigation through long-running series (e.g., *One Piece*, *Naruto*).

### 2. Multi-Source Scraper Engine & Custom Source DSL
Built-in extractors and scrapers with automatic anti-hotlinking header injection (`Referer`, `Origin`, `User-Agent`):
- **Aniwatch** (`https://aniwatchtv.ro/`) — AJAX episode/server resolver + MegaCloud integration
- **Anikoto** (`https://anikototv.to/home`) — Multi-server catalog & embed resolver
- **Miruro** (`https://www.miruro.cx/`) — Fast JSON API & direct HLS stream resolver
- **ReAnime** (`https://reanime.to/home`) — Catalog, search, and multi-quality stream extractor
- **KAA / KickAssAnime** (`https://kaa.lt/`) — API catalog + native AES-CBC-PKCS7 manifest decryption
- **WCO** (`https://www.wco.tv/`) — Base64 embed decoder for anime & English-dubbed streams
- **AnimeFLV** (`https://www3.animeflv.net`) — Jsoup DOM & embedded script scraper
- **Catalog & Metadata Providers**: **AniList GraphQL** (high-res 16:9 banners) + **Jikan v4 REST API**
- **Video Host Extractors**: **MegaCloud**, **StreamWish** (JS `p,a,c,k,e,d` unpacker), **Doodstream**, **Vidplay**, **Mp4Upload**, and **Direct HLS/MP4**
- **Dynamic Custom Source Engine**: Add, toggle, reorder, latency-test, and configure custom scrapers directly from the TV UI using Form fields or declarative JSON CSS selectors.

### 3. Media3 ExoPlayer & TV Remote HUD
- **Adaptive Streaming**: Supports HLS (`.m3u8`) and progressive MP4 playback with custom HTTP headers and WebVTT/SRT subtitle tracks.
- **Remote-Optimized HUD**: Auto-hides after 5 seconds of playback inactivity and reveals immediately on any D-pad input.
- **Fast ±10s D-Pad Seeking**: Left/Right D-pad keys trigger instant ±10-second seeks with a center-screen visual seek indicator pill (`<< -10s` / `>> +10s`).
- **Live Track & Server Switching**: Switch video quality (`Auto`, `1080p`, `720p`, `480p`, `360p`), closed captions (`Off` / language tracks), audio mode (`SUB` / `DUB`), and streaming servers while preserving playback position.
- **Automatic Stream Failover**: Automatically switches to the next available streaming server if the primary stream encounters a playback error.
- **Auto-Resume & Watch History**: Saves playback timestamps every 5 seconds and on exit to local SQLite storage, automatically resuming where you left off.

---

## 🏗️ Project Structure

```text
app/src/main/java/com/darknet/animetv/
├── AnimeTvApplication.kt          # Application lifecycle & singleton storage initialization
├── MainActivity.kt                # Android TV Leanback launcher & top-level screen coordinator
├── data/
│   ├── model/Anime.kt             # Domain models (Anime, Episode, VideoSource, WatchProgress, CustomSource)
│   ├── repository/                # Multi-source failover repository & live latency health checker
│   ├── source/                    # 7 streaming scrapers, AniList/Jikan catalogs, DynamicHtmlSource
│   │   └── extractor/             # MegaCloud, StreamWish, Doodstream, Vidplay, Mp4Upload, Direct
│   └── storage/AppDatabase.kt     # SQLite persistence for Watch History, Watchlist, and Custom Sources
├── player/
│   ├── TvPlayerScreen.kt          # Full-screen Media3 ExoPlayer composable & D-pad key interceptor
│   ├── TvPlayerHud.kt             # TV remote overlay & Quality/Subtitle/Server modal dialogs
│   └── VideoPlayerManager.kt      # ExoPlayer builder, track selection & auto-resume controller
└── ui/
    ├── theme/                     # TV Material 3 dark theme, colors & typography
    ├── components/                # FocusModifiers, TvAnimeCard, TvHeroBanner, CategoryChip
    └── screens/                   # HomeScreen, DetailsScreen, SearchScreen, SourcesScreen
```

---

## 🚀 Building & RunningTests

### Prerequisites
- **JDK**: Java 17 or Java 21 LTS
- **Android SDK**: API Level 34 (`compileSdk 34`, `minSdk 26`)
- **Gradle**: 8.9 (included via Gradle Wrapper)

### Build Debug APK
```powershell
.\gradlew.bat assembleDebug
```
The compiled Android TV APK is output to:
`app/build/outputs/apk/debug/app-debug.apk`

### Run Unit & 4-Tier E2E Test Suite
```powershell
.\gradlew.bat testDebugUnitTest
```
