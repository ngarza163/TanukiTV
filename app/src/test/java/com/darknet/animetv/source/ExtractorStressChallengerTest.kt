package com.darknet.animetv.source

import com.darknet.animetv.data.model.VideoSource
import com.darknet.animetv.data.repository.DefaultAnimeRepository
import com.darknet.animetv.data.source.AniListCatalogSource
import com.darknet.animetv.data.source.AnikotoSource
import com.darknet.animetv.data.source.AnimeFlvSource
import com.darknet.animetv.data.source.AnimeSource
import com.darknet.animetv.data.source.DynamicHtmlSource
import com.darknet.animetv.data.source.KaaSource
import com.darknet.animetv.data.source.ReAnimeSource
import com.darknet.animetv.data.source.SourceHealthResult
import com.darknet.animetv.data.source.WcoSource
import com.darknet.animetv.data.source.extractor.DoodstreamExtractor
import com.darknet.animetv.data.source.extractor.MegaCloudExtractor
import com.darknet.animetv.data.source.extractor.Mp4UploadExtractor
import com.darknet.animetv.data.source.extractor.StreamWishExtractor
import com.darknet.animetv.data.source.extractor.VidplayExtractor
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Empirical Stress Test Suite created by challenger_m2_1.
 * Adversarially challenges extractors and scrapers against malformed, corrupted,
 * out-of-spec, and edge-case inputs.
 */
@RunWith(RobolectricTestRunner::class)
class ExtractorStressChallengerTest {

    // =========================================================================
    // 1. STREAMWISH UNPACKER ADVERSARIAL STRESS
    // =========================================================================

    @Test
    fun testDeanEdwardsCorruptedWrapperSyntax() {
        val extractor = StreamWishExtractor()

        // Missing arguments
        assertNull(extractor.unpackDeanEdwards("eval(function(p,a,c,k,e,d){ return p; }('broken'))"))

        // Truncated split
        assertNull(extractor.unpackDeanEdwards("eval(function(p,a,c,k,e,r){return p}('payload', 36, 5, 'a|b'"))

        // Non-number radix and count
        assertNull(extractor.unpackDeanEdwards("eval(function(p,a,c,k,e,d){ return p;}('payload', 'not_a_num', 'bad', 'dict'.split('|')))"))

        // Empty string
        assertNull(extractor.unpackDeanEdwards(""))
    }

    @Test
    fun testDeanEdwardsExtremeRadixAndBaseN() {
        val extractor = StreamWishExtractor()

        // Radix = 1 (illegal for Character.digit, must not throw uncaught IllegalArgumentException)
        val scriptRadix1 = """eval(function(p,a,c,k,e,d){return p;}('0 1 2', 1, 3, 'zero|one|two'.split('|')))"""
        val result1 = extractor.unpackDeanEdwards(scriptRadix1)
        // Should decode gracefully or keep words intact without crashing
        assertNotNull(result1)

        // Radix = 100 (exceeds Character.MAX_RADIX 36, must catch IllegalArgumentException)
        val scriptRadix100 = """eval(function(p,a,c,k,e,d){return p;}('xyz 99', 100, 2, 'alpha|beta'.split('|')))"""
        val result100 = extractor.unpackDeanEdwards(scriptRadix100)
        assertNotNull(result100)
    }

    @Test
    fun testDeanEdwardsAdversarialDictionaryRegexTokens() {
        val extractor = StreamWishExtractor()

        // Dictionary contains regex metacharacters: $, \, \1, &, | and quotes
        val packed = """eval(function(p,a,c,k,e,d){return p;}('0 1 2 3 4', 36, 5, '\$1|\\backslash|&amp;|&quot;|\${'$'}'.split('|')))"""
        val unpacked = extractor.unpackDeanEdwards(packed)
        assertNotNull(unpacked)
        assertTrue(unpacked!!.contains("\$1"))
        assertTrue(unpacked.contains("\\backslash"))
    }

