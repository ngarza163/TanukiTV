package com.darknet.animetv.data.source.extension

import android.util.Log
import com.darknet.animetv.data.storage.AppStorage
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Manager handling external JSON extension repositories, over-the-air parser rule updates,
 * and modular cloud provider installation without requiring APK recompilation.
 */
object CloudExtensionManager {
    private const val TAG = "CloudExtensionManager"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    // Curated default cloud repository
    const val DEFAULT_COMMUNITY_REPO_URL = "https://raw.githubusercontent.com/darknet-anime/extensions/main/repo.json"

    /**
     * Parses extension repository content from a raw JSON string.
     */
    fun parseRepositoryJson(jsonString: String, repoUrl: String = ""): ExtensionRepository {
        val root = JsonParser.parseString(jsonString).asJsonObject
        val name = root.get("name")?.asString ?: "Community Extensions"
        val version = root.get("version")?.asInt ?: 1
        val description = root.get("description")?.asString ?: ""

        val extensions = mutableListOf<CloudExtension>()
        val extArray = root.getAsJsonArray("extensions")
        extArray?.forEach { el ->
            try {
                val obj = el.asJsonObject
                val id = obj.get("id").asString
                val extName = obj.get("name").asString
                val extVersion = obj.get("version")?.asString ?: "1.0.0"
                val author = obj.get("author")?.asString ?: "Community"
                val baseUrl = obj.get("baseUrl").asString
                val desc = obj.get("description")?.asString ?: ""
                val search = obj.get("searchEndpoint")?.asString ?: "/search?q={query}"
                val animeListSel = obj.get("animeListSelector")?.asString ?: ".anime-card, .flw-item"
                val titleSel = obj.get("titleSelector")?.asString ?: "h3, .title, .film-name a"
                val posterSel = obj.get("posterSelector")?.asString ?: "img@src, img@data-src"
                val epSel = obj.get("episodeSelector")?.asString ?: "ul.episodes a, .ep-item"
                val streamSel = obj.get("streamSelector")?.asString ?: "iframe@src, video@src"

                val headers = mutableMapOf<String, String>()
                obj.getAsJsonObject("headers")?.entrySet()?.forEach { (k, v) ->
                    headers[k] = v.asString
                }

                extensions.add(
                    CloudExtension(
                        id = id,
                        name = extName,
                        version = extVersion,
                        author = author,
                        baseUrl = baseUrl,
                        description = desc,
                        searchEndpoint = search,
                        animeListSelector = animeListSel,
                        titleSelector = titleSel,
                        posterSelector = posterSel,
                        episodeSelector = epSel,
                        streamSelector = streamSel,
                        headers = headers
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse individual cloud extension", e)
            }
        }

        return ExtensionRepository(
            name = name,
            repoUrl = repoUrl,
            version = version,
            description = description,
            extensions = extensions
        )
    }

    /**
     * Fetches and parses an extension repository from a remote URL.
     */
    suspend fun fetchRepository(url: String): Result<ExtensionRepository> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "AnimeTV/2.0 (Android TV Modular Extensions)")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP Error: ${response.code}"))
                }
                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty repository payload"))
                Result.success(parseRepositoryJson(body, url))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Installs or updates a cloud extension into the app's persistent storage.
     */
    fun installExtension(extension: CloudExtension, storage: AppStorage): Long {
        val existing = storage.getSources().find { it.baseUrl.trimEnd('/') == extension.baseUrl.trimEnd('/') }
        val customSource = extension.toCustomSource().copy(
            id = existing?.id ?: 0L,
            isEnabled = existing?.isEnabled ?: true
        )
        return storage.saveSource(customSource)
    }

    /**
     * Synchronizes all extensions from a repository into storage.
     */
    suspend fun syncRepository(url: String, storage: AppStorage): Result<Int> = withContext(Dispatchers.IO) {
        val res = fetchRepository(url)
        if (res.isFailure) {
            return@withContext Result.failure(res.exceptionOrNull() ?: Exception("Failed to sync repo"))
        }

        val repo = res.getOrNull()!!
        var installedCount = 0
        repo.extensions.forEach { ext ->
            installExtension(ext, storage)
            installedCount++
        }
        Result.success(installedCount)
    }

    /**
     * Generates a sample community repository JSON string for testing or documentation.
     */
    fun getSampleRepositoryJson(): String {
        return """
            {
              "name": "Community Stream Hubs",
              "version": 1,
              "description": "Verified high-speed anime scraper extensions",
              "extensions": [
                {
                  "id": "ext-otakustream",
                  "name": "OtakuStream Cloud",
                  "version": "1.3.0",
                  "author": "Darknet Devs",
                  "baseUrl": "https://otakustream.tv",
                  "description": "Ultra fast 1080p stream provider",
                  "searchEndpoint": "/search?keyword={query}",
                  "animeListSelector": ".video-item a",
                  "titleSelector": ".title",
                  "posterSelector": "img@src",
                  "episodeSelector": ".episode-list a",
                  "streamSelector": "iframe@src",
                  "headers": {
                    "Referer": "https://otakustream.tv/"
                  }
                },
                {
                  "id": "ext-animesub",
                  "name": "AnimeSub Direct",
                  "version": "2.1.0",
                  "author": "OpenScraper",
                  "baseUrl": "https://animesub.cc",
                  "description": "Multi-audio English and Japanese catalog",
                  "searchEndpoint": "/filter?name={query}",
                  "animeListSelector": ".card-anime a",
                  "titleSelector": "h4",
                  "posterSelector": "img@data-src",
                  "episodeSelector": ".eps a",
                  "streamSelector": "video source@src"
                }
              ]
            }
        """.trimIndent()
    }
}
