package com.darknet.animetv.data.model

/**
 * Represents a character in an anime along with their voice actor (Seiyuu)
 * and character role (MAIN vs SUPPORTING).
 */
data class CharacterCast(
    val id: String,
    val characterName: String,
    val characterRole: String = "MAIN", // "MAIN" or "SUPPORTING"
    val characterImageUrl: String = "",
    val voiceActorName: String = "",
    val voiceActorLanguage: String = "Japanese",
    val voiceActorImageUrl: String = ""
)
