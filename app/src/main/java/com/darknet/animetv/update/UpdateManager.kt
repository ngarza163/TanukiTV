package com.darknet.animetv.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.darknet.animetv.BuildConfig
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Manages GitHub Releases OTA updates for TanukiTV Leanback Android TV.
 */
class UpdateManager(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
    val githubRepo: String = DEFAULT_GITHUB_REPO
) {

    companion object {
        const val DEFAULT_GITHUB_REPO = "ngarza163/TanukiTV"
        private const val GITHUB_API_URL = "https://api.github.com/repos"

        /**
         * Cleans version strings by stripping 'v', build metadata, and trimming.
         */
        fun normalizeVersion(version: String): String {
            return version.trim().removePrefix("v").removePrefix("V").split("-")[0]
        }

        /**
         * Returns true if remoteVersion is strictly newer than currentVersion.
         * Supports semver like "2.1.0" > "2.0.0", "2.0.1" > "2.0.0", "1.10.0" > "1.9.0".
         */
        fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
            val remoteParts = normalizeVersion(remoteVersion).split(".").mapNotNull { it.toIntOrNull() }
            val currentParts = normalizeVersion(currentVersion).split(".").mapNotNull { it.toIntOrNull() }

            val maxLen = maxOf(remoteParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
            return false
        }
    }

    /**
     * Checks for updates against GitHub Releases latest API.
     */
    suspend fun checkForUpdates(currentVersion: String = BuildConfig.VERSION_NAME): UpdateInfo = withContext(Dispatchers.IO) {
        val endpoint = "$GITHUB_API_URL/$githubRepo/releases/latest"
        val request = Request.Builder()
            .url(endpoint)
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "TanukiTV-AndroidTV-Updater")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: return@withContext fallbackUpdateInfo(currentVersion, "Empty response body")
                    return@withContext parseReleaseJson(bodyString, currentVersion)
                }

                // Fallback to /releases?per_page=1 if /releases/latest returns 404 (e.g. prerelease or first tag)
                if (response.code == 404) {
                    val listReq = Request.Builder()
                        .url("$GITHUB_API_URL/$githubRepo/releases?per_page=1")
                        .header("Accept", "application/vnd.github.v3+json")
                        .header("User-Agent", "TanukiTV-AndroidTV-Updater")
                        .build()
                    client.newCall(listReq).execute().use { listResp ->
                        if (listResp.isSuccessful) {
                            val listBody = listResp.body?.string().orEmpty()
                            val arr = JsonParser.parseString(listBody).takeIf { it.isJsonArray }?.asJsonArray
                            if (arr != null && arr.size() > 0 && arr[0].isJsonObject) {
                                return@withContext parseReleaseJson(arr[0].asJsonObject.toString(), currentVersion)
                            } else {
                                return@withContext fallbackUpdateInfo(
                                    currentVersion,
                                    "Connected to github.com/$githubRepo (no releases published yet)"
                                )
                            }
                        }
                    }
                }

                val code = response.code
                return@withContext fallbackUpdateInfo(currentVersion, "GitHub release check ($githubRepo) returned HTTP $code")
            }
        } catch (e: Exception) {
            fallbackUpdateInfo(currentVersion, e.message ?: "Network error during update check")
        }
    }

    /**
     * Parses the GitHub release JSON and extracts APK download URL and metadata.
     */
    fun parseReleaseJson(json: String, currentVersion: String): UpdateInfo {
        return try {
            val root = JsonParser.parseString(json).asJsonObject
            val rawTag = root.get("tag_name")?.takeIf { !it.isJsonNull }?.asString ?: "2.0.0"
            val title = root.get("name")?.takeIf { !it.isJsonNull }?.asString?.ifBlank { "TanukiTV $rawTag" } ?: "TanukiTV $rawTag"
            val body = root.get("body")?.takeIf { !it.isJsonNull }?.asString?.ifBlank { "Maintenance and stability improvements for Android TV." }
                ?: "Maintenance and stability improvements for Android TV."
            val publishedAt = root.get("published_at")?.takeIf { !it.isJsonNull }?.asString ?: ""
            val isPrerelease = root.get("prerelease")?.takeIf { !it.isJsonNull }?.asBoolean ?: false

            var apkUrl = ""
            var apkSize = 0L

            if (root.has("assets") && root.get("assets").isJsonArray) {
                val assets = root.getAsJsonArray("assets")
                val apkAssets = mutableListOf<Pair<String, Pair<String, Long>>>()
                for (element in assets) {
                    if (!element.isJsonObject) continue
                    val asset = element.asJsonObject
                    val name = asset.get("name")?.takeIf { !it.isJsonNull }?.asString ?: ""
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        val url = asset.get("browser_download_url")?.takeIf { !it.isJsonNull }?.asString ?: ""
                        val size = asset.get("size")?.takeIf { !it.isJsonNull }?.asLong ?: 0L
                        if (url.isNotBlank()) {
                            apkAssets.add(name to (url to size))
                        }
                    }
                }
                val preferredToken = if (BuildConfig.DEBUG) "debug" else "release"
                val chosen = apkAssets.firstOrNull { it.first.contains(preferredToken, ignoreCase = true) }
                    ?: apkAssets.firstOrNull()
                if (chosen != null) {
                    apkUrl = chosen.second.first
                    apkSize = chosen.second.second
                }
            }

            val hasUpdate = isNewerVersion(rawTag, currentVersion) && apkUrl.isNotEmpty()

            UpdateInfo(
                hasUpdate = hasUpdate,
                currentVersion = currentVersion,
                latestVersion = normalizeVersion(rawTag),
                releaseTitle = title,
                releaseNotes = body,
                apkDownloadUrl = apkUrl,
                apkSizeBytes = apkSize,
                publishedAt = publishedAt,
                isPrerelease = isPrerelease
            )
        } catch (e: Exception) {
            fallbackUpdateInfo(currentVersion, "Failed to parse release information: ${e.message}")
        }
    }

    /**
     * Fallback when repo is unreachable or before first public GitHub tag release.
     */
    fun fallbackUpdateInfo(currentVersion: String, reason: String = ""): UpdateInfo {
        return UpdateInfo(
            hasUpdate = false,
            currentVersion = currentVersion,
            latestVersion = currentVersion,
            releaseTitle = "TanukiTV $currentVersion (Latest)",
            releaseNotes = if (reason.isNotEmpty()) "Up to date ($reason)" else "You have the latest version installed.",
            apkDownloadUrl = "",
            apkSizeBytes = 0L
        )
    }

    /**
     * Downloads an APK to cache or external files directory and launches Android package installer.
     */
    suspend fun downloadAndInstallApk(
        context: Context,
        downloadUrl: String,
        onProgress: (progressPercent: Int) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val destinationDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.cacheDir
            if (!destinationDir.exists()) destinationDir.mkdirs()

            val apkFile = File(destinationDir, "TanukiTV-latest-update.apk")
            if (apkFile.exists()) apkFile.delete()

            val request = Request.Builder().url(downloadUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code} downloading update APK"))
                }

                val body = response.body ?: return@withContext Result.failure(Exception("Empty body response"))
                val totalBytes = body.contentLength()
                var downloadedBytes = 0L

                val inputStream: InputStream = body.byteStream()
                val outputStream = FileOutputStream(apkFile)

                val buffer = ByteArray(16 * 1024)
                var bytesRead: Int

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    if (totalBytes > 0) {
                        val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                        withContext(Dispatchers.Main) {
                            onProgress(progress)
                        }
                    }
                }
                outputStream.flush()
                outputStream.close()
                inputStream.close()
            }

            // Launch package install intent
            withContext(Dispatchers.Main) {
                launchInstallIntent(context, apkFile)
            }

            Result.success(apkFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Builds and launches the Android Package Installer intent using FileProvider.
     */
    fun launchInstallIntent(context: Context, apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            try {
                val permIntent = Intent(
                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(permIntent)
                return
            } catch (_: Exception) {
                // Fall through to standard package installer intent
            }
        }

        val authority = "${context.packageName}.fileprovider"
        val contentUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        context.startActivity(installIntent)
    }
}
