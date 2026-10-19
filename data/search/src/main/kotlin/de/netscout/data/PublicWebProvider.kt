package de.netscout.data

import de.netscout.core.*
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Browser fallback keeps the original required terms and source restriction, without credentials. */
fun publicSearchUrl(source: SearchSource, request: SearchRequest): String = "https://search.brave.com/search".toHttpUrl().newBuilder()
    .addQueryParameter("q", indexQuery(source, request)).addQueryParameter("source", "web").addQueryParameter("spellcheck", "0").build().toString()

/** Public search pages, without an account, API key, shared secret or app quota. */
class PublicWebProvider(
    override val source: SearchSource,
    http: JsonHttpClient,
    braveEndpoint: HttpUrl = "https://search.brave.com/search".toHttpUrl(),
    duckEndpoint: HttpUrl = "https://html.duckduckgo.com/html/".toHttpUrl(),
) : SearchProvider {
    private val delegate = IndexFallbackProvider(
        HtmlSearchProvider(source, http, PublicSearchBackend.BRAVE, braveEndpoint),
        HtmlSearchProvider(source, http, PublicSearchBackend.DUCKDUCKGO, duckEndpoint),
        "Erster Suchdienst nicht verfügbar; ",
    )

    override suspend fun search(request: SearchRequest, cursor: SearchCursor?) = delegate.search(request, cursor)
}

internal enum class PublicSearchBackend(val label: String) { BRAVE("Brave"), DUCKDUCKGO("DuckDuckGo") }

