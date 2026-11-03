package com.darknet.animetv.e2e.harness

import com.darknet.animetv.data.model.Anime
import com.darknet.animetv.data.model.AnimeDetail
import com.darknet.animetv.data.model.CustomSource
import com.darknet.animetv.data.model.Episode
import com.darknet.animetv.data.model.SubtitleTrack
import com.darknet.animetv.data.model.VideoSource
import com.darknet.animetv.data.model.WatchProgress

object E2eTestFixtures {

    fun createSampleAnime(
        id: String = "anime-001",
        title: String = "Frieren: Beyond Journey's End",
        score: Double = 9.35,
        genres: List<String> = listOf("Adventure", "Drama", "Fantasy"),
        sourceId: String = "aniwatch",
        totalEpisodes: Int = 28
    ): Anime {
        return Anime(
            id = id,
            title = title,
            posterUrl = "https://images.animetv.mock/poster/$id.jpg",
            backdropUrl = "https://images.animetv.mock/backdrop/$id.jpg",
            score = score,
            year = 2023,
            status = "Completed",
            genres = genres,
            synopsis = "An elf and her friends defeated the Demon King in a great war...",
            totalEpisodes = totalEpisodes,
            sourceId = sourceId
        )
    }

    fun createSampleAnimeList(count: Int = 10, sourceId: String = "aniwatch"): List<Anime> {
        val titles = listOf(
            "Frieren: Beyond Journey's End",
            "Demon Slayer: Kimetsu no Yaiba",
            "Jujutsu Kaisen Season 2",
            "Solo Leveling",
            "Attack on Titan: The Final Season",
            "One Piece",
            "Chainsaw Man",
            "Bleach: Thousand-Year Blood War",
            "Bocchi the Rock!",
            "Vinland Saga Season 2",
            "Spy x Family",
            "Steins;Gate"
        )
        return (0 until count).map { i ->
            val title = titles[i % titles.size] + if (i >= titles.size) " Part ${i / titles.size + 1}" else ""
            createSampleAnime(
                id = "anime-${100 + i}",
                title = title,
                score = 8.0 + (i % 20) * 0.1,
                sourceId = sourceId
            )
        }
    }

    fun createSampleDetail(
        animeId: String = "anime-001",
        episodeCount: Int = 12
    ): AnimeDetail {
        val anime = createSampleAnime(id = animeId, totalEpisodes = episodeCount)
        val episodes = (1..episodeCount).map { num ->
            Episode(
                id = "ep-$animeId-$num",
                animeId = animeId,
                number = num,
                title = "Episode $num: The Journey Continues",
                thumbnail = "https://images.animetv.mock/thumb/$animeId-$num.jpg",
                releaseDate = "2023-10-${num.toString().padStart(2, '0')}"
            )
        }
        return AnimeDetail(
            anime = anime,
            episodes = episodes,
            trailerUrl = "https://youtube.com/watch?v=sample-trailer",
            studio = "Madhouse"
        )
    }

    fun createSampleStreams(
        animeId: String = "anime-001",
        episodeId: String = "ep-001-1"
    ): List<VideoSource> {
        val subtitles = listOf(
            SubtitleTrack("en", "English", "https://cdn.animetv.mock/subs/en.vtt"),
            SubtitleTrack("es", "Spanish", "https://cdn.animetv.mock/subs/es.vtt"),
            SubtitleTrack("ja", "Japanese", "https://cdn.animetv.mock/subs/ja.vtt")
        )

        return listOf(
            VideoSource(
                serverName = "MegaCloud (Primary)",
                url = "https://stream.megacloud.mock/hls/$animeId/$episodeId/master.m3u8",
                quality = "1080p",
                isHls = true,
                headers = mapOf(
                    "Referer" to "https://aniwatchtv.ro/",
                    "User-Agent" to "Mozilla/5.0 (AnimeTV-Test/1.0)"
                ),
                subtitles = subtitles
            ),
            VideoSource(
                serverName = "StreamWish (Backup 1)",
                url = "https://stream.streamwish.mock/hls/$animeId/$episodeId/720p.m3u8",
                quality = "720p",
                isHls = true,
                headers = mapOf("Referer" to "https://streamwish.to/"),
                subtitles = subtitles
            ),
            VideoSource(
                serverName = "Mp4Upload (Backup 2)",
                url = "https://mp4upload.mock/storage/$animeId-$episodeId.mp4",
                quality = "480p",
                isHls = false,
                headers = emptyMap(),
                subtitles = emptyList()
            )
        )
    }

