package com.darknet.animetv.data.repository

import com.darknet.animetv.data.model.AniListEntry
import com.darknet.animetv.data.model.AniListUser
import com.darknet.animetv.data.model.Anime
import com.darknet.animetv.data.model.MalUser
import com.darknet.animetv.data.model.SyncResult
import com.darknet.animetv.data.model.WatchProgress
import com.darknet.animetv.data.storage.AppStorage
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Service managing AniList OAuth authentication, two-way anime watchlist synchronization,
 * MyAnimeList (MAL) username importing, and automatic episode scrobbling.
 */
object AniListSyncManager {

    var anilistEndpoint: String = "https://graphql.anilist.co"
    const val ANILIST_CLIENT_ID = "22736"
    val OAUTH_AUTH_URL = "https://anilist.co/api/v2/oauth/authorize?client_id=$ANILIST_CLIENT_ID&response_type=token"
    const val MAL_CLIENT_ID = "tanukitv_mal_android_tv"
    val MAL_OAUTH_AUTH_URL = "https://myanimelist.net/v1/oauth2/authorize?response_type=code&client_id=$MAL_CLIENT_ID"

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    /**
     * Authenticate with an AniList personal access token or OAuth bearer token.
     * Verifies the token via GraphQL Viewer query and caches the profile in AppStorage.
     */
    suspend fun loginWithToken(token: String, storage: AppStorage): Result<AniListUser> = withContext(Dispatchers.IO) {
        try {
            val query = """
                query {
                    Viewer {
                        id
                        name
                        avatar {
                            large
                            medium
                        }
                        statistics {
                            anime {
                                count
                                episodesWatched
                            }
                        }
                    }
                }
            """.trimIndent()

            val payload = JsonObject().apply {
                addProperty("query", query)
            }

            val request = Request.Builder()
                .url(anilistEndpoint)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .header("Authorization", "Bearer ${token.trim()}")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "AnimeTV/2.0 (Android TV)")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP Error: ${response.code}"))
                }

                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
                val root = JsonParser.parseString(body).asJsonObject
                val viewerObj = root.getAsJsonObject("data")?.getAsJsonObject("Viewer")
                    ?: return@withContext Result.failure(Exception("Viewer data not found in response"))

                val id = viewerObj.get("id").asInt
                val name = viewerObj.get("name").asString
                val avatarUrl = viewerObj.getAsJsonObject("avatar")?.get("large")?.asString
                    ?: viewerObj.getAsJsonObject("avatar")?.get("medium")?.asString ?: ""

                val animeStats = viewerObj.getAsJsonObject("statistics")?.getAsJsonObject("anime")
                val totalAnime = animeStats?.get("count")?.asInt ?: 0
                val episodesWatched = animeStats?.get("episodesWatched")?.asInt ?: 0

                val user = AniListUser(
                    id = id,
                    name = name,
                    avatarUrl = avatarUrl,
                    totalAnime = totalAnime,
                    episodesWatched = episodesWatched
                )

                storage.saveAniListToken(token.trim())
                storage.saveAniListUser(user)