internal class HtmlSearchProvider(
    override val source: SearchSource,
    private val http: JsonHttpClient,
    private val backend: PublicSearchBackend,
    private val endpoint: HttpUrl,
) : SearchProvider {
    override suspend fun search(request: SearchRequest, cursor: SearchCursor?): SourcePage {
        val query = indexQuery(source, request)
        val parameters = cursorParameters(cursor)
        val url = endpoint.newBuilder().addQueryParameter("q", query).apply {
            if (backend == PublicSearchBackend.BRAVE) {
                addQueryParameter("source", "web")
                addQueryParameter("spellcheck", "0")
                parameters["offset"]?.let { addQueryParameter("offset", it) }
            } else {
                addQueryParameter("kl", "wt-wt")
                parameters.forEach { (name, value) -> addQueryParameter(name, value) }
            }
        }.build()
        val html = try {
            withTimeoutOrNull(6_000) { http.getText(url, mapOf("Accept" to "text/html")) }
                ?: throw SourceException(SourceStatus.ERROR, "Öffentlicher Suchdienst antwortet zu langsam.")
        } catch (failure: SourceException) {
            if (failure.status != SourceStatus.SETUP_REQUIRED) throw failure
            http.postpone(endpoint.host, 600)
            throw SourceException(SourceStatus.RATE_LIMITED, "Öffentlicher Suchdienst blockiert die Anfrage vorübergehend. Bitte später erneut suchen.")
        }
        val document = Jsoup.parse(html, endpoint.toString())
        val rows = if (backend == PublicSearchBackend.BRAVE) document.select(".snippet[data-type=web]")
            else document.select(".result").filter { it.selectFirst(".result__a") != null && !it.hasClass("result--ad") }
        if (document.select(".anomaly-modal, #challenge-form, #captcha, form[action*=anomaly], form[action*=captcha]").isNotEmpty() ||
            rows.isEmpty() && Regex("captcha|verify (?:that )?you are human|security verification", RegexOption.IGNORE_CASE).containsMatchIn(document.title())) {
            http.postpone(endpoint.host, 600)
            throw SourceException(SourceStatus.RATE_LIMITED, "Suchdienst verlangt eine Browserprüfung. Bitte später erneut suchen oder einen eigenen Suchdienst verwenden.")
        }
        if (rows.isEmpty() && !emptyResults(document)) throw SourceException(SourceStatus.ERROR, "Suchdienst liefert keine lesbare Trefferliste. Bitte später erneut versuchen.")
        val items = rows.mapIndexedNotNull { index, row ->
            val titleNode = row.selectFirst(if (backend == PublicSearchBackend.BRAVE) ".title" else ".result__a") ?: return@mapIndexedNotNull null
            val anchor = if (backend == PublicSearchBackend.BRAVE) titleNode.closest("a[href]") else titleNode
            val link = resultUrl(anchor?.attr("href").orEmpty()) ?: return@mapIndexedNotNull null
            if (!acceptsIndexUrl(source, link)) return@mapIndexedNotNull null
            val title = titleNode.text().trim().take(700).takeIf(String::isNotBlank) ?: return@mapIndexedNotNull null
            // Answers, related links, site labels and navigation are not evidence for this result.
            val content = row.selectFirst(if (backend == PublicSearchBackend.BRAVE)
                ".generic-snippet .content, .video-snippet .content, .inline-qa-question" else ".result__snippet")
            val body = content?.let { fullText(it.html()) }.orEmpty()
            SearchItem(link.toString(), source, title, link.toString(), request.keywordQuery.snippet(body),
                publishedAt = publishedDate(row), rank = (cursor?.offset ?: 0) + index + 1,
                context = "${backend.label} · öffentlicher Suchindex · ${link.host}", searchText = body)
        }.distinctBy { it.url }
        val next = if (rows.isEmpty()) null else nextCursor(document, query, parameters, (cursor?.offset ?: 0) + rows.size)
        return SourcePage(items, next, "Ohne Schlüssel über ${backend.label}. " +
            (if (source == SearchSource.WEB) "Bewertet werden Titel und Index-Textstellen. " else "Öffentlich indexierte ${source.label}-Seiten; kein vollständiger Direktzugriff. ") +
            "Datum nur bei ausdrücklich bekanntem Veröffentlichungsdatum. Suchdienste können Anfragen begrenzen.")
    }

    private fun cursorParameters(cursor: SearchCursor?): Map<String, String> {
        if (cursor == null) return emptyMap()
        if (backend == PublicSearchBackend.BRAVE) {
            val offset = cursor.token.toIntOrNull()?.takeIf { it > 0 } ?: invalidCursor()
            return mapOf("offset" to offset.toString())
        }
        if (cursor.token.length > 8192) invalidCursor()
        val json = runCatching { Json.parseToJsonElement(cursor.token) as? JsonObject }.getOrNull() ?: invalidCursor()
        if (json.keys.any { it !in duckParameters } || json.text("s").toIntOrNull()?.takeIf { it > 0 } == null || json.values.any { it !is JsonPrimitive || !it.isString || it.content.length > 2048 }) invalidCursor()
        return json.keys.associateWith { json.text(it) }
    }

    private fun nextCursor(document: Document, query: String, parameters: Map<String, String>, rankOffset: Int): SearchCursor? {
        if (backend == PublicSearchBackend.BRAVE) {
            val current = parameters["offset"]?.toIntOrNull() ?: 0
            val next = document.select(".pagination a[href]").mapNotNull { anchor ->
                val link = endpoint.resolve(anchor.attr("href")) ?: return@mapNotNull null
                if (link.host != endpoint.host || link.port != endpoint.port || link.encodedPath != endpoint.encodedPath) return@mapNotNull null
                link.queryParameter("offset")?.toIntOrNull()?.takeIf { it > current }
            }.minOrNull() ?: return null
            return SearchCursor(next.toString(), rankOffset)
        }
        val current = parameters["s"]?.toIntOrNull() ?: 0
        val next = document.select("form").mapNotNull { form ->
            val fields = form.select("input[type=hidden][name]").associate { it.attr("name") to it.attr("value") }
            if (fields["q"] != query || (fields["s"]?.toIntOrNull() ?: 0) <= current) return@mapNotNull null
            fields.filterKeys { it in duckParameters }
        }.firstOrNull() ?: return null
        return SearchCursor(JsonObject(next.mapValues { JsonPrimitive(it.value) }).toString(), rankOffset)
    }

    private fun resultUrl(value: String): HttpUrl? {
        val resolved = endpoint.resolve(value) ?: return null
        return if ((resolved.host == "duckduckgo.com" || resolved.host.endsWith(".duckduckgo.com")) && resolved.encodedPath == "/l/") {
            resolved.queryParameter("uddg")?.toHttpUrlOrNull()
        } else resolved.takeUnless { it.host == endpoint.host }
    }

    private fun publishedDate(row: Element) = row.select(".item-attributes .attr").firstOrNull {
        it.selectFirst("strong")?.text()?.trim() == "Published:"
    }?.selectFirst("span")?.text()?.let { text ->
        // Relative dates and generic index dates can be crawl/update dates; keep them unknown.
        runCatching { LocalDate.parse(text, DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.ENGLISH)).atStartOfDay().toInstant(ZoneOffset.UTC) }.getOrNull()
    }

    private fun emptyResults(document: Document): Boolean = document.select(".no-results, .no-results__message, #no-results").isNotEmpty() ||
        Regex("^(No results found|No results for|No matching results|Keine Ergebnisse)", RegexOption.IGNORE_CASE).containsMatchIn(document.selectFirst("main")?.text().orEmpty())

    private fun invalidCursor(): Nothing = throw SourceException(SourceStatus.ERROR, "Ungültige Suchseite. Bitte die Suche erneut starten.")

    private companion object {
        val duckParameters = setOf("s", "dc", "vqd", "nextParams", "v", "o", "api")
    }
}
