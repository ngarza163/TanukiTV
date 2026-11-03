package com.darknet.animetv.source

import com.darknet.animetv.data.source.extension.CloudExtension
import com.darknet.animetv.data.source.extension.CloudExtensionManager
import com.darknet.animetv.data.storage.AppStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CloudExtensionManagerTest {

    @Test
    fun testParseSampleRepositoryJson() {
        val sampleJson = CloudExtensionManager.getSampleRepositoryJson()
        val repo = CloudExtensionManager.parseRepositoryJson(sampleJson, "https://test.com/repo.json")

        assertEquals("Community Stream Hubs", repo.name)
        assertEquals(1, repo.version)
        assertEquals(2, repo.extensions.size)

        val firstExt = repo.extensions[0]
        assertEquals("ext-otakustream", firstExt.id)
        assertEquals("OtakuStream Cloud", firstExt.name)
        assertEquals("https://otakustream.tv", firstExt.baseUrl)
        assertEquals("https://otakustream.tv/", firstExt.headers["Referer"])
    }

    @Test
    fun testExtensionToCustomSourceConversion() {
        val ext = CloudExtension(
            id = "test-provider",
            name = "Test Provider",
            version = "1.0.5",
            baseUrl = "https://testanime.org",
            animeListSelector = ".list a",
            episodeSelector = ".ep a",
            streamSelector = "iframe"
        )

        val customSource = ext.toCustomSource()
        assertEquals("Test Provider (v1.0.5)", customSource.name)
        assertEquals("https://testanime.org", customSource.baseUrl)
        assertEquals("CLOUD_EXTENSION", customSource.sourceType)
        assertTrue(customSource.isEnabled)
    }

    @Test
    fun testInstallExtensionIntoStorage() {
        val context = RuntimeEnvironment.getApplication()
        val storage = AppStorage(context)

        val ext = CloudExtension(
            id = "anime-cloud-hub",
            name = "Anime Cloud Hub",
            version = "2.0.0",
            baseUrl = "https://animecloudhub.com"
        )

        val id = CloudExtensionManager.installExtension(ext, storage)
        assertTrue(id > 0L)

        val sources = storage.getSources()
        val installed = sources.find { it.baseUrl == "https://animecloudhub.com" }
        assertNotNull(installed)
        assertEquals("Anime Cloud Hub (v2.0.0)", installed?.name)
    }
}
