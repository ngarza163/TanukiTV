package com.darknet.animetv.data.source.extractor

import com.darknet.animetv.data.model.SubtitleTrack
import com.darknet.animetv.data.model.VideoSource
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Resolves real HLS streams from MegaPlay embeds (megaplay.buzz / megaplay.su), used by Aniwatch.
 *
 * Flow:
 *  1. GET embed page -> parse `#megaplay-player[data-id]`
 *  2. GET `/stream/getSources?id={dataId}` -> JSON with `tracks` + AES-CBC encrypted `enc`
 *  3. Decrypt `enc` (AES-256-CBC, zero-padded key/iv, base64url) -> `{"file": ".../master.m3u8"}`
 *
 * The resulting manifest requires `Referer: https://{embedHost}/`.
 */
class MegaPlayExtractor(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
) : VideoExtractor {

    override val serverName: String = "MegaPlay"

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    override fun canHandle(url: String): Boolean = url.lowercase().contains("megaplay.")

    override suspend fun extract(embedUrl: String, referer: String?): List<VideoSource> = withContext(Dispatchers.IO) {
        val out = mutableListOf<VideoSource>()
        try {
            val uri = URI(embedUrl)
            val host = "${uri.scheme ?: "https"}://${uri.host}"
            val sParam = Regex("""[?&]s=([a-zA-Z0-9_-]+)""").find(embedUrl)?.groupValues?.get(1)

            // 1. Embed page -> data-id
            val pageReq = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", userAgent)
                .header("Referer", referer ?: "https://aniwatchtv.ro/")
                .build()
            val html = client.newCall(pageReq).execute().use { if (it.isSuccessful) it.body?.string().orEmpty() else "" }
            val dataId = Pattern.compile("""id="megaplay-player"[^>]*?data-id="(\d+)"""", Pattern.DOTALL)
                .matcher(html).let { if (it.find()) it.group(1) else null }
                ?: Pattern.compile("""data-id="(\d+)"""").matcher(html).let { if (it.find()) it.group(1) else null }
                ?: return@withContext emptyList()

            // 2. getSources
            val apiUrl = buildString {
                append("$host/stream/getSources?id=$dataId")
                if (!sParam.isNullOrBlank()) append("&s=$sParam")
            }
            val apiReq = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", userAgent)
                .header("Referer", embedUrl)
                .header("X-Requested-With", "XMLHttpRequest")
                .build()
            val body = client.newCall(apiReq).execute().use { if (it.isSuccessful) it.body?.string().orEmpty() else "" }
            if (body.isBlank()) return@withContext emptyList()
            val root = JsonParser.parseString(body).asJsonObject

            val subtitles = parseTracks(root)
            val files = mutableListOf<String>()

            // Plain `sources` (older format)
            root.get("sources")?.let { el ->
                when {
                    el.isJsonArray -> el.asJsonArray.forEach { s ->
                        s.asJsonObject.get("file")?.asString?.let(files::add)
                    }
                    el.isJsonObject -> el.asJsonObject.get("file")?.asString?.let(files::add)
                    el.isJsonPrimitive -> decryptToFile(el.asString)?.let(files::add)
                    else -> Unit
                }
            }
            // Encrypted `enc` (current format)
            root.get("enc")?.takeIf { it.isJsonPrimitive }?.asString?.let { enc ->
                decryptToFile(enc)?.let(files::add)
            }

            val headers = mapOf(
                "Referer" to "$host/",
                "Origin" to host,
                "User-Agent" to userAgent
            )
            files.filter { it.startsWith("http") }.distinct().forEach { file ->
                val isHls = file.contains(".m3u8", ignoreCase = true)
                out.add(
                    VideoSource(
                        serverName = serverName,
                        url = file,
                        quality = if (isHls) "Auto (HLS)" else "1080p",
                        isHls = isHls,
                        headers = headers,
                        subtitles = subtitles
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        out
    }

    private fun parseTracks(root: JsonObject): List<SubtitleTrack> {
        val subs = mutableListOf<SubtitleTrack>()
        root.getAsJsonArray("tracks")?.forEach { tEl ->
            val t = tEl.asJsonObject
            val kind = t.get("kind")?.asString.orEmpty()
            val file = t.get("file")?.asString.orEmpty()
            val label = t.get("label")?.asString ?: "Unknown"
            if (file.startsWith("http") && kind != "thumbnails") {
                val lang = when {
                    label.startsWith("English", true) -> "en"
                    label.startsWith("Spanish", true) -> "es"
                    label.startsWith("Portuguese", true) -> "pt"
                    label.startsWith("French", true) -> "fr"
                    label.startsWith("German", true) -> "de"
                    label.startsWith("Arabic", true) -> "ar"
                    else -> label.take(2).lowercase()
                }
                subs.add(SubtitleTrack(language = lang, label = label, url = file))
            }
        }
        // Default (English) first
        return subs.sortedByDescending { it.language == "en" }
    }

    private fun decryptToFile(enc: String): String? {
        val plain = decrypt(enc) ?: return null
        return try {
            val parsed = JsonParser.parseString(plain)
            when {
                parsed.isJsonObject -> parsed.asJsonObject.get("file")?.asString
                parsed.isJsonArray -> parsed.asJsonArray.firstOrNull()?.asJsonObject?.get("file")?.asString
                else -> null
            }
        } catch (_: Exception) {
            Regex("""https?://[^\s"']+\.m3u8[^\s"']*""").find(plain)?.value
        }
    }

    internal fun decrypt(enc: String): String? = try {
        val key = padBytes(AES_KEY, 32)
        val iv = padBytes(AES_IV, 16)
        var b64 = enc.replace('-', '+').replace('_', '/')
        while (b64.length % 4 != 0) b64 += "="
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        String(cipher.doFinal(Base64.getDecoder().decode(b64)), StandardCharsets.UTF_8)
    } catch (e: Exception) {
        null
    }

    private fun padBytes(s: String, len: Int): ByteArray {
        val src = s.toByteArray(StandardCharsets.UTF_8)
        return ByteArray(len).also { System.arraycopy(src, 0, it, 0, minOf(len, src.size)) }
    }

    companion object {
        private const val AES_KEY = "i?LMTAx0Q6,:}50U"
        private const val AES_IV = "W0;27ToaUpl_P%'c"
    }
}
