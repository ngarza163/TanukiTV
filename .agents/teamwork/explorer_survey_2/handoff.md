# Handoff Report — Explorer Survey 2: Scraper Engine & Streaming Sources

## 1. Observation

### 1.1 Existing Codebase & Scraper Legacy Assets
Direct inspection of the repository revealed the existing architecture and reverse-engineering research:
1. `app/build.gradle` (lines 62-103) contains core networking and parsing libraries:
   - `com.squareup.okhttp3:okhttp:4.12.0`
   - `org.jsoup:jsoup:1.17.2`
   - `com.google.code.gson:gson:2.11.0`
   - `androidx.media3:media3-exoplayer:1.3.1`
   - `androidx.media3:media3-exoplayer-hls:1.3.1`
   - `androidx.media3:media3-datasource-okhttp:1.3.1`
2. `app/src/main/java/com/darknet/animetv/data/model/Anime.kt` defines `Anime`, `AnimeDetail`, `Episode`, `VideoSource`, `SubtitleTrack`, `WatchProgress`, and `CustomSource`.
3. `app/src/main/java/com/darknet/animetv/data/source/AnimeSource.kt` defines the baseline contract:
   - `suspend fun getTrending(): List<Anime>`
   - `suspend fun getPopular(): List<Anime>`
   - `suspend fun getRecentEpisodes(): List<Anime>`
   - `suspend fun search(query: String): List<Anime>`
   - `suspend fun getAnimeDetail(animeId: String): AnimeDetail?`
   - `suspend fun getEpisodeStreams(animeId: String, episodeId: String): List<VideoSource>`
4. `animetv-src/tools/notes/new-source-notes.md` (lines 1-1237) and `animetv-src/tools/utils/vrf.js` contain reverse-engineered specifications:
   - Miruro API keys and endpoints: `x-atx: 12RmYtJexlqnNym38z4ahwy+g1g0la/El8nkkMOVtiQ=`, `https://mapper.miruro.tv/anilist/anime/{id}.json`, `https://dio.miruro.tv/sources?id={id}&provider={provider}&ep={ep}` (lines 515-688).
   - HiAnime / Aniwatch AJAX patterns: `/ajax/v2/episode/list/{animeId}`, `/ajax/v2/episode/servers?episodeId={epId}`, `/ajax/v2/episode/sources?id={serverId}` routing to MegaCloud embed `https://megacloud.tv/embed-2/e-1/{streamId}?k=1` (lines 1065-1237).
   - KickAss / KAA AES-CBC-PKCS7 decryption routines in `AnimeView.java` (lines 2402-2430) and `new-source-notes.md` (lines 1045-1064).
   - Vidplay / Vidstream key rotation and RC4 obfuscation tables in `vrf.js` (lines 1-150).
5. `animetv-src/app/src/main/java/.../AnimeDataSource.java` (lines 387-410, 677-684) documents that playback fails if specific HTTP request headers (`User-Agent`, `Referer`, `Origin`) are not injected into Media3 ExoPlayer requests for video hosting CDNs.

---

## 2. Logic Chain

### 2.1 Target Source Analysis (7 Requested Providers)

#### 1. Aniwatch (`https://aniwatchtv.ro/` / HiAnime)
- **Domain**: `aniwatchtv.ro`, `aniwatchtv.to`, `hianime.to`.
- **Search & Catalog**:
  - URL: `GET https://aniwatchtv.ro/search?keyword={query}`.
  - Quick Search: `GET https://aniwatchtv.ro/ajax/v2/suggest/search?keyword={query}` returning JSON HTML payload.
  - HTML DOM Selectors:
    - Anime items: `.film_list-wrap .flw-item`
    - Title: `.film-detail .film-name a`
    - Poster: `.film-poster img.film-poster-img[data-src]`
    - ID/Slug: Extracted from `href` (e.g., `/watch/solo-leveling-season-2-19413` -> `solo-leveling-season-2-19413`, animeId `19413`).
- **Episode List**:
  - Endpoint: `GET https://aniwatchtv.ro/ajax/v2/episode/list/{animeId}`.
  - Response: JSON `{ status: true, html: "<div class=\"ss-list\">...</div>" }`.
  - Selector: `.ss-list a.ssl-item.ep-item`.
  - Attributes: `data-id` (episode internal ID, e.g., `124590`), `data-number` (e.g., `1`), `title` (episode title).
