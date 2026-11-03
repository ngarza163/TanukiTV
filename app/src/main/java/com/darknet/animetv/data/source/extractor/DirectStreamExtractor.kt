package com.darknet.animetv.data.source.extractor

import com.darknet.animetv.data.model.VideoSource
import java.net.URI

class DirectStreamExtractor : VideoExtractor {

    override val serverName: String = "Direct Stream"

    override fun canHandle(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://")
    }

    override suspend fun extract(embedUrl: String, referer: String?): List<VideoSource> {
        val isM3u8 = embedUrl.contains(".m3u8", ignoreCase = true)
        val host = try {
            URI(embedUrl).host ?: "direct"
        } catch (_: Exception) {
            "direct"
        }

        val ref = referer ?: "https://$host/"

        return listOf(
            VideoSource(
                serverName = if (isM3u8) "Direct FastCDN (HLS)" else "Direct Video Feed ($host)",
                url = embedUrl,
                quality = if (isM3u8) "Auto (Multi-Bitrate)" else "1080p",
                isHls = isM3u8,
                headers = mapOf(
                    "Referer" to ref,
                    "Origin" to "https://$host",
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )
            )
        )
    }
}
