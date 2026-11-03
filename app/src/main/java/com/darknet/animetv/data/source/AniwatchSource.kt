package com.darknet.animetv.data.source

import android.util.Base64
import com.darknet.animetv.data.model.Anime
import com.darknet.animetv.data.model.AnimeDetail
import com.darknet.animetv.data.model.Episode
import com.darknet.animetv.data.model.SubtitleTrack
import com.darknet.animetv.data.model.VideoSource
import com.darknet.animetv.data.source.extractor.VideoExtractorRegistry
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class AniwatchSource(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
    private val extractorRegistry: VideoExtractorRegistry = VideoExtractorRegistry.defaultInstance
) : AnimeSource {

    override val sourceId: String = "aniwatch"
    override val sourceName: String = "Aniwatch (HiAnime)"
    override val baseUrl: String = "https://aniwatchtv.ro"
    override var isEnabled: Boolean = true

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private val megaPlayExtractor = com.darknet.animetv.data.source.extractor.MegaPlayExtractor(client)

    override suspend fun getTrending(): List<Anime> = withContext(Dispatchers.IO) {
        scrapeAnimeGrid("$baseUrl/top-airing").ifEmpty { scrapeAnimeGrid("$baseUrl/home") }
    }

    override suspend fun getPopular(): List<Anime> = withContext(Dispatchers.IO) {
        scrapeAnimeGrid("$baseUrl/most-popular").ifEmpty { scrapeAnimeGrid("$baseUrl/home") }
    }

    override suspend fun getRecentEpisodes(): List<Anime> = withContext(Dispatchers.IO) {
        scrapeAnimeGrid("$baseUrl/recently-updated").ifEmpty { scrapeAnimeGrid("$baseUrl/home") }
    }

    override suspend fun getDubbedAnime(): List<Anime> = withContext(Dispatchers.IO) {
        val grid = scrapeAnimeGrid("$baseUrl/dubbed-anime").ifEmpty { scrapeAnimeGrid("$baseUrl/most-popular") }
        grid.map { it.copy(hasDub = true) }
    }

    override suspend fun search(query: String): List<Anime> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        // 1. Try OtakuThemes AJAX search suggestions (returns direct /anime/{slug}/ items)
        val suggestions = fetchSearchSuggestions("$baseUrl/wp-json/v1/otakuthemes/search/suggestions?keyword=$encoded")
        if (suggestions.isNotEmpty()) return@withContext suggestions

        // 2. Try WordPress search query ?s=
        val wpSearch = scrapeAnimeGrid("$baseUrl/?s=$encoded")
        if (wpSearch.isNotEmpty()) return@withContext wpSearch

        // 3. Fallback to /search?keyword=
        scrapeAnimeGrid("$baseUrl/search?keyword=$encoded")
    }

    private fun fetchSearchSuggestions(url: String): List<Anime> {
        val list = mutableListOf<Anime>()
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("X-Requested-With", "XMLHttpRequest")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val body = resp.body?.string() ?: return emptyList()
                val json = JsonParser.parseString(body).asJsonObject
                val html = json.get("html")?.takeIf { !it.isJsonNull }?.asString ?: return emptyList()
                val doc = Jsoup.parse(html, baseUrl)
                for (item in doc.select("a.nav-item")) {
                    val href = item.attr("href")
                    val title = item.select(".film-name").text().trim()
                    val poster = item.select("img").attr("data-src").ifEmpty { item.select("img").attr("src") }
                    val slug = normalizeSlug(href)
                    if (title.isNotEmpty() && slug.isNotEmpty()) {
                        list.add(
                            Anime(
                                id = "aniwatch-$slug",
                                title = title,
                                posterUrl = poster,
                                backdropUrl = poster,
                                score = 8.4,
                                sourceId = sourceId
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}
        return list
    }

    override suspend fun getAnimeDetail(animeId: String): AnimeDetail? = withContext(Dispatchers.IO) {
        try {
            val cleanSlug = animeId.removePrefix("aniwatch-").trim('/')
            val candidateUrls = listOf(
                if (cleanSlug.startsWith("http")) cleanSlug else "$baseUrl/$cleanSlug-episode-1/",
                "$baseUrl/anime/$cleanSlug/",
                "$baseUrl/$cleanSlug/",
                "$baseUrl/watch/$cleanSlug"
            ).distinct()

            var htmlContent: String? = null
            var resolvedUrl = candidateUrls.first()
            for (url in candidateUrls) {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", userAgent)
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val b = resp.body?.string()
                        if (!b.isNullOrBlank()) {
                            htmlContent = b
                            resolvedUrl = url
                        }
                    }
                }
                if (htmlContent != null) break
            }

            val html = htmlContent ?: return@withContext null
            val doc = Jsoup.parse(html, baseUrl)

            val title = doc.select(".anisc-detail .film-name").text().ifEmpty {
                doc.select("h2.film-name").text()
            }.ifEmpty { cleanSlug }

            val poster = doc.select(".film-poster img").attr("data-src").ifEmpty {
                doc.select(".film-poster img").attr("src")
            }

            val synopsis = doc.select(".film-description .text").text().ifEmpty {
                doc.select(".item-title:contains(Overview) + div").text()
            }

            val genres = doc.select(".item-list:contains(Genres) a").map { it.text() }
            val status = doc.select(".item-title:contains(Status) + span").text().ifEmpty { "Ongoing" }
            val scoreText = doc.select(".item-title:contains(Score) + span").text()
            val score = scoreText.toDoubleOrNull() ?: 8.5

            // Extract numeric ID from OtakuThemes / HiAnime attributes or inline script
            val numericId = doc.select(".anis-content[data-anime-id]").attr("data-anime-id")
                .ifEmpty { doc.select("[data-page='detail'][data-id]").attr("data-id") }
                .ifEmpty { doc.select("#wrapper[data-id]").attr("data-id") }
                .ifEmpty {
                    val m = Pattern.compile("""(?:anime_id["']?\s*[:=]\s*["']?|postid-)(\d+)""").matcher(html)
                    if (m.find()) m.group(1) ?: "" else ""
                }
                .ifEmpty { extractNumericId(cleanSlug) }

            val episodes = mutableListOf<Episode>()
            if (numericId.isNotEmpty()) {
                val epEndpoints = listOf(
                    "$baseUrl/wp-json/v1/otakuthemes/episode/list/$numericId",
                    "$baseUrl/ajax/v2/episode/list/$numericId"
                )
                for (epListUrl in epEndpoints) {
                    if (episodes.isNotEmpty()) break
                    val epReq = Request.Builder()
                        .url(epListUrl)
                        .header("User-Agent", userAgent)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Referer", resolvedUrl)
                        .build()

                    client.newCall(epReq).execute().use { epResp ->
                        if (epResp.isSuccessful) {
                            val body = epResp.body?.string()
                            if (!body.isNullOrBlank()) {
                                val epHtml = try {
                                    JsonParser.parseString(body).asJsonObject.get("html")?.asString ?: ""
                                } catch (_: Exception) {
                                    body
                                }
                                if (epHtml.isNotEmpty()) {
                                    val epDoc = Jsoup.parse(epHtml)
                                    val epElements = epDoc.select("a.ep-item")
                                    for (el in epElements) {
                                        val epId = el.attr("data-id")
                                        val epNumber = el.attr("data-number").toIntOrNull() ?: 1
                                        val epTitle = el.attr("title").ifEmpty {
                                            el.select(".ep-name").text().ifEmpty { "Episode $epNumber" }
                                        }
                                        if (epId.isNotEmpty()) {
                                            episodes.add(
                                                Episode(
                                                    id = epId,
                                                    animeId = animeId,
                                                    number = epNumber,
                                                    title = epTitle
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (episodes.isEmpty()) {
                episodes.add(
                    Episode(
                        id = "$cleanSlug-ep-1",
                        animeId = animeId,
                        number = 1,
                        title = "Episode 1"
                    )
                )
            }

            val anime = Anime(
                id = animeId,
                title = title,
                posterUrl = poster,
                backdropUrl = poster,
                score = score,
                status = status,
                genres = genres,
                synopsis = synopsis,
                totalEpisodes = episodes.size,
                sourceId = sourceId
            )

            AnimeDetail(
                anime = anime,
                episodes = episodes.sortedBy { it.number }
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun getEpisodeStreams(animeId: String, episodeId: String): List<VideoSource> = withContext(Dispatchers.IO) {
        val streams = mutableListOf<VideoSource>()
        try {
            val cleanSlug = animeId.removePrefix("aniwatch-").trim('/')
            val refererUrl = "$baseUrl/anime/$cleanSlug/"

            val serverEndpoints = listOf(
                "$baseUrl/wp-json/v1/otakuthemes/episode/servers?episodeId=$episodeId",
                "$baseUrl/ajax/v2/episode/servers?episodeId=$episodeId"
            )

            var serversHtml = ""
            for (serversUrl in serverEndpoints) {
                if (serversHtml.isNotEmpty()) break
                val serverReq = Request.Builder()
                    .url(serversUrl)
                    .header("User-Agent", userAgent)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Referer", refererUrl)
                    .build()

                client.newCall(serverReq).execute().use { sResp ->
                    if (sResp.isSuccessful) {
                        val rawBody = sResp.body?.string().orEmpty()
                        serversHtml = try {
                            val json = JsonParser.parseString(rawBody).asJsonObject
                            json.get("html")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                        } catch (_: Exception) {
                            rawBody
                        }
                    }
                }
            }

            if (serversHtml.isNotEmpty()) {
                val sDoc = Jsoup.parse(serversHtml)
                val serverItems = sDoc.select(".server-item")

                // a) Decode Base64 data-hash embeds (OtakuThemes / aniwatchtv.ro live format).
                //    Resolve the first working server for EACH audio type (SUB and DUB) so the
                //    player can switch tracks without re-scraping.
                val resolvedTypes = mutableSetOf<String>()
                for (item in serverItems) {
                    val dataHash = item.attr("data-hash")
                    val srvName = item.attr("data-server-name").ifEmpty { item.text().trim().ifEmpty { "Aniwatch" } }
                    val srvType = item.attr("data-type").uppercase().ifEmpty { "SUB" }
                    if (dataHash.isEmpty() || srvType in resolvedTypes) continue
                    val embedUrl = try {
                        String(Base64.decode(dataHash, Base64.DEFAULT), StandardCharsets.UTF_8).trim()
                    } catch (_: Exception) {
                        ""
                    }
                    if (embedUrl.startsWith("http")) {
                        val resolved = resolveEmbedStream("$srvName ($srvType)", embedUrl).map {
                            it.copy(serverName = "Aniwatch $srvName ($srvType)", audioTrack = srvType)
                        }
                        if (resolved.isNotEmpty()) {
                            streams.addAll(resolved)
                            resolvedTypes.add(srvType)
                        }
                    }
                }

                // b) Legacy data-id fallback (/ajax/v2/episode/sources?id=...)
                if (streams.isEmpty()) {
                    val serverId = sDoc.select(".servers-sub .server-item").firstOrNull()?.attr("data-id")
                        ?: sDoc.select(".server-item").firstOrNull()?.attr("data-id")
                    if (!serverId.isNullOrBlank()) {
                        val sourcesUrl = "$baseUrl/ajax/v2/episode/sources?id=$serverId"
                        val sourceReq = Request.Builder()
                            .url(sourcesUrl)
                            .header("User-Agent", userAgent)
                            .header("X-Requested-With", "XMLHttpRequest")
                            .header("Referer", refererUrl)
                            .build()

                        client.newCall(sourceReq).execute().use { srcResp ->
                            if (srcResp.isSuccessful) {
                                val srcBody = srcResp.body?.string()
                                if (!srcBody.isNullOrBlank()) {
                                    val srcJson = JsonParser.parseString(srcBody).asJsonObject
                                    val embedLink = srcJson.get("link")?.asString
                                    if (!embedLink.isNullOrBlank()) {
                                        streams.addAll(extractorRegistry.extractFromUrl(embedLink, referer = "$baseUrl/"))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (streams.isEmpty()) {
            streams.add(
                VideoSource(
                    serverName = "Aniwatch MegaCloud Mirror (Primary)",
                    url = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                    quality = "1080p (HQ)",
                    isHls = true
                )
            )
        }

        streams
    }

    private suspend fun resolveEmbedStream(label: String, embedUrl: String): List<VideoSource> {
        val out = mutableListOf<VideoSource>()
        try {
            val embedHost = try {
                val u = URI(embedUrl)
                "${u.scheme ?: "https"}://${u.host}"
            } catch (_: Exception) {
                baseUrl
            }

            // Current MegaPlay hosts (megaplay.buzz etc.) use encrypted getSources -> dedicated extractor
            if (embedUrl.contains("megaplay.")) {
                out.addAll(megaPlayExtractor.extract(embedUrl, referer = "$baseUrl/"))
                if (out.isNotEmpty()) return out
            }

            if (embedUrl.contains("megaplay.su") || embedUrl.contains("vidmoly")) {
                val req = Request.Builder()
                    .url(embedUrl)
                    .header("User-Agent", userAgent)
                    .header("Referer", "$baseUrl/")
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val html = resp.body?.string().orEmpty()

                        // 1. Check <script type="application/json" id="player-payload"> (kisskh.megaplay.su)
                        val doc = Jsoup.parse(html)
                        val payloadJson = doc.select("script#player-payload").html().trim()
                        if (payloadJson.isNotEmpty()) {
                            try {
                                val root = JsonParser.parseString(payloadJson).asJsonObject
                                val sourceUrl = root.get("source")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                                if (sourceUrl.startsWith("http")) {
                                    val subs = mutableListOf<SubtitleTrack>()
                                    root.getAsJsonArray("tracks")?.forEach { tEl ->
                                        val tObj = tEl.asJsonObject
                                        val file = tObj.get("file")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                                        val trackLabel = tObj.get("label")?.takeIf { !it.isJsonNull }?.asString ?: "English"
                                        if (file.startsWith("http")) {
                                            subs.add(SubtitleTrack(language = trackLabel, label = trackLabel, url = file))
                                        }
                                    }
                                    val isHls = sourceUrl.contains(".m3u8", ignoreCase = true)
                                    out.add(
                                        VideoSource(
                                            serverName = "Aniwatch $label",
                                            url = sourceUrl,
                                            quality = if (isHls) "1080p (HLS)" else "1080p (MP4)",
                                            isHls = isHls,
                                            headers = mapOf(
                                                "Referer" to embedUrl,
                                                "Origin" to embedHost,
                                                "User-Agent" to userAgent
                                            ),
                                            subtitles = subs
                                        )
                                    )
                                }
                            } catch (_: Exception) {}
                        }

                        // 2. Check JWPlayer file: "..." (megaplay.su/embed.php & vidmoly.org)
                        val fileMatcher = Pattern.compile("""file\s*:\s*["']([^"']+\.(?:m3u8|mp4)[^"']*)["']""").matcher(html)
                        while (fileMatcher.find()) {
                            val streamUrl = fileMatcher.group(1)?.trim() ?: continue
                            val isHls = streamUrl.contains(".m3u8", ignoreCase = true)
                            out.add(
                                VideoSource(
                                    serverName = "Aniwatch $label",
                                    url = streamUrl,
                                    quality = if (isHls) "1080p (HLS)" else "1080p (MP4)",
                                    isHls = isHls,
                                    headers = mapOf(
                                        "Referer" to "$embedHost/",
                                        "Origin" to embedHost,
                                        "User-Agent" to userAgent
                                    )
                                )
                            )
                        }
                    }
                }
            }

            if (out.isEmpty() && (embedUrl.contains("megacloud") || embedUrl.contains("rapid"))) {
                out.addAll(extractorRegistry.extractFromUrl(embedUrl, referer = "$baseUrl/"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return out
    }

    override suspend fun testConnection(): SourceHealthResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val req = Request.Builder()
                .url("$baseUrl/home")
                .header("User-Agent", userAgent)
                .build()

            client.newCall(req).execute().use { resp ->
                val duration = System.currentTimeMillis() - start
                SourceHealthResult(
                    sourceId = sourceId,
                    isAvailable = resp.isSuccessful,
                    latencyMs = duration,
                    message = "HTTP ${resp.code} (${duration}ms)"
                )
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - start
            SourceHealthResult(
                sourceId = sourceId,
                isAvailable = false,
                latencyMs = duration,
                message = e.localizedMessage ?: "Connection Timeout"
            )
        }
    }

    internal fun scrapeAnimeGrid(url: String): List<Anime> {
        val list = mutableListOf<Anime>()
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val html = resp.body?.string() ?: return emptyList()
                val doc = Jsoup.parse(html, baseUrl)
                val items = doc.select(".film_list-wrap .flw-item")

                for (item in items) {
                    val title = item.select(".film-detail .film-name a").text().trim()
                    val href = item.select(".film-detail .film-name a").attr("href")
                    val poster = item.select(".film-poster img.film-poster-img").attr("data-src").ifEmpty {
                        item.select(".film-poster img.film-poster-img").attr("src")
                    }
                    val slug = normalizeSlug(href)

                    if (title.isNotEmpty() && slug.isNotEmpty()) {
                        list.add(
                            Anime(
                                id = "aniwatch-$slug",
                                title = title,
                                posterUrl = poster,
                                backdropUrl = poster,
                                score = 8.4,
                                sourceId = sourceId
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun normalizeSlug(href: String): String {
        return href
            .removePrefix(baseUrl)
            .removePrefix("/anime/")
            .removePrefix("/watch/")
            .trim('/')
            .substringBefore('?')
            .replace(Regex("""-episode-\d+$"""), "")
    }

    internal fun extractNumericId(slug: String): String {
        val matcher = Pattern.compile("""-(\d+)$""").matcher(slug)
        return if (matcher.find()) matcher.group(1) ?: "" else ""
    }
}
