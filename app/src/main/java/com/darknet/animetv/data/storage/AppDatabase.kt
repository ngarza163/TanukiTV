package com.darknet.animetv.data.storage

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.darknet.animetv.data.model.AniListUser
import com.darknet.animetv.data.model.Anime
import com.darknet.animetv.data.model.CustomSource
import com.darknet.animetv.data.model.MalUser
import com.darknet.animetv.data.model.UserSettings
import com.darknet.animetv.data.model.WatchProgress
import java.io.Closeable
import org.json.JSONArray

class AppDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, "animetv_v2.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS watch_history (
                anime_id TEXT PRIMARY KEY,
                anime_title TEXT,
                poster_url TEXT,
                episode_id TEXT,
                episode_number INTEGER,
                episode_title TEXT,
                position_ms INTEGER,
                duration_ms INTEGER,
                updated_at INTEGER
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS favorites (
                anime_id TEXT PRIMARY KEY,
                title TEXT,
                poster_url TEXT,
                backdrop_url TEXT,
                score REAL,
                year INTEGER,
                status TEXT,
                genres TEXT,
                synopsis TEXT,
                total_episodes INTEGER DEFAULT 0,
                has_dub INTEGER DEFAULT 0,
                dubbed_episodes_count INTEGER DEFAULT 0,
                added_at INTEGER
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS sources (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT,
                base_url TEXT,
                anime_list_selector TEXT,
                episode_selector TEXT,
                stream_url_selector TEXT,
                is_enabled INTEGER DEFAULT 1,
                source_type TEXT,
                priority_order INTEGER DEFAULT 0
            )
        """)

        // Seed default sources
        seedDefaultSources(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS sources")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS sources (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT,
                base_url TEXT,
                anime_list_selector TEXT,
                episode_selector TEXT,
                stream_url_selector TEXT,
                is_enabled INTEGER DEFAULT 1,
                source_type TEXT,
                priority_order INTEGER DEFAULT 0
            )
        """)
        seedDefaultSources(db)
    }

    private fun seedDefaultSources(db: SQLiteDatabase) {
        val defaultSources = listOf(
            CustomSource(
                name = "Aniwatch (aniwatchtv.ro)",
                baseUrl = "https://aniwatchtv.ro/",
                animeListSelector = "div.flw-item",
                episodeSelector = "div.ss-list a.ssl-item",
                streamUrlSelector = "div.ps_-block a.item",
                isEnabled = true,
                sourceType = "ANIWATCH"
            ),
            CustomSource(
                name = "Anikoto (anikototv.to)",
                baseUrl = "https://anikototv.to/home",
                animeListSelector = "div.item, article.anime-card",
                episodeSelector = "ul.episodes li a",
                streamUrlSelector = "div.server-item, iframe",
                isEnabled = true,
                sourceType = "ANIKOTO"
            ),
            CustomSource(
                name = "Miruro (miruro.cx)",
                baseUrl = "https://www.miruro.cx/",
                animeListSelector = "data.trending",
                episodeSelector = "data.episodes",
                streamUrlSelector = "data.sources",
                isEnabled = true,
                sourceType = "MIRURO"
            ),
            CustomSource(
                name = "ReAnime (reanime.to)",
                baseUrl = "https://reanime.to/home",
                animeListSelector = "div.anime-card, a[href*='/anime/']",
                episodeSelector = "div.episode-list a",
                streamUrlSelector = "iframe, video",
                isEnabled = true,
                sourceType = "REANIME"
            ),
            CustomSource(
                name = "KAA / KickAssAnime (kaa.lt)",
                baseUrl = "https://kaa.lt/",
                animeListSelector = "api/show",
                episodeSelector = "api/show/episodes",
                streamUrlSelector = "api/show/episode",
                isEnabled = true,
                sourceType = "KAA"
            ),
            CustomSource(
                name = "WCO (wco.tv)",
                baseUrl = "https://www.wco.tv/",
                animeListSelector = "div.recent-release a, ul.items li a",
                episodeSelector = "div.cat-eps a, ul.episodes li a",
                streamUrlSelector = "iframe, video source",
                isEnabled = true,
                sourceType = "WCO"
            ),
            CustomSource(
                name = "AnimeFLV (Official Scraper)",
                baseUrl = "https://www3.animeflv.net",
                animeListSelector = "ul.ListAnimes li.Anime",
                episodeSelector = "ul.ListCaps li.fa-play-circle",
                streamUrlSelector = "table.RTBL tbody tr",
                isEnabled = true,
                sourceType = "ANIMEFLV"
            ),
            CustomSource(
                name = "AniList GraphQL Catalog",
                baseUrl = "https://graphql.anilist.co",
                animeListSelector = "Page.media",
                episodeSelector = "streamingEpisodes",
                streamUrlSelector = "url",
                isEnabled = true,
                sourceType = "ANILIST"
            ),
            CustomSource(
                name = "AniList / Jikan Catalog",
                baseUrl = "https://api.jikan.moe/v4",
                animeListSelector = "data",
                episodeSelector = "episodes",
                streamUrlSelector = "url",
                isEnabled = true,
                sourceType = "JIKAN"
            ),
            CustomSource(
                name = "Gogoanime / Consumet Stream",
                baseUrl = "https://api.consumet.org/anime/gogoanime",
                animeListSelector = "results",
                episodeSelector = "episodes",
                streamUrlSelector = "sources",
                isEnabled = true,
                sourceType = "CONSUMET"
            )
        )

        defaultSources.forEachIndexed { index, source ->
            val values = ContentValues().apply {
                put("name", source.name)
                put("base_url", source.baseUrl)
                put("anime_list_selector", source.animeListSelector)
                put("episode_selector", source.episodeSelector)
                put("stream_url_selector", source.streamUrlSelector)
                put("is_enabled", if (source.isEnabled) 1 else 0)
                put("source_type", source.sourceType)
                put("priority_order", index)
            }
            db.insert("sources", null, values)
        }
    }
}