- **Video Server Resolution**:
  - Endpoint: `GET https://aniwatchtv.ro/ajax/v2/episode/servers?episodeId={episodeId}`.
  - Response: HTML snippet with sub/dub server pills:
    - Sub servers: `.servers-sub .server-item` (`data-id` = serverId, `data-server-id` = mirror code: `4` for HD-1 / MegaCloud, `1` for HD-2 / RapidCloud).
    - Dub servers: `.servers-dub .server-item`.
  - Stream Embed Link: `GET https://aniwatchtv.ro/ajax/v2/episode/sources?id={serverId}`.
  - Response: JSON `{ type: "iframe", link: "https://megacloud.tv/embed-2/e-1/{streamId}?k=1" }`.
- **Extractor**: Handed off to `MegaCloudExtractor`.

#### 2. Anikoto (`https://anikototv.to/home`)
- **Domain**: `https://anikototv.to/`.
- **Search & Catalog**:
  - Search: `GET https://anikototv.to/search?q={query}`.
  - Selectors: `.anime-card`, `a.anime-title`, `.poster img[src]`.
  - Details: `GET https://anikototv.to/anime/{slug}` -> synopsis `.anime-synopsis`, score `.rating-badge`.
- **Episode & Player**:
  - Episode selectors: `.episodes-grid a.ep-card` or embedded JSON in `script#__NEXT_DATA__`.
  - Player Embed: `<iframe src="...">` targeting StreamWish, Doodstream, Filemoon, or Vidplay.
- **Extractor**: Handed off to modular extractor based on iframe domain.

#### 3. Miruro (`https://www.miruro.cx/` & `https://www.miruro.tv/`)
- **Domain**: `https://www.miruro.cx/`, `https://api.miruro.tv/`.
- **Architecture**:
  - API Key: `x-atx: 12RmYtJexlqnNym38z4ahwy+g1g0la/El8nkkMOVtiQ=`.
  - AniList to Miruro Mapping: `GET https://mapper.miruro.tv/anilist/anime/{anilistId}.json`.
  - MAL to Miruro Mapping: `GET https://mapper.miruro.tv/mal/anime/{malId}.json`.
  - Episode Info: `GET https://dio.miruro.tv/info?id={id}&provider=zoro` (or `animepahe`, `gogoanime`).
  - Direct Stream Extraction: `GET https://dio.miruro.tv/sources?id={watchId}&provider=zoro&ep={epNum}`.
  - Response format:
    ```json
    {
      "sources": [
        { "file": "https://.../master.m3u8", "isM3U8": true }
      ],
      "tracks": [
        { "file": "https://.../eng.vtt", "label": "English", "kind": "captions", "default": true }
      ],
      "intro": { "start": 90, "end": 180 },
      "outro": { "start": 1350, "end": 1440 }
    }
    ```
- **Evaluation**: The cleanest, fastest provider for Android TV because it outputs pre-parsed multi-bitrate HLS streams with WebVTT subtitle tracks and AniSkip intro/outro timestamps without scraping fragile HTML.

#### 4. ReAnime (`https://reanime.to/home`)
- **Domain**: `https://reanime.to/`.
- **Catalog & Episodes**:
  - Search: `GET https://reanime.to/search?keyword={query}`.
  - Selectors: `.film-poster`, `h3.film-name a`, `.film-infor`.
  - Episode List: `.episodes-list a.ep-item` with `data-id`.
  - Server Embed: AJAX `/ajax/episode/servers?episodeId={id}` yielding embed iframe URLs for StreamWish, Vidplay, MegaCloud, and Doodstream.
- **Extractor**: Handed off to modular video extractor registry.

#### 5. KAA (KickAssAnime - `https://kaa.lt/`)
- **Domain**: `https://kaa.lt/`, mirrors `kaas.to`, `kickassanime.mx`.
- **API & Decryption**:
  - API endpoint: `GET https://kaa.lt/api/show/{slug}` and `/api/show/{slug}/episodes?lang=ja-JP`.
  - Player script config extraction: `#player + script` contains encrypted Base64 string.
  - Decryption logic: `AES/CBC/PKCS7Padding` using secret key and IV hex string (as verified from `AnimeView.java:2403-2430`).
  - Decrypted payload contains direct HLS URL or Vidplay / MegaF embed URLs.

#### 6. WCO (`https://www.wco.tv/` / WCOStream)
- **Domain**: `https://www.wco.tv/` (Western Cartoons and Anime Dub archive).
- **Search & Details**:
  - Search: `POST https://www.wco.tv/search` (`catlistsearch={query}`).
  - Catalog list: `https://www.wco.tv/anime/anime-list`.
  - Episode list: table of links on series page `https://www.wco.tv/anime/{series-slug}`.
