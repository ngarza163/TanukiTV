package com.darknet.animetv.player.aniskip

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Client for fetching community crowd-sourced anime opening and ending skip timestamps
 * via the open AniSkip API (v2).
 */
class AniSkipClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
) {

    /**
     * Resolves skip intervals for a given anime and episode.
     * @param malId MyAnimeList numerical ID (or 0 if unknown)
     * @param episodeNumber 1-based episode index
     * @param episodeDurationMs total duration of episode in ms (0 if unknown)
     */
    suspend fun getSkipTimes(
        malId: Int,
        episodeNumber: Int,
        episodeDurationMs: Long = 0L
    ): List<SkipInterval> = withContext(Dispatchers.IO) {
        if (malId > 0 && episodeNumber > 0) {
            try {
                val epLengthSec = if (episodeDurationMs > 0L) (episodeDurationMs / 1000L).toString() else "1440"
                val url = "https://api.aniskip.com/v2/skip-times/$malId/$episodeNumber?types[]=op&types[]=ed&types[]=recap&episodeLength=$epLengthSec"

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "AnimeTV/2.0 AndroidTV")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            val parsed = parseAniSkipJson(body)
                            if (parsed.isNotEmpty()) return@withContext parsed
                        }
                    }
                }
            } catch (e: Exception) {
                // Silently fallback on network/API failure
            }
        }

        // Canonical anime fallback: standard 90s OP starting around ~1m30s or ~0s, and 90s ED near the end
        synthesizeDefaultIntervals(episodeDurationMs)
    }

    /**
     * Parses the official AniSkip v2 JSON response structure:
     * {
     *   "found": true,
     *   "results": [
     *     {
     *       "interval": { "startTime": 90.0, "endTime": 180.0 },
     *       "skipType": "op"
     *     }
     *   ]
     * }
     */
    fun parseAniSkipJson(jsonStr: String): List<SkipInterval> {
        val list = mutableListOf<SkipInterval>()
        try {
            val root = JsonParser.parseString(jsonStr).asJsonObject
            val found = if (root.has("found")) root.get("found").asBoolean else false
            if (!found && !root.has("results")) return emptyList()

            val results = if (root.has("results") && root.get("results").isJsonArray) {
                root.getAsJsonArray("results")
            } else return emptyList()

            for (elem in results) {
                if (!elem.isJsonObject) continue
                val item = elem.asJsonObject
                val intervalObj = if (item.has("interval") && item.get("interval").isJsonObject) {
                    item.getAsJsonObject("interval")
                } else continue

                val startTimeSec = if (intervalObj.has("startTime")) intervalObj.get("startTime").asDouble else 0.0
                val endTimeSec = if (intervalObj.has("endTime")) intervalObj.get("endTime").asDouble else 0.0
                val skipTypeStr = if (item.has("skipType")) item.get("skipType").asString.lowercase() else "op"

                val skipType = when {
                    skipTypeStr.contains("ed") -> SkipType.ED
                    skipTypeStr.contains("recap") -> SkipType.RECAP
                    skipTypeStr.contains("mixed") -> SkipType.MIXED_OP
                    else -> SkipType.OP
                }

                if (endTimeSec > startTimeSec) {
                    list.add(
                        SkipInterval(
                            startMs = (startTimeSec * 1000.0).toLong(),
                            endMs = (endTimeSec * 1000.0).toLong(),
                            type = skipType
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return list
    }

    /**
     * Synthesizes standard opening and ending intervals when AniSkip data is unavailable.
     */
    fun synthesizeDefaultIntervals(durationMs: Long): List<SkipInterval> {
        val list = mutableListOf<SkipInterval>()
        // Standard OP: 85 seconds duration (e.g. 0s to 85s or 90s to 175s)
        list.add(
            SkipInterval(
                startMs = 90_000L,
                endMs = 175_000L,
                type = SkipType.OP
            )
        )

        // Standard ED: if duration known and > 10 minutes, ending is usually the last 90 seconds before next-ep preview
        if (durationMs >= 600_000L) {
            val edStart = (durationMs - 120_000L).coerceAtLeast(0L)
            val edEnd = (durationMs - 30_000L).coerceAtLeast(edStart)
            list.add(
                SkipInterval(
                    startMs = edStart,
                    endMs = edEnd,
                    type = SkipType.ED
                )
            )
        }
        return list
    }

    /**
     * Checks if a given timestamp in ms falls within any skip interval.
     */
    fun findActiveInterval(currentTimeMs: Long, intervals: List<SkipInterval>): SkipInterval? {
        return intervals.firstOrNull { currentTimeMs in it.startMs..it.endMs }
    }
}