class AppStorage(context: Context) : Closeable {
    private val dbHelper = AppDatabaseHelper(context)
    private val prefs: SharedPreferences = context.getSharedPreferences("animetv_settings", Context.MODE_PRIVATE)

    override fun close() {
        dbHelper.close()
    }

    // --- User Preferences & Settings ---
    fun getUserSettings(): UserSettings {
        return UserSettings(
            defaultQuality = prefs.getString("pref_quality", "1080p") ?: "1080p",
            preferredAudio = prefs.getString("pref_audio", "SUB") ?: "SUB",
            preferredSubLanguage = prefs.getString("pref_sub_lang", "English") ?: "English",
            autoPlayNextEpisode = prefs.getBoolean("pref_autoplay", true),
            autoSkipIntro = prefs.getBoolean("pref_skip_intro", true),
            introSkipSeconds = prefs.getInt("pref_intro_skip_sec", 85),
            seekStepSeconds = prefs.getInt("pref_seek_step", 10),
            focusRingColor = prefs.getString("pref_focus_color", "White") ?: "White",
            focusScale = prefs.getFloat("pref_focus_scale", 1.08f),
            uiSoundEffects = prefs.getBoolean("pref_ui_sound_effects", true),
            hardwareAcceleration = prefs.getBoolean("pref_hw_accel", true),
            primaryCatalog = prefs.getString("pref_primary_catalog", "AniList") ?: "AniList",
            autoFailover = prefs.getBoolean("pref_auto_failover", true),
            autoScrobble = prefs.getBoolean("pref_auto_scrobble", true),
            syncProvider = prefs.getString("pref_sync_provider", "AniList") ?: "AniList",
            subtitleFontSize = prefs.getString("pref_sub_font_size", "24sp") ?: "24sp",
            subtitleTextColor = prefs.getString("pref_sub_text_color", "White") ?: "White",
            subtitleBackground = prefs.getString("pref_sub_bg", "Translucent") ?: "Translucent",
            subtitleOffsetMs = prefs.getLong("pref_sub_offset_ms", 0L),
            useExternalPlayer = prefs.getBoolean("pref_use_external_player", false),
            audioPreset = prefs.getString("pref_audio_preset", "Standard") ?: "Standard",
            audioPassthrough = prefs.getBoolean("pref_audio_passthrough", false),
            bufferMode = prefs.getString("pref_buffer_mode", "Balanced (50s)") ?: "Balanced (50s)",
            maxBitrateCap = prefs.getString("pref_max_bitrate", "Unlimited") ?: "Unlimited"
        )
    }