                Result.success(user)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Link an AniList account using a public username (without requiring OAuth token).
     * Fetches public user profile information.
     */
    suspend fun loginWithUsername(username: String, storage: AppStorage): Result<AniListUser> = withContext(Dispatchers.IO) {
        try {
            val trimmed = username.trim()
            if (trimmed.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Username cannot be blank"))
            }

            val query = """
                query (${'$'}name: String) {
                    User(name: ${'$'}name) {
                        id
                        name
                        avatar {
                            large
                            medium
                        }
                        statistics {
                            anime {
                                count
                                episodesWatched
                            }
                        }
                    }
                }
            """.trimIndent()

            val variables = JsonObject().apply {
                addProperty("name", trimmed)
            }

            val payload = JsonObject().apply {
                addProperty("query", query)
                add("variables", variables)
            }

            val request = Request.Builder()
                .url(anilistEndpoint)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "AnimeTV/2.0 (Android TV)")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("AniList lookup failed: HTTP ${response.code}"))
                }

                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
                val root = JsonParser.parseString(body).asJsonObject
                val userObj = root.getAsJsonObject("data")?.getAsJsonObject("User")
                    ?: return@withContext Result.failure(Exception("User '$trimmed' not found on AniList"))

                val id = userObj.get("id").asInt
                val name = userObj.get("name").asString
                val avatarUrl = userObj.getAsJsonObject("avatar")?.get("large")?.takeIf { !it.isJsonNull }?.asString
                    ?: userObj.getAsJsonObject("avatar")?.get("medium")?.takeIf { !it.isJsonNull }?.asString ?: ""

                val animeStats = userObj.getAsJsonObject("statistics")?.getAsJsonObject("anime")
                val totalAnime = animeStats?.get("count")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                val episodesWatched = animeStats?.get("episodesWatched")?.takeIf { !it.isJsonNull }?.asInt ?: 0

                val user = AniListUser(
                    id = id,
                    name = name,
                    avatarUrl = avatarUrl,
                    totalAnime = totalAnime,
                    episodesWatched = episodesWatched
                )

                storage.saveAniListToken(null)
                storage.saveAniListUser(user)

                Result.success(user)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Synchronize user's personal AniList anime lists (Watching, Plan to Watch, Completed)
     * into the local Android TV SQLite database.
     */
    suspend fun syncAniListWatchlist(storage: AppStorage): SyncResult = withContext(Dispatchers.IO) {
        try {
            val token = storage.getAniListToken()
            val user = storage.getAniListUser()

            if (token?.startsWith("demo_") == true) {
                // Explicit demo token in test or offline simulation
                return@withContext performDemoSync(storage)
            }

            if (user == null && token.isNullOrBlank()) {
                return@withContext SyncResult(false, 0, "No AniList account linked.")
            }

            val query = """
                query (${'$'}userId: Int, ${'$'}userName: String) {
                    MediaListCollection(userId: ${'$'}userId, userName: ${'$'}userName, type: ANIME) {
                        lists {
                            name
                            status
                            entries {
                                id
                                mediaId
                                status
                                score(format: POINT_10_DECIMAL)
                                progress
                                media {
                                    id
                                    title {
                                        romaji
                                        english
                                        native
                                    }
                                    coverImage {
                                        extraLarge
                                        large
                                    }
                                    bannerImage
                                    episodes
                                    status
                                    genres
                                    averageScore
                                    description(asHtml: false)
                                }
                            }
                        }
                    }
                }
            """.trimIndent()

            val variables = JsonObject().apply {
                if (user != null && user.id > 0) {
                    addProperty("userId", user.id)
                } else if (user != null && user.name.isNotBlank()) {
                    addProperty("userName", user.name)
                }
            }

            val payload = JsonObject().apply {
                addProperty("query", query)
                add("variables", variables)
            }

            val reqBuilder = Request.Builder()
                .url(anilistEndpoint)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "AnimeTV/2.0 (Android TV)")

            if (!token.isNullOrBlank()) {
                reqBuilder.header("Authorization", "Bearer $token")
            }

            val request = reqBuilder.build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext SyncResult(false, 0, "AniList Sync Failed (HTTP ${response.code})")
                }

                val body = response.body?.string() ?: return@withContext SyncResult(false, 0, "Empty AniList response")
                val root = JsonParser.parseString(body).asJsonObject
                val dataObj = root.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
                val mediaListCollectionObj = dataObj?.get("MediaListCollection")?.takeIf { it.isJsonObject }?.asJsonObject
                val listsArray = mediaListCollectionObj?.get("lists")?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()

                var syncedCount = 0

                listsArray.forEach { listEl ->
                    val listObj = listEl.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                    val entries = listObj.get("entries")?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()

                    entries.forEach entryLoop@{ entryEl ->
                        val entry = entryEl.takeIf { it.isJsonObject }?.asJsonObject ?: return@entryLoop
                        val media = entry.get("media")?.takeIf { it.isJsonObject }?.asJsonObject ?: return@entryLoop

                        val mediaId = media.get("id")?.takeIf { !it.isJsonNull }?.asInt?.toString() ?: return@entryLoop
                        val titles = media.get("title")?.takeIf { it.isJsonObject }?.asJsonObject
                        val englishTitle = titles?.get("english")?.takeIf { !it.isJsonNull }?.asString
                        val romajiTitle = titles?.get("romaji")?.takeIf { !it.isJsonNull }?.asString
                        val title = englishTitle ?: romajiTitle ?: "Anime #$mediaId"

                        val cover = media.get("coverImage")?.takeIf { it.isJsonObject }?.asJsonObject
                        val posterUrl = cover?.get("extraLarge")?.takeIf { !it.isJsonNull }?.asString
                            ?: cover?.get("large")?.takeIf { !it.isJsonNull }?.asString ?: ""

                        val backdropUrl = media.get("bannerImage")?.takeIf { !it.isJsonNull }?.asString
                            ?: posterUrl

                        val avgScore = media.get("averageScore")?.takeIf { !it.isJsonNull }?.asDouble ?: 0.0
                        val score = if (avgScore > 0) avgScore / 10.0 else entry.get("score")?.takeIf { !it.isJsonNull }?.asDouble ?: 8.0

                        val totalEpisodes = media.get("episodes")?.takeIf { !it.isJsonNull }?.asInt ?: 12
                        val progress = entry.get("progress")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                        val status = media.get("status")?.takeIf { !it.isJsonNull }?.asString ?: "Finished"

                        val genres = mutableListOf<String>()
                        media.get("genres")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { g ->
                            if (!g.isJsonNull) genres.add(g.asString)
                        }

                        val synopsis = media.get("description")?.takeIf { !it.isJsonNull }?.asString ?: ""

                        val anime = Anime(
                            id = mediaId,
                            title = title,
                            posterUrl = posterUrl,
                            backdropUrl = backdropUrl,
                            score = (score * 10).toInt() / 10.0,
                            year = 2024,
                            status = status,
                            genres = genres,
                            synopsis = synopsis,
                            totalEpisodes = totalEpisodes,
                            sourceId = "anilist",
                            hasDub = !englishTitle.isNullOrBlank(),
                            dubbedEpisodesCount = if (!englishTitle.isNullOrBlank()) totalEpisodes else 0
                        )

                        storage.addFavorite(anime)

                        // If user has watched episodes, seed the watch progress
                        if (progress > 0) {
                            val estDurationMs = 1_440_000L // 24 min
                            storage.saveWatchProgress(
                                WatchProgress(
                                    animeId = mediaId,
                                    animeTitle = title,
                                    posterUrl = posterUrl,
                                    episodeId = "$mediaId-ep-$progress",
                                    episodeNumber = progress,
                                    episodeTitle = "Episode $progress",
                                    positionMs = estDurationMs,
                                    durationMs = estDurationMs
                                )
                            )
                        }

                        syncedCount++
                    }
                }

                SyncResult(true, syncedCount, "Synced $syncedCount anime from your AniList account")
            }
        } catch (e: Exception) {
            SyncResult(false, 0, "Sync Error: ${e.message}")
        }
    }

    private val resolvedMediaIdCache = java.util.concurrent.ConcurrentHashMap<String, Int>()

    /**
     * Resolves an anime title or slug to an AniList numeric Media ID with local caching.
     */
    suspend fun resolveMediaId(titleOrId: String, token: String? = null): Int? = withContext(Dispatchers.IO) {
        titleOrId.toIntOrNull()?.let { return@withContext it }
        resolvedMediaIdCache[titleOrId]?.let { return@withContext it }

        try {
            val query = """
                query (${'$'}search: String) {
                    Media(search: ${'$'}search, type: ANIME) {
                        id
                    }
                }
            """.trimIndent()
            val variables = JsonObject().apply {
                addProperty("search", titleOrId)
            }
            val payload = JsonObject().apply {
                addProperty("query", query)
                add("variables", variables)
            }
            val reqBuilder = Request.Builder()
                .url(anilistEndpoint)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "AnimeTV/2.0 (Android TV)")

            if (!token.isNullOrBlank() && !token.startsWith("demo_")) {
                reqBuilder.header("Authorization", "Bearer $token")
            }

            httpClient.newCall(reqBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val root = JsonParser.parseString(body).asJsonObject
                val id = root.getAsJsonObject("data")?.getAsJsonObject("Media")?.get("id")?.asInt
                if (id != null) {
                    resolvedMediaIdCache[titleOrId] = id
                }
                id
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Automatic scrobbler: updates watched episode count and status on AniList.
     * Fires when an episode ends or exceeds 80% watched progress.
     */
    suspend fun scrobbleProgress(
        mediaIdStr: String,
        episodeNumber: Int,
        totalEpisodes: Int = 0,
        storage: AppStorage,
        animeTitle: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val token = storage.getAniListToken()
            if (token.isNullOrBlank()) return@withContext false
            if (token.startsWith("demo_")) return@withContext true

            val mediaId = mediaIdStr.toIntOrNull()
                ?: resolveMediaId(animeTitle ?: mediaIdStr, token)
                ?: return@withContext false
            val isFinished = totalEpisodes in 1..episodeNumber
            val status = if (isFinished) "COMPLETED" else "CURRENT"

            val mutation = """
                mutation (${'$'}mediaId: Int, ${'$'}progress: Int, ${'$'}status: MediaListStatus) {
                    SaveMediaListEntry(mediaId: ${'$'}mediaId, progress: ${'$'}progress, status: ${'$'}status) {
                        id
                        progress
                        status
                    }
                }
            """.trimIndent()

            val variables = JsonObject().apply {
                addProperty("mediaId", mediaId)
                addProperty("progress", episodeNumber)
                addProperty("status", status)
            }

            val payload = JsonObject().apply {
                addProperty("query", mutation)
                add("variables", variables)
            }

            val request = Request.Builder()
                .url(anilistEndpoint)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "AnimeTV/2.0 (Android TV)")
                .build()

            httpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Authenticate with a MyAnimeList OAuth2 Bearer access token via MAL API v2 (`/v2/users/@me`).
     */
    suspend fun loginMalWithToken(token: String, storage: AppStorage): Result<MalUser> = withContext(Dispatchers.IO) {
        try {
            val trimmed = token.trim()
            if (trimmed.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Token cannot be blank"))
            }

            val url = "https://api.myanimelist.net/v2/users/@me?fields=anime_statistics,picture"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $trimmed")
                .header("Accept", "application/json")
                .header("User-Agent", "AnimeTV/2.0 (Android TV)")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("MAL OAuth Error: HTTP ${response.code}"))
                }

                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty MAL response"))
                val root = JsonParser.parseString(body).asJsonObject
                val id = root.get("id")?.takeIf { !it.isJsonNull }?.asInt ?: 1
                val name = root.get("name")?.takeIf { !it.isJsonNull }?.asString
                    ?: return@withContext Result.failure(Exception("MAL username missing in profile"))
                val picture = root.get("picture")?.takeIf { !it.isJsonNull }?.asString ?: ""

                val stats = root.getAsJsonObject("anime_statistics")
                val totalAnime = stats?.get("num_items")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                val episodesWatched = stats?.get("num_episodes")?.takeIf { !it.isJsonNull }?.asInt ?: 0

                val malUser = MalUser(
                    id = id,
                    name = name,
                    avatarUrl = picture,
                    totalAnime = totalAnime,
                    episodesWatched = episodesWatched
                )

                storage.saveMalToken(trimmed)
                storage.saveMalUser(malUser)
                Result.success(malUser)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Link a MyAnimeList account using a public username via Jikan v4 (`/v4/users/{username}/full`).
     */
    suspend fun loginMalWithUsername(username: String, storage: AppStorage): Result<MalUser> = withContext(Dispatchers.IO) {
        try {
            val trimmed = username.trim()
            if (trimmed.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Username cannot be blank"))
            }

            val encoded = java.net.URLEncoder.encode(trimmed, "UTF-8")
            val url = "https://api.jikan.moe/v4/users/$encoded/full"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "AnimeTV/2.0 (Android TV)")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("MyAnimeList user '$trimmed' not found (HTTP ${response.code})"))
                }

                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
                val root = JsonParser.parseString(body).asJsonObject
                val dataObj = root.getAsJsonObject("data")
                    ?: return@withContext Result.failure(Exception("MAL user '$trimmed' not found"))

                val id = dataObj.get("mal_id")?.takeIf { !it.isJsonNull }?.asInt ?: 1
                val name = dataObj.get("username")?.takeIf { !it.isJsonNull }?.asString ?: trimmed
                val images = dataObj.getAsJsonObject("images")?.getAsJsonObject("webp")
                    ?: dataObj.getAsJsonObject("images")?.getAsJsonObject("jpg")
                val avatarUrl = images?.get("image_url")?.takeIf { !it.isJsonNull }?.asString ?: ""

                val animeStats = dataObj.getAsJsonObject("statistics")?.getAsJsonObject("anime")
                val totalAnime = animeStats?.get("total_entries")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                val episodesWatched = animeStats?.get("episodes_watched")?.takeIf { !it.isJsonNull }?.asInt ?: 0

                val malUser = MalUser(
                    id = id,
                    name = name,
                    avatarUrl = avatarUrl,
                    totalAnime = totalAnime,
                    episodesWatched = episodesWatched
                )

                storage.saveMalToken(null)
                storage.saveMalUser(malUser)
                Result.success(malUser)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Import and synchronize a user's anime list from MyAnimeList (MAL) via:
     * 1. Authenticated MAL API v2 (`/v2/users/@me/animelist`) if a MAL OAuth token is saved
     * 2. Public MAL JSON endpoint (`/animelist/{username}/load.json`)
     * 3. Jikan v4 user animelist & favorites endpoints (`/v4/users/{username}/animelist/all` & `/favorites`)
     */
    suspend fun syncMalWatchlist(username: String, storage: AppStorage): SyncResult = withContext(Dispatchers.IO) {
        try {
            val trimmed = username.trim()
            if (trimmed.isBlank()) {
                return@withContext SyncResult(false, 0, "Username cannot be empty")
            }

            val malToken = storage.getMalToken()
            var count = 0

            // 1. Try Authenticated MAL v2 API if Bearer token is present
            if (!malToken.isNullOrBlank()) {
                try {
                    val v2Url = "https://api.myanimelist.net/v2/users/@me/animelist?fields=list_status,num_episodes,mean,genres,synopsis&limit=100"
                    val v2Req = Request.Builder()
                        .url(v2Url)
                        .header("Authorization", "Bearer $malToken")
                        .header("Accept", "application/json")
                        .header("User-Agent", "AnimeTV/2.0 (Android TV)")
                        .build()

                    httpClient.newCall(v2Req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string().orEmpty()
                            val root = JsonParser.parseString(body).asJsonObject
                            val dataArr = root.getAsJsonArray("data") ?: JsonArray()
                            dataArr.forEach { el ->
                                val obj = el.asJsonObject
                                val node = obj.getAsJsonObject("node") ?: return@forEach
                                val listStatus = obj.getAsJsonObject("list_status")
                                val malId = node.get("id")?.asInt?.toString() ?: return@forEach
                                val title = node.get("title")?.asString ?: return@forEach
                                val pic = node.getAsJsonObject("main_picture")
                                val posterUrl = pic?.get("large")?.asString ?: pic?.get("medium")?.asString ?: ""
                                val score = listStatus?.get("score")?.takeIf { !it.isJsonNull && it.asDouble > 0 }?.asDouble
                                    ?: node.get("mean")?.takeIf { !it.isJsonNull }?.asDouble ?: 8.0
                                val totalEps = node.get("num_episodes")?.takeIf { !it.isJsonNull }?.asInt ?: 12
                                val watchedEps = listStatus?.get("num_episodes_watched")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                                val statusStr = listStatus?.get("status")?.takeIf { !it.isJsonNull }?.asString ?: "watching"

                                val anime = Anime(
                                    id = malId,
                                    title = title,
                                    posterUrl = posterUrl,
                                    backdropUrl = posterUrl,
                                    score = score,
                                    year = 2024,
                                    status = "MAL Synced",
                                    genres = listOf("MAL Import"),
                                    synopsis = node.get("synopsis")?.takeIf { !it.isJsonNull }?.asString
                                        ?: "Synced from MyAnimeList user $trimmed",
                                    totalEpisodes = totalEps,
                                    sourceId = "jikan"
                                )
                                storage.addFavorite(anime)
                                if (watchedEps > 0 || statusStr == "completed") {
                                    val isCompleted = statusStr == "completed" || (totalEps > 0 && watchedEps >= totalEps)
                                    val epNum = watchedEps.coerceAtLeast(1)
                                    storage.saveWatchProgress(
                                        WatchProgress(
                                            animeId = malId,
                                            animeTitle = title,
                                            posterUrl = posterUrl,
                                            episodeId = "$malId-ep-$epNum",
                                            episodeNumber = epNum,
                                            episodeTitle = "Episode $epNum",
                                            positionMs = if (isCompleted) 1_420_000L else 720_000L,
                                            durationMs = 1_440_000L
                                        )
                                    )
                                }
                                count++
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            val encodedUser = java.net.URLEncoder.encode(trimmed, "UTF-8")

            // 2. Try Public MAL JSON list endpoint (myanimelist.net/animelist/{user}/load.json)
            if (count == 0) {
                try {
                    val malLoadUrl = "https://myanimelist.net/animelist/$encodedUser/load.json?status=7&offset=0"
                    val malLoadReq = Request.Builder()
                        .url(malLoadUrl)
                        .header("Accept", "application/json")
                        .header("User-Agent", "AnimeTV/2.0 (Android TV)")
                        .build()

                    httpClient.newCall(malLoadReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string().orEmpty()
                            val parsed = JsonParser.parseString(body)
                            if (parsed.isJsonArray) {
                                parsed.asJsonArray.forEach { itemEl ->
                                    if (!itemEl.isJsonObject) return@forEach
                                    val item = itemEl.asJsonObject
                                    val malId = item.get("anime_id")?.takeIf { !it.isJsonNull }?.asInt?.toString() ?: return@forEach
                                    val title = item.get("anime_title_eng")?.takeIf { !it.isJsonNull && it.asString.isNotBlank() }?.asString
                                        ?: item.get("anime_title")?.takeIf { !it.isJsonNull }?.asString ?: return@forEach
                                    val rawImg = item.get("anime_image_path")?.takeIf { !it.isJsonNull }?.asString ?: ""
                                    val posterUrl = if (rawImg.startsWith("//")) "https:$rawImg" else rawImg
                                    val score = item.get("score")?.takeIf { !it.isJsonNull && it.asDouble > 0 }?.asDouble ?: 8.0
                                    val totalEps = item.get("anime_num_episodes")?.takeIf { !it.isJsonNull }?.asInt ?: 12
                                    val watchedEps = item.get("num_watched_episodes")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                                    val statusInt = item.get("status")?.takeIf { !it.isJsonNull }?.asInt ?: 1 // 1=watching, 2=completed, 6=plan

                                    val anime = Anime(
                                        id = malId,
                                        title = title,
                                        posterUrl = posterUrl,
                                        backdropUrl = posterUrl,
                                        score = score,
                                        year = 2024,
                                        status = "MAL Synced",
                                        genres = listOf("MAL Import"),
                                        synopsis = "Imported from MyAnimeList user $trimmed",
                                        totalEpisodes = totalEps,
                                        sourceId = "jikan"
                                    )
                                    storage.addFavorite(anime)
                                    if (watchedEps > 0 || statusInt == 2) {
                                        val isCompleted = statusInt == 2 || (totalEps > 0 && watchedEps >= totalEps)
                                        val epNum = watchedEps.coerceAtLeast(1)
                                        storage.saveWatchProgress(
                                            WatchProgress(
                                                animeId = malId,
                                                animeTitle = title,
                                                posterUrl = posterUrl,
                                                episodeId = "$malId-ep-$epNum",
                                                episodeNumber = epNum,
                                                episodeTitle = "Episode $epNum",
                                                positionMs = if (isCompleted) 1_420_000L else 720_000L,
                                                durationMs = 1_440_000L
                                            )
                                        )
                                    }
                                    count++
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // 3. Fallback to Jikan v4 animelist/all or user favorites
            if (count == 0) {
                val url = "https://api.jikan.moe/v4/users/$encodedUser/animelist/all"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "AnimeTV/2.0 (Android TV)")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        val root = JsonParser.parseString(body).asJsonObject
                        val dataArray = root.getAsJsonArray("data") ?: JsonArray()

                        dataArray.forEach { itemEl ->
                            val item = itemEl.asJsonObject
                            val entry = item.getAsJsonObject("entry") ?: item
                            val malId = entry.get("mal_id")?.takeIf { !it.isJsonNull }?.asInt?.toString() ?: return@forEach
                            val title = entry.get("title")?.takeIf { !it.isJsonNull }?.asString ?: return@forEach

                            val images = entry.getAsJsonObject("images")?.getAsJsonObject("webp")
                                ?: entry.getAsJsonObject("images")?.getAsJsonObject("jpg")
                            val posterUrl = images?.get("large_image_url")?.asString
                                ?: images?.get("image_url")?.asString ?: ""

                            val score = item.get("score")?.takeIf { !it.isJsonNull }?.asDouble ?: 8.0

                            val anime = Anime(
                                id = malId,
                                title = title,
                                posterUrl = posterUrl,
                                backdropUrl = posterUrl,
                                score = score,
                                year = 2024,
                                status = "MAL Synced",
                                genres = listOf("MAL Import"),
                                synopsis = "Imported from MyAnimeList user $trimmed",
                                sourceId = "jikan"
                            )

                            storage.addFavorite(anime)
                            count++
                        }
                    } else {
                        // Also check Jikan v4 user favorites (/v4/users/{username}/favorites)
                        val favUrl = "https://api.jikan.moe/v4/users/$encodedUser/favorites"
                        val favReq = Request.Builder()
                            .url(favUrl)
                            .header("User-Agent", "AnimeTV/2.0 (Android TV)")
                            .build()
                        httpClient.newCall(favReq).execute().use { favResp ->
                            if (!favResp.isSuccessful) {
                                return@withContext SyncResult(false, 0, "MAL User not found or Jikan rate limit (HTTP ${response.code})")
                            }
                            val favBody = favResp.body?.string().orEmpty()
                            val favRoot = JsonParser.parseString(favBody).asJsonObject
                            val animeFavs = favRoot.getAsJsonObject("data")?.getAsJsonArray("anime") ?: JsonArray()
                            animeFavs.forEach { favEl ->
                                val entry = favEl.asJsonObject
                                val malId = entry.get("mal_id")?.takeIf { !it.isJsonNull }?.asInt?.toString() ?: return@forEach
                                val title = entry.get("title")?.takeIf { !it.isJsonNull }?.asString ?: return@forEach
                                val images = entry.getAsJsonObject("images")?.getAsJsonObject("webp")
                                    ?: entry.getAsJsonObject("images")?.getAsJsonObject("jpg")
                                val posterUrl = images?.get("large_image_url")?.asString
                                    ?: images?.get("image_url")?.asString ?: ""
                                val anime = Anime(
                                    id = malId,
                                    title = title,
                                    posterUrl = posterUrl,
                                    backdropUrl = posterUrl,
                                    score = 8.5,
                                    year = entry.get("start_year")?.takeIf { !it.isJsonNull }?.asInt ?: 2024,
                                    status = "MAL Synced",
                                    genres = listOf("MAL Favorite"),
                                    synopsis = "Imported from MyAnimeList user $trimmed",
                                    sourceId = "jikan"
                                )
                                storage.addFavorite(anime)
                                count++
                            }
                        }
                    }
                }
            }

            storage.saveMalUsername(trimmed)
            if (storage.getMalUser() == null) {
                storage.saveMalUser(
                    MalUser(
                        id = 1,
                        name = trimmed,
                        totalAnime = count,
                        episodesWatched = count * 12
                    )
                )
            }
            SyncResult(true, count, "Successfully synced $count anime from MyAnimeList user '$trimmed'")
        } catch (e: Exception) {
            SyncResult(false, 0, "MAL Sync Error: ${e.message}")
        }
    }

    /**
     * Demo sync provider for instant TV testing and verification.
     */
    fun performDemoSync(storage: AppStorage): SyncResult {
        val demoUser = AniListUser(
            id = 998822,
            name = "AnimeFan_TV",
            avatarUrl = "https://s4.anilist.co/file/anilistcdn/user/avatar/large/default.png",
            totalAnime = 24,
            episodesWatched = 384
        )
        storage.saveAniListUser(demoUser)
        storage.saveAniListToken("demo_token_anilist_tv")

        // Seed 4 popular titles into favorites
        val titles = listOf(
            Anime(
                id = "16498",
                title = "Attack on Titan Final Season",
                posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx131681-m8n90184b25.jpg",
                backdropUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/banner/131681-229.jpg",
                score = 8.8,
                year = 2023,
                status = "Finished",
                genres = listOf("Action", "Drama", "Fantasy"),
                synopsis = "Gabi Braun and Falco Grice have been training their entire lives to inherit one of the seven Titans under Marley's control.",
                totalEpisodes = 16
            ),
            Anime(
                id = "21",
                title = "One Piece",
                posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx21-YCDuy1e51zZ4.jpg",
                backdropUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/banner/21-1YmC2.jpg",
                score = 8.7,
                year = 1999,
                status = "Releasing",
                genres = listOf("Action", "Adventure", "Comedy"),
                synopsis = "Gol D. Roger was known as the 'Pirate King,' the strongest and most infamous being to have sailed the Grand Line.",
                totalEpisodes = 1115
            ),
            Anime(
                id = "101922",
                title = "Demon Slayer: Kimetsu no Yaiba",
                posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx101922-PEn1CTDYxTr6.jpg",
                backdropUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/banner/101922-mrmD0h30e9f1.jpg",
                score = 8.4,
                year = 2019,
                status = "Finished",
                genres = listOf("Action", "Fantasy", "Supernatural"),
                synopsis = "Ever since the death of his father, the burden of supporting the family has fallen upon Tanjirou Kamado's shoulders.",
                totalEpisodes = 26
            ),
            Anime(
                id = "153518",
                title = "Jujutsu Kaisen Season 2",
                posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx153518-l5wU3M77c385.jpg",
                backdropUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/banner/153518-eN2.jpg",
                score = 8.9,
                year = 2023,
                status = "Finished",
                genres = listOf("Action", "Fantasy", "Supernatural"),
                synopsis = "The year is 2006, and the halls of Jujutsu High echo with the relentless bickering and debate between two formidable peers.",
                totalEpisodes = 23
            )
        )

        titles.forEach { anime ->
            storage.addFavorite(anime)
            storage.saveWatchProgress(
                WatchProgress(
                    animeId = anime.id,
                    animeTitle = anime.title,
                    posterUrl = anime.posterUrl,
                    episodeId = "${anime.id}-ep-3",
                    episodeNumber = 3,
                    episodeTitle = "Episode 3",
                    positionMs = 720_000L,
                    durationMs = 1_440_000L
                )
            )
        }

        return SyncResult(true, titles.size, "Connected as ${demoUser.name} and synced ${titles.size} anime")
    }
}
