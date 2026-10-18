package de.netscout.data

import de.netscout.core.*
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class HackerNewsProvider(
    private val http: JsonHttpClient,
    private val baseUrl: HttpUrl = "https://hn.algolia.com/api/v1/".toHttpUrl(),
) : SearchProvider {
    override val source = SearchSource.HACKER_NEWS
    override suspend fun search(request: SearchRequest, cursor: SearchCursor?): SourcePage {
        val query = request.keywordQuery
        val page = cursor?.token?.toIntOrNull() ?: 0
        val dates = listOfNotNull(
            request.startInstant?.let { "created_at_i>=${it.epochSecond}" },
            request.endExclusive?.let { "created_at_i<${it.epochSecond}" },
        )
        val url = baseUrl.newBuilder().addPathSegment(if (request.sort == SortOrder.NEWEST) "search_by_date" else "search")
            .addQueryParameter("query", query.remoteQuery).addQueryParameter("tags", "story")
            .addQueryParameter("advancedSyntax", "true").addQueryParameter("typoTolerance", "false")
            .addQueryParameter("queryType", "prefixNone").addQueryParameter("removeWordsIfNoResults", "none")
            .addQueryParameter("restrictSearchableAttributes", "title,story_text")
            .addQueryParameter("hitsPerPage", "30").addQueryParameter("page", page.toString())
            .apply { if (dates.isNotEmpty()) addQueryParameter("numericFilters", dates.joinToString(",")) }.build()
        val result = http.get(url)
        val rows = result.requireRows("hits")
        val items = rows.mapIndexedNotNull { index, row ->
            val id = row.text("objectID").takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
            val body = fullText(row.text("story_text"))
            SearchItem(id, source, cleanHtml(row.text("title")), "https://news.ycombinator.com/item?id=$id",
                query.snippet(body), row.text("author"),
                epoch(row.decimal("created_at_i")), (cursor?.offset ?: 0) + index + 1, "Hacker News · ${row.number("points")} Punkte", searchText = body)
        }
        val next = if (page + 1 < result.number("nbPages")) SearchCursor((page + 1).toString(), (cursor?.offset ?: 0) + rows.size) else null
        return SourcePage(items, next, "HN-Beiträge über Algolia; Link öffnet die Diskussion.")
    }
}
