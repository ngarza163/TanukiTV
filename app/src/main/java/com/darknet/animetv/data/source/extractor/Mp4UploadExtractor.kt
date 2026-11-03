package com.darknet.animetv.data.source.extractor

import com.darknet.animetv.data.model.VideoSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class Mp4UploadExtractor(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : VideoExtractor {

    override val serverName: String = "Mp4Upload"

    override fun canHandle(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("mp4upload.com") || lower.contains("mp4upload")
    }

    override suspend fun extract(embedUrl: String, referer: String?): List<VideoSource> = withContext(Dispatchers.IO) {
        val results = mutableListOf<VideoSource>()
        try {
            val req = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Referer", referer ?: "https://www.mp4upload.com/")
                .build()

            val html = client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() ?: "" else ""
            }

            if (html.isNotEmpty()) {
                val directUrl = extractDirectMp4Url(html)
                if (directUrl != null) {
                    results.add(
                        VideoSource(
                            serverName = "$serverName (Direct MP4)",
                            url = directUrl,
                            quality = "1080p",
                            isHls = false,
                            headers = mapOf(
                                "Referer" to "https://www.mp4upload.com/",
                                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                            )
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (results.isEmpty()) {
            results.add(
                VideoSource(
                    serverName = "$serverName (Direct MP4)",
                    url = if (embedUrl.contains(".mp4")) embedUrl else "$embedUrl.mp4",
                    quality = "1080p",
                    isHls = false,
                    headers = mapOf(
                        "Referer" to "https://www.mp4upload.com/",
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                    )
                )
            )
        }

        results
    }

    internal fun extractDirectMp4Url(html: String): String? {
        val patterns = listOf(
            Pattern.compile("""player\.src\(\s*\{\s*type:\s*["'][^"']+["'],\s*src:\s*["'](https?://[^"':]+(?::\d+)?/[^"']+\.mp4[^"']*)["']"""),
            Pattern.compile("""src:\s*["'](https?://[^"':]+(?::\d+)?/[^"']+\.mp4[^"']*)["']"""),
            Pattern.compile("""["'](https?://[^"':]+(?::\d+)?/[^"']+\.mp4[^"']*)["']""")
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(html)
            if (matcher.find()) {
                val candidate = matcher.group(1)
                if (candidate != null && !candidate.contains("/ads/") && !candidate.contains("banner")) {
                    return candidate
                }
            }
        }
        return null
    }
}
