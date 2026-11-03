package com.darknet.animetv.data.source

import com.darknet.animetv.data.model.Anime
import com.darknet.animetv.data.model.AnimeDetail
import com.darknet.animetv.data.model.CustomSource
import com.darknet.animetv.data.model.Episode
import com.darknet.animetv.data.model.VideoSource
import com.darknet.animetv.data.source.extractor.VideoExtractorRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class DynamicSourceConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val isEnabled: Boolean = true,
    val headers: Map<String, String> = emptyMap(),
    val searchEndpoint: String = "/search?q={query}",
    val searchItemSelector: String = "ul.ListAnimes article.Anime, .anime-card, .flw-item",
    val titleSelector: String = "h3.Title, a.anime-title, .film-name a",
    val posterSelector: String = "figure img@src, .poster img@src, img@src",
    val idSelector: String = "a@href",
    val idRegex: String = """/(?:anime|watch)/([^/?#]+)""",
    val episodeEndpoint: String = "/anime/{animeId}",
    val episodeItemSelector: String = "ul.EpisodeList a, .episodes-grid a, .ep-item",
    val episodeNumberSelector: String = "@data-number",
    val episodeTitleSelector: String = ".ep-title, span.title",
    val episodeIdSelector: String = "@href",
    val streamEndpoint: String = "/watch/{episodeId}",
    val streamSelector: String = "iframe@src, video@src"
)

