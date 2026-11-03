package com.darknet.animetv.data.source

import com.darknet.animetv.data.model.AiringScheduleItem
import com.darknet.animetv.data.model.Anime
import com.darknet.animetv.data.model.AnimeDetail
import com.darknet.animetv.data.model.CharacterCast
import com.darknet.animetv.data.model.Episode
import com.darknet.animetv.data.model.VideoSource
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class AniListCatalogSource(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) : AnimeSource {

    override val sourceId: String = "anilist"
    override val sourceName: String = "AniList (HD Banners & Metadata)"
    override val baseUrl: String = "https://graphql.anilist.co"
    override var isEnabled: Boolean = true

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val mediaFragment = """
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
        averageScore
        seasonYear
        status
        genres
        description(asHtml: false)
        episodes
        nextAiringEpisode {
            episode
            timeUntilAiring
        }
    """.trimIndent()

    override suspend fun getTrending(): List<Anime> = withContext(Dispatchers.IO) {
        val query = """
            query {
                Page(page: 1, perPage: 20) {
                    media(sort: [TRENDING_DESC, POPULARITY_DESC], status_in: [RELEASING, FINISHED], type: ANIME, isAdult: false) {
                        $mediaFragment
                    }
                }
            }
        """.trimIndent()
        executeQuery(query)
    }

    override suspend fun getPopular(): List<Anime> = withContext(Dispatchers.IO) {
        val query = """
            query {
                Page(page: 1, perPage: 20) {
                    media(sort: [POPULARITY_DESC], status_in: [RELEASING, FINISHED], type: ANIME, isAdult: false) {
                        $mediaFragment
                    }
                }
            }
        """.trimIndent()
        executeQuery(query)
    }

    override suspend fun getRecentEpisodes(): List<Anime> = withContext(Dispatchers.IO) {
        // 1. Query the most recently aired episodes (notYetAired: false, sorted newest first)
        try {
            val recentAiringGql = """
                query {
                    Page(page: 1, perPage: 40) {
                        airingSchedules(
                            notYetAired: false,
                            sort: [TIME_DESC]
                        ) {
                            episode
                            airingAt
                            media {
                                isAdult
                                type
                                $mediaFragment
                            }
                        }
                    }
                }
            """.trimIndent()

            val payload = JsonObject().apply { addProperty("query", recentAiringGql) }
            val req = Request.Builder()
                .url(baseUrl)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .header("User-Agent", "TanukiTV/2.0 (Android TV)")
                .build()

            val airedList = mutableListOf<Anime>()
            val seenIds = mutableSetOf<String>()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrBlank()) {
                        val root = JsonParser.parseString(body).asJsonObject
                        val pageObj = root.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
                            ?.get("Page")?.takeIf { it.isJsonObject }?.asJsonObject
                        val schedules = pageObj?.get("airingSchedules")?.takeIf { it.isJsonArray }?.asJsonArray
                        schedules?.forEach { el ->
                            if (el.isJsonObject) {
                                val schedObj = el.asJsonObject
                                val epNum = schedObj.get("episode")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                                val mediaObj = schedObj.get("media")?.takeIf { it.isJsonObject }?.asJsonObject
                                val isAdult = mediaObj?.get("isAdult")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
                                val mediaType = mediaObj?.get("type")?.takeIf { !it.isJsonNull }?.asString ?: "ANIME"
                                if (mediaObj != null && !isAdult && mediaType == "ANIME") {
                                    val parsed = parseMediaNode(mediaObj).let { anime ->
                                        if (epNum > 0) anime.copy(latestEpisode = epNum) else anime
                                    }
                                    if (parsed.posterUrl.isNotBlank() && seenIds.add(parsed.id)) {
                                        airedList.add(parsed)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (airedList.isNotEmpty()) {
                return@withContext airedList.take(24)
            }
        } catch (_: Exception) {}

        // 2. Fallback to recently updated releasing anime
        val query = """
            query {
                Page(page: 1, perPage: 20) {
                    media(sort: [UPDATED_TIME_DESC], status: RELEASING, type: ANIME, isAdult: false) {
                        $mediaFragment
                    }
                }
            }
        """.trimIndent()
        executeQuery(query)
    }

    override suspend fun search(query: String): List<Anime> = withContext(Dispatchers.IO) {
        val escapedQuery = query.replace("\"", "\\\"")
        val gql = """
            query {
                Page(page: 1, perPage: 24) {
                    media(search: "$escapedQuery", type: ANIME, isAdult: false, sort: [SEARCH_MATCH]) {
                        $mediaFragment
                    }
                }
            }
        """.trimIndent()
        executeQuery(gql)
    }

    override suspend fun getAnimeDetail(animeId: String): AnimeDetail? = withContext(Dispatchers.IO) {
        try {
            val cleanId = animeId.removePrefix("anilist-").toIntOrNull() ?: return@withContext null
            val gql = """
                query {
                    Media(id: $cleanId, type: ANIME) {
                        $mediaFragment
                        nextAiringEpisode {
                            episode
                            timeUntilAiring
                        }
                        trailer {
                            id
                            site
                        }
                        studios(isMain: true) {
                            nodes {
                                name
                            }
                        }
                        streamingEpisodes {
                            title
                            thumbnail
                            url
                        }
                    }
                }
            """.trimIndent()

            val payload = JsonObject().apply {
                addProperty("query", gql)
            }
            val req = Request.Builder()
                .url(baseUrl)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .header("User-Agent", "TanukiTV/2.0 (Android TV)")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val root = JsonParser.parseString(body).asJsonObject
                val media = root.getAsJsonObject("data")?.getAsJsonObject("Media") ?: return@withContext null

                val anime = parseMediaNode(media)
                val trailerObj = media.getAsJsonObject("trailer")
                val trailerSite = trailerObj?.get("site")?.asString
                val trailerId = trailerObj?.get("id")?.asString
                val trailerUrl = if (trailerSite == "youtube" && trailerId != null) {
                    "https://www.youtube.com/embed/$trailerId"
                } else null

                val studio = media.getAsJsonObject("studios")
                    ?.getAsJsonArray("nodes")
                    ?.firstOrNull()?.asJsonObject
                    ?.get("name")?.asString ?: "Unknown"

                val streamingArray = media.getAsJsonArray("streamingEpisodes")
                val streamingMap = mutableMapOf<Int, Pair<String, String>>()
                var autoIndex = 1
                streamingArray?.forEach { el ->
                    if (el.isJsonObject) {
                        val obj = el.asJsonObject
                        val rawTitle = obj.get("title")?.takeIf { !it.isJsonNull }?.asString ?: ""
                        val thumb = obj.get("thumbnail")?.takeIf { !it.isJsonNull }?.asString ?: ""
                        val match = Regex("""(?:Episode|Ep\.?)\s*(\d+)""", RegexOption.IGNORE_CASE).find(rawTitle)
                        val epNum = match?.groupValues?.get(1)?.toIntOrNull() ?: autoIndex
                        streamingMap[epNum] = Pair(rawTitle, thumb)
                        autoIndex = epNum + 1
                    }
                }

                val defaultThumb = anime.backdropUrl.ifBlank { anime.posterUrl }
                val totalCount = when {
                    anime.totalEpisodes in 1..2000 -> anime.totalEpisodes
                    streamingMap.isNotEmpty() -> streamingMap.keys.maxOrNull() ?: 12
                    else -> 12
                }

                val episodes = mutableListOf<Episode>()
                for (i in 1..totalCount) {
                    val info = streamingMap[i]
                    val epTitle = info?.first?.ifBlank { null } ?: "Episode $i"
                    val epThumb = info?.second?.ifBlank { null } ?: defaultThumb
                    episodes.add(
                        Episode(
                            id = "al-$cleanId-ep-$i",
                            animeId = anime.id,
                            number = i,
                            title = epTitle,
                            thumbnail = epThumb
                        )
                    )
                }

                AnimeDetail(
                    anime = anime,
                    episodes = episodes,
                    trailerUrl = trailerUrl,
                    studio = studio
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun getEpisodeStreams(animeId: String, episodeId: String): List<VideoSource> {
        return emptyList()
    }

    override suspend fun getDubbedAnime(): List<Anime> = withContext(Dispatchers.IO) {
        val query = """
            query {
                Page(page: 1, perPage: 24) {
                    media(sort: [POPULARITY_DESC], type: ANIME, isAdult: false, countryOfOrigin: "JP") {
                        $mediaFragment
                    }
                }
            }
        """.trimIndent()
        executeQuery(query).map { it.copy(hasDub = true, dubbedEpisodesCount = it.totalEpisodes) }
    }

    suspend fun getAiringSchedule(dayOfWeek: String? = null): List<AiringScheduleItem> = withContext(Dispatchers.IO) {
        val nowSec = System.currentTimeMillis() / 1000L
        val airingAtGreater = (nowSec - 86400L).toInt()
        val airingAtLesser = (nowSec + 7 * 86400L).toInt()

        val gql = """
            query (${'$'}airingAtGreater: Int, ${'$'}airingAtLesser: Int) {
                Page(page: 1, perPage: 50) {
                    airingSchedules(
                        airingAt_greater: ${'$'}airingAtGreater,
                        airingAt_lesser: ${'$'}airingAtLesser,
                        sort: [TIME]
                    ) {
                        id
                        airingAt
                        timeUntilAiring
                        episode
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
                            format
                            genres
                            averageScore
                        }
                    }
                }
            }
        """.trimIndent()

        val list = mutableListOf<AiringScheduleItem>()
        try {
            val variables = JsonObject().apply {
                addProperty("airingAtGreater", airingAtGreater)
                addProperty("airingAtLesser", airingAtLesser)
            }
            val payload = JsonObject().apply {
                addProperty("query", gql)
                add("variables", variables)
            }
            val req = Request.Builder()
                .url(baseUrl)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .header("User-Agent", "TanukiTV/2.0 (Android TV)")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val body = resp.body?.string() ?: return@withContext emptyList()
                val root = JsonParser.parseString(body).asJsonObject
                val schedules = root.getAsJsonObject("data")
                    ?.getAsJsonObject("Page")
                    ?.getAsJsonArray("airingSchedules") ?: return@withContext emptyList()

                val dayNames = arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
                val cal = java.util.Calendar.getInstance()

                for (el in schedules) {
                    if (!el.isJsonObject) continue
                    val obj = el.asJsonObject
                    val schedId = obj.get("id")?.asInt?.toString() ?: continue
                    val airingAt = obj.get("airingAt")?.asLong ?: 0L
                    val timeUntilAiring = obj.get("timeUntilAiring")?.asLong ?: 0L
                    val episode = obj.get("episode")?.asInt ?: 1

                    val media = obj.getAsJsonObject("media") ?: continue
                    val mediaId = media.get("id")?.asInt ?: 0
                    val titleObj = media.getAsJsonObject("title")
                    val englishTitle = titleObj?.get("english")?.takeIf { !it.isJsonNull }?.asString
                    val romajiTitle = titleObj?.get("romaji")?.takeIf { !it.isJsonNull }?.asString ?: ""
                    val nativeTitle = titleObj?.get("native")?.takeIf { !it.isJsonNull }?.asString ?: ""
                    val title = englishTitle ?: romajiTitle.ifEmpty { nativeTitle.ifEmpty { "Anime #$mediaId" } }

                    val coverObj = media.getAsJsonObject("coverImage")
                    val poster = coverObj?.get("extraLarge")?.takeIf { !it.isJsonNull }?.asString
                        ?: coverObj?.get("large")?.takeIf { !it.isJsonNull }?.asString ?: ""
                    val banner = media.get("bannerImage")?.takeIf { !it.isJsonNull }?.asString ?: poster

                    val format = media.get("format")?.takeIf { !it.isJsonNull }?.asString ?: "TV"
                    val rawScore = media.get("averageScore")?.takeIf { !it.isJsonNull }?.asDouble ?: 0.0
                    val score = if (rawScore > 0.0) rawScore / 10.0 else 0.0

                    val genres = mutableListOf<String>()
                    media.getAsJsonArray("genres")?.forEach {
                        if (!it.isJsonNull) genres.add(it.asString)
                    }

                    cal.timeInMillis = airingAt * 1000L
                    val dayOfWeekStr = dayNames[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1]

                    list.add(
                        AiringScheduleItem(
                            id = "anilist-sched-$schedId",
                            animeId = "anilist-$mediaId",
                            title = title,
                            romajiTitle = romajiTitle,
                            posterUrl = poster,
                            bannerUrl = banner,
                            episodeNumber = episode,
                            airingAt = airingAt,
                            timeUntilAiringSeconds = timeUntilAiring,
                            dayOfWeek = dayOfWeekStr,
                            genres = genres,
                            score = score,
                            format = format
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (dayOfWeek.isNullOrBlank() || dayOfWeek.equals("All", ignoreCase = true)) {
            list
        } else {
            list.filter { it.dayOfWeek.equals(dayOfWeek, ignoreCase = true) }
        }
    }

    suspend fun getCharacterCast(animeId: String, animeTitle: String = ""): List<CharacterCast> = withContext(Dispatchers.IO) {
        val cleanId = animeId.removePrefix("anilist-").removePrefix("al-").toIntOrNull()
        val queryTitle = animeTitle.ifBlank { null }
        if (cleanId == null && queryTitle.isNullOrBlank()) {
            return@withContext emptyList()
        }

        val gql = """
            query (${'$'}id: Int, ${'$'}search: String) {
                Media(id: ${'$'}id, search: ${'$'}search, type: ANIME) {
                    id
                    characters(sort: [ROLE, RELEVANCE], perPage: 25) {
                        edges {
                            role
                            node {
                                id
                                name {
                                    full
                                    native
                                }
                                image {
                                    large
                                    medium
                                }
                            }
                            voiceActors(sort: [RELEVANCE]) {
                                id
                                name {
                                    full
                                    native
                                }
                                languageV2
                                image {
                                    large
                                    medium
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        val list = mutableListOf<CharacterCast>()
        try {
            val variables = JsonObject().apply {
                if (cleanId != null && cleanId > 0) {
                    addProperty("id", cleanId)
                } else if (!queryTitle.isNullOrBlank()) {
                    addProperty("search", queryTitle)
                }
            }
            val payload = JsonObject().apply {
                addProperty("query", gql)
                add("variables", variables)
            }
            val req = Request.Builder()
                .url(baseUrl)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .header("User-Agent", "TanukiTV/2.0 (Android TV)")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val body = resp.body?.string() ?: return@withContext emptyList()
                val root = JsonParser.parseString(body).asJsonObject
                val media = root.getAsJsonObject("data")?.getAsJsonObject("Media") ?: return@withContext emptyList()
                val charEdges = media.getAsJsonObject("characters")?.getAsJsonArray("edges") ?: return@withContext emptyList()

                for (edgeEl in charEdges) {
                    if (!edgeEl.isJsonObject) continue
                    val edge = edgeEl.asJsonObject
                    val role = edge.get("role")?.takeIf { !it.isJsonNull }?.asString ?: "SUPPORTING"
                    val charNode = edge.getAsJsonObject("node") ?: continue
                    val charId = charNode.get("id")?.asInt ?: 0
                    val charName = charNode.getAsJsonObject("name")?.get("full")?.takeIf { !it.isJsonNull }?.asString ?: "Character"
                    val charImg = charNode.getAsJsonObject("image")?.get("large")?.takeIf { !it.isJsonNull }?.asString
                        ?: charNode.getAsJsonObject("image")?.get("medium")?.takeIf { !it.isJsonNull }?.asString ?: ""

                    val vaArray = edge.getAsJsonArray("voiceActors")
                    var vaName = ""
                    var vaLang = "Japanese"
                    var vaImg = ""

                    var jaVa: JsonObject? = null
                    var enVa: JsonObject? = null
                    if (vaArray != null) {
                        for (vaEl in vaArray) {
                            if (!vaEl.isJsonObject) continue
                            val vaObj = vaEl.asJsonObject
                            val lang = vaObj.get("languageV2")?.takeIf { !it.isJsonNull }?.asString ?: ""
                            if (lang.equals("Japanese", ignoreCase = true) && jaVa == null) {
                                jaVa = vaObj
                            } else if (lang.equals("English", ignoreCase = true) && enVa == null) {
                                enVa = vaObj
                            }
                        }
                    }
                    val selectedVa = jaVa ?: enVa ?: vaArray?.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
                    if (selectedVa != null) {
                        vaName = selectedVa.getAsJsonObject("name")?.get("full")?.takeIf { !it.isJsonNull }?.asString ?: ""
                        vaLang = selectedVa.get("languageV2")?.takeIf { !it.isJsonNull }?.asString ?: "Japanese"
                        vaImg = selectedVa.getAsJsonObject("image")?.get("large")?.takeIf { !it.isJsonNull }?.asString
                            ?: selectedVa.getAsJsonObject("image")?.get("medium")?.takeIf { !it.isJsonNull }?.asString ?: ""
                    }

                    val normalizedCharName = when {
                        charName.contains("Tanjirou", ignoreCase = true) -> charName.replace("Tanjirou", "Tanjiro", ignoreCase = true)
                        charName.contains("Jin-U Seong", ignoreCase = true) || charName.contains("Jinwoo Sung", ignoreCase = true) -> "Sung Jin-woo"
                        else -> charName
                    }
                    val normalizedVaName = when {
                        vaName.contains("Yuuki Kaji", ignoreCase = true) -> vaName.replace("Yuuki Kaji", "Yūki Kaji", ignoreCase = true)
                        vaName.contains("Akari Kitou", ignoreCase = true) -> vaName.replace("Akari Kitou", "Akari Kitō", ignoreCase = true)
                        else -> vaName
                    }

                    list.add(
                        CharacterCast(
                            id = "al-char-$charId",
                            characterName = normalizedCharName,
                            characterRole = role,
                            characterImageUrl = charImg,
                            voiceActorName = normalizedVaName,
                            voiceActorLanguage = vaLang,
                            voiceActorImageUrl = vaImg
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    override suspend fun testConnection(): SourceHealthResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val pingGql = """query { Page(page: 1, perPage: 1) { media(type: ANIME) { id } } }"""
            val payload = JsonObject().apply { addProperty("query", pingGql) }
            val req = Request.Builder()
                .url(baseUrl)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .header("User-Agent", "TanukiTV/2.0 (Android TV)")
                .build()

            client.newCall(req).execute().use { resp ->
                val duration = System.currentTimeMillis() - start
                SourceHealthResult(
                    sourceId = sourceId,
                    isAvailable = resp.isSuccessful,
                    latencyMs = duration,
                    message = "GraphQL API HTTP ${resp.code} (${duration}ms)"
                )
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - start
            SourceHealthResult(
                sourceId = sourceId,
                isAvailable = false,
                latencyMs = duration,
                message = e.localizedMessage ?: "Connection Timeout"
            )
        }
    }

    private fun executeQuery(gql: String): List<Anime> {
        val list = mutableListOf<Anime>()
        try {
            val payload = JsonObject().apply {
                addProperty("query", gql)
            }
            val req = Request.Builder()
                .url(baseUrl)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .header("User-Agent", "TanukiTV/2.0 (Android TV)")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val body = resp.body?.string() ?: return emptyList()
                val root = JsonParser.parseString(body).asJsonObject
                val mediaArray = root.getAsJsonObject("data")
                    ?.getAsJsonObject("Page")
                    ?.getAsJsonArray("media") ?: return emptyList()

                for (i in 0 until mediaArray.size()) {
                    val mediaObj = mediaArray.get(i).asJsonObject
                    list.add(parseMediaNode(mediaObj))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    internal fun parseMediaNode(media: JsonObject): Anime {
        val id = media.get("id")?.asInt ?: 0
        val titleObj = media.getAsJsonObject("title")
        val englishTitle = titleObj?.get("english")?.let { if (!it.isJsonNull) it.asString else null }
        val romajiTitle = titleObj?.get("romaji")?.let { if (!it.isJsonNull) it.asString else null }
        val nativeTitle = titleObj?.get("native")?.let { if (!it.isJsonNull) it.asString else null }
        val title = englishTitle ?: romajiTitle ?: nativeTitle ?: "Untitled"

        val coverObj = media.getAsJsonObject("coverImage")
        val poster = coverObj?.get("extraLarge")?.let { if (!it.isJsonNull) it.asString else null }
            ?: coverObj?.get("large")?.let { if (!it.isJsonNull) it.asString else null }
            ?: ""

        // Crucial 16:9 banner support for TV UI
        val banner = media.get("bannerImage")?.let { if (!it.isJsonNull) it.asString else null }
        val backdrop = if (!banner.isNullOrBlank()) banner else poster

        val rawScore = media.get("averageScore")?.let { if (!it.isJsonNull) it.asDouble else 0.0 } ?: 0.0
        val normalizedScore = if (rawScore > 0.0) rawScore / 10.0 else 0.0

        val year = media.get("seasonYear")?.let { if (!it.isJsonNull) it.asInt else 0 } ?: 0
        val status = media.get("status")?.let { if (!it.isJsonNull) it.asString else "Ongoing" } ?: "Ongoing"
        val synopsis = media.get("description")?.let { if (!it.isJsonNull) it.asString else "" } ?: ""
        val totalEp = media.get("episodes")?.let { if (!it.isJsonNull) it.asInt else 0 } ?: 0
        val nextAiringObj = media.get("nextAiringEpisode")?.takeIf { it.isJsonObject }?.asJsonObject
        val nextAiringEp = nextAiringObj?.get("episode")?.takeIf { !it.isJsonNull }?.asInt ?: 0
        val latestEp = when {
            nextAiringEp > 1 -> nextAiringEp - 1
            totalEp > 0 -> totalEp
            else -> 0
        }

        val genres = mutableListOf<String>()
        media.getAsJsonArray("genres")?.forEach {
            if (!it.isJsonNull) genres.add(it.asString)
        }

        return Anime(
            id = "anilist-$id",
            title = title,
            posterUrl = poster,
            backdropUrl = backdrop,
            score = normalizedScore,
            year = year,
            status = status,
            genres = genres,
            synopsis = synopsis,
            totalEpisodes = totalEp,
            sourceId = sourceId,
            hasDub = !englishTitle.isNullOrBlank(),
            dubbedEpisodesCount = if (!englishTitle.isNullOrBlank()) totalEp else 0,
            latestEpisode = latestEp
        )
    }
}
