package de.netscout.data

import de.netscout.core.SearchRequest
import de.netscout.core.SearchSource
import okhttp3.HttpUrl

/** Site operators help retrieval; validation remains necessary when an index ignores them. */
internal fun indexQuery(source: SearchSource, request: SearchRequest): String {
    val scope = when (source) {
        SearchSource.REDDIT -> "site:reddit.com"
        SearchSource.YOUTUBE -> "site:youtube.com/watch"
        SearchSource.GITHUB -> "site:github.com"
        SearchSource.HACKER_NEWS -> "site:news.ycombinator.com/item"
        SearchSource.FORUMS -> "(site:stackoverflow.com OR site:superuser.com OR site:serverfault.com)"
        SearchSource.WEB -> ""
    }
    return "$scope ${request.keywordQuery.remoteQuery}".trim()
}

internal fun acceptsIndexUrl(source: SearchSource, url: HttpUrl): Boolean {
    if (url.username.isNotEmpty() || url.password.isNotEmpty()) return false
    val host = url.host.removePrefix("www.")
    fun domain(value: String) = host == value || host.endsWith(".$value")
    val parts = url.pathSegments.filter(String::isNotBlank)
    return when (source) {
        SearchSource.WEB -> true
        SearchSource.REDDIT -> domain("reddit.com") && (
            parts.size >= 4 && parts[0] == "r" && parts[2] == "comments" ||
            parts.size >= 2 && parts[0] == "comments")
        SearchSource.YOUTUBE -> domain("youtube.com") && (
            url.encodedPath == "/watch" && !url.queryParameter("v").isNullOrBlank() ||
            parts.size >= 2 && parts[0] in setOf("shorts", "live")) || host == "youtu.be" && parts.isNotEmpty()
        SearchSource.GITHUB -> host == "github.com" && parts.size >= 2 && parts[0] !in setOf("search", "topics", "collections", "features", "orgs", "settings")
        SearchSource.HACKER_NEWS -> host == "news.ycombinator.com" && url.encodedPath == "/item" && url.queryParameter("id")?.toLongOrNull() != null
        SearchSource.FORUMS -> host in setOf("stackoverflow.com", "superuser.com", "serverfault.com") && parts.size >= 2 && parts[0] in setOf("questions", "q") && parts[1].toLongOrNull() != null
    }
}