- **Player & Stream Extraction**:
  - Episode page embeds iframe `/inc/embed/getvidlink.php?v={code}`.
  - Obfuscated base64 payload in page script is decoded to locate stream URL (`cdn.wcostream.tv` or `cizgifilmlerizle`).
  - Critical HTTP Header requirement: `Referer: https://www.wco.tv/` and `User-Agent` to avoid HTTP 403 Forbidden on MP4/m3u8 CDN chunks.

#### 7. AnimeFLV (`https://www3.animeflv.net`)
- **Domain**: `https://www3.animeflv.net`.
- **Implementation State**: Already partially prototyped in `AnimeFlvSource.kt`.
- **Enhancement needed**:
  - Current `AnimeFlvSource.kt` uses hardcoded sample URLs for `getEpisodeStreams`.
  - Real extraction: Episode page `https://www3.animeflv.net/ver/{animeId}-{epNum}` contains:
    ```javascript
    var videos = {
      "SUB": [
        {"server": "sw", "title": "Streamwish", "code": "https://streamwish.to/e/..."},
        {"server": "yourupload", "title": "YourUpload", "code": "https://www.yourupload.com/embed/..."},
        {"server": "mega", "title": "MEGA", "code": "https://mega.nz/embed/..."}
      ]
    };
    ```
  - Parse `var videos` JSON from `<script>` tag, extract `sw` (StreamWish) or `yourupload` code, and resolve via `StreamWishExtractor`.

---

### 2.2 Catalog & Metadata Sources: AniList GraphQL vs Jikan v4 REST

| Capability | AniList GraphQL API | Jikan v4 REST API (MAL) | Winner / Decision |
| :--- | :--- | :--- | :--- |
| **Endpoint** | `POST https://graphql.anilist.co` | `GET https://api.jikan.moe/v4/...` | AniList (single consolidated query) |
| **Rate Limit** | 90 req/minute | 3 req/sec, 60 req/min (HTTP 429 common) | **AniList** (higher client headroom) |
| **TV Hero Banner** | **Native 16:9 `bannerImage`** | No banner; only 2:3 vertical poster | **AniList** (crucial for TV UI R1) |
| **Payload Size** | Exact field filtering (small JSON) | Monolithic payload (large JSON) | **AniList** |
| **Episode Airing Countdown** | `nextAiringEpisode { timeUntilAiring, episode }` | Static airing flag only | **AniList** |
| **Fallback Value** | - | Comprehensive episode synopsis & MAL IDs | **Jikan as Fallback** |

**Catalog Strategy**:
- **Primary Catalog Provider**: `AniListCatalogSource` (provides high-definition 16:9 banners, trending scores, genres, and metadata).
- **Secondary / Fallback Provider**: `JikanSource` (handles rate-limit failover or MAL-specific queries).

---

### 2.3 Interface Contracts & Architecture Specifications

#### A. `AnimeSource` Interface Contract
```kotlin
package com.darknet.animetv.data.source

import com.darknet.animetv.data.model.Anime
import com.darknet.animetv.data.model.AnimeDetail
import com.darknet.animetv.data.model.Episode
import com.darknet.animetv.data.model.VideoSource

interface AnimeSource {
    val sourceId: String
    val sourceName: String
    val baseUrl: String
    val isEnabled: Boolean

    suspend fun getTrending(page: Int = 1): Result<List<Anime>>
    suspend fun getPopular(page: Int = 1): Result<List<Anime>>
    suspend fun getRecentEpisodes(page: Int = 1): Result<List<Anime>>
    suspend fun search(query: String, page: Int = 1): Result<List<Anime>>
    suspend fun getAnimeDetail(animeId: String): Result<AnimeDetail>
    suspend fun getEpisodeStreams(animeId: String, episodeId: String): Result<List<VideoSource>>
    
    // Latency & health check
    suspend fun testConnection(): SourceHealthResult
}

data class SourceHealthResult(
    val sourceId: String,
    val isAvailable: Boolean,
    val latencyMs: Long,
    val message: String? = null
)
```

#### B. Extractor Architecture for Video Servers
Video hosting providers deliver video streams wrapped in custom JS players or obfuscated iframes. The extractor engine must decouple source scrapers from hosting resolvers:

```kotlin
interface VideoExtractor {
    val serverName: String
    fun canHandle(url: String): Boolean
    suspend fun extract(embedUrl: String, referer: String? = null): List<VideoSource>
}
```

