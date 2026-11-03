package com.darknet.animetv.data.source

import com.darknet.animetv.data.model.Anime
import com.darknet.animetv.data.model.AnimeDetail
import com.darknet.animetv.data.model.Episode
import com.darknet.animetv.data.model.VideoSource
import com.darknet.animetv.data.source.extractor.VideoExtractorRegistry
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class AnimeFlvSource(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
    private val extractorRegistry: VideoExtractorRegistry = VideoExtractorRegistry.defaultInstance
) : AnimeSource {

    override val sourceId: String = "animeflv"
    override val sourceName: String = "AnimeFLV"
    override val baseUrl: String = "https://www3.animeflv.net"
    override var isEnabled: Boolean = true

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    override suspend fun getTrending(): List<Anime> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Anime>()
        try {
            val doc = Jsoup.connect("$baseUrl/browse?order=rating")
                .userAgent(userAgent)
                .timeout(10000)
                .get()

            val elements = doc.select("ul.ListAnimes article.Anime")
            for (el in elements) {
                val title = el.select("h3.Title").text()
                val relUrl = el.select("a").attr("href")
                val poster = el.select("figure img").attr("src")
                val score = el.select("span.Vts").text().toDoubleOrNull() ?: 8.5
                val id = relUrl.removePrefix("/anime/")

                if (title.isNotEmpty()) {
                    list.add(
                        Anime(
                            id = "aflv-$id",
                            title = title,
                            posterUrl = if (poster.startsWith("http")) poster else "$baseUrl$poster",
                            backdropUrl = if (poster.startsWith("http")) poster else "$baseUrl$poster",
                            score = score,
                            sourceId = sourceId
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    override suspend fun getPopular(): List<Anime> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Anime>()
        try {
            val doc = Jsoup.connect("$baseUrl/browse?order=views")
                .userAgent(userAgent)
                .timeout(10000)
                .get()

            val elements = doc.select("ul.ListAnimes article.Anime")
            for (el in elements) {
                val title = el.select("h3.Title").text()
                val relUrl = el.select("a").attr("href")
                val poster = el.select("figure img").attr("src")
                val score = el.select("span.Vts").text().toDoubleOrNull() ?: 8.0
                val id = relUrl.removePrefix("/anime/")

                if (title.isNotEmpty()) {
                    list.add(
                        Anime(
                            id = "aflv-$id",
                            title = title,
                            posterUrl = if (poster.startsWith("http")) poster else "$baseUrl$poster",
                            backdropUrl = if (poster.startsWith("http")) poster else "$baseUrl$poster",
                            score = score,
                            sourceId = sourceId
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    override suspend fun getRecentEpisodes(): List<Anime> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Anime>()
        try {
            val doc = Jsoup.connect(baseUrl)
                .userAgent(userAgent)
                .timeout(10000)
                .get()

            val elements = doc.select("ul.ListCaps li.fa-play-circle")
            for (el in elements) {
                val title = el.select("strong.Title").text()
                val epText = el.select("span.Capi").text() // e.g. "Episodio 12"
                val poster = el.select("span.Image img").attr("src")
                val relUrl = el.select("a").attr("href")
                val id = relUrl.removePrefix("/ver/")

                if (title.isNotEmpty()) {
                    list.add(
                        Anime(
                            id = "aflv-$id",
                            title = "$title ($epText)",
                            posterUrl = if (poster.startsWith("http")) poster else "$baseUrl$poster",
                            backdropUrl = if (poster.startsWith("http")) poster else "$baseUrl$poster",
                            score = 8.0,
                            sourceId = sourceId
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    override suspend fun search(query: String): List<Anime> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Anime>()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val doc = Jsoup.connect("$baseUrl/browse?q=$encoded")
                .userAgent(userAgent)
                .timeout(10000)
                .get()

            val elements = doc.select("ul.ListAnimes article.Anime")
            for (el in elements) {
                val title = el.select("h3.Title").text()
                val relUrl = el.select("a").attr("href")
                val poster = el.select("figure img").attr("src")
                val score = el.select("span.Vts").text().toDoubleOrNull() ?: 8.0
                val id = relUrl.removePrefix("/anime/")

                if (title.isNotEmpty()) {
                    list.add(
                        Anime(
                            id = "aflv-$id",
                            title = title,
                            posterUrl = if (poster.startsWith("http")) poster else "$baseUrl$poster",
                            backdropUrl = if (poster.startsWith("http")) poster else "$baseUrl$poster",
                            score = score,
                            sourceId = sourceId
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    override suspend fun getAnimeDetail(animeId: String): AnimeDetail? = withContext(Dispatchers.IO) {
        try {
            val cleanId = animeId.removePrefix("aflv-")
            val doc = Jsoup.connect("$baseUrl/anime/$cleanId")
                .userAgent(userAgent)
                .timeout(10000)
                .get()

            val title = doc.select("h1.Title").text()
            val synopsis = doc.select("div.Description p").text()
            val poster = doc.select("div.Image figure img").attr("src")
            val genres = doc.select("nav.Nvgnrs a").map { it.text() }
            val status = doc.select("span.fa-tv").text().ifEmpty { "Finished" }

            val episodes = mutableListOf<Episode>()
            val scripts = doc.select("script")
            for (script in scripts) {
                val data = script.data()
                if (data.contains("var episodes = [")) {
                    val regex = Regex("""\[(\d+),\s*(\d+)\]""")
                    val matches = regex.findAll(data)
                    for (m in matches) {
                        val epNum = m.groupValues[1].toIntOrNull() ?: 1
                        episodes.add(
                            Episode(
                                id = "$cleanId-$epNum",
                                animeId = animeId,
                                number = epNum,
                                title = "Episode $epNum"
                            )
                        )
                    }
                    break
                }
            }

            if (episodes.isEmpty()) {
                episodes.add(Episode(id = "$cleanId-1", animeId = animeId, number = 1, title = "Episode 1"))
            }

            val anime = Anime(
                id = animeId,
                title = title,
                posterUrl = if (poster.startsWith("http")) poster else "$baseUrl$poster",
                backdropUrl = if (poster.startsWith("http")) poster else "$baseUrl$poster",
                score = 8.5,
                genres = genres,
                status = status,
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

        streams
    }

    override suspend fun testConnection(): SourceHealthResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val req = Request.Builder()
                .url(baseUrl)
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

    /**
     * Extracts video embed/stream URLs from the `var videos = {...};` script in AnimeFLV.
     */
    internal fun extractVideosJson(html: String): List<String> {
        val urls = mutableListOf<String>()
        val pattern = Pattern.compile("""var\s+videos\s*=\s*(\{.*?\});""", Pattern.DOTALL)
        val matcher = pattern.matcher(html)
        if (matcher.find()) {
            val jsonStr = matcher.group(1) ?: return emptyList()
            try {
                val root = JsonParser.parseString(jsonStr).asJsonObject
                // AnimeFLV has "SUB" or "LAT" keys with arrays of video servers
                for (entry in root.entrySet()) {
                    if (entry.value.isJsonArray) {
                        val arr = entry.value.asJsonArray
                        for (el in arr) {
                            val obj = el.asJsonObject
                            val code = obj.get("code")?.asString
                            if (!code.isNullOrBlank()) {
                                urls.add(code)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return urls
    }
}
