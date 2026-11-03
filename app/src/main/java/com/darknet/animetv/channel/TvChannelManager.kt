package com.darknet.animetv.channel

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.ChannelLogoUtils
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.darknet.animetv.R
import com.darknet.animetv.data.model.Anime
import com.darknet.animetv.data.model.Episode

/**
 * Android TV Home Launcher Channels and "Watch Next" manager.
 * Publishes and updates Preview Channels and Watch Next programs using TvContractCompat.
 */
object TvChannelManager {
    private const val TAG = "TvChannelManager"
    const val TRENDING_CHANNEL_ID_KEY = "channel_trending_anime"

    /**
     * Publishes or refreshes the "Trending Anime" preview channel on the Android TV home screen.
     */
    fun publishTrendingChannel(context: Context, animeList: List<Anime>): Long {
        if (animeList.isEmpty()) return -1L
        try {
            val channelId = getOrCreateTrendingChannel(context)
            if (channelId <= 0L) {
                Log.w(TAG, "Failed to get or create Trending Channel")
                return -1L
            }

            // Remove existing preview programs for this channel
            context.contentResolver.delete(
                TvContractCompat.PreviewPrograms.CONTENT_URI,
                "${TvContractCompat.PreviewPrograms.COLUMN_CHANNEL_ID} = ?",
                arrayOf(channelId.toString())
            )

            // Insert top 15 trending anime as preview cards
            animeList.take(15).forEachIndexed { index, anime ->
                val deepLinkUri = Uri.parse("tanukitv://details?id=${anime.id}&title=${Uri.encode(anime.title)}")
                val posterUri = if (anime.posterUrl.isNotBlank()) Uri.parse(anime.posterUrl) else null

                val program = PreviewProgram.Builder()
                    .setChannelId(channelId)
                    .setTitle(anime.title)
                    .setDescription(anime.synopsis.ifBlank { "Stream on TanukiTV" })
                    .setType(TvContractCompat.PreviewProgramColumns.TYPE_TV_SERIES)
                    .setWeight(100 - index)
                    .setIntentUri(deepLinkUri)
                    .setInternalProviderId(anime.id)
                    .apply {
                        if (posterUri != null) {
                            setPosterArtUri(posterUri)
                            setPosterArtAspectRatio(TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_2_3)
                        }
                    }
                    .build()

                context.contentResolver.insert(
                    TvContractCompat.PreviewPrograms.CONTENT_URI,
                    program.toContentValues()
                )
            }

            Log.d(TAG, "Successfully published ${animeList.size.coerceAtMost(15)} items to TV channel #$channelId")
            return channelId
        } catch (e: Exception) {
            Log.e(TAG, "Error publishing trending channel to TV launcher", e)
            return -1L
        }
    }

    /**
     * Finds or creates the default "Trending Anime" preview channel.
     */
    fun getOrCreateTrendingChannel(context: Context): Long {
        val cursor = context.contentResolver.query(
            TvContractCompat.Channels.CONTENT_URI,
            arrayOf(TvContractCompat.Channels._ID, TvContractCompat.Channels.COLUMN_INTERNAL_PROVIDER_ID),
            "${TvContractCompat.Channels.COLUMN_INTERNAL_PROVIDER_ID} = ?",
            arrayOf(TRENDING_CHANNEL_ID_KEY),
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val idIndex = it.getColumnIndex(TvContractCompat.Channels._ID)
                if (idIndex >= 0) {
                    return it.getLong(idIndex)
                }
            }
        }

        // Create new channel
        val channel = Channel.Builder()
            .setType(TvContractCompat.Channels.TYPE_PREVIEW)
            .setDisplayName("TanukiTV - Trending")
            .setDescription("Featured and trending anime broadcasts")
            .setAppLinkIntentUri(Uri.parse("tanukitv://home"))
            .setInternalProviderId(TRENDING_CHANNEL_ID_KEY)
            .build()

