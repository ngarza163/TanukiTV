package com.darknet.animetv.source

import com.darknet.animetv.data.model.CustomSource
import com.darknet.animetv.data.model.PingRating
import com.darknet.animetv.data.model.StreamBenchmarkResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScraperBenchmarkTest {

    @Test
    fun testPingRatingCategorization() {
        val ultraFast = StreamBenchmarkResult(
            sourceId = "kaa",
            sourceName = "KAA",
            pingMs = 120L,
            throughputMbps = 45.2,
            isHealthy = true
        )
        assertEquals(PingRating.EXCELLENT, ultraFast.pingRating)
        assertEquals(0xFF10B981L, ultraFast.pingRating.colorHex)

        val moderate = StreamBenchmarkResult(
            sourceId = "aniwatch",
            sourceName = "Aniwatch",
            pingMs = 350L,
            throughputMbps = 22.0,
            isHealthy = true
        )
        assertEquals(PingRating.GOOD, moderate.pingRating)
        assertEquals(0xFFF59E0BL, moderate.pingRating.colorHex)

        val slow = StreamBenchmarkResult(
            sourceId = "wco",
            sourceName = "WCO",
            pingMs = 620L,
            throughputMbps = 8.5,
            isHealthy = true
        )
        assertEquals(PingRating.POOR, slow.pingRating)
        assertEquals(0xFFEF4444L, slow.pingRating.colorHex)

        val dead = StreamBenchmarkResult(
            sourceId = "offline-source",
            sourceName = "Offline",
            pingMs = 5000L,
            throughputMbps = 0.0,
            isHealthy = false
        )
        assertEquals(PingRating.DEAD, dead.pingRating)
    }

    @Test
    fun testAutoRankingBySpeed() {
        val sources = listOf(
            CustomSource(id = 1, name = "Slow Source", baseUrl = "https://slow.com", sourceType = "SLOW"),
            CustomSource(id = 2, name = "Fastest Source", baseUrl = "https://fast.com", sourceType = "FAST"),
            CustomSource(id = 3, name = "Medium Source", baseUrl = "https://medium.com", sourceType = "MEDIUM"),
            CustomSource(id = 4, name = "Dead Source", baseUrl = "https://dead.com", sourceType = "DEAD")
        )

        val benchmarks = mapOf(
            "slow" to StreamBenchmarkResult("slow", "Slow", pingMs = 600, throughputMbps = 5.0, isHealthy = true),
            "fast" to StreamBenchmarkResult("fast", "Fast", pingMs = 95, throughputMbps = 65.0, isHealthy = true),
            "medium" to StreamBenchmarkResult("medium", "Medium", pingMs = 280, throughputMbps = 30.0, isHealthy = true),
            "dead" to StreamBenchmarkResult("dead", "Dead", pingMs = 5000, throughputMbps = 0.0, isHealthy = false)
        )

        val ranked = sources.sortedWith(
            compareByDescending<CustomSource> { src ->
                benchmarks[src.sourceType.lowercase()]?.isHealthy == true
            }.thenByDescending { src ->
                benchmarks[src.sourceType.lowercase()]?.throughputMbps ?: 0.0
            }.thenBy { src ->
                benchmarks[src.sourceType.lowercase()]?.pingMs ?: Long.MAX_VALUE
            }
        )

        assertEquals("Fastest Source", ranked[0].name)
        assertEquals("Medium Source", ranked[1].name)
        assertEquals("Slow Source", ranked[2].name)
        assertEquals("Dead Source", ranked[3].name)
    }
}
