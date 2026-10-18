package de.netscout.data

import de.netscout.core.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class ForumsProvider(
    private val http: JsonHttpClient,
    private val baseUrl: HttpUrl = "https://api.stackexchange.com/2.3/".toHttpUrl(),
) : SearchProvider {
    override val source = SearchSource.FORUMS
    private val sites = listOf("stackoverflow", "superuser", "serverfault")
    override suspend fun search(request: SearchRequest, cursor: SearchCursor?): SourcePage = coroutineScope {
        val page = cursor?.token?.toIntOrNull() ?: 1
        val responses = sites.map { site -> async {
            try {
                val url = baseUrl.newBuilder().addPathSegments("search/advanced")
                    .addQueryParameter("site", site).addQueryParameter("q", request.keywords)
                    .addQueryParameter("page", page.toString()).addQueryParameter("pagesize", "15")
                    .addQueryParameter("filter", "withbody").addQueryParameter("order", "desc")
                    .addQueryParameter("sort", if (request.sort == SortOrder.NEWEST) "creation" else "relevance")
                    .apply {
                        request.startInstant?.let { addQueryParameter("fromdate", it.epochSecond.toString()) }
                        request.endExclusive?.let { addQueryParameter("todate", (it.epochSecond - 1).toString()) }
                    }.build()
                val json = http.get(url)
                if (json.number("backoff") > 0) http.postpone(baseUrl.host, json.number("backoff").toLong())
                if (json.number("error_id") != 0) throw SourceException(SourceStatus.RATE_LIMITED, "Stack Exchange verlangt eine Pause oder hat die Anfrage abgelehnt.")
                json.requireRows("items")
                Result.success(json)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { Result.failure(failure) }
        } }.awaitAll()
        val successes = responses.mapNotNull { it.getOrNull() }
        if (successes.isEmpty()) throw responses.first().exceptionOrNull()!!
        val items = responses.flatMapIndexed { siteIndex, response ->
            response.getOrNull()?.rows("items").orEmpty().mapIndexed { index, row ->
                SearchItem("${sites[siteIndex]}:${row.text("question_id")}", source, cleanHtml(row.text("title")), row.text("link"),
                    cleanHtml(row.text("body")), cleanHtml(row.obj("owner").text("display_name")), epoch(row.decimal("creation_date")),
                    (cursor?.offset ?: 0) + index * sites.size + siteIndex + 1, sites[siteIndex], row.text("content_license").ifBlank { "CC BY-SA" })
            }
        }
        val next = if (successes.any { it.flag("has_more") }) SearchCursor((page + 1).toString(), (cursor?.offset ?: 0) + 45) else null
        val limited = responses.count { it.isFailure }
        val quota = successes.minOf { it.number("quota_remaining") }
        SourcePage(items, next, "Stack Overflow · Super User · Server Fault. " +
            (if (limited > 0) "$limited Foren nicht erreichbar. " else "") +
            (if (successes.any { it.number("backoff") > 0 }) "API-Pause vor nächster Seite. " else "") + "API-Restkontingent: $quota.")
    }
}
