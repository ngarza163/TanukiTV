package com.darknet.animetv.data.model

data class Anime(
    val id: String,
    val title: String,
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val score: Double = 0.0,
    val year: Int = 0,
    val status: String = "Ongoing",
    val genres: List<String> = emptyList(),
    val synopsis: String = "",
    val totalEpisodes: Int = 0,
    val sourceId: String = "default",
    val hasDub: Boolean = false,
    val dubbedEpisodesCount: Int = 0,
    val latestEpisode: Int = 0
)

data class AnimeDetail(
    val anime: Anime,
    val episodes: List<Episode> = emptyList(),
    val trailerUrl: String? = null,
    val studio: String = ""
)

data class Episode(
    val id: String,
    val animeId: String,
    val number: Int,
    val title: String,
    val thumbnail: String = "",
    val releaseDate: String = "",
    val hasDub: Boolean = false,
    val audioTrack: String = "SUB"
)

data class VideoSource(
    val serverName: String,
    val url: String,
    val quality: String = "1080p",
    val isHls: Boolean = url.contains(".m3u8", ignoreCase = true),
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<SubtitleTrack> = emptyList(),
    val audioTrack: String = "SUB"
)

data class SubtitleTrack(
    val language: String,
    val label: String,
    val url: String
)

data class WatchProgress(
    val animeId: String,
    val animeTitle: String,
    val posterUrl: String,
    val episodeId: String,
    val episodeNumber: Int,
    val episodeTitle: String,
    val positionMs: Long,
    val durationMs: Long,
    val lastWatchedTimestamp: Long = System.currentTimeMillis()
) {
    val progressPercentage: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}

data class CustomSource(
    val id: Long = 0,
    val name: String,
    val baseUrl: String,
    val animeListSelector: String = "ul.ListAnimes a[href]",
    val episodeSelector: String = "ul.EpisodeList a[href]",
    val streamUrlSelector: String = "iframe, video",
    val isEnabled: Boolean = true,
    val sourceType: String = "HTML_SCRAPER"
)
