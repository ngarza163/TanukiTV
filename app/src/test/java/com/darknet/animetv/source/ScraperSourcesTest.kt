package com.darknet.animetv.source

import com.darknet.animetv.data.source.AnikotoSource
import com.darknet.animetv.data.source.AnimeFlvSource
import com.darknet.animetv.data.source.AniwatchSource
import com.darknet.animetv.data.source.KaaSource
import com.darknet.animetv.data.source.MiruroSource
import com.darknet.animetv.data.source.ReAnimeSource
import com.darknet.animetv.data.source.WcoSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
class ScraperSourcesTest {

    @Test
    fun testAniwatchSourcePropertiesAndNumericId() {
        val source = AniwatchSource()
        assertEquals("aniwatch", source.sourceId)
        assertEquals("Aniwatch (HiAnime)", source.sourceName)
        assertEquals("https://aniwatchtv.ro", source.baseUrl)
        assertTrue(source.isEnabled)

        val id = source.extractNumericId("solo-leveling-season-2-19413")
        assertEquals("19413", id)
    }

    @Test
    fun testAnikotoSourceProperties() {
        val source = AnikotoSource()
        assertEquals("anikoto", source.sourceId)
        assertEquals("Anikoto", source.sourceName)
        assertEquals("https://anikototv.to", source.baseUrl)
        assertTrue(source.isEnabled)
    }

    @Test
    fun testMiruroSourceProperties() {
        val source = MiruroSource()
        assertEquals("miruro", source.sourceId)
        assertEquals("Miruro", source.sourceName)
        assertEquals("https://www.miruro.cx", source.baseUrl)
        assertTrue(source.isEnabled)
    }

    @Test
    fun testReAnimeSourceProperties() {
        val source = ReAnimeSource()
        assertEquals("reanime", source.sourceId)
        assertEquals("ReAnime", source.sourceName)
        assertEquals("https://reanime.to", source.baseUrl)
        assertTrue(source.isEnabled)
    }

    @Test
    fun testKaaSourceAesCbcDecryption() {
        val source = KaaSource()
        assertEquals("kaa", source.sourceId)
        assertEquals("KickAssAnime (KAA)", source.sourceName)
        assertEquals("https://kaa.lt", source.baseUrl)
        assertTrue(source.isEnabled)

        // Test genuine AES-CBC-PKCS7 decryption
        val plainText = """{"sources":["https://stream.kaa.lt/master.m3u8"]}"""
        val key = "1234567890123456" // 16 bytes key
        val ivHex = "00112233445566778899aabbccddeeff" // 16 bytes IV in hex

        val ivBytes = source.hexStringToByteArray(ivHex)
        val keyBytes = key.toByteArray(StandardCharsets.UTF_8)

        // Encrypt plainText to generate test cipherText
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(ivBytes))
        val cipherBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        val base64Cipher = android.util.Base64.encodeToString(cipherBytes, android.util.Base64.NO_WRAP)

        // Perform decryption with KaaSource
        val decrypted = source.decryptAesCbc(base64Cipher, key, ivHex)
        assertEquals(plainText, decrypted)
    }

    @Test
    fun testWcoSourceBase64StreamExtraction() {
        val source = WcoSource()
        assertEquals("wco", source.sourceId)
        assertEquals("WCO (Western Cartoons & Anime)", source.sourceName)
        assertEquals("https://www.wco.tv", source.baseUrl)
        assertTrue(source.isEnabled)

        val streamUrl = "https://cdn.wcostream.tv/getvid?evid=test1234.mp4"
        val base64Encoded = android.util.Base64.encodeToString(streamUrl.toByteArray(StandardCharsets.UTF_8), android.util.Base64.NO_WRAP)
        val htmlSnippet = """
            <script>
                var vid = atob("$base64Encoded");
                play(vid);
            </script>
        """.trimIndent()

        val extracted = source.extractBase64Stream(htmlSnippet)
        assertEquals(streamUrl, extracted)
    }

    @Test
    fun testAnimeFlvSourceVideosJsonExtraction() {
        val source = AnimeFlvSource()
        assertEquals("animeflv", source.sourceId)
        assertEquals("AnimeFLV", source.sourceName)
        assertEquals("https://www3.animeflv.net", source.baseUrl)
        assertTrue(source.isEnabled)

        val html = """
            <html>
            <script>
                var videos = {
                    "SUB": [
                        {"server": "sw", "title": "Streamwish", "code": "https://streamwish.to/e/abcdef123"},
                        {"server": "yourupload", "title": "YourUpload", "code": "https://www.yourupload.com/embed/xyz789"}
                    ]
                };
            </script>
            </html>
        """.trimIndent()

        val videoCodes = source.extractVideosJson(html)
        assertEquals(2, videoCodes.size)
        assertTrue(videoCodes.contains("https://streamwish.to/e/abcdef123"))
        assertTrue(videoCodes.contains("https://www.yourupload.com/embed/xyz789"))
    }
}
