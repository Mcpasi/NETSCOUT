package de.netscout.data

import de.netscout.core.*
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class RedditProvider(
    private val http: JsonHttpClient,
    private val accessToken: String,
    private val baseUrl: HttpUrl = "https://oauth.reddit.com/".toHttpUrl(),
) : SearchProvider {
    override val source = SearchSource.REDDIT
    override suspend fun search(request: SearchRequest, cursor: SearchCursor?): SourcePage {
        val query = request.keywordQuery
        if (accessToken.isBlank()) throw SourceException(SourceStatus.SETUP_REQUIRED, SearchMessage(MessageKey.REDDIT_SETUP))
        val url = baseUrl.newBuilder().addPathSegment("search").addQueryParameter("q", query.terms.joinToString(" AND ") { "\"$it\"" })
            .addQueryParameter("type", "link").addQueryParameter("limit", "25").addQueryParameter("raw_json", "1")
            .addQueryParameter("t", "all").addQueryParameter("sort", if (request.sort == SortOrder.NEWEST) "new" else "relevance")
            .apply { cursor?.let { addQueryParameter("after", it.token) } }.build()
        val data = http.get(url, mapOf("Authorization" to "Bearer $accessToken")).obj("data")
        val rows = data.requireRows("children")
        val items = rows.mapIndexedNotNull { index, wrapper ->
            val row = wrapper.obj("data")
            val path = row.text("permalink").takeIf { it.startsWith("/r/") } ?: return@mapIndexedNotNull null
            val body = markdownText(row.text("selftext"))
            SearchItem(row.text("name"), source, row.text("title"), "https://www.reddit.com$path", query.snippet(body),
                row.text("author"), epoch(row.decimal("created_utc")), (cursor?.offset ?: 0) + index + 1, row.text("subreddit_name_prefixed"), searchText = body)
        }
        return SourcePage(items, data.text("after").takeIf { it.isNotBlank() }?.let { SearchCursor(it, (cursor?.offset ?: 0) + rows.size) },
            listOf(SearchMessage(MessageKey.REDDIT_NOTE)))
    }
}