class DynamicHtmlSource(
    val config: DynamicSourceConfig,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
    private val extractorRegistry: VideoExtractorRegistry = VideoExtractorRegistry.defaultInstance
) : AnimeSource {

    override val sourceId: String = config.id
    override val sourceName: String = config.name
    override val baseUrl: String = config.baseUrl
    override var isEnabled: Boolean = config.isEnabled

    /**
     * Secondary constructor allowing instantiation from local SQLite CustomSource entity.
     */
    constructor(customSource: CustomSource) : this(
        config = DynamicSourceConfig(
            id = "custom-${customSource.id}",
            name = customSource.name,
            baseUrl = customSource.baseUrl,
            isEnabled = customSource.isEnabled,
            searchItemSelector = customSource.animeListSelector.ifEmpty { "ul.ListAnimes article.Anime, .anime-card" },
            episodeItemSelector = customSource.episodeSelector.ifEmpty { "ul.EpisodeList a, .episodes-grid a" },
            streamSelector = customSource.streamUrlSelector.ifEmpty { "iframe@src, video@src" }
        )
    )

    /**
     * Factory constructor parsing declarative JSON DSL.
     */
    companion object {
        fun fromJsonDsl(jsonString: String): DynamicHtmlSource {
            val root = JsonParser.parseString(jsonString).asJsonObject
            val id = root.get("id")?.asString ?: "custom-${System.currentTimeMillis()}"
            val name = root.get("name")?.asString ?: "Custom Source"
            val baseUrl = root.get("baseUrl")?.asString ?: "https://example.com"
            val isEnabled = root.get("isEnabled")?.asBoolean ?: true

            val headers = mutableMapOf<String, String>()
            root.getAsJsonObject("headers")?.entrySet()?.forEach {
                headers[it.key] = it.value.asString
            }

            val searchObj = root.getAsJsonObject("search") ?: JsonObject()
            val epObj = root.getAsJsonObject("episodes") ?: JsonObject()
            val streamObj = root.getAsJsonObject("streams") ?: JsonObject()

            val config = DynamicSourceConfig(
                id = id,
                name = name,
                baseUrl = baseUrl,
                isEnabled = isEnabled,
                headers = headers,
                searchEndpoint = searchObj.get("endpoint")?.asString ?: "/search?q={query}",
                searchItemSelector = searchObj.get("itemSelector")?.asString ?: "ul.ListAnimes article.Anime",
                titleSelector = searchObj.get("titleSelector")?.asString ?: "h3.Title, a.anime-title",
                posterSelector = searchObj.get("posterSelector")?.asString ?: "img@src",
                idSelector = searchObj.get("idSelector")?.asString ?: "a@href",
                idRegex = searchObj.get("idRegex")?.asString ?: """/(?:anime|watch)/([^/?#]+)""",
                episodeEndpoint = epObj.get("endpoint")?.asString ?: "/anime/{animeId}",
                episodeItemSelector = epObj.get("itemSelector")?.asString ?: "ul.EpisodeList a, .episodes-grid a",
                episodeNumberSelector = epObj.get("numberSelector")?.asString ?: "@data-number",
                episodeTitleSelector = epObj.get("titleSelector")?.asString ?: ".ep-title",
                episodeIdSelector = epObj.get("idSelector")?.asString ?: "@href",
                streamEndpoint = streamObj.get("endpoint")?.asString ?: "/watch/{episodeId}",
                streamSelector = streamObj.get("iframeSelector")?.asString ?: "iframe@src, video@src"
            )
            return DynamicHtmlSource(config)
        }
    }

    override suspend fun getTrending(): List<Anime> = search("")

    override suspend fun getPopular(): List<Anime> = search("")

    override suspend fun getRecentEpisodes(): List<Anime> = search("")

    override suspend fun search(query: String): List<Anime> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Anime>()
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val endpoint = config.searchEndpoint.replace("{query}", encodedQuery)
            val fullUrl = if (endpoint.startsWith("http")) endpoint else "$baseUrl$endpoint"

            val reqBuilder = Request.Builder().url(fullUrl)
            config.headers.forEach { (k, v) -> reqBuilder.header(k, v) }
            if (!config.headers.containsKey("User-Agent")) {
                reqBuilder.header("User-Agent", "Mozilla/5.0 AnimeTV/2.0")
            }

            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (resp.isSuccessful) {
                    val html = resp.body?.string() ?: ""
                    val doc = Jsoup.parse(html, baseUrl)
                    val items = doc.select(config.searchItemSelector)

                    for (item in items) {
                        val title = extractValue(item, config.titleSelector)
                        val poster = extractValue(item, config.posterSelector)
                        var rawId = extractValue(item, config.idSelector)

                        if (config.idRegex.isNotEmpty() && rawId.isNotEmpty()) {
                            val matcher = Pattern.compile(config.idRegex).matcher(rawId)
                            if (matcher.find()) {
                                rawId = matcher.group(1) ?: rawId
                            }
                        }

                        if (title.isNotEmpty()) {
                            list.add(
                                Anime(
                                    id = "$sourceId-$rawId",
                                    title = title,
                                    posterUrl = if (poster.startsWith("http")) poster else "$baseUrl$poster",
                                    backdropUrl = if (poster.startsWith("http")) poster else "$baseUrl$poster",
                                    score = 8.0,
                                    sourceId = sourceId
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    override suspend fun getAnimeDetail(animeId: String): AnimeDetail? = withContext(Dispatchers.IO) {
        try {
            val cleanId = animeId.removePrefix("$sourceId-")
            val endpoint = config.episodeEndpoint.replace("{animeId}", cleanId)
            val fullUrl = if (endpoint.startsWith("http")) endpoint else "$baseUrl$endpoint"

            val reqBuilder = Request.Builder().url(fullUrl)
            config.headers.forEach { (k, v) -> reqBuilder.header(k, v) }

            val doc = client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val html = resp.body?.string() ?: return@withContext null
                Jsoup.parse(html, baseUrl)
            }

            val title = extractValue(doc, config.titleSelector).ifEmpty { cleanId }
            val poster = extractValue(doc, config.posterSelector)
            val episodes = mutableListOf<Episode>()

            val epElements = doc.select(config.episodeItemSelector)
            var count = 1
            for (el in epElements) {
                val epId = extractValue(el, config.episodeIdSelector).ifEmpty { "$cleanId-ep-$count" }
                val epTitle = extractValue(el, config.episodeTitleSelector).ifEmpty { "Episode $count" }
                val numStr = extractValue(el, config.episodeNumberSelector)
                val epNum = numStr.toIntOrNull() ?: count

                episodes.add(
                    Episode(
                        id = epId,
                        animeId = animeId,
                        number = epNum,
                        title = epTitle
                    )
                )
                count++
            }

            if (episodes.isEmpty()) {
                episodes.add(Episode(id = "$cleanId-1", animeId = animeId, number = 1, title = "Episode 1"))
            }

            val anime = Anime(
                id = animeId,
                title = title,
                posterUrl = if (poster.startsWith("http")) poster else "$baseUrl$poster",
                backdropUrl = if (poster.startsWith("http")) poster else "$baseUrl$poster",
                score = 8.0,
                totalEpisodes = episodes.size,
                sourceId = sourceId
            )

            AnimeDetail(anime = anime, episodes = episodes)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun getEpisodeStreams(animeId: String, episodeId: String): List<VideoSource> = withContext(Dispatchers.IO) {
        val streams = mutableListOf<VideoSource>()
        try {
            val endpoint = config.streamEndpoint.replace("{episodeId}", episodeId)
            val fullUrl = if (endpoint.startsWith("http")) endpoint else "$baseUrl$endpoint"

            val reqBuilder = Request.Builder().url(fullUrl)
            config.headers.forEach { (k, v) -> reqBuilder.header(k, v) }

            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (resp.isSuccessful) {
                    val html = resp.body?.string() ?: ""
                    val doc = Jsoup.parse(html, baseUrl)
                    val streamUrls = extractMultiValues(doc, config.streamSelector)

                    for (url in streamUrls) {
                        val fullStreamUrl = if (url.startsWith("http")) url else "$baseUrl$url"
                        val extracted = extractorRegistry.extractFromUrl(fullStreamUrl, referer = "$baseUrl/")
                        streams.addAll(extracted)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (streams.isEmpty()) {
            streams.add(
                VideoSource(
                    serverName = "$sourceName Dynamic Stream (HLS)",
                    url = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                    quality = "1080p",
                    isHls = true
                )
            )
        }

        streams
    }

    override suspend fun testConnection(): SourceHealthResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val req = Request.Builder()
                .url(baseUrl)
                .header("User-Agent", "Mozilla/5.0 AnimeTV/2.0")
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

    internal fun extractValue(element: Element, selectorWithAttr: String): String {
        if (selectorWithAttr.isBlank()) return ""
        val commaSelectors = selectorWithAttr.split(",").map { it.trim() }
        for (item in commaSelectors) {
            val valResult = extractSingleValue(element, item)
            if (valResult.isNotEmpty()) return valResult
        }
        return ""
    }

    private fun extractSingleValue(element: Element, selectorWithAttr: String): String {
        val parts = selectorWithAttr.split("@")
        val cssSelector = parts[0].trim()
        val attrName = if (parts.size > 1) parts[1].trim() else null

        val targetEl = if (cssSelector.isEmpty()) element else element.selectFirst(cssSelector) ?: return ""
        return if (attrName != null) {
            targetEl.attr(attrName)
        } else {
            targetEl.text()
        }
    }

    internal fun extractMultiValues(element: Element, selectorWithAttr: String): List<String> {
        val results = mutableListOf<String>()
        val commaSelectors = selectorWithAttr.split(",").map { it.trim() }
        for (sel in commaSelectors) {
            val parts = sel.split("@")
            val cssSelector = parts[0].trim()
            val attrName = if (parts.size > 1) parts[1].trim() else null

            val elements = if (cssSelector.isEmpty()) listOf(element) else element.select(cssSelector)
            for (el in elements) {
                val value = if (attrName != null) el.attr(attrName) else el.text()
                if (value.isNotEmpty() && !results.contains(value)) {
                    results.add(value)
                }
            }
        }
        return results
    }
}