    @Test
    fun testDeanEdwardsDictionaryIndexUnderflowAndOverflow() {
        val extractor = StreamWishExtractor()

        // Payload has word 'zz' (base36 = 1295), but dictionary has only 2 items
        val packed = """eval(function(p,a,c,k,e,d){return p;}('0 zz 1', 36, 2, 'first|second'.split('|')))"""
        val unpacked = extractor.unpackDeanEdwards(packed)
        assertNotNull(unpacked)
        // 'zz' is out of bounds (1295 >= 2), should remain 'zz'
        assertTrue(unpacked!!.contains("first"))
        assertTrue(unpacked.contains("zz"))
        assertTrue(unpacked.contains("second"))
    }

    @Test
    fun testStreamWishExtractFromHtmlGarbageAndSpecialChars() {
        val extractor = StreamWishExtractor()

        // 1. Completely binary / corrupted HTML
        val binaryHtml = "\u0000\u0001\u0002<script>alert(1)</script>\u00FF\u00FE"
        val res1 = extractor.extractFromHtml(binaryHtml, "https://streamwish.to/e/123")
        assertTrue(res1.isEmpty())

        // 2. HTML with unclosed string inside sources regex
        val brokenScript = """<script>sources: ["https://cdn.streamwish.to/hls/test.m3u8</script>"""
        val res2 = extractor.extractFromHtml(brokenScript, "https://streamwish.to/e/123")
        // Broader regex might or might not match, but must not crash
        assertNotNull(res2)

        // 3. HTML with valid unpacked script containing m3u8
        val packedValid = """
            <script>
            eval(function(p,a,c,k,e,d){return p;}('sources:[{file:"1://2.3/4.5"}];',36,6,'|https|stream|com|video|m3u8'.split('|')))
            </script>
        """.trimIndent()
        val res3 = extractor.extractFromHtml(packedValid, "https://streamwish.to/e/test")
        assertEquals(1, res3.size)
        assertEquals("https://stream.com/video.m3u8", res3[0].url)
    }

    // =========================================================================
    // 2. MEGACLOUD ROBUSTNESS & TOKEN MALFORMATIONS
    // =========================================================================

    @Test
    fun testMegaCloudStreamIdMalformedUrls() {
        val extractor = MegaCloudExtractor()

        // Trailing slashes
        assertEquals("abc123xyz", extractor.extractStreamId("https://megacloud.tv/embed-2/e-1/abc123xyz/"))

        // Multiple query params
        assertEquals("abc123xyz", extractor.extractStreamId("https://megacloud.tv/embed-2/e-1/abc123xyz?autoPlay=1&oa=0"))

        // Embedded id= parameter
        assertEquals("token999", extractor.extractStreamId("https://rapidcloud.co/embed?id=token999&auth=yes"))

        // Bare host with no path segment falls back to last URL segment
        assertEquals("megacloud.tv", extractor.extractStreamId("https://megacloud.tv/"))
    }

    @Test
    fun testMegaCloudParseGetSourcesCorruptedJson() {
        val extractor = MegaCloudExtractor()
        val results = mutableListOf<VideoSource>()

        // 1. Non-JSON plain text
        extractor.parseGetSourcesResponse("502 Bad Gateway - Cloudflare", "https://megacloud.tv", results)
        assertTrue(results.isEmpty())

        // 2. Truncated JSON
        extractor.parseGetSourcesResponse("{\"sources\": [{\"file\":", "https://megacloud.tv", results)
        assertTrue(results.isEmpty())

        // 3. Primitive sources instead of JSON array
        extractor.parseGetSourcesResponse("{\"sources\": \"https://megacloud.tv/stream.m3u8\"}", "https://megacloud.tv", results)
        assertTrue(results.isEmpty())

        // 4. Tracks array with null / missing / non-string fields
        val jsonWithWeirdTracks = """
            {
                "sources": [
                    { "file": "https://megacloud.tv/master.m3u8", "type": "hls" }
                ],
                "tracks": [
                    { "file": "", "label": "EmptyFile" },
                    { "label": "NoFile" },
                    { "file": "https://megacloud.tv/sub.vtt", "label": "English", "kind": "captions" }
                ]
            }
        """.trimIndent()
        extractor.parseGetSourcesResponse(jsonWithWeirdTracks, "https://megacloud.tv", results)
        assertEquals(1, results.size)
        // Only the valid track with non-empty file should be added
        assertEquals(1, results[0].subtitles.size)
        assertEquals("English", results[0].subtitles[0].label)
    }

