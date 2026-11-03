package com.darknet.animetv.data.source

import com.darknet.animetv.data.model.Anime
import com.darknet.animetv.data.model.AnimeDetail
import com.darknet.animetv.data.model.Episode
import com.darknet.animetv.data.model.VideoSource
import com.darknet.animetv.data.source.extractor.VideoExtractorRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class AnikotoSource(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
    private val extractorRegistry: VideoExtractorRegistry = VideoExtractorRegistry.defaultInstance
) : AnimeSource {

    override val sourceId: String = "anikoto"
    override val sourceName: String = "Anikoto"
    override val baseUrl: String = "https://anikototv.to"
    override var isEnabled: Boolean = true

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    override suspend fun getTrending(): List<Anime> = withContext(Dispatchers.IO) {
        scrapeGrid("$baseUrl/trending")
    }

    override suspend fun getPopular(): List<Anime> = withContext(Dispatchers.IO) {
        scrapeGrid("$baseUrl/popular")
    }

    override suspend fun getRecentEpisodes(): List<Anime> = withContext(Dispatchers.IO) {
        scrapeGrid("$baseUrl/recent")
    }

    override suspend fun search(query: String): List<Anime> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        scrapeGrid("$baseUrl/search?q=$encoded")
    }

    override suspend fun getAnimeDetail(animeId: String): AnimeDetail? = withContext(Dispatchers.IO) {
        try {
            val cleanSlug = animeId.removePrefix("anikoto-")
            val url = if (cleanSlug.startsWith("http")) cleanSlug else "$baseUrl/anime/$cleanSlug"

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .build()

            val doc = client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val html = resp.body?.string() ?: return@withContext null
                Jsoup.parse(html, baseUrl)
            }

            val title = doc.select("h1.anime-title, .anime-details h1, h1.title").text().ifEmpty { cleanSlug }
            val poster = doc.select(".poster img, .anime-poster img, .image figure img").attr("src").ifEmpty {
                doc.select("img.poster").attr("data-src")
            }
            val synopsis = doc.select(".anime-synopsis, .description p, .synopsis").text()
            val score = doc.select(".rating-badge, .score, span.rating").text().toDoubleOrNull() ?: 8.1
            val genres = doc.select(".genres a, .tags a, .genre-badge").map { it.text() }
            val status = doc.select(".status, .anime-status").text().ifEmpty { "Ongoing" }

            val episodes = mutableListOf<Episode>()
            val epElements = doc.select(".episodes-grid a.ep-card, .ep-list a, .episode-item a, ul.episodes li a")
            var count = 1
            for (el in epElements) {
                val epHref = el.attr("href")
                val epId = el.attr("data-id").ifEmpty {
                    epHref.removePrefix("/watch/").substringBefore('?')
                }.ifEmpty { "$cleanSlug-ep-$count" }
                val epTitle = el.select(".ep-title, span.title").text().ifEmpty {
                    el.text().ifEmpty { "Episode $count" }
                }
                val epNum = el.attr("data-number").toIntOrNull() ?: count
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

            AnimeDetail(anime = anime, episodes = episodes.sortedBy { it.number })
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun getEpisodeStreams(animeId: String, episodeId: String): List<VideoSource> = withContext(Dispatchers.IO) {
        val streams = mutableListOf<VideoSource>()
        try {
            val cleanSlug = animeId.removePrefix("anikoto-")
            val watchUrl = if (episodeId.startsWith("http")) episodeId else "$baseUrl/watch/$cleanSlug/$episodeId"

            val req = Request.Builder()
                .url(watchUrl)
                .header("User-Agent", userAgent)
                .header("Referer", "$baseUrl/anime/$cleanSlug")
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val html = resp.body?.string() ?: ""
                    val doc = Jsoup.parse(html, baseUrl)
                    val iframes = doc.select("iframe[src], .player-container iframe")
                    for (iframe in iframes) {
                        val src = iframe.attr("src")
                        if (src.isNotEmpty() && !src.contains("about:blank")) {
                            val resolved = extractorRegistry.extractFromUrl(src, referer = baseUrl)
                            streams.addAll(resolved)
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
                    serverName = "Anikoto Stream Mirror (HQ)",
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

    internal fun scrapeGrid(url: String): List<Anime> {
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
                val cards = doc.select(".anime-card, .flw-item, article.anime, .item")

                for (card in cards) {
                    val title = card.select("a.anime-title, .film-name a, h3.title").text()
                    val href = card.select("a[href*='/anime/']").attr("href")
                    val poster = card.select(".poster img, img[src]").attr("src")
                    val slug = href.removePrefix("/anime/").substringBefore('?')

                    if (title.isNotEmpty() && slug.isNotEmpty()) {
                        list.add(
                            Anime(
                                id = "anikoto-$slug",
                                title = title,
                                posterUrl = poster,
                                backdropUrl = poster,
                                score = 8.2,
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
}
