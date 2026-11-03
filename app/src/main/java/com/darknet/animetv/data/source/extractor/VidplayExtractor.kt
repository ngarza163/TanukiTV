package com.darknet.animetv.data.source.extractor

import com.darknet.animetv.data.model.SubtitleTrack
import com.darknet.animetv.data.model.VideoSource
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class VidplayExtractor(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : VideoExtractor {

    override val serverName: String = "Vidplay / MegaF"

    override fun canHandle(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("vidplay.online") ||
                lower.contains("vid2a41.site") ||
                lower.contains("megaf.cc") ||
                lower.contains("vidplay.site") ||
                lower.contains("vidplay")
    }

    override suspend fun extract(embedUrl: String, referer: String?): List<VideoSource> = withContext(Dispatchers.IO) {
        val results = mutableListOf<VideoSource>()
        try {
            val uri = URI(embedUrl)
            val scheme = uri.scheme ?: "https"
            val host = uri.host ?: "vidplay.online"
            val baseDomain = "$scheme://$host"

            val videoId = extractVideoId(embedUrl)
            if (videoId != null) {
                // Try fetching mediainfo endpoint
                val mediaInfoUrl = "$baseDomain/mediainfo/$videoId"
                val req = Request.Builder()
                    .url(mediaInfoUrl)
                    .header("Referer", embedUrl)
                    .header("Origin", baseDomain)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (!body.isNullOrBlank()) {
                            parseMediaInfoJson(body, baseDomain, embedUrl, results)
                        }
                    }
                }
            }

            // If empty, try fetching the HTML directly
            if (results.isEmpty()) {
                val pageReq = Request.Builder()
                    .url(embedUrl)
                    .header("Referer", referer ?: baseDomain)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()

                client.newCall(pageReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val html = resp.body?.string() ?: ""
                        parseHtmlForSources(html, baseDomain, embedUrl, results)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (results.isEmpty()) {
            val videoId = extractVideoId(embedUrl) ?: "video"
            results.add(
                VideoSource(
                    serverName = "$serverName (HLS)",
                    url = if (embedUrl.contains(".m3u8")) embedUrl else "https://vidplay.online/hls/$videoId/master.m3u8",
                    quality = "Auto (HLS)",
                    isHls = true,
                    headers = mapOf(
                        "Referer" to embedUrl,
                        "Origin" to "https://vidplay.online",
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                    )
                )
            )
        }

        results
    }

    internal fun extractVideoId(url: String): String? {
        val pattern = Pattern.compile("""(?:/e/|/v/|/video/)([^/?&]+)""")
        val matcher = pattern.matcher(url)
        if (matcher.find()) {
            return matcher.group(1)
        }
        val segments = url.trimEnd('/').split('/')
        return segments.lastOrNull()?.substringBefore('?')
    }

    internal fun parseMediaInfoJson(json: String, baseDomain: String, embedUrl: String, outList: MutableList<VideoSource>) {
        try {
            val root = JsonParser.parseString(json).asJsonObject
            val resObj = if (root.has("result") && root.get("result").isJsonObject) {
                root.getAsJsonObject("result")
            } else {
                root
            }

            val subtitles = mutableListOf<SubtitleTrack>()
            val tracksArray = resObj.getAsJsonArray("tracks")
            tracksArray?.forEach { trackEl ->
                val tObj = trackEl.asJsonObject
                val file = tObj.get("file")?.asString ?: ""
                val label = tObj.get("label")?.asString ?: "Unknown"
                if (file.isNotEmpty()) {
                    subtitles.add(SubtitleTrack(language = label, label = label, url = file))
                }
            }

            val sourcesArray = resObj.getAsJsonArray("sources")
            sourcesArray?.forEach { sEl ->
                val sObj = sEl.asJsonObject
                val file = sObj.get("file")?.asString ?: ""
                if (file.isNotEmpty()) {
                    outList.add(
                        VideoSource(
                            serverName = "$serverName (Adaptive)",
                            url = file,
                            quality = "Auto (HLS)",
                            isHls = true,
                            headers = mapOf(
                                "Referer" to embedUrl,
                                "Origin" to baseDomain,
                                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                            ),
                            subtitles = subtitles
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    internal fun parseHtmlForSources(html: String, baseDomain: String, embedUrl: String, outList: MutableList<VideoSource>) {
        val m3u8Pattern = Pattern.compile("""["'](https?://[^"']+\.m3u8[^"']*)["']""")
        val matcher = m3u8Pattern.matcher(html)
        while (matcher.find()) {
            val streamUrl = matcher.group(1)
            if (streamUrl != null && !outList.any { it.url == streamUrl }) {
                outList.add(
                    VideoSource(
                        serverName = "$serverName (Master HLS)",
                        url = streamUrl,
                        quality = "1080p",
                        isHls = true,
                        headers = mapOf(
                            "Referer" to embedUrl,
                            "Origin" to baseDomain,
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                        )
                    )
                )
            }
        }
    }
}
