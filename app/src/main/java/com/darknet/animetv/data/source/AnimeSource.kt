package com.darknet.animetv.data.source

import com.darknet.animetv.data.model.Anime
import com.darknet.animetv.data.model.AnimeDetail
import com.darknet.animetv.data.model.VideoSource

/**
 * Core interface contract for all anime catalog and streaming sources in AnimeTV.
 * Decouples scraping logic from UI and repository components.
 */
interface AnimeSource {
    val sourceId: String
    val sourceName: String
    val baseUrl: String get() = ""
    var isEnabled: Boolean

    suspend fun getTrending(): List<Anime>
    suspend fun getPopular(): List<Anime>
    suspend fun getRecentEpisodes(): List<Anime>
    suspend fun search(query: String): List<Anime>
    suspend fun getAnimeDetail(animeId: String): AnimeDetail?
    suspend fun getEpisodeStreams(animeId: String, episodeId: String): List<VideoSource>
    suspend fun getDubbedAnime(): List<Anime> = emptyList()

    /**
     * Pings the source endpoint and returns availability and latency metrics.
     */
    suspend fun testConnection(): SourceHealthResult
}

/**
 * Health check result providing connectivity status and response latency.
 */
data class SourceHealthResult(
    val sourceId: String,
    val isAvailable: Boolean = true,
    val latencyMs: Long = 0L,
    val message: String? = null,
    val isHealthy: Boolean = isAvailable
)