    @Test
    fun testMegaCloudOfflineFailoverGeneratesSafeStream() = runBlocking {
        val extractor = MegaCloudExtractor()
        // Non-existent server embedUrl will fail network connection and must trigger safe HLS fallback
        val sources = extractor.extract("https://invalid-host-999.megacloud.tv/embed-2/e-1/stressTestId")
        assertEquals(1, sources.size)
        assertTrue(sources[0].isHls)
        assertTrue(sources[0].url.contains("stressTestId"))
        assertNotNull(sources[0].headers["Referer"])
    }

    // =========================================================================
    // 3. VIDPLAY & DOODSTREAM ADVERSARIAL PARSING
    // =========================================================================

    @Test
    fun testVidplayExtractVideoIdEdgeCases() {
        val extractor = VidplayExtractor()

        assertEquals("vid456", extractor.extractVideoId("https://vidplay.online/e/vid456"))
        assertEquals("vid456", extractor.extractVideoId("https://vidplay.online/v/vid456?autostart=true"))
        assertEquals("vid456", extractor.extractVideoId("https://vidplay.online/video/vid456/"))
        assertEquals("standalone", extractor.extractVideoId("https://vidplay.online/standalone"))
    }

    @Test
    fun testVidplayParseMediaInfoCorruptedJson() {
        val extractor = VidplayExtractor()
        val list = mutableListOf<VideoSource>()

        // Syntax error JSON
        extractor.parseMediaInfoJson("{ unquoted_key: }", "https://vidplay.online", "https://vidplay.online/e/1", list)
        assertTrue(list.isEmpty())

        // JSON where result is a boolean
        extractor.parseMediaInfoJson("{\"result\": false}", "https://vidplay.online", "https://vidplay.online/e/1", list)
        assertTrue(list.isEmpty())

        // JSON without result wrapper directly having sources array
        val directJson = """
            {
                "sources": [{ "file": "https://vidplay.online/hls/direct.m3u8" }]
            }
        """.trimIndent()
        extractor.parseMediaInfoJson(directJson, "https://vidplay.online", "https://vidplay.online/e/1", list)
        assertEquals(1, list.size)
        assertEquals("https://vidplay.online/hls/direct.m3u8", list[0].url)
    }

    @Test
    fun testDoodstreamPassMd5PathMalformedHtml() {
        val extractor = DoodstreamExtractor()

        // Normal path
        assertEquals("/pass_md5/token123", extractor.extractPassMd5Path("""$.get('/pass_md5/token123', function(data)"""))

        // Double quotes
        assertEquals("/pass_md5/abc_def-456", extractor.extractPassMd5Path("""fetch("/pass_md5/abc_def-456")"""))

        // Unclosed quotes (should not match)
        assertNull(extractor.extractPassMd5Path("""$.get('/pass_md5/incomplete"""))

        // Empty string
        assertNull(extractor.extractPassMd5Path(""))
    }

    @Test
    fun testMp4UploadDirectRegexStress() {
        val extractor = Mp4UploadExtractor()

        // Missing src in script
        assertNull(extractor.extractDirectMp4Url("<script>console.log('empty');</script>"))

        // Spaces and weird quotes
        val trickyScript = """<script>player.src({ type: 'video/mp4' , src: "https://www2.mp4upload.com:282/d/sample.mp4" });</script>"""
        assertEquals("https://www2.mp4upload.com:282/d/sample.mp4", extractor.extractDirectMp4Url(trickyScript))
    }

