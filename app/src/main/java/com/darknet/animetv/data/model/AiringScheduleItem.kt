package com.darknet.animetv.data.model

import java.util.Locale

/**
 * Representation of a scheduled simulcast anime broadcast.
 */
data class AiringScheduleItem(
    val id: String,
    val animeId: String,
    val title: String,
    val romajiTitle: String = "",
    val posterUrl: String,
    val bannerUrl: String = "",
    val episodeNumber: Int,
    val airingAt: Long, // Unix epoch seconds
    val timeUntilAiringSeconds: Long,
    val dayOfWeek: String, // "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
    val genres: List<String> = emptyList(),
    val score: Double = 0.0,
    val format: String = "TV"
) {
    /**
     * Formats the countdown until broadcast into a TV-friendly pill text.
     */
    fun formatCountdown(): String {
        if (timeUntilAiringSeconds <= 0L) {
            return "Aired Today"
        }
        val hours = timeUntilAiringSeconds / 3600
        val minutes = (timeUntilAiringSeconds % 3600) / 60
        return when {
            hours >= 48 -> "In ${(hours / 24)} days"
            hours >= 24 -> "In 1 day ${(hours % 24)}h"
            hours > 0 -> "In ${hours}h ${minutes}m"
            else -> "In ${minutes}m"
        }
    }

    /**
     * Converts this schedule entry to an [Anime] model for details navigation.
     */
    fun toAnime(): Anime {
        return Anime(
            id = animeId,
            title = title,
            posterUrl = posterUrl,
            backdropUrl = bannerUrl.ifBlank { posterUrl },
            score = score,
            year = 2026,
            status = "Ongoing",
            genres = genres,
            totalEpisodes = episodeNumber,
            synopsis = "Simulcast episode $episodeNumber airing on $dayOfWeek."
        )
    }
}
