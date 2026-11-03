package com.darknet.animetv.update

/**
 * Representation of an AnimeTV application update payload.
 */
data class UpdateInfo(
    val hasUpdate: Boolean,
    val currentVersion: String,
    val latestVersion: String,
    val releaseTitle: String = "",
    val releaseNotes: String = "",
    val apkDownloadUrl: String = "",
    val apkSizeBytes: Long = 0,
    val publishedAt: String = "",
    val isPrerelease: Boolean = false
) {
    /**
     * Formats bytes into a human-readable MB string.
     */
    fun formattedSize(): String {
        if (apkSizeBytes <= 0) return "Unknown size"
        val mb = apkSizeBytes.toDouble() / (1024 * 1024)
        return String.format(java.util.Locale.US, "%.1f MB", mb)
    }
}
