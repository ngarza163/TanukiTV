package com.darknet.animetv.player.aniskip

/**
 * Data structures for crowd-sourced anime opening and ending skip timestamps.
 */
data class SkipInterval(
    val startMs: Long,
    val endMs: Long,
    val type: SkipType
) {
    val durationSeconds: Int
        get() = ((endMs - startMs) / 1000L).toInt().coerceAtLeast(0)

    val durationMs: Long
        get() = (endMs - startMs).coerceAtLeast(0L)

    val isOpening: Boolean
        get() = type == SkipType.OP || type == SkipType.MIXED_OP

    val isEnding: Boolean
        get() = type == SkipType.ED

    val isRecap: Boolean
        get() = type == SkipType.RECAP
}

enum class SkipType(val label: String, val badgeColorHex: Long) {
    OP("Opening", 0xFFEF4444),       // Red
    ED("Ending", 0xFF3B82F6),        // Blue
    RECAP("Recap", 0xFFF59E0B),      // Amber
    MIXED_OP("Opening", 0xFFEC4899)  // Pink
}

data class AniSkipResult(
    val interval: SkipInterval,
    val skipId: String = ""
)
