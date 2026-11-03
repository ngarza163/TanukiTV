package com.darknet.animetv.data.source.extension

import com.darknet.animetv.data.model.CustomSource
import com.darknet.animetv.data.source.DynamicSourceConfig

/**
 * Representation of a modular cloud provider extension loaded dynamically from an external repository.
 */
data class CloudExtension(
    val id: String,
    val name: String,
    val version: String = "1.0.0",
    val author: String = "Community",
    val baseUrl: String,
    val description: String = "",
    val searchEndpoint: String = "/search?q={query}",
    val animeListSelector: String = ".anime-card, .flw-item, ul.ListAnimes a",
    val titleSelector: String = "h3, .title, .film-name a",
    val posterSelector: String = "img@src, img@data-src",
    val episodeSelector: String = "ul.episodes a, .ep-item, ul.EpisodeList a",
    val streamSelector: String = "iframe@src, video@src",
    val headers: Map<String, String> = emptyMap(),
    val isEnabled: Boolean = true
) {
    /**
     * Converts to internal SQLite CustomSource entity.
     */
    fun toCustomSource(): CustomSource {
        return CustomSource(
            name = "$name (v$version)",
            baseUrl = baseUrl,
            animeListSelector = animeListSelector,
            episodeSelector = episodeSelector,
            streamUrlSelector = streamSelector,
            isEnabled = isEnabled,
            sourceType = "CLOUD_EXTENSION"
        )
    }

    /**
     * Converts to dynamic scraper runtime configuration.
     */
    fun toDynamicConfig(): DynamicSourceConfig {
        return DynamicSourceConfig(
            id = id,
            name = name,
            baseUrl = baseUrl,
            isEnabled = isEnabled,
            headers = headers,
            searchEndpoint = searchEndpoint,
            searchItemSelector = animeListSelector,
            titleSelector = titleSelector,
            posterSelector = posterSelector,
            episodeItemSelector = episodeSelector,
            streamSelector = streamSelector
        )
    }
}

/**
 * Repository grouping multiple cloud provider extensions.
 */
data class ExtensionRepository(
    val name: String,
    val repoUrl: String,
    val version: Int = 1,
    val description: String = "",
    val extensions: List<CloudExtension> = emptyList()
)
