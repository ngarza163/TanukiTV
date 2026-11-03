package com.darknet.animetv.data.source.extractor

import com.darknet.animetv.data.model.VideoSource

class VideoExtractorRegistry(
    private val extractors: List<VideoExtractor> = listOf(
        MegaPlayExtractor(),
        MegaCloudExtractor(),
        VoeExtractor(),
        FilemoonExtractor(),
        StreamWishExtractor(),
        StreamTapeExtractor(),
        DoodstreamExtractor(),
        VidplayExtractor(),
        Mp4UploadExtractor(),
        DirectStreamExtractor()
    )
) {

    /**
     * Resolves playable streams for the provided embed URL by selecting the first
     * matching extractor in the registry, falling back to DirectStreamExtractor.
     */
    suspend fun extractFromUrl(embedUrl: String, referer: String? = null): List<VideoSource> {
        val target = embedUrl.trim()
        if (target.isEmpty()) return emptyList()

        for (extractor in extractors) {
            if (extractor.canHandle(target)) {
                try {
                    val result = extractor.extract(target, referer)
                    if (result.isNotEmpty()) {
                        return result
                    }
                } catch (t: Throwable) {
                    // Suppress network/crypto initialization errors during extractor execution
                }
            }
        }

        // Ultimate fallback
        return DirectStreamExtractor().extract(target, referer)
    }

    companion object {
        val defaultInstance: VideoExtractorRegistry by lazy { VideoExtractorRegistry() }
    }
}
