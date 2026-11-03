package com.darknet.animetv.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UpdateManagerTest {

    private lateinit var updateManager: UpdateManager

    @Before
    fun setup() {
        updateManager = UpdateManager()
    }

    @Test
    fun testNormalizeVersion() {
        assertEquals("2.1.0", UpdateManager.normalizeVersion("v2.1.0"))
        assertEquals("2.1.0", UpdateManager.normalizeVersion("V2.1.0"))
        assertEquals("2.1.0", UpdateManager.normalizeVersion("2.1.0-release"))
        assertEquals("2.0.0", UpdateManager.normalizeVersion(" 2.0.0 "))
    }

    @Test
    fun testIsNewerVersion() {
        assertTrue(UpdateManager.isNewerVersion("2.1.0", "2.0.0"))
        assertTrue(UpdateManager.isNewerVersion("v2.0.1", "2.0.0"))
        assertTrue(UpdateManager.isNewerVersion("3.0.0", "2.9.9"))
        assertTrue(UpdateManager.isNewerVersion("2.0.0.1", "2.0.0"))

        assertFalse(UpdateManager.isNewerVersion("2.0.0", "2.0.0"))
        assertFalse(UpdateManager.isNewerVersion("1.9.9", "2.0.0"))
        assertFalse(UpdateManager.isNewerVersion("2.0.0", "2.1.0"))
    }

    @Test
    fun testParseReleaseJsonWithApkAsset() {
        val sampleJson = """
            {
              "tag_name": "v2.1.0",
              "name": "AnimeTV 2.1.0 - Auto-Updater & Performance",
              "body": "### Changes\n- Added GitHub OTA updates\n- TV Subtitle Customization\n- Performance tuning",
              "published_at": "2026-10-27T12:00:00Z",
              "prerelease": false,
              "assets": [
                {
                  "name": "animetv-release.apk",
                  "size": 36175872,
                  "browser_download_url": "https://github.com/darknet-tv/animetv/releases/download/v2.1.0/animetv-release.apk"
                },
                {
                  "name": "mapping.txt",
                  "size": 1024,
                  "browser_download_url": "https://github.com/darknet-tv/animetv/releases/download/v2.1.0/mapping.txt"
                }
              ]
            }
        """.trimIndent()

        val info = updateManager.parseReleaseJson(sampleJson, "2.0.0")

        assertTrue(info.hasUpdate)
        assertEquals("2.1.0", info.latestVersion)
        assertEquals("AnimeTV 2.1.0 - Auto-Updater & Performance", info.releaseTitle)
        assertTrue(info.releaseNotes.contains("Added GitHub OTA updates"))
        assertEquals("https://github.com/darknet-tv/animetv/releases/download/v2.1.0/animetv-release.apk", info.apkDownloadUrl)
        assertEquals(36175872L, info.apkSizeBytes)
        assertEquals("34.5 MB", info.formattedSize())
    }

    @Test
    fun testParseReleaseJsonWithoutApkAssetDoesNotTriggerUpdate() {
        val sampleJson = """
            {
              "tag_name": "v2.2.0",
              "name": "AnimeTV 2.2.0 Source Only",
              "body": "No APK compiled yet",
              "assets": []
            }
        """.trimIndent()

        val info = updateManager.parseReleaseJson(sampleJson, "2.0.0")

        // Should be false because there is no apk download url
        assertFalse(info.hasUpdate)
    }

    @Test
    fun testFallbackUpdateInfo() {
        val fallback = updateManager.fallbackUpdateInfo("2.0.0", "No internet connection")
        assertFalse(fallback.hasUpdate)
        assertEquals("2.0.0", fallback.currentVersion)
        assertTrue(fallback.releaseNotes.contains("No internet connection"))
    }
}
