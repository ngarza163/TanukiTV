package com.darknet.animetv.data.model

/**
 * User preferences and playback configuration for AnimeTV Android TV.
 */
data class UserSettings(
    val defaultQuality: String = "1080p", // "Auto", "1080p", "720p", "480p", "360p"
    val preferredAudio: String = "SUB", // "SUB", "DUB"
    val preferredSubLanguage: String = "English", // "English", "Spanish", "Portuguese", "Off"
    val autoPlayNextEpisode: Boolean = true,
    val autoSkipIntro: Boolean = true,
    val introSkipSeconds: Int = 85, // 0, 85, 90
    val seekStepSeconds: Int = 10, // 5, 10, 15, 30
    val focusRingColor: String = "White", // "White", "Crimson Red", "Gold", "Neon Purple", "Cyan"
    val focusScale: Float = 1.08f, // 1.05f, 1.08f, 1.12f, 1.15f
    val uiSoundEffects: Boolean = true,
    val hardwareAcceleration: Boolean = true,
    val primaryCatalog: String = "AniList", // "AniList", "Jikan", "Direct"
    val autoFailover: Boolean = true,
    val autoScrobble: Boolean = true,
    val syncProvider: String = "AniList",
    // TV Subtitle Customization
    val subtitleFontSize: String = "24sp", // "18sp", "24sp", "32sp", "40sp"
    val subtitleTextColor: String = "White", // "White", "Yellow", "Cyan", "Green"
    val subtitleBackground: String = "Translucent", // "None", "Translucent", "Solid"
    val subtitleOffsetMs: Long = 0L, // -2000L to +2000L in ms
    // External Player Hand-off
    val useExternalPlayer: Boolean = false, // false = Internal Media3 ExoPlayer, true = Hand-off to external app
    // Living Room Audio Enhancements
    val audioPreset: String = "Standard", // "Standard", "Clear Voice", "Night Mode", "Bass Boost"
    val audioPassthrough: Boolean = false,
    // Adaptive Bitrate & Wi-Fi Buffer Tuning
    val bufferMode: String = "Balanced (50s)", // "Balanced (50s)", "Stable TV Wi-Fi (120s)", "Aggressive (180s)", "Low Latency (20s)"
    val maxBitrateCap: String = "Unlimited" // "Unlimited", "20 Mbps (4K UHD)", "10 Mbps (1080p HQ)", "5 Mbps (720p)", "2.5 Mbps (Saver)"
)
