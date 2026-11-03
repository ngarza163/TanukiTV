package com.darknet.animetv.data.source.extractor

import com.darknet.animetv.data.model.VideoSource

/**
 * Interface contract for resolving playable video streams and subtitle tracks
 * from embedded third-party video hosts (MegaCloud, StreamWish, Doodstream, etc.).
 */
interface VideoExtractor {
    val serverName: String

    /**
     * Determines whether this extractor can resolve streams for the given embed URL.
     */
    fun canHandle(url: String): Boolean

    /**
     * Resolves playable video stream sources from the provided embed URL.
     * @param embedUrl The embed or player URL from the streaming host.
     * @param referer Optional HTTP referer header required by the host.
     */
    suspend fun extract(embedUrl: String, referer: String? = null): List<VideoSource>
}
