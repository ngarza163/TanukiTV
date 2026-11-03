package com.darknet.animetv.data.model

/**
 * Represents a single connected entry in an anime's media franchise
 * (prequels, sequels, side-stories, movies, OVAs, or spin-offs).
 */
data class FranchiseRelation(
    val animeId: String,
    val title: String,
    val relationType: FranchiseRelationType,
    val year: Int = 0,
    val format: String = "TV",
    val posterUrl: String = "",
    val score: Double = 0.0,
    val chronologicalOrder: Int = 1
)

enum class FranchiseRelationType(val label: String, val badgeColorHex: Long) {
    PREQUEL("Prequel", 0xFF3B82F6),        // Blue
    MAIN("Main Series", 0xFFEF4444),       // Red
    SEQUEL("Sequel", 0xFF10B981),         // Green
    SIDE_STORY("Side Story", 0xFF8B5CF6),  // Purple
    MOVIE("Movie", 0xFFF59E0B),           // Amber/Gold
    OVA("OVA / Special", 0xFFEC4899),      // Pink
    SPIN_OFF("Spin-Off", 0xFF6366F1),     // Indigo
    SUMMARY("Recap / Summary", 0xFF6B7280) // Gray
}
