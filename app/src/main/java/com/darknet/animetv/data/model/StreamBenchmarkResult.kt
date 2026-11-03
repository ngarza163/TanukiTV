package com.darknet.animetv.data.model

/**
 * Latency and throughput benchmark metrics for a streaming scraper or server.
 */
data class StreamBenchmarkResult(
    val sourceId: String,
    val sourceName: String,
    val pingMs: Long,
    val throughputMbps: Double,
    val isHealthy: Boolean = true,
    val statusMessage: String = "OK"
) {
    val pingRating: PingRating
        get() = when {
            !isHealthy -> PingRating.DEAD
            pingMs < 200L -> PingRating.EXCELLENT
            pingMs < 500L -> PingRating.GOOD
            else -> PingRating.POOR
        }
}

enum class PingRating(val label: String, val colorHex: Long) {
    EXCELLENT("Ultra Fast (<200ms)", 0xFF10B981), // Green
    GOOD("Moderate (<500ms)", 0xFFF59E0B),       // Yellow
    POOR("Slow (>500ms)", 0xFFEF4444),           // Red
    DEAD("Unreachable / Error", 0xFF6B7280)      // Gray
}
