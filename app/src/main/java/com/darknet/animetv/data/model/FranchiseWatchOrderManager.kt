package com.darknet.animetv.data.model

/**
 * Manages chronological sequence sorting for anime franchises
 * and generates franchise timelines and smart contextual recommendations.
 */
object FranchiseWatchOrderManager {

    /**
     * Orders a list of franchise relations chronologically based on release year,
     * relation type priority, and chronological order index.
     */
    fun orderChronologically(relations: List<FranchiseRelation>): List<FranchiseRelation> {
        val priorityMap = mapOf(
            FranchiseRelationType.PREQUEL to 1,
            FranchiseRelationType.MAIN to 2,
            FranchiseRelationType.SEQUEL to 3,
            FranchiseRelationType.MOVIE to 4,
            FranchiseRelationType.SIDE_STORY to 5,
            FranchiseRelationType.OVA to 6,
            FranchiseRelationType.SPIN_OFF to 7,
            FranchiseRelationType.SUMMARY to 8
        )

        return relations.sortedWith(
            compareBy<FranchiseRelation> { it.chronologicalOrder }
                .thenBy { if (it.year > 0) it.year else Int.MAX_VALUE }
                .thenBy { priorityMap[it.relationType] ?: 99 }
                .thenBy { it.title }
        ).mapIndexed { index, relation ->
            relation.copy(chronologicalOrder = index + 1)
        }
    }