    fun createSampleWatchProgress(
        animeId: String = "anime-001",
        episodeNumber: Int = 1,
        positionMs: Long = 120_000L,
        durationMs: Long = 1_440_000L
    ): WatchProgress {
        return WatchProgress(
            animeId = animeId,
            animeTitle = "Frieren: Beyond Journey's End",
            posterUrl = "https://images.animetv.mock/poster/$animeId.jpg",
            episodeId = "ep-$animeId-$episodeNumber",
            episodeNumber = episodeNumber,
            episodeTitle = "Episode $episodeNumber: The Journey Continues",
            positionMs = positionMs,
            durationMs = durationMs,
            lastWatchedTimestamp = System.currentTimeMillis()
        )
    }

    fun createSampleCustomSource(
        id: Long = 1,
        name: String = "Custom Community Anime",
        baseUrl: String = "https://anime-community.mock",
        animeListSelector: String = "div.catalog div.card a",
        episodeSelector: String = "ul.episodes li a",
        streamUrlSelector: String = "video source, iframe",
        isEnabled: Boolean = true
    ): CustomSource {
        return CustomSource(
            id = id,
            name = name,
            baseUrl = baseUrl,
            animeListSelector = animeListSelector,
            episodeSelector = episodeSelector,
            streamUrlSelector = streamUrlSelector,
            isEnabled = isEnabled,
            sourceType = "HTML_SCRAPER"
        )
    }

    // Synthetic HTML fixtures for scraper testing
    val MOCK_ANIMEFLV_BROWSE_HTML = """
        <!DOCTYPE html>
        <html>
        <body>
            <ul class="ListAnimes">
                <li class="Anime">
                    <article class="Anime">
                        <a href="/anime/sousou-no-frieren">
                            <figure><img src="https://animeflv.net/uploads/animes/covers/frieren.jpg" /></figure>
                            <h3 class="Title">Sousou no Frieren</h3>
                            <span class="Vts">9.4</span>
                        </a>
                    </article>
                </li>
                <li class="Anime">
                    <article class="Anime">
                        <a href="/anime/kimetsu-no-yaiba">
                            <figure><img src="https://animeflv.net/uploads/animes/covers/kimetsu.jpg" /></figure>
                            <h3 class="Title">Kimetsu no Yaiba</h3>
                            <span class="Vts">8.9</span>
                        </a>
                    </article>
                </li>
            </ul>
        </body>
        </html>
    """.trimIndent()

    val MOCK_ANILIST_GRAPHQL_JSON = """
        {
          "data": {
            "Page": {
              "media": [
                {
                  "id": 154587,
                  "title": { "romaji": "Sousou no Frieren", "english": "Frieren: Beyond Journey's End" },
                  "bannerImage": "https://s4.anilist.co/file/anilistcdn/media/anime/banner/154587.jpg",
                  "coverImage": { "extraLarge": "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/154587.jpg" },
                  "averageScore": 94,
                  "genres": ["Adventure", "Drama", "Fantasy"],
                  "episodes": 28,
                  "status": "FINISHED",
                  "description": "An elf mage looks back on the journey she took..."
                }
              ]
            }
          }
        }
    """.trimIndent()

    val MOCK_JIKAN_REST_JSON = """
        {
          "data": [
            {
              "mal_id": 52991,
              "title": "Sousou no Frieren",
              "score": 9.38,
              "images": {
                "jpg": {
                  "large_image_url": "https://cdn.myanimelist.net/images/anime/1015/138006l.jpg"
                }
              },
              "synopsis": "During their decade-long quest to defeat the Demon King...",
              "episodes": 28,
              "status": "Finished Airing",
              "genres": [{ "name": "Adventure" }, { "name": "Fantasy" }]
            }
          ]
        }
    """.trimIndent()

    val MOCK_MEGACLOUD_EMBED_HTML = """
        <!DOCTYPE html>
        <html>
        <head><title>MegaCloud Player</title></head>
        <body>
            <script>
                var videoSources = [
                    { "file": "https://megacloud.stream/hls/master.m3u8", "type": "hls", "label": "auto" }
                ];
                var tracks = [
                    { "file": "https://megacloud.stream/sub/eng.vtt", "kind": "captions", "label": "English" }
                ];
            </script>
        </body>
        </html>
    """.trimIndent()
}