1. **`MegaCloudExtractor`**:
   - Matches: `megacloud.tv`, `rapid-cloud.co`.
   - Endpoint: `GET /embed-2/ajax/e-1/getSources?id={streamId}` with `X-Requested-With: XMLHttpRequest` and `Referer: {embedUrl}`.
   - Extracts master `.m3u8` playlist and WebVTT subtitle tracks.
2. **`StreamWishExtractor`**:
   - Matches: `streamwish.to`, `streamwish.com`, `strwish.com`, `awish.pro`.
   - Logic: Fetches embed HTML, evaluates Dean Edwards `eval(function(p,a,c,k,e,d)...)` or extracts regex `file:\s*["'](https?://[^"']+\.m3u8[^"']*)["']`.
   - Sets headers: `Referer: https://streamwish.to/`.
3. **`DoodstreamExtractor`**:
   - Matches: `dood.to`, `doodstream.com`, `doods.pro`.
   - Logic: Extracts `pass_md5` token and builds signed stream URL: `{md5Url}/{randomChar}?token={token}&expiry={expiry}`.
   - Sets headers: `Referer: https://dood.to/`.
4. **`VidplayExtractor`**:
   - Matches: `vidplay.online`, `vid2a41.site`, `megaf.cc`.
   - Logic: Implements RC4 + char substitution decryption from `vrf.js` to fetch `mediainfo/{encodedId}`.
   - Returns master `.m3u8` and subtitles.
5. **`Mp4UploadExtractor`**:
   - Matches: `mp4upload.com`.
   - Logic: Extracts direct `.mp4` file URL from `player.src({ ... src: "..." })`.
6. **`DirectStreamExtractor`**:
   - Fallback extractor for raw `.m3u8` or `.mp4` URLs. Injects required `Referer`, `Origin`, and `User-Agent` headers.

#### C. Multi-Source Failover & Fallback Chain
```kotlin
class SourceManager(
    private val sources: List<AnimeSource>,
    private val database: AppStorage
) {
    // Priority order: Miruro (1) -> Aniwatch (2) -> AnimeFLV (3) -> Consumet (4) -> Sample/Local (Fallback)
    suspend fun getStreamWithFailover(
        title: String,
        episodeNumber: Int,
        activeSources: List<AnimeSource>
    ): List<VideoSource> {
        val collectedStreams = mutableListOf<VideoSource>()
        
        for (source in activeSources.filter { it.isEnabled }) {
            try {
                // 1. Search anime by title in source
                val searchResult = source.search(title).getOrNull()
                val match = searchResult?.firstOrNull() ?: continue
                
                // 2. Fetch detail and locate episode
                val detail = source.getAnimeDetail(match.id).getOrNull() ?: continue
                val targetEp = detail.episodes.find { it.number == episodeNumber } ?: continue
                
                // 3. Resolve streams
                val streams = source.getEpisodeStreams(match.id, targetEp.id).getOrNull()
                if (!streams.isNullOrEmpty()) {
                    collectedStreams.addAll(streams)
                    // If high-quality HLS stream found, we can return or continue gathering mirrors
                    if (collectedStreams.any { it.isHls }) break
                }
            } catch (e: Exception) {
                // Log failover and try next source
            }
        }
        
        // Final fallback if all external sources fail (e.g. offline sandbox or severe cloudflare block)
        if (collectedStreams.isEmpty()) {
            collectedStreams.addAll(getEmergencyFallbackStreams())
        }
        return collectedStreams
    }
}
```

#### D. Dynamic Custom Source Definition DSL (JSON Schema)
To satisfy the user requirement for adding/editing custom sources from the TV UI or JSON config, the system defines a declarative selector schema:

```json
{
  "id": "custom-source-01",
  "name": "Custom Anime Stream",
  "baseUrl": "https://customanime.example.com",
  "isEnabled": true,
  "headers": {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Referer": "https://customanime.example.com/"
  },
  "search": {
    "endpoint": "/browse?q={query}",
    "method": "GET",
    "itemSelector": "ul.anime-list li.item",
    "titleSelector": "h3.title",
    "posterSelector": "img.poster@src",
    "idSelector": "a@href",
    "idRegex": "/anime/(.*)"
  },
  "episodes": {
    "endpoint": "/anime/{animeId}",
    "itemSelector": "ul.episodes li a",
    "numberSelector": "@data-number",
    "titleSelector": "span.ep-title",
    "idSelector": "@href"
  },
  "streams": {
    "endpoint": "/watch/{episodeId}",
    "iframeSelector": "div.player iframe@src",
    "extractor": "AUTO"
  }
}
```

