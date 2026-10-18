package de.netscout.data

import de.netscout.core.*

object ProviderFactory {
    fun create(settings: ApiSettings, http: JsonHttpClient = JsonHttpClient()): List<SearchProvider> {
        require(settings.validationError() == null)
        fun withIndex(primary: SearchProvider, directConfigured: Boolean): SearchProvider {
            if (settings.searxngUrl.isBlank()) return primary
            val fallback = SearxngProvider(primary.source, http, settings.searxngUrl)
            if (!directConfigured) return fallback
            return IndexFallbackProvider(primary, fallback)
        }
        return listOf(
            HackerNewsProvider(http), GitHubProvider(http, settings.githubToken), ForumsProvider(http),
            withIndex(YouTubeProvider(http, settings.youtubeApiKey), settings.youtubeApiKey.isNotBlank()),
            withIndex(RedditProvider(http, settings.redditAccessToken), settings.redditAccessToken.isNotBlank()),
            SearxngProvider(SearchSource.WEB, http, settings.searxngUrl),
        )
    }
}
