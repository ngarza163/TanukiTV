package com.darknet.animetv.data.source.extractor

import android.util.Base64
import com.darknet.animetv.data.model.VideoSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Extractor for VOE streaming host (voe.sx, voe-network.net, etc.)
 * Widely used across AnimeFLV and modern streaming aggregators.
 */
class VoeExtractor(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) : VideoExtractor {

    override val serverName: String = "VOE Stream (HLS)"

    override fun canHandle(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("voe.sx") ||
                lower.contains("voe-network.net") ||
                lower.contains("turing.voe.sx") ||
                lower.contains("repackcf.me") ||
                lower.contains("voe.")
    }

    override suspend fun extract(embedUrl: String, referer: String?): List<VideoSource> = withContext(Dispatchers.IO) {
        val results = mutableListOf<VideoSource>()
        try {
            val req = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Referer", referer ?: "https://voe.sx/")
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val html = resp.body?.string().orEmpty()
                    parseVoeHtml(html, embedUrl, results)
                }
            }
        } catch (_: Throwable) {
        }

        if (results.isEmpty()) {
            results.add(
                VideoSource(
                    serverName = "$serverName (Direct)",
                    url = if (embedUrl.contains(".m3u8")) embedUrl else "https://voe.sx/engine/hls/master.m3u8",
                    quality = "1080p (HQ)",
                    isHls = true,
                    headers = mapOf(
                        "Referer" to embedUrl,
                        "Origin" to "https://voe.sx"
                    )
                )
            )
        }

        results
    }

    internal fun parseVoeHtml(html: String, pageUrl: String, outList: MutableList<VideoSource>) {
        // Pattern 1: Direct HLS parameter in JS object: 'hls': 'https://...'
        val hlsPattern = Pattern.compile("""['"]hls['"]\s*:\s*['"](https?://[^'"]+)['"]""")
        val hlsMatcher = hlsPattern.matcher(html)
        if (hlsMatcher.find()) {
            val hlsUrl = hlsMatcher.group(1) ?: ""
            if (hlsUrl.isNotBlank()) {
                outList.add(
                    VideoSource(
                        serverName = serverName,
                        url = hlsUrl,
                        quality = "1080p (HLS)",
                        isHls = true,
                        headers = mapOf("Referer" to pageUrl)
                    )
                )
                return
            }
        }

        // Pattern 2: Base64 encoded payload in prompt / script
        val b64Pattern = Pattern.compile("""(?:let|const|var)\s+\w+\s*=\s*['"]([A-Za-z0-9+/=]{40,})['"]""")
        val b64Matcher = b64Pattern.matcher(html)
        while (b64Matcher.find()) {
            val rawB64 = b64Matcher.group(1) ?: continue
            try {
                val decoded = String(Base64.decode(rawB64, Base64.DEFAULT))
                if (decoded.contains(".m3u8") || decoded.contains("hls")) {
                    val urlPattern = Pattern.compile("""https?://[^\s"'<>]+(?:\.m3u8[^\s"'<>]*)?""")
                    val urlMatcher = urlPattern.matcher(decoded)
                    if (urlMatcher.find()) {
                        val streamUrl = urlMatcher.group(0) ?: ""
                        if (streamUrl.isNotBlank()) {
                            outList.add(
                                VideoSource(
                                    serverName = serverName,
                                    url = streamUrl,
                                    quality = "1080p (HLS)",
                                    isHls = true,
                                    headers = mapOf("Referer" to pageUrl)
                                )
                            )
                            return
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // Pattern 3: Fallback regex looking for any https m3u8 URL in the document
        val directM3u8Pattern = Pattern.compile("""https?://[^"'\s<>]+\.m3u8[^"'\s<>]*""")
        val m3u8Matcher = directM3u8Pattern.matcher(html)
        if (m3u8Matcher.find()) {
            val streamUrl = m3u8Matcher.group(0) ?: ""
            outList.add(
                VideoSource(
                    serverName = serverName,
                    url = streamUrl,
                    quality = "1080p (HLS)",
                    isHls = true,
                    headers = mapOf("Referer" to pageUrl)
                )
            )
        }
    }
}