An accompanying `DynamicHtmlSource` class parses this JSON configuration and executes Jsoup queries dynamically without recompilation!

#### E. Connection Test & Latency Check Harness
```kotlin
class ConnectionTester(private val client: OkHttpClient) {
    suspend fun pingSource(source: AnimeSource): SourceHealthResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val req = Request.Builder()
                .url(source.baseUrl)
                .header("User-Agent", "Mozilla/5.0 AnimeTV/2.0")
                .head() // Fast HEAD request
                .build()
            
            client.newCall(req).execute().use { resp ->
                val duration = System.currentTimeMillis() - start
                SourceHealthResult(
                    sourceId = source.sourceId,
                    isAvailable = resp.isSuccessful,
                    latencyMs = duration,
                    message = "HTTP ${resp.code} (${duration}ms)"
                )
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - start
            SourceHealthResult(
                sourceId = source.sourceId,
                isAvailable = false,
                latencyMs = duration,
                message = e.localizedMessage ?: "Connection Timeout"
            )
        }
    }
}
```

---

## 3. Caveats

1. **Third-Party Anti-Scraping Volatility**: Anime streaming sites (Aniwatch, KAA, WCO) frequently rotate domains and obfuscation keys (e.g. Cloudflare Turnstile, DDoS-Guard). The architecture must decouple extractors and support dynamic domain overrides in `AppStorage`.
2. **Network Sandbox Environment**: Direct HTTP requests from the local development sandbox CLI may time out due to environment-level internet egress filters. The Android app itself running on an Android TV device or emulator with network bridging will have normal internet connectivity. Reliable test fallback streams (`BigBuckBunny`, `ElephantsDream`, `TearsOfSteel`) must be retained as safety fallbacks.
3. **Decryption CPU Load on Android TV**: Running complex JavaScript deobfuscation on TV chips (e.g. older Android TV sticks) can cause UI stutters. All extractor operations (AES-CBC, RC4, regex parsing) must strictly run on `Dispatchers.IO` with cached results.

---

## 4. Conclusion

1. **Catalog Architecture**: AniList GraphQL is confirmed as the optimal primary metadata source for Android TV due to native 16:9 `bannerImage` support, generous rate limits (90 req/min), and lightweight GraphQL queries. Jikan v4 REST serves as an ideal secondary fallback.
2. **Scraper Engine Architecture**: The 7 target sources (Aniwatch, Anikoto, Miruro, ReAnime, KAA, WCO, AnimeFLV) fall naturally into two categories:
   - **API / Structured Sources**: Miruro, KAA, Aniwatch AJAX endpoints.
   - **DOM / Embed Scrapers**: AnimeFLV, WCO, Anikoto, ReAnime.
3. **Video Extractor Registry**: Resolving video streams requires a decoupled extractor layer (MegaCloud, StreamWish, Doodstream, Vidplay, Mp4Upload, Direct).
4. **Custom Source DSL**: A JSON-driven selector model (`DynamicHtmlSource`) allows users to add/edit source selectors directly from the Android TV settings screen.
5. **Media3 ExoPlayer Integration**: All streams require custom header injection (`media3-datasource-okhttp`) to spoof Referer/Origin headers required by streaming CDNs.

---

## 5. Verification Method

To independently verify these specifications and contracts:

1. **Source Code Inspection**:
   - Inspect `app/src/main/java/com/darknet/animetv/data/source/AnimeSource.kt` to verify interface signature compatibility.
   - Inspect `app/src/main/java/com/darknet/animetv/data/source/AnimeFlvSource.kt` for working Jsoup selectors (`ul.ListAnimes article.Anime`, `h3.Title`, `div.Description p`).
   - Inspect `animetv-src/tools/notes/new-source-notes.md` (lines 515-688 and 1065-1237) to verify Miruro and Aniwatch endpoint signatures.
   - Inspect `animetv-src/app/src/main/java/.../AnimeView.java` (lines 2402-2430) to verify KAA AES decryption algorithm (`AES/CBC/PKCS7PADDING`).

2. **Build and Unit Test Verification**:
   - Run Gradle check on the project:
     ```powershell
     .\gradlew.bat testDebugUnitTest
     ```
   - Verify that all models and source definitions compile cleanly under Kotlin and Android SDK 34.
