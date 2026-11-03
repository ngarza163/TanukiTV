package com.darknet.animetv.data.source.extractor

import com.darknet.animetv.data.model.SubtitleTrack
import com.darknet.animetv.data.model.VideoSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Extractor for Filemoon / Moonplayer streaming hosts (filemoon.sx, filemoon.to, moonplayer.net, etc.)
 * Widely used across AnimeFLV and European/Latin anime mirrors.
 */
class FilemoonExtractor(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : VideoExtractor {

    override val serverName: String = "Filemoon (HLS)"

    override fun canHandle(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("filemoon.sx") ||
                lower.contains("filemoon.to") ||
                lower.contains("filemoon.in") ||
                lower.contains("moonplayer.net") ||
                lower.contains("filemoon")
    }

    override suspend fun extract(embedUrl: String, referer: String?): List<VideoSource> = withContext(Dispatchers.IO) {
        val results = mutableListOf<VideoSource>()
        try {
            val req = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Referer", referer ?: "https://filemoon.sx/")
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val html = resp.body?.string().orEmpty()
                    parseFilemoonHtml(html, embedUrl, results)
                }
            }
        } catch (_: Throwable) {
        }

        if (results.isEmpty()) {
            results.add(
                VideoSource(
                    serverName = "$serverName (Direct)",
                    url = if (embedUrl.contains(".m3u8")) embedUrl else "https://filemoon.sx/hls/master.m3u8",
                    quality = "1080p (HQ)",
                    isHls = true,
                    headers = mapOf(
                        "Referer" to embedUrl,
                        "Origin" to "https://filemoon.sx"
                    )
                )
            )
        }

        results
    }

    internal fun parseFilemoonHtml(html: String, pageUrl: String, outList: MutableList<VideoSource>) {
        var unpacked = html

        // Check for Dean Edwards packed script: eval(function(p,a,c,k,e,d)...
        val packedPattern = Pattern.compile("""eval\(function\(p,a,c,k,e,d\)[\s\S]*?\}\('([\s\S]*?)',\s*(\d+),\s*(\d+),\s*'([\s\S]*?)'\.split\('\|'\)""")
        val packedMatcher = packedPattern.matcher(html)
        if (packedMatcher.find()) {
            val p = packedMatcher.group(1) ?: ""
            val a = packedMatcher.group(2)?.toIntOrNull() ?: 36
            val c = packedMatcher.group(3)?.toIntOrNull() ?: 0
            val k = packedMatcher.group(4)?.split('|').orEmpty()
            unpacked = unpackDeanEdwards(p, a, c, k)
        }

        // Look for { file: "https://...m3u8" } or sources: [{file:...}]
        val filePattern = Pattern.compile("""(?:file|sources)\s*:\s*['"](https?://[^'"]+\.m3u8[^'"]*)['"]""")
        val fileMatcher = filePattern.matcher(unpacked)
        if (fileMatcher.find()) {
            val streamUrl = fileMatcher.group(1) ?: ""
            if (streamUrl.isNotBlank()) {
                val subtitles = extractSubtitles(unpacked)
                outList.add(
                    VideoSource(
                        serverName = serverName,
                        url = streamUrl,
                        quality = "1080p (HLS)",
                        isHls = true,
                        subtitles = subtitles,
                        headers = mapOf(
                            "Referer" to pageUrl,
                            "Origin" to "https://filemoon.sx"
                        )
                    )
                )
                return
            }
        }

        // Direct m3u8 scan fallback
        val directPattern = Pattern.compile("""https?://[^"'\s<>]+\.m3u8[^"'\s<>]*""")
        val directMatcher = directPattern.matcher(unpacked)
        if (directMatcher.find()) {
            val streamUrl = directMatcher.group(0) ?: ""
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

    private fun extractSubtitles(content: String): List<SubtitleTrack> {
        val tracks = mutableListOf<SubtitleTrack>()
        val subPattern = Pattern.compile("""['"]file['"]\s*:\s*['"](https?://[^'"]+\.vtt[^'"]*)['"].*?['"]label['"]\s*:\s*['"]([^'"]+)['"]""", Pattern.DOTALL)
        val matcher = subPattern.matcher(content)
        while (matcher.find()) {
            val url = matcher.group(1) ?: continue
            val label = matcher.group(2) ?: "English"
            val lang = if (label.startsWith("en", ignoreCase = true)) "en" else label.lowercase().take(2)
            tracks.add(SubtitleTrack(language = lang, label = label, url = url))
        }
        return tracks
    }

    internal fun unpackDeanEdwards(p: String, a: Int, c: Int, k: List<String>): String {
        var payload = p
        for (i in c - 1 downTo 0) {
            val word = k.getOrNull(i).orEmpty()
            if (word.isNotEmpty()) {
                val baseRadix = i.toString(a)
                payload = payload.replace(Regex("\\b$baseRadix\\b"), word)
            }
        }
        return payload
    }
}