    fun saveUserSettings(settings: UserSettings) {
        prefs.edit()
            .putString("pref_quality", settings.defaultQuality)
            .putString("pref_audio", settings.preferredAudio)
            .putString("pref_sub_lang", settings.preferredSubLanguage)
            .putBoolean("pref_autoplay", settings.autoPlayNextEpisode)
            .putBoolean("pref_skip_intro", settings.autoSkipIntro)
            .putInt("pref_intro_skip_sec", settings.introSkipSeconds)
            .putInt("pref_seek_step", settings.seekStepSeconds)
            .putString("pref_focus_color", settings.focusRingColor)
            .putFloat("pref_focus_scale", settings.focusScale)
            .putBoolean("pref_ui_sound_effects", settings.uiSoundEffects)
            .putBoolean("pref_hw_accel", settings.hardwareAcceleration)
            .putString("pref_primary_catalog", settings.primaryCatalog)
            .putBoolean("pref_auto_failover", settings.autoFailover)
            .putBoolean("pref_auto_scrobble", settings.autoScrobble)
            .putString("pref_sync_provider", settings.syncProvider)
            .putString("pref_sub_font_size", settings.subtitleFontSize)
            .putString("pref_sub_text_color", settings.subtitleTextColor)
            .putString("pref_sub_bg", settings.subtitleBackground)
            .putLong("pref_sub_offset_ms", settings.subtitleOffsetMs)
            .putBoolean("pref_use_external_player", settings.useExternalPlayer)
            .putString("pref_audio_preset", settings.audioPreset)
            .putBoolean("pref_audio_passthrough", settings.audioPassthrough)
            .putString("pref_buffer_mode", settings.bufferMode)
            .putString("pref_max_bitrate", settings.maxBitrateCap)
            .apply()
    }

    // --- AniList & MAL Auth & Sync ---
    fun getAniListToken(): String? {
        return prefs.getString("anilist_token", null)
    }

    fun saveAniListToken(token: String?) {
        if (token.isNullOrBlank()) {
            prefs.edit().remove("anilist_token").apply()
        } else {
            prefs.edit().putString("anilist_token", token.trim()).apply()
        }
    }

    fun getAniListUser(): AniListUser? {
        val id = prefs.getInt("anilist_user_id", -1)
        val name = prefs.getString("anilist_user_name", null) ?: return null
        val avatar = prefs.getString("anilist_user_avatar", "") ?: ""
        val animeCount = prefs.getInt("anilist_user_anime_count", 0)
        val epCount = prefs.getInt("anilist_user_ep_count", 0)
        return AniListUser(
            id = id,
            name = name,
            avatarUrl = avatar,
            totalAnime = animeCount,
            episodesWatched = epCount
        )
    }

    fun saveAniListUser(user: AniListUser?) {
        if (user == null) {
            prefs.edit()
                .remove("anilist_user_id")
                .remove("anilist_user_name")
                .remove("anilist_user_avatar")
                .remove("anilist_user_anime_count")
                .remove("anilist_user_ep_count")
                .apply()
        } else {
            prefs.edit()
                .putInt("anilist_user_id", user.id)
                .putString("anilist_user_name", user.name)
                .putString("anilist_user_avatar", user.avatarUrl)
                .putInt("anilist_user_anime_count", user.totalAnime)
                .putInt("anilist_user_ep_count", user.episodesWatched)
                .apply()
        }
    }

    fun clearAniListAuth() {
        saveAniListToken(null)
        saveAniListUser(null)
    }

    fun getMalUsername(): String? {
        return prefs.getString("mal_username", null)
    }

    fun saveMalUsername(username: String?) {
        if (username.isNullOrBlank()) {
            prefs.edit().remove("mal_username").apply()
        } else {
            prefs.edit().putString("mal_username", username.trim()).apply()
        }
    }

    fun getMalToken(): String? {
        return prefs.getString("mal_token", null)
    }

    fun saveMalToken(token: String?) {
        if (token.isNullOrBlank()) {
            prefs.edit().remove("mal_token").apply()
        } else {
            prefs.edit().putString("mal_token", token.trim()).apply()
        }
    }

    fun getMalUser(): MalUser? {
        val name = prefs.getString("mal_user_name", null)
            ?: prefs.getString("mal_username", null)
            ?: return null
        val id = prefs.getInt("mal_user_id", 1)
        val avatar = prefs.getString("mal_user_avatar", "") ?: ""
        val animeCount = prefs.getInt("mal_user_anime_count", 0)
        val epCount = prefs.getInt("mal_user_ep_count", 0)
        return MalUser(
            id = id,
            name = name,
            avatarUrl = avatar,
            totalAnime = animeCount,
            episodesWatched = epCount
        )
    }

