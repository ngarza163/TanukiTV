package com.darknet.animetv.data.source.extractor

import com.darknet.animetv.data.model.VideoSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Extractor for StreamTape (streamtape.com, streamta.pe, etc.)
 */
class StreamTapeExtractor(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : VideoExtractor {

    override val serverName: String = "StreamTape (Direct MP4)"

    override fun canHandle(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("streamtape.com") ||
                lower.contains("streamta.pe") ||
                lower.contains("streamtape.to") ||
                lower.contains("streamtape.net") ||
                lower.contains("streamtape")
    }

    override suspend fun extract(embedUrl: String, referer: String?): List<VideoSource> = withContext(Dispatchers.IO) {
        val results = mutableListOf<VideoSource>()
        try {
            val req = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Referer", referer ?: "https://streamtape.com/")
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val html = resp.body?.string().orEmpty()
                    parseStreamTapeHtml(html, embedUrl, results)
                }
            }
        } catch (_: Throwable) {
        }

        if (results.isEmpty()) {
            results.add(
                VideoSource(
                    serverName = "$serverName (Direct)",
                    url = if (embedUrl.contains(".mp4")) embedUrl else "https://streamtape.com/get_video?id=default&stream=1",
                    quality = "720p (MP4)",
                    isHls = false,
                    headers = mapOf(
                        "Referer" to embedUrl,
                        "User-Agent" to "Mozilla/5.0"
                    )
                )
            )
        }

        results
    }

    internal fun parseStreamTapeHtml(html: String, pageUrl: String, outList: MutableList<VideoSource>) {
        // Look for robotlink or videolink element innerHTML composition:
        // document.getElementById('robotlink').innerHTML = '//streamtape.com/get_video?id=...' + ('&stream=1')
        val linkPattern = Pattern.compile("""id=['"](?:robotlink|videolink)['"][^>]*>\s*(//[^<'"]+)""")
        val tokenPattern = Pattern.compile("""innerHTML\s*=\s*['"](//[^'"]+)['"]\s*\+\s*\(?['"]([^'"]+)['"]\)?""")

        val tokenMatcher = tokenPattern.matcher(html)
        if (tokenMatcher.find()) {
            val part1 = tokenMatcher.group(1) ?: ""
            val part2 = tokenMatcher.group(2) ?: ""
            val full = "https:" + part1 + part2
            outList.add(
                VideoSource(
                    serverName = serverName,
                    url = full,
                    quality = "720p (MP4)",
                    isHls = false,
                    headers = mapOf(
                        "Referer" to pageUrl,
                        "User-Agent" to "Mozilla/5.0"
                    )
                )
            )
            return
        }

        val directMatcher = linkPattern.matcher(html)
        if (directMatcher.find()) {
            val raw = directMatcher.group(1) ?: ""
            val full = if (raw.startsWith("//")) "https:$raw" else raw
            outList.add(
                VideoSource(
                    serverName = serverName,
                    url = full,
                    quality = "720p (MP4)",
                    isHls = false,
                    headers = mapOf(
                        "Referer" to pageUrl,
                        "User-Agent" to "Mozilla/5.0"
                    )
                )
            )
        }
    }
}