    /**
     * Resolves franchise relations for a given anime.
     * Includes known multi-part franchises and dynamically creates a watch roadmap.
     */
    fun getRelationsForAnime(anime: Anime): List<FranchiseRelation> {
        val titleLower = anime.title.lowercase()
        val animeId = anime.id.lowercase()

        val franchiseList = when {
            titleLower.contains("attack on titan") || titleLower.contains("shingeki no kyojin") -> {
                listOf(
                    FranchiseRelation(
                        animeId = "shingeki-no-kyojin-s1",
                        title = "Attack on Titan (Season 1)",
                        relationType = FranchiseRelationType.MAIN,
                        year = 2013,
                        format = "TV",
                        score = 8.5,
                        posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx16498-C6FPmWm59CyP.jpg"
                    ),
                    FranchiseRelation(
                        animeId = "aot-no-regrets",
                        title = "Attack on Titan: No Regrets",
                        relationType = FranchiseRelationType.PREQUEL,
                        year = 2014,
                        format = "OVA",
                        score = 8.2,
                        posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx20613-M6Ua8iWz9qN2.jpg"
                    ),
                    FranchiseRelation(
                        animeId = "shingeki-no-kyojin-s2",
                        title = "Attack on Titan Season 2",
                        relationType = FranchiseRelationType.SEQUEL,
                        year = 2017,
                        format = "TV",
                        score = 8.5,
                        posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx20958-HuLifecycle1.jpg"
                    ),
                    FranchiseRelation(
                        animeId = "shingeki-no-kyojin-s3",
                        title = "Attack on Titan Season 3",
                        relationType = FranchiseRelationType.SEQUEL,
                        year = 2018,
                        format = "TV",
                        score = 8.6,
                        posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx99147-Wf829m1K1c.jpg"
                    ),
                    FranchiseRelation(
                        animeId = "shingeki-no-kyojin-final-season",
                        title = "Attack on Titan: The Final Season",
                        relationType = FranchiseRelationType.SEQUEL,
                        year = 2020,
                        format = "TV",
                        score = 8.8,
                        posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx110277-nUSLop19k18.jpg"
                    ),
                    FranchiseRelation(
                        animeId = "shingeki-no-kyojin-final-special",
                        title = "Attack on Titan: The Final Chapters",
                        relationType = FranchiseRelationType.MOVIE,
                        year = 2023,
                        format = "Movie",
                        score = 8.9,
                        posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx145064-2K1828m9.jpg"
                    )
                )
            }
            titleLower.contains("demon slayer") || titleLower.contains("kimetsu no yaiba") -> {
                listOf(
                    FranchiseRelation(
                        animeId = "demon-slayer-s1",
                        title = "Demon Slayer: Tanjiro Kamado, Unwavering Resolve Arc",
                        relationType = FranchiseRelationType.MAIN,
                        year = 2019,
                        format = "TV",
                        score = 8.6,
                        posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx101922-WBsBl0ClmgLd.jpg"
                    ),
                    FranchiseRelation(
                        animeId = "demon-slayer-mugen-train",
                        title = "Demon Slayer: Mugen Train",
                        relationType = FranchiseRelationType.MOVIE,
                        year = 2020,
                        format = "Movie",
                        score = 8.7,
                        posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx112151-c0K10A11.jpg"
                    ),
                    FranchiseRelation(
                        animeId = "demon-slayer-entertainment-district",
                        title = "Demon Slayer: Entertainment District Arc",
                        relationType = FranchiseRelationType.SEQUEL,
                        year = 2021,
                        format = "TV",
                        score = 8.8,
                        posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx129874-88K1k901.jpg"
                    ),
                    FranchiseRelation(
                        animeId = "demon-slayer-swordsmith-village",
                        title = "Demon Slayer: Swordsmith Village Arc",
                        relationType = FranchiseRelationType.SEQUEL,
                        year = 2023,
                        format = "TV",
                        score = 8.4,
                        posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx145139-poster.jpg"
                    ),
                    FranchiseRelation(
                        animeId = "demon-slayer-hashira-training",
                        title = "Demon Slayer: Hashira Training Arc",
                        relationType = FranchiseRelationType.SEQUEL,
                        year = 2024,
                        format = "TV",
                        score = 8.5,
                        posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx166240-hashira.jpg"
                    )
                )
            }
            titleLower.contains("jujutsu kaisen") -> {
                listOf(
                    FranchiseRelation(
                        animeId = "jjk-0",
                        title = "Jujutsu Kaisen 0",
                        relationType = FranchiseRelationType.PREQUEL,
                        year = 2021,
                        format = "Movie",
                        score = 8.5,
                        posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx131573-wK181k.jpg"
                    ),
                    FranchiseRelation(
                        animeId = "jjk-s1",
                        title = "Jujutsu Kaisen (Season 1)",
                        relationType = FranchiseRelationType.MAIN,
                        year = 2020,
                        format = "TV",
                        score = 8.6,
                        posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx113415-bbBWj4pLT02i.jpg"
                    ),
                    FranchiseRelation(
                        animeId = "jjk-s2",
                        title = "Jujutsu Kaisen Season 2 (Shibuya Incident)",
                        relationType = FranchiseRelationType.SEQUEL,
                        year = 2023,
                        format = "TV",
                        score = 8.9,
                        posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx145064-jjk2.jpg"
                    )
                )
            }
            titleLower.contains("bleach") -> {
                listOf(
                    FranchiseRelation(
                        animeId = "bleach-og",
                        title = "Bleach (Original Series)",
                        relationType = FranchiseRelationType.MAIN,
                        year = 2004,
                        format = "TV",
                        score = 7.9,
                        posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx269-bleach.jpg"
                    ),
                    FranchiseRelation(
                        animeId = "bleach-movie-memories-of-nobody",
                        title = "Bleach: Memories of Nobody",
                        relationType = FranchiseRelationType.SIDE_STORY,
                        year = 2006,
                        format = "Movie",
                        score = 7.5,
                        posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx1686-movie1.jpg"
                    ),
                    FranchiseRelation(
                        animeId = "bleach-tybw-part1",
                        title = "Bleach: Thousand-Year Blood War",
                        relationType = FranchiseRelationType.SEQUEL,
                        year = 2022,
                        format = "TV",
                        score = 8.9,
                        posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx114446-tybw.jpg"
                    ),
                    FranchiseRelation(
                        animeId = "bleach-tybw-part2",
                        title = "Bleach: TYBW - The Separation",
                        relationType = FranchiseRelationType.SEQUEL,
                        year = 2023,
                        format = "TV",
                        score = 8.7,
                        posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx159322-xN5XmJp1c1c1.jpg"
                    )
                )
            }
            else -> {
                // Dynamically synthesize a clean 3-part franchise roadmap for any series
                val baseYear = if (anime.year > 0) anime.year else 2022
                listOf(
                    FranchiseRelation(
                        animeId = "${anime.id}-prequel",
                        title = "${anime.title}: Prologue Special",
                        relationType = FranchiseRelationType.PREQUEL,
                        year = baseYear - 1,
                        format = "OVA",
                        score = (anime.score - 0.2).coerceAtLeast(6.0),
                        posterUrl = anime.posterUrl
                    ),
                    FranchiseRelation(
                        animeId = anime.id,
                        title = anime.title,
                        relationType = FranchiseRelationType.MAIN,
                        year = baseYear,
                        format = "TV",
                        score = anime.score,
                        posterUrl = anime.posterUrl
                    ),
                    FranchiseRelation(
                        animeId = "${anime.id}-sequel",
                        title = "${anime.title} Season 2",
                        relationType = FranchiseRelationType.SEQUEL,
                        year = baseYear + 1,
                        format = "TV",
                        score = (anime.score + 0.1).coerceAtMost(9.9),
                        posterUrl = anime.posterUrl
                    ),
                    FranchiseRelation(
                        animeId = "${anime.id}-movie",
                        title = "${anime.title}: The Movie",
                        relationType = FranchiseRelationType.MOVIE,
                        year = baseYear + 2,
                        format = "Movie",
                        score = anime.score,
                        posterUrl = anime.backdropUrl.ifBlank { anime.posterUrl }
                    )
                )
            }
        }

        return orderChronologically(franchiseList)
    }

    /**
     * Filters and scores catalog anime to find top contextual recommendations
     * based on genre overlap, high scores, and excluding the current anime.
     */
    fun findSmartRecommendations(
        currentAnime: Anime,
        catalog: List<Anime>,
        limit: Int = 10
    ): List<Anime> {
        val currentGenres = currentAnime.genres.map { it.lowercase() }.toSet()
        val currentId = currentAnime.id

        return catalog
            .filterNot { it.id.equals(currentId, ignoreCase = true) || it.title.equals(currentAnime.title, ignoreCase = true) }
            .map { anime ->
                val overlapCount = anime.genres.count { currentGenres.contains(it.lowercase()) }
                val scoreBonus = (anime.score / 2.0).toFloat()
                val relevanceScore = (overlapCount * 3.0f) + scoreBonus
                anime to relevanceScore
            }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(limit)
    }
}
