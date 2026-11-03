package com.darknet.animetv.e2e.harness

import com.darknet.animetv.data.model.Anime
import com.darknet.animetv.data.model.AnimeDetail
import com.darknet.animetv.data.model.CustomSource
import com.darknet.animetv.data.model.VideoSource
import com.darknet.animetv.data.source.AnimeSource
import com.darknet.animetv.data.source.SourceHealthResult
import org.jsoup.Jsoup
import java.io.IOException

class MockAnimeSource(
    override val sourceId: String,
    override val sourceName: String,
    override var isEnabled: Boolean = true,
    var shouldFail: Boolean = false,
    var failureException: Exception? = null,
    var simulatedLatencyMs: Long = 10L,
    private val defaultAnimeList: List<Anime> = E2eTestFixtures.createSampleAnimeList(sourceId = sourceId)
) : AnimeSource {

    var searchCallCount = 0
    var detailCallCount = 0
    var streamsCallCount = 0

    override suspend fun getTrending(): List<Anime> {
        checkHealth()
        return defaultAnimeList
    }

    override suspend fun getPopular(): List<Anime> {
        checkHealth()
        return defaultAnimeList.reversed()
    }

    override suspend fun getRecentEpisodes(): List<Anime> {
        checkHealth()
        return defaultAnimeList.take(5)
    }

    override suspend fun search(query: String): List<Anime> {
        searchCallCount++
        checkHealth()
        if (query.isBlank()) return emptyList()
        return defaultAnimeList.filter { it.title.contains(query, ignoreCase = true) }
    }

    override suspend fun getAnimeDetail(animeId: String): AnimeDetail? {
        detailCallCount++
        checkHealth()
        val anime = defaultAnimeList.find { it.id == animeId }
            ?: E2eTestFixtures.createSampleAnime(id = animeId, sourceId = sourceId)
        return E2eTestFixtures.createSampleDetail(animeId = anime.id)
    }

    override suspend fun getEpisodeStreams(animeId: String, episodeId: String): List<VideoSource> {
        streamsCallCount++
        checkHealth()
        return E2eTestFixtures.createSampleStreams(animeId = animeId, episodeId = episodeId)
    }

    override suspend fun testConnection(): SourceHealthResult {
        return if (shouldFail) {
            SourceHealthResult(
                sourceId = sourceId,
                isAvailable = false,
                latencyMs = simulatedLatencyMs,
                message = failureException?.message ?: "Simulated provider failure"
            )
        } else {
            SourceHealthResult(
                sourceId = sourceId,
                isAvailable = true,
                latencyMs = simulatedLatencyMs
            )
        }
    }

    private fun checkHealth() {
        if (!isEnabled) {
            throw IllegalStateException("Source $sourceId is disabled")
        }
        if (shouldFail) {
            throw (failureException ?: IOException("Network error on $sourceId: 503 Service Unavailable"))
        }
    }
}

class MockFailoverRepository(
    private val sources: MutableList<MockAnimeSource> = mutableListOf()
) {
    fun addSource(source: MockAnimeSource) {
        sources.add(source)
    }

    fun setSources(newSources: List<MockAnimeSource>) {
        sources.clear()
        sources.addAll(newSources)
    }

    fun getActiveSources(): List<MockAnimeSource> = sources.filter { it.isEnabled }

    suspend fun getTrending(): List<Anime> {
        for (source in getActiveSources()) {
            try {
                val results = source.getTrending()
                if (results.isNotEmpty()) return results
            } catch (e: Exception) {
                // Failover to next source
                continue
            }
        }
        return emptyList()
    }

    suspend fun searchWithFailover(query: String): List<Anime> {
        for (source in getActiveSources()) {
            try {
                val results = source.search(query)
                if (results.isNotEmpty()) return results
            } catch (e: Exception) {
                // Failover to next source
                continue
            }
        }
        return emptyList()
    }

    suspend fun getEpisodeStreamsWithFailover(
        animeId: String,
        episodeId: String,
        preferredSourceId: String? = null
    ): List<VideoSource> {
        val orderedSources = if (preferredSourceId != null) {
            getActiveSources().sortedByDescending { it.sourceId == preferredSourceId }
        } else {
            getActiveSources()
        }

        for (source in orderedSources) {
            try {
                val streams = source.getEpisodeStreams(animeId, episodeId)
                if (streams.isNotEmpty()) return streams
            } catch (e: Exception) {
                // Failover
                continue
            }
        }
        return emptyList()
    }

    suspend fun testAllSources(): Map<String, SourceHealthResult> {
        return sources.associate { it.sourceId to it.testConnection() }
    }
}

interface VideoExtractorContract {
    val serverName: String
    fun canHandle(url: String): Boolean
    fun extract(embedUrl: String, referer: String? = null): List<VideoSource>
}

class MegaCloudExtractor : VideoExtractorContract {
    override val serverName: String = "MegaCloud"
    override fun canHandle(url: String): Boolean = url.contains("megacloud", ignoreCase = true)

    override fun extract(embedUrl: String, referer: String?): List<VideoSource> {
        return listOf(
            VideoSource(
                serverName = serverName,
                url = "https://megacloud.stream/hls/master.m3u8",
                quality = "1080p",
                isHls = true,
                headers = mapOf("Referer" to (referer ?: "https://aniwatchtv.ro/"))
            )
        )
    }
}

class StreamWishExtractor : VideoExtractorContract {
    override val serverName: String = "StreamWish"
    override fun canHandle(url: String): Boolean = url.contains("streamwish", ignoreCase = true)

    override fun extract(embedUrl: String, referer: String?): List<VideoSource> {
        return listOf(
            VideoSource(
                serverName = serverName,
                url = "https://streamwish.to/hls/adaptive.m3u8",
                quality = "720p",
                isHls = true,
                headers = mapOf("Referer" to (referer ?: "https://streamwish.to/"))
            )
        )
    }
}

class Mp4UploadExtractor : VideoExtractorContract {
    override val serverName: String = "Mp4Upload"
    override fun canHandle(url: String): Boolean = url.contains("mp4upload", ignoreCase = true)

    override fun extract(embedUrl: String, referer: String?): List<VideoSource> {
        return listOf(
            VideoSource(
                serverName = serverName,
                url = "https://mp4upload.mock/direct.mp4",
                quality = "480p",
                isHls = false,
                headers = emptyMap()
            )
        )
    }
}

class DynamicHtmlSourceEngine {
    fun parseAnimeList(html: String, customSource: CustomSource): List<Anime> {
        val doc = Jsoup.parse(html)
        val elements = doc.select(customSource.animeListSelector)
        val results = mutableListOf<Anime>()
        for ((idx, el) in elements.withIndex()) {
            val title = el.text().ifEmpty { el.attr("title") }
            val href = el.attr("href")
            if (title.isNotEmpty() || href.isNotEmpty()) {
                results.add(
                    Anime(
                        id = "custom-${customSource.id}-$idx",
                        title = title.ifEmpty { "Anime $idx" },
                        posterUrl = customSource.baseUrl + "/thumb.jpg",
                        sourceId = "custom-${customSource.id}"
                    )
                )
            }
        }
        return results
    }

    fun parseStreamUrl(html: String, customSource: CustomSource): String? {
        val doc = Jsoup.parse(html)
        val el = doc.select(customSource.streamUrlSelector).firstOrNull()
        return el?.attr("src")?.ifEmpty { el.attr("href") }
    }
}
