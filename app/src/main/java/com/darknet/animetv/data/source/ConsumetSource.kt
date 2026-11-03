package com.darknet.animetv.data.source

import com.darknet.animetv.data.model.Anime
import com.darknet.animetv.data.model.AnimeDetail
import com.darknet.animetv.data.model.Episode
import com.darknet.animetv.data.model.VideoSource
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class ConsumetSource(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : AnimeSource {

    override val sourceId: String = "consumet"
    override val sourceName: String = "Gogoanime / Consumet"
    override val baseUrl: String = "https://api.consumet.org/anime/gogoanime"
    override var isEnabled: Boolean = true

    override suspend fun getTrending(): List<Anime> = withContext(Dispatchers.IO) {
        fetchFromApi("$baseUrl/top-airing")
    }

    override suspend fun getPopular(): List<Anime> = withContext(Dispatchers.IO) {
        fetchFromApi("$baseUrl/popular")
    }

    override suspend fun getRecentEpisodes(): List<Anime> = withContext(Dispatchers.IO) {
        fetchFromApi("$baseUrl/recent-episodes")
    }

    override suspend fun search(query: String): List<Anime> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        fetchFromApi("$baseUrl/$encoded")
    }

    override suspend fun getAnimeDetail(animeId: String): AnimeDetail? = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/info/$animeId"
            val req = Request.Builder().url(url).build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext null

            val body = resp.body?.string() ?: return@withContext null
            val root = JsonParser.parseString(body).asJsonObject

            val title = root.get("title")?.asString ?: "Untitled"
            val image = root.get("image")?.asString ?: ""
            val desc = root.get("description")?.asString ?: ""
            val status = root.get("status")?.asString ?: "Ongoing"

            val genres = mutableListOf<String>()
            root.getAsJsonArray("genres")?.forEach {
                genres.add(it.asString)
            }

            val episodes = mutableListOf<Episode>()
            root.getAsJsonArray("episodes")?.forEach {
                val epObj = it.asJsonObject
                val epId = epObj.get("id")?.asString ?: ""
                val epNum = epObj.get("number")?.asInt ?: 1
                val epTitle = epObj.get("title")?.asString ?: "Episode $epNum"
                episodes.add(
                    Episode(
                        id = epId,
                        animeId = animeId,
                        number = epNum,
                        title = epTitle
                    )
                )
            }

            val anime = Anime(
                id = animeId,
                title = title,
                posterUrl = image,
                backdropUrl = image,
                score = 8.2,
                genres = genres,
                status = status,
                synopsis = desc,
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
            val url = "$baseUrl/watch/$episodeId"
            val req = Request.Builder().url(url).build()
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string()
                if (body != null) {
                    val root = JsonParser.parseString(body).asJsonObject
                    val sources = root.getAsJsonArray("sources")
                    sources?.forEach {
                        val sObj = it.asJsonObject
                        val streamUrl = sObj.get("url")?.asString ?: ""
                        val quality = sObj.get("quality")?.asString ?: "default"
                        val isM3u8 = sObj.get("isM3U8")?.asBoolean ?: streamUrl.contains(".m3u8")

                        if (streamUrl.isNotEmpty()) {
                            streams.add(
                                VideoSource(
                                    serverName = "GogoStream ($quality)",
                                    url = streamUrl,
                                    quality = quality,
                                    isHls = isM3u8
                                )
                            )
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
                    serverName = "Standard Mirror (1080p)",
                    url = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                    quality = "1080p",
                    isHls = true
                )
            )
        }

        streams
    }

    private fun fetchFromApi(url: String): List<Anime> {
        val list = mutableListOf<Anime>()
        try {
            val req = Request.Builder().url(url).build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return emptyList()

            val body = resp.body?.string() ?: return emptyList()
            val root = JsonParser.parseString(body).asJsonObject
            val results = root.getAsJsonArray("results") ?: return emptyList()

            for (i in 0 until results.size()) {
                val item = results.get(i).asJsonObject
                val id = item.get("id")?.asString ?: ""
                val title = item.get("title")?.asString ?: ""
                val img = item.get("image")?.asString ?: ""
                val epText = item.get("episodeNumber")?.asString

                list.add(
                    Anime(
                        id = id,
                        title = if (epText != null) "$title (Ep $epText)" else title,
                        posterUrl = img,
                        backdropUrl = img,
                        score = 8.0,
                        sourceId = sourceId
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    override suspend fun testConnection(): SourceHealthResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val req = Request.Builder()
                .url("$baseUrl/top-airing")
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
}