        val channelUri = context.contentResolver.insert(
            TvContractCompat.Channels.CONTENT_URI,
            channel.toContentValues()
        ) ?: return -1L

        val channelId = ContentUris.parseId(channelUri)

        // Set channel logo if available
        try {
            val logo = BitmapFactory.decodeResource(context.resources, R.drawable.splash)
            if (logo != null) {
                ChannelLogoUtils.storeChannelLogo(context, channelId, logo)
            }
        } catch (_: Exception) {}

        // Request system to make channel browsable
        try {
            TvContractCompat.requestChannelBrowsable(context, channelId)
        } catch (_: Exception) {}

        return channelId
    }

    /**
     * Publishes or updates an anime episode in Android TV's system "Watch Next" row.
     */
    fun updateWatchNextProgram(
        context: Context,
        anime: Anime,
        episode: Episode,
        positionMs: Long,
        durationMs: Long
    ) {
        if (positionMs <= 5_000L) return // Don't clutter Watch Next for under 5 seconds
        val effectiveDuration = if (durationMs > 0L) durationMs else 1_440_000L

        try {
            val deepLinkUri = Uri.parse(
                "tanukitv://player?animeId=${anime.id}&episodeId=${episode.id}&episodeNum=${episode.number}&title=${Uri.encode(anime.title)}"
            )
            val thumbnailUri = if (episode.thumbnail.isNotBlank()) {
                Uri.parse(episode.thumbnail)
            } else if (anime.posterUrl.isNotBlank()) {
                Uri.parse(anime.posterUrl)
            } else null

            // Find existing watch next item for this anime
            val cursor = context.contentResolver.query(
                TvContractCompat.WatchNextPrograms.CONTENT_URI,
                arrayOf(TvContractCompat.WatchNextPrograms._ID, TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID),
                "${TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID} = ?",
                arrayOf("wn_${anime.id}"),
                null
            )

            var existingProgramId = -1L
            cursor?.use {
                if (it.moveToFirst()) {
                    val idCol = it.getColumnIndex(TvContractCompat.WatchNextPrograms._ID)
                    if (idCol >= 0) existingProgramId = it.getLong(idCol)
                }
            }

            val program = WatchNextProgram.Builder()
                .setType(TvContractCompat.WatchNextPrograms.TYPE_TV_EPISODE)
                .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                .setTitle(anime.title)
                .setEpisodeTitle("Episode ${episode.number}: ${episode.title}")
                .setEpisodeNumber(episode.number)
                .setLastPlaybackPositionMillis(positionMs.toInt())
                .setDurationMillis(effectiveDuration.toInt())
                .setLastEngagementTimeUtcMillis(System.currentTimeMillis())
                .setIntentUri(deepLinkUri)
                .setInternalProviderId("wn_${anime.id}")
                .apply {
                    if (thumbnailUri != null) {
                        setPosterArtUri(thumbnailUri)
                    }
                }
                .build()

            if (existingProgramId > 0L) {
                val programUri = TvContractCompat.buildWatchNextProgramUri(existingProgramId)
                context.contentResolver.update(
                    programUri,
                    program.toContentValues(),
                    null,
                    null
                )
            } else {
                context.contentResolver.insert(
                    TvContractCompat.WatchNextPrograms.CONTENT_URI,
                    program.toContentValues()
                )
            }
            Log.d(TAG, "Updated Watch Next for '${anime.title}' ep ${episode.number} at ${positionMs}ms")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update Watch Next program", e)
        }
    }

    /**
     * Removes an anime from the "Watch Next" row when fully completed.
     */
    fun removeWatchNextProgram(context: Context, animeId: String) {
        try {
            context.contentResolver.delete(
                TvContractCompat.WatchNextPrograms.CONTENT_URI,
                "${TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID} = ?",
                arrayOf("wn_$animeId")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove Watch Next program for $animeId", e)
        }
    }
}
