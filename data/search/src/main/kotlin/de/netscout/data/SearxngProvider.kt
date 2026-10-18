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
        if (instance.isBlank() || base == null) throw SourceException(SourceStatus.SETUP_REQUIRED, "SearXNG-HTTPS-URL in Zugänge einrichten; JSON muss dort aktiviert sein.")
        val page = cursor?.token?.toIntOrNull() ?: 1
        val query = when (source) {
            SearchSource.REDDIT -> "site:reddit.com ${request.keywords}"
            SearchSource.YOUTUBE -> "site:youtube.com/watch ${request.keywords}"
            else -> request.keywords
        }
        val url = base.newBuilder().addPathSegment("search").addQueryParameter("q", query)
            .addQueryParameter("format", "json").addQueryParameter("pageno", page.toString())
            .addQueryParameter("language", "auto").addQueryParameter("safesearch", "1").build()
        val json = http.get(url)
        val rows = json.requireRows("results")
        val items = rows.mapIndexedNotNull { index, row ->
            val link = row.text("url")
            val parsed = link.toHttpUrlOrNull() ?: return@mapIndexedNotNull null
            val host = parsed.host.removePrefix("www.")
            if (source == SearchSource.REDDIT && host != "reddit.com" && !host.endsWith(".reddit.com")) return@mapIndexedNotNull null
            if (source == SearchSource.YOUTUBE && host != "youtube.com" && !host.endsWith(".youtube.com") && host != "youtu.be") return@mapIndexedNotNull null
            SearchItem(link, source, cleanHtml(row.text("title")), link, cleanHtml(row.text("content")),
                publishedAt = parseDate(row.text("publishedDate")), rank = (cursor?.offset ?: 0) + index + 1, context = "Suchindex · $host")
        }
        val next = if (rows.isNotEmpty() && page < 10) SearchCursor((page + 1).toString(), (cursor?.offset ?: 0) + rows.size) else null
        val warnings = json.array("unresponsive_engines").size
        return SourcePage(items, next, "Indexsuche über SearXNG. Datum nur bei bekanntem Veröffentlichungsdatum; keine garantierte Vollständigkeit." +
            if (warnings > 0) " $warnings Suchmaschinen nicht erreichbar." else "")
    }
}
