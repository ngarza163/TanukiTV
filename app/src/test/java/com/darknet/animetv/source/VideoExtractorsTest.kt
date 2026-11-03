package com.darknet.animetv.source

import com.darknet.animetv.data.source.extractor.DirectStreamExtractor
import com.darknet.animetv.data.source.extractor.DoodstreamExtractor
import com.darknet.animetv.data.source.extractor.MegaCloudExtractor
import com.darknet.animetv.data.source.extractor.Mp4UploadExtractor
import com.darknet.animetv.data.source.extractor.StreamWishExtractor
import com.darknet.animetv.data.source.extractor.VideoExtractorRegistry
import com.darknet.animetv.data.source.extractor.VidplayExtractor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoExtractorsTest {

    @Test
    fun testMegaCloudExtractorCanHandleAndExtractId() {
        val extractor = MegaCloudExtractor()
        assertTrue(extractor.canHandle("https://megacloud.tv/embed-2/e-1/2I5tr56M4UQJ?k=1"))
        assertTrue(extractor.canHandle("https://rapid-cloud.co/embed-2/e-1/flxWZ4Pey1Za"))
        assertTrue(extractor.canHandle("https://megacloud.club/embed-2/e-1/test1234"))
        assertFalse(extractor.canHandle("https://streamwish.to/e/abc"))

        val id = extractor.extractStreamId("https://megacloud.tv/embed-2/e-1/2I5tr56M4UQJ?k=1")
        assertEquals("2I5tr56M4UQJ", id)
    }

    @Test
    fun testMegaCloudParseGetSourcesResponse() {
        val extractor = MegaCloudExtractor()
        val json = """
            {
                "sources": [
                    { "file": "https://megacloud.tv/hls/master.m3u8", "type": "hls" }
                ],
                "tracks": [
                    { "file": "https://megacloud.tv/sub/eng.vtt", "label": "English", "kind": "captions", "default": true },
                    { "file": "https://megacloud.tv/sub/spa.vtt", "label": "Spanish", "kind": "captions" }
                ],
                "encrypted": false
            }
        """.trimIndent()

        val results = mutableListOf<com.darknet.animetv.data.model.VideoSource>()
        extractor.parseGetSourcesResponse(json, "https://megacloud.tv", results)

        assertEquals(1, results.size)
        val source = results[0]
        assertEquals("https://megacloud.tv/hls/master.m3u8", source.url)
        assertTrue(source.isHls)
        assertEquals(2, source.subtitles.size)
        assertEquals("English", source.subtitles[0].label)
        assertEquals("https://megacloud.tv/sub/eng.vtt", source.subtitles[0].url)
        assertEquals("https://megacloud.tv/", source.headers["Referer"])
    }

    @Test
    fun testStreamWishExtractorCanHandleAndUnpack() {
        val extractor = StreamWishExtractor()
        assertTrue(extractor.canHandle("https://streamwish.to/e/abcdef"))
        assertTrue(extractor.canHandle("https://strwish.com/e/12345"))
        assertTrue(extractor.canHandle("https://awish.pro/e/test"))
        assertFalse(extractor.canHandle("https://megacloud.tv/embed-2"))

        // Test Dean Edwards packer unpacking
        val packed = """
            eval(function(p,a,c,k,e,d){while(c--)if(k[c])p=p.replace(new RegExp('\\b'+c.toString(a)+'\\b','g'),k[c]);return p;}('0.1("2://3.4/5.6");',10,7,'player|src|https|cdn|streamwish|master|m3u8'.split('|'),0,{}))
        """.trimIndent()

        val unpacked = extractor.unpackDeanEdwards(packed)
        assertNotNull(unpacked)
        assertTrue(unpacked!!.contains("https://cdn.streamwish/master.m3u8"))

        val sources = extractor.extractFromHtml(packed, "https://streamwish.to/e/test")
        assertEquals(1, sources.size)
        assertEquals("https://cdn.streamwish/master.m3u8", sources[0].url)
        assertTrue(sources[0].isHls)
    }

    @Test
    fun testDoodstreamExtractorCanHandleAndRegex() {
        val extractor = DoodstreamExtractor()
        assertTrue(extractor.canHandle("https://dood.to/e/xyz"))
        assertTrue(extractor.canHandle("https://doodstream.com/e/123"))
        assertFalse(extractor.canHandle("https://youtube.com"))

        val html = """
            <html>
                <body>
                    <script>
                        $.get('/pass_md5/abc123token456', function(data) { ... });
                    </script>
                </body>
            </html>
        """.trimIndent()

        val md5Path = extractor.extractPassMd5Path(html)
        assertEquals("/pass_md5/abc123token456", md5Path)
    }

    @Test
    fun testVidplayExtractorCanHandleAndParseJson() {
        val extractor = VidplayExtractor()
        assertTrue(extractor.canHandle("https://vidplay.online/e/xyz123"))
        assertTrue(extractor.canHandle("https://vid2a41.site/e/test"))
        assertTrue(extractor.canHandle("https://megaf.cc/e/alpha"))

        val videoId = extractor.extractVideoId("https://vidplay.online/e/xyz123?autostart=true")
        assertEquals("xyz123", videoId)

        val json = """
            {
                "result": {
                    "sources": [
                        { "file": "https://vidplay.online/hls/xyz123/master.m3u8", "type": "hls" }
                    ],
                    "tracks": [
                        { "file": "https://vidplay.online/sub.vtt", "label": "English" }
                    ]
                }
            }
        """.trimIndent()

        val list = mutableListOf<com.darknet.animetv.data.model.VideoSource>()
        extractor.parseMediaInfoJson(json, "https://vidplay.online", "https://vidplay.online/e/xyz123", list)
        assertEquals(1, list.size)
        assertEquals("https://vidplay.online/hls/xyz123/master.m3u8", list[0].url)
        assertEquals(1, list[0].subtitles.size)
    }

    @Test
    fun testMp4UploadExtractorCanHandleAndRegex() {
        val extractor = Mp4UploadExtractor()
        assertTrue(extractor.canHandle("https://www.mp4upload.com/embed-abc.html"))
        assertFalse(extractor.canHandle("https://streamwish.to"))

        val html = """
            <script>
                player.src({ type: "video/mp4", src: "https://www2.mp4upload.com:282/d/directstream.mp4" });
            </script>
        """.trimIndent()

        val mp4Url = extractor.extractDirectMp4Url(html)
        assertEquals("https://www2.mp4upload.com:282/d/directstream.mp4", mp4Url)
    }

    @Test
    fun testDirectStreamExtractor() = runBlocking {
        val extractor = DirectStreamExtractor()
        assertTrue(extractor.canHandle("https://example.com/anime.m3u8"))
        assertTrue(extractor.canHandle("https://example.com/video.mp4"))

        val hlsSources = extractor.extract("https://example.com/playlist.m3u8")
        assertEquals(1, hlsSources.size)
        assertTrue(hlsSources[0].isHls)
        assertEquals("https://example.com/", hlsSources[0].headers["Referer"])

        val mp4Sources = extractor.extract("https://example.com/movie.mp4")
        assertEquals(1, mp4Sources.size)
        assertFalse(mp4Sources[0].isHls)
    }

    @Test
    fun testVideoExtractorRegistry() = runBlocking {
        val registry = VideoExtractorRegistry.defaultInstance

        // Resolves via direct extractor when given direct m3u8 or mp4
        val directResult = registry.extractFromUrl("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4")
        assertFalse(directResult.isEmpty())
        assertEquals("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4", directResult[0].url)

        // Resolves via streamwish extractor fallback
        val wishResult = registry.extractFromUrl("https://streamwish.to/e/demo123")
        assertFalse(wishResult.isEmpty())
        assertTrue(wishResult[0].url.contains("streamwish"))

        // Resolves via VOE extractor
        val voeResult = registry.extractFromUrl("https://voe.sx/e/voe123")
        assertFalse(voeResult.isEmpty())
        assertTrue(voeResult[0].url.contains("voe"))

        // Resolves via Filemoon extractor
        val filemoonResult = registry.extractFromUrl("https://filemoon.sx/e/fm123")
        assertFalse(filemoonResult.isEmpty())
        assertTrue(filemoonResult[0].url.contains("filemoon"))

        // Resolves via StreamTape extractor
        val tapeResult = registry.extractFromUrl("https://streamtape.com/e/tape123")
        assertFalse(tapeResult.isEmpty())
        assertTrue(tapeResult[0].url.contains("streamtape"))
    }

    @Test
    fun testVoeExtractorCanHandleAndParse() {
        val extractor = com.darknet.animetv.data.source.extractor.VoeExtractor()
        assertTrue(extractor.canHandle("https://voe.sx/e/abc123xyz"))
        assertTrue(extractor.canHandle("https://voe-network.net/e/sample"))
        assertFalse(extractor.canHandle("https://streamwish.to/e/xyz"))

        val htmlWithHls = """
            <script>
                var sources = {
                    'hls': 'https://delivery-node-1.voe.sx/engine/hls/test/master.m3u8'
                };
            </script>
        """.trimIndent()

        val results = mutableListOf<com.darknet.animetv.data.model.VideoSource>()
        extractor.parseVoeHtml(htmlWithHls, "https://voe.sx/e/test", results)
        assertEquals(1, results.size)
        assertEquals("https://delivery-node-1.voe.sx/engine/hls/test/master.m3u8", results[0].url)
        assertTrue(results[0].isHls)
    }

    @Test
    fun testFilemoonExtractorCanHandleAndParse() {
        val extractor = com.darknet.animetv.data.source.extractor.FilemoonExtractor()
        assertTrue(extractor.canHandle("https://filemoon.sx/e/sample123"))
        assertTrue(extractor.canHandle("https://filemoon.to/d/sample"))
        assertTrue(extractor.canHandle("https://moonplayer.net/e/xyz"))
        assertFalse(extractor.canHandle("https://youtube.com"))

        val html = """
            <script>
                var player = {
                    file: 'https://storage.filemoon.sx/hls/sample/index.m3u8'
                };
            </script>
        """.trimIndent()

        val results = mutableListOf<com.darknet.animetv.data.model.VideoSource>()
        extractor.parseFilemoonHtml(html, "https://filemoon.sx/e/sample", results)
        assertEquals(1, results.size)
        assertEquals("https://storage.filemoon.sx/hls/sample/index.m3u8", results[0].url)
        assertTrue(results[0].isHls)
    }

    @Test
    fun testStreamTapeExtractorCanHandleAndParse() {
        val extractor = com.darknet.animetv.data.source.extractor.StreamTapeExtractor()
        assertTrue(extractor.canHandle("https://streamtape.com/e/abc12345"))
        assertTrue(extractor.canHandle("https://streamta.pe/v/xyz"))
        assertFalse(extractor.canHandle("https://voe.sx"))

        val html = """
            <div id="videolink" style="display:none;">//streamtape.com/get_video?id=test&expires=12345&ip=1.1.1.1</div>
            <script>
                document.getElementById('robotlink').innerHTML = '//streamtape.com/get_video?id=test&expires=12345' + ('&stream=1');
            </script>
        """.trimIndent()

        val results = mutableListOf<com.darknet.animetv.data.model.VideoSource>()
        extractor.parseStreamTapeHtml(html, "https://streamtape.com/e/test", results)
        assertEquals(1, results.size)
        assertEquals("https://streamtape.com/get_video?id=test&expires=12345&stream=1", results[0].url)
        assertFalse(results[0].isHls)
    }
}