    // =========================================================================
    // 4. KAA AES CRYPTOGRAPHIC RESILIENCE & CORRUPTION
    // =========================================================================

    @Test
    fun testKaaAesInvalidKeyLengthHandling() {
        val source = KaaSource()

        // Valid IV, but key is 5 bytes (must be 16, 24, or 32 for AES)
        val ivHex = "00112233445566778899aabbccddeeff"
        val cipherText = "dGVzdA==" // base64 "test"
        val result = source.decryptAesCbc(cipherText, "short", ivHex)
        // Must catch InvalidKeyException and safely return empty string
        assertEquals("", result)

        // Key is empty string
        assertEquals("", source.decryptAesCbc(cipherText, "", ivHex))
    }

    @Test
    fun testKaaAesCorruptedHexIv() {
        val source = KaaSource()
        val key = "1234567890123456"

        // Odd-length IV: hexStringToByteArray prepends "0"
        val oddIv = source.hexStringToByteArray("abc")
        assertEquals(2, oddIv.size)

        // Empty IV string: results in 0-length byte array, init throws InvalidAlgorithmParameterException
        val resultEmptyIv = source.decryptAesCbc("dGVzdA==", key, "")
        assertEquals("", resultEmptyIv)
    }

    @Test
    fun testKaaAesCorruptedBase64CipherText() {
        val source = KaaSource()
        val key = "1234567890123456"
        val ivHex = "00112233445566778899aabbccddeeff"

        // Non-base64 characters
        val badBase64 = "!!!@#\$%^&*()_+"
        val result = source.decryptAesCbc(badBase64, key, ivHex)
        assertEquals("", result)
    }

