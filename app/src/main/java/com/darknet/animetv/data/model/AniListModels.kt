package com.darknet.animetv.data.model

data class AniListUser(
    val id: Int,
    val name: String,
    val avatarUrl: String = "",
    val totalAnime: Int = 0,
    val episodesWatched: Int = 0
)

data class MalUser(
    val id: Int,
    val name: String,
    val avatarUrl: String = "",
    val totalAnime: Int = 0,
    val episodesWatched: Int = 0
)

data class AniListEntry(
    val mediaId: Int,
    val title: String,
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val score: Double = 0.0,
    val progress: Int = 0,
    val totalEpisodes: Int = 0,
    val status: String = "CURRENT", // CURRENT, PLANNING, COMPLETED, DROPPED, PAUSED, REPEATING
    val genres: List<String> = emptyList(),
    val synopsis: String = ""
)

enum class SyncProvider(val displayName: String) {
    ANILIST("AniList"),
    MYANIMELIST("MyAnimeList (MAL)")
}

data class SyncResult(
    val isSuccess: Boolean,
    val syncedCount: Int = 0,
    val message: String = ""
)
