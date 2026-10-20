package de.netscout.data

import de.netscout.core.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class SearxngProvider(
    override val source: SearchSource,
    private val http: JsonHttpClient,
    private val instance: String,
) : SearchProvider {
    override suspend fun search(request: SearchRequest, cursor: SearchCursor?): SourcePage {
        val base = instance.trimEnd('/').plus('/').toHttpUrlOrNull()
        if (instance.isBlank() || base == null) throw SourceException(SourceStatus.SETUP_REQUIRED, SearchMessage(MessageKey.SEARX_SETUP))
        val page = cursor?.token?.toIntOrNull() ?: 1
        val keywords = request.keywordQuery
        val query = indexQuery(source, request)
        val url = base.newBuilder().addPathSegment("search").addQueryParameter("q", query)
            .addQueryParameter("format", "json").addQueryParameter("pageno", page.toString())
            .addQueryParameter("language", "all").addQueryParameter("safesearch", "1").build()
        val json = http.get(url)
        val rows = json.requireRows("results")
        val items = rows.mapIndexedNotNull { index, row ->
            val link = row.text("url")
            val parsed = link.toHttpUrlOrNull() ?: return@mapIndexedNotNull null
            val host = parsed.host.removePrefix("www.")
            if (!acceptsIndexUrl(source, parsed)) return@mapIndexedNotNull null
            val body = fullText(row.text("content"))
            SearchItem(link, source, cleanHtml(row.text("title")), link, keywords.snippet(body),
                publishedAt = parseDate(row.text("publishedDate")), rank = (cursor?.offset ?: 0) + index + 1, context = "Suchindex · $host", searchText = body,
                contextMessage = SearchMessage(MessageKey.SEARX_CONTEXT, host))
        }
        val next = if (rows.isNotEmpty() && page < 10) SearchCursor((page + 1).toString(), (cursor?.offset ?: 0) + rows.size) else null
        val warnings = json.array("unresponsive_engines").size
        return SourcePage(items, next, buildList {
            add(SearchMessage(MessageKey.SEARX_NOTE))
            if (warnings > 0) add(SearchMessage(MessageKey.SEARX_UNAVAILABLE, warnings.toString()))
        })
    }
}