    fun saveMalUser(user: MalUser?) {
        if (user == null) {
            prefs.edit()
                .remove("mal_user_id")
                .remove("mal_user_name")
                .remove("mal_user_avatar")
                .remove("mal_user_anime_count")
                .remove("mal_user_ep_count")
                .remove("mal_username")
                .apply()
        } else {
            prefs.edit()
                .putInt("mal_user_id", user.id)
                .putString("mal_user_name", user.name)
                .putString("mal_user_avatar", user.avatarUrl)
                .putInt("mal_user_anime_count", user.totalAnime)
                .putInt("mal_user_ep_count", user.episodesWatched)
                .putString("mal_username", user.name)
                .apply()
        }
    }

    fun clearMalAuth() {
        saveMalToken(null)
        saveMalUser(null)
    }

    fun resetSettings() {
        prefs.edit().clear().apply()
    }

    fun getWatchHistoryCount(): Int {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM watch_history", null)
        cursor.use {
            if (it.moveToFirst()) return it.getInt(0)
        }
        return 0
    }

    fun getWatchlistCount(): Int {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM favorites", null)
        cursor.use {
            if (it.moveToFirst()) return it.getInt(0)
        }
        return 0
    }

    fun clearWatchlist() {
        val db = dbHelper.writableDatabase
        db.delete("favorites", null, null)
    }

