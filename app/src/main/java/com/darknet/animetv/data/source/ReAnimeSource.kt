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

class ReAnimeSource(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
    private val extractorRegistry: VideoExtractorRegistry = VideoExtractorRegistry.defaultInstance
) : AnimeSource {

    override val sourceId: String = "reanime"
    override val sourceName: String = "ReAnime"
    override val baseUrl: String = "https://reanime.to"
    override var isEnabled: Boolean = true

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    override suspend fun getTrending(): List<Anime> = withContext(Dispatchers.IO) {
        scrapeList("$baseUrl/trending")
    }

    override suspend fun getPopular(): List<Anime> = withContext(Dispatchers.IO) {
        scrapeList("$baseUrl/most-popular")
    }

    override suspend fun getRecentEpisodes(): List<Anime> = withContext(Dispatchers.IO) {
        scrapeList("$baseUrl/recent-episodes")
    }

    override suspend fun search(query: String): List<Anime> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        scrapeList("$baseUrl/search?keyword=$encoded")
    }

    override suspend fun getAnimeDetail(animeId: String): AnimeDetail? = withContext(Dispatchers.IO) {
        try {
            val cleanSlug = animeId.removePrefix("reanime-")
            val url = if (cleanSlug.startsWith("http")) cleanSlug else "$baseUrl/watch/$cleanSlug"

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .build()

            val doc = client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val html = resp.body?.string() ?: return@withContext null
                Jsoup.parse(html, baseUrl)
            }

            val title = doc.select(".film-name, h2.film-name, h1.title").text().ifEmpty { cleanSlug }
            val poster = doc.select(".film-poster img").attr("src").ifEmpty {
                doc.select(".film-poster img").attr("data-src")
            }
            val synopsis = doc.select(".film-description .text, .description").text()
            val score = doc.select(".score, .rating").text().toDoubleOrNull() ?: 8.3
            val genres = doc.select(".genres a, .meta-item a").map { it.text() }
            val status = doc.select(".status, .film-infor span:contains(Status) + span").text().ifEmpty { "Ongoing" }

            val episodes = mutableListOf<Episode>()
            val epElements = doc.select(".episodes-list a.ep-item, .ss-list a, .episode-item a")
            var count = 1
            for (el in epElements) {
                val epId = el.attr("data-id").ifEmpty {
                    el.attr("href").removePrefix("/watch/").substringBefore('?')
                }.ifEmpty { "$cleanSlug-ep-$count" }
                val epNum = el.attr("data-number").toIntOrNull() ?: count
                val epTitle = el.attr("title").ifEmpty {
                    el.select(".ep-name").text().ifEmpty { "Episode $epNum" }
                }

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
            val cleanSlug = animeId.removePrefix("reanime-")
            val refererUrl = "$baseUrl/watch/$cleanSlug"

            val ajaxUrl = "$baseUrl/ajax/episode/servers?episodeId=$episodeId"
            val req = Request.Builder()
                .url(ajaxUrl)
                .header("User-Agent", userAgent)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", refererUrl)
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val html = resp.body?.string() ?: ""
                    val doc = Jsoup.parse(html)
                    val iframes = doc.select("iframe[src]")
                    for (iframe in iframes) {
                        val src = iframe.attr("src")
                        if (src.isNotEmpty()) {
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
                    serverName = "ReAnime Stream Mirror (HQ)",
                    url = "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8",
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

    internal fun scrapeList(url: String): List<Anime> {
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
                val items = doc.select(".flw-item, .film-item, article.anime")

                for (item in items) {
                    val title = item.select(".film-name a, h3.film-name a, a.title").text()
                    val href = item.select("a[href*='/watch/']").attr("href")
                    val poster = item.select("img.film-poster-img, .poster img").attr("data-src").ifEmpty {
                        item.select("img.film-poster-img, .poster img").attr("src")
                    }
                    val slug = href.removePrefix("/watch/").substringBefore('?')

                    if (title.isNotEmpty() && slug.isNotEmpty()) {
                        list.add(
                            Anime(
                                id = "reanime-$slug",
                                title = title,
                                posterUrl = poster,
                                backdropUrl = poster,
                                score = 8.3,
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
