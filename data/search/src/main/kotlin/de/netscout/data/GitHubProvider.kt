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
        val query = request.keywordQuery
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
        val kinds = listOf("repository", "is:issue", "is:pull-request")
        val responses = kinds.map { kind -> async {
            try {
                val scope = if (kind == "repository") "in:name,description" else "$kind in:title,body"
                val url = baseUrl.newBuilder().addPathSegments(if (kind == "repository") "search/repositories" else "search/issues")
                    .addQueryParameter("q", "${query.remoteQuery} is:public $scope $date".trim())
                    .addQueryParameter("page", page.toString()).addQueryParameter("per_page", "30")
                    .apply { if (request.sort == SortOrder.NEWEST) addQueryParameter("sort", if (kind == "repository") "updated" else "created").addQueryParameter("order", "desc") }.build()
                Result.success(http.get(url, headers).also { it.requireRows("items") })
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { Result.failure(failure) }
        } }.awaitAll()
        val successes = responses.mapNotNull { it.getOrNull() }
        if (successes.isEmpty()) throw responses.first().exceptionOrNull()!!
        val items = responses.flatMapIndexed { kindIndex, response ->
            response.getOrNull()?.rows("items").orEmpty().mapIndexed { index, row ->
                val repository = kindIndex == 0
                // Repository descriptions are plain text; issue/PR bodies are Markdown.
                val body = if (repository) row.text("description") else markdownText(row.text("body"))
                SearchItem(row.text("id"), source, row.text(if (repository) "full_name" else "title"), row.text("html_url"), query.snippet(body),
                    row.obj(if (repository) "owner" else "user").text("login"), parseDate(row.text("created_at")), (cursor?.offset ?: 0) + index * kinds.size + kindIndex + 1,
                    when (kindIndex) { 0 -> "Repository"; 1 -> "Issue"; else -> "Pull Request" }, searchText = body)
            }
        }
        val next = if (successes.any { it.rows("items").isNotEmpty() && page * 30 < minOf(it.number("total_count"), 1_000) })
            SearchCursor((page + 1).toString(), (cursor?.offset ?: 0) + 90) else null
        val note = "Öffentliche Repositories, Issues und Pull Requests; alle Sprachen. Drei API-Abfragen pro Seite. " +
            (if (responses.any { it.isFailure }) "Teilabfrage fehlgeschlagen. " else "") +
            (if (successes.any { it.flag("incomplete_results") }) "GitHub meldet unvollständige Ergebnisse. Suchbegriffe eingrenzen." else "Kein Code-Suchindex.")
        SourcePage(items, next, note)
    }
}
