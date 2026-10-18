package de.netscout.data

import de.netscout.core.*
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class YouTubeProvider(
    private val http: JsonHttpClient,
    private val apiKey: String,
    private val baseUrl: HttpUrl = "https://www.googleapis.com/youtube/v3/".toHttpUrl(),
) : SearchProvider {
    override val source = SearchSource.YOUTUBE
    override suspend fun search(request: SearchRequest, cursor: SearchCursor?): SourcePage {
        val query = request.keywordQuery
        if (apiKey.isBlank()) throw SourceException(SourceStatus.SETUP_REQUIRED, "YouTube-API-Key oder SearXNG in Zugänge einrichten.")
        val url = baseUrl.newBuilder().addPathSegment("search").addQueryParameter("part", "snippet")
            .addQueryParameter("type", "video").addQueryParameter("q", query.remoteQuery)
            .addQueryParameter("maxResults", "25").addQueryParameter("order", if (request.sort == SortOrder.NEWEST) "date" else "relevance")
            .apply {
                cursor?.let { addQueryParameter("pageToken", it.token) }
                // publishedAfter is exclusive; retain the inclusive start at the UTC day boundary.
                request.startInstant?.let { addQueryParameter("publishedAfter", it.minusNanos(1).toString()) }
                request.endExclusive?.let { addQueryParameter("publishedBefore", it.toString()) }
            }.build()
        val result = http.get(url, mapOf("X-Goog-Api-Key" to apiKey))
        val rows = result.requireRows("items")
        val items = rows.mapIndexedNotNull { index, row ->
            val id = row.obj("id").text("videoId").takeIf { it.matches(Regex("[A-Za-z0-9_-]+")) } ?: return@mapIndexedNotNull null
            val snippet = row.obj("snippet")
            val body = fullText(snippet.text("description"))
            SearchItem(id, source, cleanHtml(snippet.text("title")), "https://www.youtube.com/watch?v=$id",
                query.snippet(body), cleanHtml(snippet.text("channelTitle")), parseDate(snippet.text("publishedAt")),
                (cursor?.offset ?: 0) + index + 1, "Video", searchText = body)
        }
        return SourcePage(items, result.text("nextPageToken").takeIf { it.isNotBlank() }?.let { SearchCursor(it, (cursor?.offset ?: 0) + rows.size) })
    }
}
