package de.netscout.data

import de.netscout.core.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class GitHubProvider(
    private val http: JsonHttpClient,
    private val token: String = "",
    private val baseUrl: HttpUrl = "https://api.github.com/".toHttpUrl(),
) : SearchProvider {
    override val source = SearchSource.GITHUB
    override suspend fun search(request: SearchRequest, cursor: SearchCursor?): SourcePage = coroutineScope {
        val page = cursor?.token?.toIntOrNull() ?: 1
        val date = when {
            request.from != null && request.until != null -> "created:${request.from}..${request.until}"
            request.from != null -> "created:>=${request.from}"
            request.until != null -> "created:<=${request.until}"
            else -> ""
        }
        val headers = buildMap {
            put("X-GitHub-Api-Version", "2022-11-28")
            if (token.isNotBlank()) put("Authorization", "Bearer $token")
        }
        // GitHub App user tokens require an explicit kind; separate queries also work anonymously.
        val responses = listOf("is:issue", "is:pull-request").map { kind -> async {
            try {
                val url = baseUrl.newBuilder().addPathSegments("search/issues")
                    .addQueryParameter("q", "${request.keywords} is:public $kind $date".trim())
                    .addQueryParameter("page", page.toString()).addQueryParameter("per_page", "30")
                    .apply { if (request.sort == SortOrder.NEWEST) addQueryParameter("sort", "created").addQueryParameter("order", "desc") }.build()
                Result.success(http.get(url, headers).also { it.requireRows("items") })
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { Result.failure(failure) }
        } }.awaitAll()
        val successes = responses.mapNotNull { it.getOrNull() }
        if (successes.isEmpty()) throw responses.first().exceptionOrNull()!!
        val items = responses.flatMapIndexed { kindIndex, response ->
            response.getOrNull()?.rows("items").orEmpty().mapIndexed { index, row ->
                SearchItem(row.text("id"), source, row.text("title"), row.text("html_url"), cleanHtml(row.text("body")),
                    row.obj("user").text("login"), parseDate(row.text("created_at")), (cursor?.offset ?: 0) + index * 2 + kindIndex + 1,
                    if (kindIndex == 1) "Pull Request" else "Issue")
            }
        }
        val next = if (successes.any { it.rows("items").isNotEmpty() && page * 30 < minOf(it.number("total_count"), 1_000) })
            SearchCursor((page + 1).toString(), (cursor?.offset ?: 0) + 60) else null
        val note = "Öffentliche Issues und Pull Requests; zwei API-Abfragen pro Seite. " +
            (if (responses.any { it.isFailure }) "Teilabfrage fehlgeschlagen. " else "") +
            (if (successes.any { it.flag("incomplete_results") }) "GitHub meldet unvollständige Ergebnisse. Suchbegriffe eingrenzen." else "Kein Code-Suchindex.")
        SourcePage(items, next, note)
    }
}