    // --- Multi-User TV Profiles & Parental Controls ---
    fun getProfiles(): List<com.darknet.animetv.data.model.UserProfile> {
        val jsonStr = prefs.getString("user_profiles_json", null)
        if (jsonStr.isNullOrBlank()) {
            val defaults = listOf(
                com.darknet.animetv.data.model.UserProfile(
                    id = "profile_main",
                    name = "Main",
                    avatarColor = 0xFF8B52C9L,
                    avatarIcon = "🦝",
                    backgroundTheme = "Cosmic Purple",
                    bgGradientStart = 0xFF3B0764L,
                    bgGradientEnd = 0xFF0C0D14L,
                    isKidsMode = false,
                    isPinLocked = false
                ),
                com.darknet.animetv.data.model.UserProfile(
                    id = "profile_kids",
                    name = "Kids",
                    avatarColor = 0xFF10B981L,
                    avatarIcon = "👶",
                    backgroundTheme = "Emerald Shinobi",
                    bgGradientStart = 0xFF064E3BL,
                    bgGradientEnd = 0xFF0C0D14L,
                    isKidsMode = true,
                    isPinLocked = false
                )
            )
            saveProfiles(defaults)
            return defaults
        }

        return try {
            val arr = org.json.JSONArray(jsonStr)
            val list = mutableListOf<com.darknet.animetv.data.model.UserProfile>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val isKids = obj.optBoolean("isKidsMode", false)
                val defaultColor = if (isKids) 0xFF10B981L else 0xFF8B52C9L
                val defaultIcon = if (isKids) "👶" else "🦝"
                val defaultTheme = if (isKids) "Emerald Shinobi" else "Cosmic Purple"
                val defaultGradStart = if (isKids) 0xFF064E3BL else 0xFF3B0764L
                list.add(
                    com.darknet.animetv.data.model.UserProfile(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        avatarColor = obj.optLong("avatarColor", defaultColor),
                        avatarIcon = obj.optString("avatarIcon", defaultIcon),
                        avatarUrl = obj.optString("avatarUrl", ""),
                        backgroundTheme = obj.optString("backgroundTheme", defaultTheme),
                        bgImageUrl = obj.optString("bgImageUrl", ""),
                        bgGradientStart = obj.optLong("bgGradientStart", defaultGradStart),
                        bgGradientEnd = obj.optLong("bgGradientEnd", 0xFF0C0D14L),
                        isKidsMode = isKids,
                        isPinLocked = obj.optBoolean("isPinLocked", false),
                        pinHash = obj.optString("pinHash", ""),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
            if (list.isEmpty()) getProfiles() else list
        } catch (_: Exception) {
            listOf(
                com.darknet.animetv.data.model.UserProfile(
                    id = "profile_main",
                    name = "Main",
                    avatarColor = 0xFF8B52C9L,
                    avatarIcon = "🦝",
                    avatarUrl = "",
                    backgroundTheme = "Cosmic Purple",
                    bgImageUrl = "",
                    bgGradientStart = 0xFF3B0764L,
                    bgGradientEnd = 0xFF0C0D14L,
                    isKidsMode = false
                )
            )
        }
    }

    fun saveProfiles(profiles: List<com.darknet.animetv.data.model.UserProfile>) {
        val arr = org.json.JSONArray()
        for (p in profiles) {
            val obj = org.json.JSONObject()
            obj.put("id", p.id)
            obj.put("name", p.name)
            obj.put("avatarColor", p.avatarColor)
            obj.put("avatarIcon", p.avatarIcon)
            obj.put("avatarUrl", p.avatarUrl)
            obj.put("backgroundTheme", p.backgroundTheme)
            obj.put("bgImageUrl", p.bgImageUrl)
            obj.put("bgGradientStart", p.bgGradientStart)
            obj.put("bgGradientEnd", p.bgGradientEnd)
            obj.put("isKidsMode", p.isKidsMode)
            obj.put("isPinLocked", p.isPinLocked)
            obj.put("pinHash", p.pinHash)
            obj.put("createdAt", p.createdAt)
            arr.put(obj)
        }
        prefs.edit().putString("user_profiles_json", arr.toString()).apply()
    }

    fun getActiveProfile(): com.darknet.animetv.data.model.UserProfile {
        val activeId = prefs.getString("active_profile_id", "profile_main") ?: "profile_main"
        val profiles = getProfiles()
        return profiles.find { it.id == activeId } ?: profiles.firstOrNull() ?: com.darknet.animetv.data.model.UserProfile("profile_main", "Main")
    }

    fun setActiveProfile(profileId: String) {
        prefs.edit().putString("active_profile_id", profileId).apply()
    }

    fun saveProfile(profile: com.darknet.animetv.data.model.UserProfile) {
        val profiles = getProfiles().toMutableList()
        val index = profiles.indexOfFirst { it.id == profile.id }
        if (index >= 0) {
            profiles[index] = profile
        } else {
            profiles.add(profile)
        }
        saveProfiles(profiles)
    }

    fun deleteProfile(profileId: String) {
        if (profileId == "profile_main") return // Can't delete main
        val profiles = getProfiles().filterNot { it.id == profileId }
        saveProfiles(profiles)
        if (getActiveProfile().id == profileId) {
            setActiveProfile("profile_main")
        }
    }

    // --- TanukiTV Account & Cloud Authentication ---
    fun getUserAccount(): com.darknet.animetv.data.model.UserAccount? {
        val email = prefs.getString("user_account_email", null) ?: return null
        val username = prefs.getString("user_account_username", "Tanuki Otaku") ?: "Tanuki Otaku"
        val tier = prefs.getString("user_account_tier", "TanukiTV VIP") ?: "TanukiTV VIP"
        val sync = prefs.getBoolean("user_account_sync", true)
        val lastSync = prefs.getLong("user_account_last_sync", System.currentTimeMillis())
        val token = prefs.getString("user_account_token", "tanuki_tok_active") ?: "tanuki_tok_active"
        return com.darknet.animetv.data.model.UserAccount(
            email = email,
            username = username,
            token = token,
            isLoggedIn = true,
            membershipTier = tier,
            syncEnabled = sync,
            lastSyncTime = lastSync
        )
    }

    fun saveUserAccount(account: com.darknet.animetv.data.model.UserAccount) {
        prefs.edit()
            .putString("user_account_email", account.email)
            .putString("user_account_username", account.username)
            .putString("user_account_token", account.token)
            .putString("user_account_tier", account.membershipTier)
            .putBoolean("user_account_sync", account.syncEnabled)
            .putLong("user_account_last_sync", account.lastSyncTime)
            .apply()
    }

    fun clearUserAccount() {
        prefs.edit()
            .remove("user_account_email")
            .remove("user_account_username")
            .remove("user_account_token")
            .remove("user_account_tier")
            .remove("user_account_sync")
            .remove("user_account_last_sync")
            .apply()
    }

    // --- Serialization Helpers ---
    internal fun serializeGenres(genres: List<String>): String {
        val jsonArray = JSONArray()
        for (genre in genres) {
            jsonArray.put(genre)
        }
        return jsonArray.toString()
    }

    internal fun deserializeGenres(genresStr: String?): List<String> {
        if (genresStr.isNullOrBlank()) return emptyList()
        return try {
            if (genresStr.startsWith("[")) {
                val jsonArray = JSONArray(genresStr)
                val list = ArrayList<String>(jsonArray.length())
                for (i in 0 until jsonArray.length()) {
                    val genre = jsonArray.optString(i)
                    if (!genre.isNullOrEmpty()) {
                        list.add(genre)
                    }
                }
                list
            } else {
                genresStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            }
        } catch (_: Exception) {
            genresStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
    }

    // --- Watch History ---
    fun saveWatchProgress(progress: WatchProgress) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("anime_id", progress.animeId)
            put("anime_title", progress.animeTitle)
            put("poster_url", progress.posterUrl)
            put("episode_id", progress.episodeId)
            put("episode_number", progress.episodeNumber)
            put("episode_title", progress.episodeTitle)
            put("position_ms", progress.positionMs)
            put("duration_ms", progress.durationMs)
            put("updated_at", if (progress.lastWatchedTimestamp > 0L) progress.lastWatchedTimestamp else System.currentTimeMillis())
        }
        db.insertWithOnConflict("watch_history", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getWatchProgress(animeId: String): WatchProgress? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "watch_history",
            null,
            "anime_id = ?",
            arrayOf(animeId),
            null,
            null,
            null
        )
        cursor.use {
            if (it.moveToFirst()) {
                val updatedAt = it.getLong(it.getColumnIndexOrThrow("updated_at"))
                return WatchProgress(
                    animeId = it.getString(it.getColumnIndexOrThrow("anime_id")) ?: "",
                    animeTitle = it.getString(it.getColumnIndexOrThrow("anime_title")) ?: "",
                    posterUrl = it.getString(it.getColumnIndexOrThrow("poster_url")) ?: "",
                    episodeId = it.getString(it.getColumnIndexOrThrow("episode_id")) ?: "",
                    episodeNumber = it.getInt(it.getColumnIndexOrThrow("episode_number")),
                    episodeTitle = it.getString(it.getColumnIndexOrThrow("episode_title")) ?: "",
                    positionMs = it.getLong(it.getColumnIndexOrThrow("position_ms")),
                    durationMs = it.getLong(it.getColumnIndexOrThrow("duration_ms")),
                    lastWatchedTimestamp = if (updatedAt > 0L) updatedAt else System.currentTimeMillis()
                )
            }
        }
        return null
    }

    fun getAllWatchHistory(): List<WatchProgress> {
        val list = mutableListOf<WatchProgress>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "watch_history",
            null,
            null,
            null,
            null,
            null,
            "updated_at DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                val updatedAt = it.getLong(it.getColumnIndexOrThrow("updated_at"))
                list.add(
                    WatchProgress(
                        animeId = it.getString(it.getColumnIndexOrThrow("anime_id")) ?: "",
                        animeTitle = it.getString(it.getColumnIndexOrThrow("anime_title")) ?: "",
                        posterUrl = it.getString(it.getColumnIndexOrThrow("poster_url")) ?: "",
                        episodeId = it.getString(it.getColumnIndexOrThrow("episode_id")) ?: "",
                        episodeNumber = it.getInt(it.getColumnIndexOrThrow("episode_number")),
                        episodeTitle = it.getString(it.getColumnIndexOrThrow("episode_title")) ?: "",
                        positionMs = it.getLong(it.getColumnIndexOrThrow("position_ms")),
                        durationMs = it.getLong(it.getColumnIndexOrThrow("duration_ms")),
                        lastWatchedTimestamp = if (updatedAt > 0L) updatedAt else System.currentTimeMillis()
                    )
                )
            }
        }
        return list
    }

    fun removeWatchProgress(animeId: String): Boolean {
        val db = dbHelper.writableDatabase
        val rows = db.delete("watch_history", "anime_id = ?", arrayOf(animeId))
        return rows > 0
    }

    fun clearWatchHistory() {
        val db = dbHelper.writableDatabase
        db.delete("watch_history", null, null)
    }

    fun getAllWatchProgress(): List<WatchProgress> = getAllWatchHistory()

    // --- Favorites ---
    fun toggleFavorite(anime: Anime): Boolean {
        return if (isFavorite(anime.id)) {
            removeFavorite(anime.id)
            false
        } else {
            addFavorite(anime)
            true
        }
    }

    fun addFavorite(anime: Anime) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("anime_id", anime.id)
            put("title", anime.title)
            put("poster_url", anime.posterUrl)
            put("backdrop_url", anime.backdropUrl)
            put("score", anime.score)
            put("year", anime.year)
            put("status", anime.status)
            put("genres", serializeGenres(anime.genres))
            put("synopsis", anime.synopsis)
            put("total_episodes", anime.totalEpisodes)
            put("has_dub", if (anime.hasDub) 1 else 0)
            put("dubbed_episodes_count", anime.dubbedEpisodesCount)
            put("added_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict("favorites", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun removeFavorite(animeId: String) {
        val db = dbHelper.writableDatabase
        db.delete("favorites", "anime_id = ?", arrayOf(animeId))
    }

    fun isFavorite(animeId: String): Boolean {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT 1 FROM favorites WHERE anime_id = ?", arrayOf(animeId))
        cursor.use {
            return it.moveToFirst()
        }
    }

    fun getFavorite(animeId: String): Anime? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "favorites",
            null,
            "anime_id = ?",
            arrayOf(animeId),
            null,
            null,
            null
        )
        cursor.use {
            if (it.moveToFirst()) {
                val genresStr = it.getString(it.getColumnIndexOrThrow("genres"))
                val totalEpIdx = it.getColumnIndex("total_episodes")
                val hasDubIdx = it.getColumnIndex("has_dub")
                val dubCountIdx = it.getColumnIndex("dubbed_episodes_count")
                return Anime(
                    id = it.getString(it.getColumnIndexOrThrow("anime_id")) ?: "",
                    title = it.getString(it.getColumnIndexOrThrow("title")) ?: "",
                    posterUrl = it.getString(it.getColumnIndexOrThrow("poster_url")) ?: "",
                    backdropUrl = it.getString(it.getColumnIndexOrThrow("backdrop_url")) ?: "",
                    score = it.getDouble(it.getColumnIndexOrThrow("score")),
                    year = it.getInt(it.getColumnIndexOrThrow("year")),
                    status = it.getString(it.getColumnIndexOrThrow("status")) ?: "Ongoing",
                    genres = deserializeGenres(genresStr),
                    synopsis = it.getString(it.getColumnIndexOrThrow("synopsis")) ?: "",
                    totalEpisodes = if (totalEpIdx >= 0) it.getInt(totalEpIdx) else 0,
                    hasDub = if (hasDubIdx >= 0) it.getInt(hasDubIdx) == 1 else false,
                    dubbedEpisodesCount = if (dubCountIdx >= 0) it.getInt(dubCountIdx) else 0
                )
            }
        }
        return null
    }

    fun getFavorites(): List<Anime> {
        val list = mutableListOf<Anime>()
        val db = dbHelper.readableDatabase
        val cursor = db.query("favorites", null, null, null, null, null, "added_at DESC")
        cursor.use {
            while (it.moveToNext()) {
                val genresStr = it.getString(it.getColumnIndexOrThrow("genres"))
                val totalEpIdx = it.getColumnIndex("total_episodes")
                val hasDubIdx = it.getColumnIndex("has_dub")
                val dubCountIdx = it.getColumnIndex("dubbed_episodes_count")
                list.add(
                    Anime(
                        id = it.getString(it.getColumnIndexOrThrow("anime_id")) ?: "",
                        title = it.getString(it.getColumnIndexOrThrow("title")) ?: "",
                        posterUrl = it.getString(it.getColumnIndexOrThrow("poster_url")) ?: "",
                        backdropUrl = it.getString(it.getColumnIndexOrThrow("backdrop_url")) ?: "",
                        score = it.getDouble(it.getColumnIndexOrThrow("score")),
                        year = it.getInt(it.getColumnIndexOrThrow("year")),
                        status = it.getString(it.getColumnIndexOrThrow("status")) ?: "Ongoing",
                        genres = deserializeGenres(genresStr),
                        synopsis = it.getString(it.getColumnIndexOrThrow("synopsis")) ?: "",
                        totalEpisodes = if (totalEpIdx >= 0) it.getInt(totalEpIdx) else 0,
                        hasDub = if (hasDubIdx >= 0) it.getInt(hasDubIdx) == 1 else false,
                        dubbedEpisodesCount = if (dubCountIdx >= 0) it.getInt(dubCountIdx) else 0
                    )
                )
            }
        }
        return list
    }

    fun getAllFavorites(): List<Anime> = getFavorites()

    // --- Custom Sources ---
    fun getSources(): List<CustomSource> {
        val list = mutableListOf<CustomSource>()
        val db = dbHelper.readableDatabase
        val cursor = db.query("sources", null, null, null, null, null, "priority_order ASC, id ASC")
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    CustomSource(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        name = it.getString(it.getColumnIndexOrThrow("name")) ?: "",
                        baseUrl = it.getString(it.getColumnIndexOrThrow("base_url")) ?: "",
                        animeListSelector = it.getString(it.getColumnIndexOrThrow("anime_list_selector")) ?: "",
                        episodeSelector = it.getString(it.getColumnIndexOrThrow("episode_selector")) ?: "",
                        streamUrlSelector = it.getString(it.getColumnIndexOrThrow("stream_url_selector")) ?: "",
                        isEnabled = it.getInt(it.getColumnIndexOrThrow("is_enabled")) == 1,
                        sourceType = it.getString(it.getColumnIndexOrThrow("source_type")) ?: "HTML_SCRAPER"
                    )
                )
            }
        }
        return list
    }

    fun getEnabledSources(): List<CustomSource> {
        val list = mutableListOf<CustomSource>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "sources",
            null,
            "is_enabled = 1",
            null,
            null,
            null,
            "priority_order ASC, id ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    CustomSource(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        name = it.getString(it.getColumnIndexOrThrow("name")) ?: "",
                        baseUrl = it.getString(it.getColumnIndexOrThrow("base_url")) ?: "",
                        animeListSelector = it.getString(it.getColumnIndexOrThrow("anime_list_selector")) ?: "",
                        episodeSelector = it.getString(it.getColumnIndexOrThrow("episode_selector")) ?: "",
                        streamUrlSelector = it.getString(it.getColumnIndexOrThrow("stream_url_selector")) ?: "",
                        isEnabled = true,
                        sourceType = it.getString(it.getColumnIndexOrThrow("source_type")) ?: "HTML_SCRAPER"
                    )
                )
            }
        }
        return list
    }

    fun getSource(id: Long): CustomSource? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "sources",
            null,
            "id = ?",
            arrayOf(id.toString()),
            null,
            null,
            null
        )
        cursor.use {
            if (it.moveToFirst()) {
                return CustomSource(
                    id = it.getLong(it.getColumnIndexOrThrow("id")),
                    name = it.getString(it.getColumnIndexOrThrow("name")) ?: "",
                    baseUrl = it.getString(it.getColumnIndexOrThrow("base_url")) ?: "",
                    animeListSelector = it.getString(it.getColumnIndexOrThrow("anime_list_selector")) ?: "",
                    episodeSelector = it.getString(it.getColumnIndexOrThrow("episode_selector")) ?: "",
                    streamUrlSelector = it.getString(it.getColumnIndexOrThrow("stream_url_selector")) ?: "",
                    isEnabled = it.getInt(it.getColumnIndexOrThrow("is_enabled")) == 1,
                    sourceType = it.getString(it.getColumnIndexOrThrow("source_type")) ?: "HTML_SCRAPER"
                )
            }
        }
        return null
    }

    fun insertSource(source: CustomSource): Long {
        val db = dbHelper.writableDatabase
        val nextPriority = getSources().size
        val values = ContentValues().apply {
            put("name", source.name)
            put("base_url", source.baseUrl)
            put("anime_list_selector", source.animeListSelector)
            put("episode_selector", source.episodeSelector)
            put("stream_url_selector", source.streamUrlSelector)
            put("is_enabled", if (source.isEnabled) 1 else 0)
            put("source_type", source.sourceType)
            put("priority_order", nextPriority)
        }
        return db.insert("sources", null, values)
    }

    fun saveSource(source: CustomSource): Long {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            if (source.id > 0) {
                put("id", source.id)
            } else {
                put("priority_order", getSources().size)
            }
            put("name", source.name)
            put("base_url", source.baseUrl)
            put("anime_list_selector", source.animeListSelector)
            put("episode_selector", source.episodeSelector)
            put("stream_url_selector", source.streamUrlSelector)
            put("is_enabled", if (source.isEnabled) 1 else 0)
            put("source_type", source.sourceType)
        }
        return if (source.id > 0) {
            val updated = db.update("sources", values, "id = ?", arrayOf(source.id.toString()))
            if (updated > 0) source.id else db.insert("sources", null, values)
        } else {
            db.insert("sources", null, values)
        }
    }

    fun saveSourcesOrder(orderedSources: List<CustomSource>) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            orderedSources.forEachIndexed { index, source ->
                if (source.id > 0) {
                    val values = ContentValues().apply {
                        put("priority_order", index)
                    }
                    db.update("sources", values, "id = ?", arrayOf(source.id.toString()))
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun toggleSource(id: Long, isEnabled: Boolean) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("is_enabled", if (isEnabled) 1 else 0)
        }
        db.update("sources", values, "id = ?", arrayOf(id.toString()))
    }

    fun deleteSource(id: Long) {
        val db = dbHelper.writableDatabase
        db.delete("sources", "id = ?", arrayOf(id.toString()))
    }
}
