package com.darknet.animetv.data.source.extractor

import com.darknet.animetv.data.model.VideoSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.Random
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class DoodstreamExtractor(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : VideoExtractor {

    override val serverName: String = "DoodStream"

    override fun canHandle(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("dood.to") ||
                lower.contains("doodstream.com") ||
                lower.contains("doods.pro") ||
                lower.contains("dood.so") ||
                lower.contains("dood.pm") ||
                lower.contains("dood.ws") ||
                lower.contains("dood.cx") ||
                lower.contains("doodstream") ||
                lower.contains("dood.")
    }

    override suspend fun extract(embedUrl: String, referer: String?): List<VideoSource> = withContext(Dispatchers.IO) {
        val results = mutableListOf<VideoSource>()
        try {
            val uri = URI(embedUrl)
            val scheme = uri.scheme ?: "https"
            val host = uri.host ?: "dood.to"
            val baseDomain = "$scheme://$host"

            val req = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Referer", referer ?: baseDomain)
                .build()

            val html = client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() ?: "" else ""
            }

            if (html.isNotEmpty()) {
                val md5Path = extractPassMd5Path(html)
                if (md5Path != null) {
                    val passMd5Url = if (md5Path.startsWith("http")) md5Path else "$baseDomain$md5Path"
                    val tokenReq = Request.Builder()
                        .url(passMd5Url)
                        .header("Referer", embedUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .build()

                    val prefixUrl = client.newCall(tokenReq).execute().use { resp ->
                        if (resp.isSuccessful) resp.body?.string()?.trim() ?: "" else ""
                    }

                    if (prefixUrl.isNotEmpty() && prefixUrl.startsWith("http")) {
                        val randomToken = generateRandomString(10)
                        val expiry = System.currentTimeMillis()
                        val directStreamUrl = "$prefixUrl$randomToken?token=$randomToken&expiry=$expiry"

                        results.add(
                            VideoSource(
                                serverName = "$serverName (Direct)",
                                url = directStreamUrl,
                                quality = "1080p",
                                isHls = false,
                                headers = mapOf(
                                    "Referer" to embedUrl,
                                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                                )
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (results.isEmpty()) {
            results.add(
                VideoSource(
                    serverName = "$serverName (Direct)",
                    url = if (embedUrl.contains(".mp4") || embedUrl.contains(".m3u8")) embedUrl else "$embedUrl/stream.mp4",
                    quality = "1080p",
                    isHls = embedUrl.contains(".m3u8"),
                    headers = mapOf(
                        "Referer" to embedUrl,
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                    )
                )
            )
        }

        results
    }

    internal fun extractPassMd5Path(html: String): String? {
        val pattern = Pattern.compile("""['"](/pass_md5/[^'"]+)['"]""")
        val matcher = pattern.matcher(html)
        if (matcher.find()) {
            return matcher.group(1)
        }
        return null
    }

    private fun generateRandomString(length: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val rnd = Random()
        val sb = StringBuilder(length)
        for (i in 0 until length) {
            sb.append(chars[rnd.nextInt(chars.length)])
        }
        return sb.toString()
    }
}