    @Test
    fun testKaaAesPkcs7PaddingCorruptionAndWrongKey() {
        val source = KaaSource()
        val key1 = "1234567890123456"
        val key2 = "6543210987654321" // Wrong key
        val ivHex = "00112233445566778899aabbccddeeff"
        val plainText = "{\"stream\":\"https://kaa.lt/master.m3u8\"}"

        // Encrypt with key1
        val ivBytes = source.hexStringToByteArray(ivHex)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key1.toByteArray(StandardCharsets.UTF_8), "AES"), IvParameterSpec(ivBytes))
        val cipherBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        val cipherBase64 = android.util.Base64.encodeToString(cipherBytes, android.util.Base64.NO_WRAP)

        // Attempt decrypt with WRONG key2 -> BadPaddingException must be caught!
        val decryptedWrongKey = source.decryptAesCbc(cipherBase64, key2, ivHex)
        assertEquals("", decryptedWrongKey)

        // Attempt decrypt with correct key1 -> successfully returns plainText
        val decryptedCorrect = source.decryptAesCbc(cipherBase64, key1, ivHex)
        assertEquals(plainText, decryptedCorrect)
    }

    @Test
    fun testKaaAesShortCiphertext() {
        val source = KaaSource()
        val key = "1234567890123456"
        val ivHex = "00112233445566778899aabbccddeeff"

        // Base64 of only 5 bytes: AES/CBC requires multiple of 16 bytes (IllegalBlockSizeException)
        val shortCipherBase64 = android.util.Base64.encodeToString(byteArrayOf(1, 2, 3, 4, 5), android.util.Base64.NO_WRAP)
        val result = source.decryptAesCbc(shortCipherBase64, key, ivHex)
        assertEquals("", result)
    }

    // =========================================================================
    // 5. DOM SCRAPERS MALFORMED HTML & ELEMENT ABSENCE
    // =========================================================================

    @Test
    fun testAnimeFlvMalformedVideosScript() {
        val source = AnimeFlvSource()

        // 1. Script has broken JSON syntax
        val brokenJsonHtml = """
            <script>
                var videos = { "SUB": [ { "code": "broken" , } ] };
            </script>
        """.trimIndent()
        val res1 = source.extractVideosJson(brokenJsonHtml)
        assertTrue(res1.isEmpty())

        // 2. Script has SUB as string instead of array
        val primitiveSubHtml = """
            <script>
                var videos = { "SUB": "https://streamwish.to/embed" };
            </script>
        """.trimIndent()
        val res2 = source.extractVideosJson(primitiveSubHtml)
        assertTrue(res2.isEmpty())

        // 3. No videos script at all
        val emptyHtml = "<html><body><h1>No videos</h1></body></html>"
        val res3 = source.extractVideosJson(emptyHtml)
        assertTrue(res3.isEmpty())
    }

    @Test
    fun testWcoMalformedBase64ScriptExtraction() {
        val source = WcoSource()

        // 1. atob with non-base64 input
        val badHtml = """
            <script>
                var vid = atob("!@#$%%^&*");
            </script>
        """.trimIndent()
        assertNull(source.extractBase64Stream(badHtml))

        // 2. atob with valid base64 that doesn't contain a media URL
        val helloBase64 = android.util.Base64.encodeToString("Hello World".toByteArray(StandardCharsets.UTF_8), android.util.Base64.NO_WRAP)
        val nonMediaHtml = """<script>var vid = atob("$helloBase64");</script>"""
        assertNull(source.extractBase64Stream(nonMediaHtml))

        // 3. atob with valid media URL surrounded by quotes and spaces
        val mediaUrl = "   https://cdn.wcostream.tv/stream.mp4   "
        val encodedMedia = android.util.Base64.encodeToString(mediaUrl.toByteArray(StandardCharsets.UTF_8), android.util.Base64.NO_WRAP)
        val validHtml = """<script>var vid = atob("$encodedMedia");</script>"""
        assertEquals("https://cdn.wcostream.tv/stream.mp4", source.extractBase64Stream(validHtml))
    }

    @Test
    fun testDynamicHtmlSourceInvalidCssSelectorsAndRegex() {
        // User provides corrupted CSS syntax in DSL
        val corruptedConfigJson = """
            {
                "id": "stress-source",
                "name": "Stress Custom Source",
                "baseUrl": "https://example.com",
                "search": {
                    "itemSelector": "div:::broken[syntax",
                    "titleSelector": ".title@",
                    "idRegex": "[unclosed-bracket("
                }
            }
        """.trimIndent()

        val dynamicSource = DynamicHtmlSource.fromJsonDsl(corruptedConfigJson)
        assertEquals("stress-source", dynamicSource.sourceId)

        // Extraction on malformed selector must not throw unhandled exception
        val doc = org.jsoup.Jsoup.parse("<html><div class='title'>Sample</div></html>")
        val extractedVal = dynamicSource.extractValue(doc, ".title@")
        // Trailing '@' specifies empty attribute name -> returns empty string safely
        assertEquals("", extractedVal)

        // Comma-separated fallback with missing first selector recovers to valid selector
        val recoveredVal = dynamicSource.extractValue(doc, "span.missing, .title")
        assertEquals("Sample", recoveredVal)
    }

    // =========================================================================
    // 6. ANILIST GRAPHQL 16:9 BANNER & METADATA FALLBACKS
    // =========================================================================

    @Test
    fun testAniListMissingBannerFallbackToCover() {
        val source = AniListCatalogSource()

        val json = """
            {
                "id": 150000,
                "title": { "english": "Solo Leveling", "romaji": null, "native": null },
                "coverImage": { "extraLarge": "https://cdn.anilist.co/cover_xl.jpg", "large": "https://cdn.anilist.co/cover_lg.jpg" },
                "bannerImage": null,
                "averageScore": 86,
                "seasonYear": 2024,
                "status": "RELEASING",
                "genres": ["Action", "Fantasy"],
                "description": "Hunter Sung Jinwoo story",
                "episodes": 12
            }
        """.trimIndent()

        val mediaObj = JsonParser.parseString(json).asJsonObject
        val anime = source.parseMediaNode(mediaObj)

        assertEquals("anilist-150000", anime.id)
        assertEquals("Solo Leveling", anime.title)
        assertEquals("https://cdn.anilist.co/cover_xl.jpg", anime.posterUrl)

        // CRITICAL 16:9 TV CONSTRAINT: When bannerImage is null, backdropUrl MUST fallback to cover art!
        assertEquals("https://cdn.anilist.co/cover_xl.jpg", anime.backdropUrl)
        assertEquals(8.6, anime.score, 0.01)
    }

    @Test
    fun testAniListMissingAllImagesAndAllNullTitles() {
        val source = AniListCatalogSource()

        val json = """
            {
                "id": 150001,
                "title": { "english": null, "romaji": null, "native": null },
                "coverImage": { "extraLarge": null, "large": null },
                "bannerImage": null,
                "averageScore": null,
                "seasonYear": null,
                "status": null,
                "genres": [null, "Mystery", null],
                "description": null,
                "episodes": null
            }
        """.trimIndent()

        val mediaObj = JsonParser.parseString(json).asJsonObject
        val anime = source.parseMediaNode(mediaObj)

        assertEquals("anilist-150001", anime.id)
        // Must fallback to "Untitled"
        assertEquals("Untitled", anime.title)
        assertEquals("", anime.posterUrl)
        assertEquals("", anime.backdropUrl)
        assertEquals(0.0, anime.score, 0.001)
        assertEquals("Ongoing", anime.status)
        assertEquals(0, anime.totalEpisodes)
        // Null elements in genres list must be filtered or handled
        assertEquals(1, anime.genres.size)
        assertEquals("Mystery", anime.genres[0])
    }

    // =========================================================================
    // 7. MULTI-SOURCE FAILOVER RESILIENCE UNDER TOTAL PROVIDER FAILURE
    // =========================================================================

    @Test
    fun testDefaultAnimeRepositoryAllSourcesFailingResilience() = runBlocking {
        // Create broken sources that always throw exceptions
        val failingSource1 = object : AnimeSource {
            override val sourceId = "failing-1"
            override val sourceName = "Failing 1"
            override val baseUrl = "https://fail1.com"
            override var isEnabled = true
            override suspend fun getTrending() = throw IOException("Network down")
            override suspend fun getPopular() = throw IOException("Network down")
            override suspend fun getRecentEpisodes() = throw IOException("Network down")
            override suspend fun search(query: String) = throw IOException("Network down")
            override suspend fun getAnimeDetail(animeId: String) = throw IOException("Network down")
            override suspend fun getEpisodeStreams(animeId: String, episodeId: String): List<VideoSource> {
                throw IOException("Stream server unreachable 503")
            }
            override suspend fun testConnection() = SourceHealthResult(sourceId, false, 0, "Failed")
        }

        val failingSource2 = object : AnimeSource {
            override val sourceId = "failing-2"
            override val sourceName = "Failing 2"
            override val baseUrl = "https://fail2.com"
            override var isEnabled = true
            override suspend fun getTrending() = throw RuntimeException("Internal Error")
            override suspend fun getPopular() = throw RuntimeException("Internal Error")
            override suspend fun getRecentEpisodes() = throw RuntimeException("Internal Error")
            override suspend fun search(query: String) = throw RuntimeException("Internal Error")
            override suspend fun getAnimeDetail(animeId: String) = throw RuntimeException("Internal Error")
            override suspend fun getEpisodeStreams(animeId: String, episodeId: String): List<VideoSource> {
                throw RuntimeException("Cloudflare challenge 403")
            }
            override suspend fun testConnection() = SourceHealthResult(sourceId, false, 0, "Failed")
        }

        val repo = DefaultAnimeRepository(customSourcesList = listOf(failingSource1, failingSource2))

        // When all providers fail, repository MUST NOT crash; it must provide emergency fallback stream
        val streams = repo.getEpisodeStreams("Attack on Titan", 1)
        assertFalse("Repository failover must provide emergency stream to prevent player crash", streams.isEmpty())
        assertNotNull(streams[0].url)
        assertTrue(streams[0].url.startsWith("http"))
    }
}
