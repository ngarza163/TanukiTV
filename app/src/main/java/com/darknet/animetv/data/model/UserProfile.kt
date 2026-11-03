package com.darknet.animetv.data.model

/**
 * Android TV user profile supporting multi-user households, customizable themes, anime character icons,
 * popular anime background scenes, custom image uploads, and parental controls.
 */
data class UserProfile(
    val id: String,
    val name: String,
    val avatarColor: Long = 0xFF8B52C9L, // Default Tanuki Purple
    val avatarIcon: String = "🦝",       // Default Tanuki Mascot
    val avatarUrl: String = "",          // Custom image URL or content:// URI for uploaded icon
    val backgroundTheme: String = "Cosmic Purple",
    val bgImageUrl: String = "",         // Custom anime background scene URL or content:// URI
    val bgGradientStart: Long = 0xFF3B0764L, // Deep violet gradient top
    val bgGradientEnd: Long = 0xFF0C0D14L,   // Obsidian bottom
    val isKidsMode: Boolean = false,
    val isPinLocked: Boolean = false,
    val pinHash: String = "", // 4-digit PIN hash or code
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * Checks if a given anime meets the parental safety criteria for this profile.
     */
    fun isAnimeAllowed(anime: Anime): Boolean {
        if (!isKidsMode) return true

        val matureKeywords = listOf(
            "ecchi", "hentai", "erotica", "horror", "gore", "psychological", "dementia",
            "r18", "r-17", "r+", "rx", "explicit", "violence"
        )

        // Check genres
        val hasMatureGenre = anime.genres.any { genre ->
            matureKeywords.any { genre.contains(it, ignoreCase = true) }
        }
        if (hasMatureGenre) return false

        // Check title & synopsis
        val textToCheck = "${anime.title} ${anime.synopsis}".lowercase()
        val hasMatureContent = matureKeywords.any { textToCheck.contains(it) }
        return !hasMatureContent
    }
}

/**
 * Account credentials and cloud synchronization status for TanukiTV.
 */
data class UserAccount(
    val email: String,
    val username: String,
    val token: String = "tanuki_tok_" + System.currentTimeMillis(),
    val isLoggedIn: Boolean = true,
    val membershipTier: String = "TanukiTV VIP",
    val syncEnabled: Boolean = true,
    val lastSyncTime: Long = System.currentTimeMillis()
)
