package de.netscout.data

import de.netscout.core.*

object ProviderFactory {
    fun create(settings: ApiSettings, http: JsonHttpClient = JsonHttpClient()): List<SearchProvider> {
        require(settings.validationError() == null)
        fun index(source: SearchSource): SearchProvider {
            val public = PublicWebProvider(source, http)
            return if (settings.searxngUrl.isBlank()) public else IndexFallbackProvider(
                SearxngProvider(source, http, settings.searxngUrl), public, "Eigener Suchdienst nicht verfügbar; ",
            )
        }
        fun withIndex(primary: SearchProvider, directConfigured: Boolean = true): SearchProvider {
            val fallback = index(primary.source)
            if (!directConfigured) return fallback
            return IndexFallbackProvider(primary, fallback)
        }
        return listOf(
            withIndex(HackerNewsProvider(http)), withIndex(GitHubProvider(http, settings.githubToken)), withIndex(ForumsProvider(http)),
            withIndex(YouTubeProvider(http, settings.youtubeApiKey), settings.youtubeApiKey.isNotBlank()),
            withIndex(RedditProvider(http, settings.redditAccessToken), settings.redditAccessToken.isNotBlank()),
            index(SearchSource.WEB),
        )
    }
}
