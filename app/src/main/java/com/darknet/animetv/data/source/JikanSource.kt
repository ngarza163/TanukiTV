package com.darknet.animetv.data.source

import com.darknet.animetv.data.model.Anime
import com.darknet.animetv.data.model.AnimeDetail
import com.darknet.animetv.data.model.Episode
import com.darknet.animetv.data.model.VideoSource
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class JikanSource(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) : AnimeSource {

    override val sourceId: String = "jikan"
    override val sourceName: String = "AniList / Jikan Catalog"
    override val baseUrl: String = "https://api.jikan.moe/v4"
    override var isEnabled: Boolean = true

    override suspend fun getTrending(): List<Anime> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/top/anime?filter=airing&limit=15"
        fetchAnimeList(url)
    }

    override suspend fun getPopular(): List<Anime> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/top/anime?filter=bypopularity&limit=15"
        fetchAnimeList(url)
    }

    override suspend fun getRecentEpisodes(): List<Anime> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/seasons/now?limit=15"
        fetchAnimeList(url)
    }

    override suspend fun search(query: String): List<Anime> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "$baseUrl/anime?q=$encoded&limit=24&sfw=true"
        fetchAnimeList(url)
    }

    override suspend fun getAnimeDetail(animeId: String): AnimeDetail? = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/anime/$animeId/full"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "AnimeTV/2.0 (Android TV)")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val body = response.body?.string() ?: return@withContext null
            val root = JsonParser.parseString(body).asJsonObject
            val data = root.getAsJsonObject("data") ?: return@withContext null

            val anime = parseAnimeItem(data)

            // Fetch episodes
            val episodes = fetchEpisodes(animeId, anime.totalEpisodes)

            AnimeDetail(
                anime = anime,
                episodes = episodes,
                trailerUrl = data.getAsJsonObject("trailer")?.get("embed_url")?.asString,
                studio = data.getAsJsonArray("studios")?.firstOrNull()?.asJsonObject?.get("name")?.asString ?: "Unknown"
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun fetchEpisodes(animeId: String, totalEpisodes: Int): List<Episode> {
        val episodes = mutableListOf<Episode>()
        try {
            val epUrl = "$baseUrl/anime/$animeId/episodes"
            val epReq = Request.Builder()
                .url(epUrl)
                .header("User-Agent", "AnimeTV/2.0 (Android TV)")
                .build()
            val epResp = client.newCall(epReq).execute()
            if (epResp.isSuccessful) {
                val epBody = epResp.body?.string()
                if (epBody != null) {
                    val root = JsonParser.parseString(epBody).asJsonObject
                    val epArray = root.getAsJsonArray("data")
                    if (epArray != null && epArray.size() > 0) {
                        for (i in 0 until epArray.size()) {
                            val epObj = epArray.get(i).asJsonObject
                            val epNum = epObj.get("mal_id")?.asInt ?: (i + 1)
                            val epTitle = epObj.get("title")?.asString ?: "Episode $epNum"
                            episodes.add(
                                Episode(
                                    id = "$animeId-ep-$epNum",
                                    animeId = animeId,
                                    number = epNum,
                                    title = epTitle,
                                    releaseDate = epObj.get("aired")?.asString ?: ""
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // If episode API is empty or paginated, fallback to generated episode list based on count
        if (episodes.isEmpty()) {
            val count = if (totalEpisodes in 1..100) totalEpisodes else 12
            for (i in 1..count) {
                episodes.add(
                    Episode(
                        id = "$animeId-ep-$i",
                        animeId = animeId,
                        number = i,
                        title = "Episode $i"
                    )
                )
            }
        }

        return episodes
    }

    override suspend fun getEpisodeStreams(animeId: String, episodeId: String): List<VideoSource> {
        return emptyList()
    }

    private fun fetchAnimeList(url: String): List<Anime> {
        val list = mutableListOf<Anime>()
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "AnimeTV/2.0 (Android TV)")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()

            val body = response.body?.string() ?: return emptyList()
            val root = JsonParser.parseString(body).asJsonObject
            val data = root.getAsJsonArray("data") ?: return emptyList()

            for (i in 0 until data.size()) {
                val item = data.get(i).asJsonObject
                list.add(parseAnimeItem(item))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun parseAnimeItem(data: JsonObject): Anime {
        val id = data.get("mal_id")?.asString ?: ""
        val title = data.get("title")?.asString ?: "Untitled"
        val images = data.getAsJsonObject("images")?.getAsJsonObject("jpg")
        val poster = images?.get("large_image_url")?.asString
            ?: images?.get("image_url")?.asString ?: ""
        val backdrop = images?.get("large_image_url")?.asString ?: poster
        val score = data.get("score")?.let { if (!it.isJsonNull) it.asDouble else 0.0 } ?: 0.0
        val year = data.get("year")?.let { if (!it.isJsonNull) it.asInt else 0 } ?: 0
        val status = data.get("status")?.asString ?: "Airing"
        val synopsis = data.get("synopsis")?.let { if (!it.isJsonNull) it.asString else "" } ?: ""
        val totalEp = data.get("episodes")?.let { if (!it.isJsonNull) it.asInt else 0 } ?: 0

        val genres = mutableListOf<String>()
        val genreArray = data.getAsJsonArray("genres")
        if (genreArray != null) {
            for (g in genreArray) {
                val name = g.asJsonObject.get("name")?.asString
                if (name != null) genres.add(name)
            }
        }

        return Anime(
            id = id,
            title = title,
            posterUrl = poster,
            backdropUrl = backdrop,
            score = score,
            year = year,
            status = status,
            genres = genres,
            synopsis = synopsis,
            totalEpisodes = totalEp,
            sourceId = sourceId
        )
    }

    override suspend fun testConnection(): SourceHealthResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val req = Request.Builder()
                .url("$baseUrl/top/anime?limit=1")
                .header("User-Agent", "AnimeTV/2.0 (Android TV)")
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
