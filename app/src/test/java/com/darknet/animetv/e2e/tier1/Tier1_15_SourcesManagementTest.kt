package com.darknet.animetv.e2e.tier1

import com.darknet.animetv.data.model.CustomSource
import com.darknet.animetv.e2e.harness.MockAnimeSource
import com.darknet.animetv.e2e.harness.MockFailoverRepository
import org.junit.Assert.*
import org.junit.Test
import java.net.URI

class Tier1_15_SourcesManagementTest {

    @Test
    fun testSourceEnableToggle() {
        val source = CustomSource(
            name = "Test Source",
            baseUrl = "https://source.mock",
            isEnabled = true
        )
        assertTrue(source.isEnabled)

        val toggled = source.copy(isEnabled = false)
        assertFalse(toggled.isEnabled)
    }

    @Test
    fun testSourceReorderingPriority() {
        val list = mutableListOf("Aniwatch", "Miruro", "AnimeFLV")
        // Move AnimeFLV to top
        val item = list.removeAt(2)
        list.add(0, item)

        assertEquals("AnimeFLV", list[0])
        assertEquals("Aniwatch", list[1])
        assertEquals("Miruro", list[2])
    }

    @Test
    fun testCustomSourceValidation() {
        fun isValidSource(source: CustomSource): Boolean {
            return try {
                val uri = URI(source.baseUrl)
                source.name.isNotBlank() && (uri.scheme == "http" || uri.scheme == "https") && uri.host != null
            } catch (e: Exception) {
                false
            }
        }

        val validSource = CustomSource(name = "Good Source", baseUrl = "https://my-anime.org")
        assertTrue(isValidSource(validSource))

        val invalidUrlSource = CustomSource(name = "Bad URL", baseUrl = "not-a-url")
        assertFalse(isValidSource(invalidUrlSource))

        val emptyNameSource = CustomSource(name = "  ", baseUrl = "https://my-anime.org")
        assertFalse(isValidSource(emptyNameSource))
    }

    @Test
    fun testBatchLatencyHealthTesting() = kotlinx.coroutines.runBlocking {
        val s1 = MockAnimeSource("s1", "Source 1", simulatedLatencyMs = 30L)
        val s2 = MockAnimeSource("s2", "Source 2", simulatedLatencyMs = 85L)
        val s3 = MockAnimeSource("s3", "Source 3", shouldFail = true, simulatedLatencyMs = 500L)

        val repo = MockFailoverRepository(mutableListOf(s1, s2, s3))
        val healthResults = repo.testAllSources()

        assertEquals(3, healthResults.size)
        assertTrue(healthResults["s1"]!!.isAvailable)
        assertTrue(healthResults["s2"]!!.isAvailable)
        assertFalse(healthResults["s3"]!!.isAvailable)
    }

    @Test
    fun testSourceDeletionFilter() {
        val sources = mutableListOf(
            CustomSource(id = 1, name = "S1", baseUrl = "https://s1.mock"),
            CustomSource(id = 2, name = "S2", baseUrl = "https://s2.mock"),
            CustomSource(id = 3, name = "S3", baseUrl = "https://s3.mock")
        )

        sources.removeAll { it.id == 2L }
        assertEquals(2, sources.size)
        assertFalse(sources.any { it.id == 2L })
    }
}
