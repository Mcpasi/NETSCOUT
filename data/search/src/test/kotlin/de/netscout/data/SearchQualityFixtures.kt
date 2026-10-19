package de.netscout.data

import de.netscout.core.*
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

/** Fixed, labelled inputs at the real provider boundary, independent of live index changes. */
object SearchQualityFixtures {
    data class TextCase(val id: String, val provider: String, val keywords: String, val title: String, val body: String, val relevant: Boolean) {
        val source get() = when (provider) {
            "reddit" -> SearchSource.REDDIT
            "hacker_news" -> SearchSource.HACKER_NEWS
            "youtube" -> SearchSource.YOUTUBE
            else -> SearchSource.GITHUB
        }
        val request get() = SearchRequest(keywords, setOf(source))
        override fun toString() = id
    }

    fun textCases(): List<TextCase> {
        val input = checkNotNull(javaClass.getResourceAsStream("/search-quality/text-cases.json"))
        return input.bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonArray }.map { value ->
            val row = value.jsonObject
            TextCase(row.text("id"), row.text("provider"), row.text("keywords"), row.text("title"), row.text("body"), row.flag("relevant"))
        }
    }

    suspend fun retrieve(case: TextCase): SourcePage = MockWebServer().use { server ->
        fun row(vararg fields: Pair<String, String>) = buildJsonObject {
            fields.forEach { (key, value) -> put(key, value) }
        }
        val item = when (case.provider) {
            "github_repository", "github_issue" -> row("id" to case.id, "full_name" to case.title, "title" to case.title,
                "description" to case.body, "body" to case.body, "html_url" to "https://github.com/example/${case.id}")
            "reddit" -> buildJsonObject { put("data", row("name" to case.id, "title" to case.title,
                "selftext" to case.body, "permalink" to "/r/kotlin/comments/${case.id}/example/")) }
            "hacker_news" -> row("objectID" to case.id, "title" to case.title, "story_text" to case.body)
            "youtube" -> buildJsonObject {
                put("id", row("videoId" to case.id))
                put("snippet", row("title" to case.title, "description" to case.body))
            }
            else -> error("Unknown provider: ${case.provider}")
        }
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val response = when (case.provider) {
                    "github_repository", "github_issue" -> buildJsonObject {
                        val url = request.requestUrl!!
                        val selected = if (case.provider == "github_repository") url.encodedPath.endsWith("repositories")
                            else url.queryParameter("q")!!.contains("is:issue")
                        put("items", JsonArray(if (selected) listOf(item) else emptyList()))
                        put("total_count", if (selected) 1 else 0)
                    }
                    "reddit" -> buildJsonObject { put("data", buildJsonObject { put("children", JsonArray(listOf(item))) }) }
                    "hacker_news" -> buildJsonObject { put("hits", JsonArray(listOf(item))); put("nbPages", 1) }
                    else -> buildJsonObject { put("items", JsonArray(listOf(item))) }
                }
                return MockResponse().setHeader("Content-Type", "application/json").setBody(response.toString())
            }
        }
        server.start()
        val http = JsonHttpClient()
        val provider: SearchProvider = when (case.provider) {
            "reddit" -> RedditProvider(http, "fixture-token", server.url("/"))
            "hacker_news" -> HackerNewsProvider(http, server.url("/"))
            "youtube" -> YouTubeProvider(http, "fixture-key", server.url("/"))
            else -> GitHubProvider(http, baseUrl = server.url("/"))
        }
        provider.search(case.request, null).also { check(it.items.size == 1) { "Fixture ${case.id} must return one candidate" } }
    }

    data class UrlCase(val id: String, val first: String, val second: String, val same: Boolean) {
        override fun toString() = id
    }
    fun urlCases() = listOf(
        UrlCase("tracking", "https://example.org/article?utm_source=a", "https://example.org/article?utm_source=b", true),
        UrlCase("fragment", "https://example.org/article#one", "https://example.org/article#two", true),
        UrlCase("parameter-order", "https://example.org/article?a=1&b=2", "https://example.org/article?b=2&a=1", true),
        UrlCase("default-port", "https://example.org:443/article", "https://example.org/article", true),
        UrlCase("repeated-sort", "https://example.org/article?sort=title&sort=date", "https://example.org/article?sort=date&sort=title", false),
        UrlCase("repeated-filter", "https://example.org/article?filter=first&filter=last", "https://example.org/article?filter=last&filter=first", false),
        UrlCase("http-nondefault-port", "http://example.org:443/article", "https://example.org/article", false),
        UrlCase("https-nondefault-port", "https://example.org:80/article", "http://example.org/article", false),
    )
    val urlRequest = SearchRequest("kotlin,android", setOf(SearchSource.WEB))
    fun mergeUrls(case: UrlCase) = ResultMerger.merge(urlRequest, listOf(case.first, case.second).mapIndexed { index, url ->
        SearchItem("${case.id}-$index", SearchSource.WEB, "Kotlin on Android", url)
    })

    data class ReliabilityCase(val id: String, val brave: String, val duck: String, val expected: SourceStatus,
        val expectedItems: Int, val expectedDuckCalls: Int, val source: SearchSource = SearchSource.WEB, val braveStatus: Int = 200) {
        override fun toString() = id
    }
    private const val BRAVE_ROW = "<div class='snippet' data-type='web'><a href='https://example.org/article'><div class='title'>Kotlin on Android</div></a><div class='generic-snippet'><div class='content'>Kotlin on Android</div></div></div>"
    private const val DUCK_ROW = "<div class='result'><a class='result__a' href='https://example.org/article'>Kotlin on Android</a><div class='result__snippet'>Kotlin on Android</div></div>"
    fun reliabilityCases() = listOf(
        ReliabilityCase("normal", BRAVE_ROW, DUCK_ROW, SourceStatus.SUCCESS, 1, 0),
        ReliabilityCase("explicit-empty", "<main class='no-results'>No results found</main>", DUCK_ROW, SourceStatus.SUCCESS, 0, 0),
        ReliabilityCase("challenge-fallback", "<form id='challenge-form'></form>", DUCK_ROW, SourceStatus.SUCCESS, 1, 1),
        ReliabilityCase("http-fallback", "Forbidden", DUCK_ROW, SourceStatus.SUCCESS, 1, 1, braveStatus = 403),
        ReliabilityCase("missing-title-fallback", BRAVE_ROW.replace("class='title'", "class='changed-title'"), DUCK_ROW, SourceStatus.SUCCESS, 1, 1),
        ReliabilityCase("missing-link-fallback", BRAVE_ROW.replace("href=", "data-href="), DUCK_ROW, SourceStatus.SUCCESS, 1, 1),
        ReliabilityCase("scope-exclusion", BRAVE_ROW, DUCK_ROW, SourceStatus.SUCCESS, 0, 0, SearchSource.REDDIT),
        ReliabilityCase("unreadable-final-service", "Forbidden", "<div class='result'><a class='result__a'>Kotlin on Android</a></div>", SourceStatus.ERROR, 0, 1, braveStatus = 403),
    )
    data class ReliabilityResult(val status: SourceStatus, val items: Int, val duckCalls: Int)
    suspend fun reliability(case: ReliabilityCase): ReliabilityResult = MockWebServer().use { server ->
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = if (request.path!!.startsWith("/brave"))
                MockResponse().setResponseCode(case.braveStatus).setBody(case.brave)
            else MockResponse().setBody(case.duck)
        }
        server.start()
        // The production services have different hosts and independent cooldowns.
        val duck = server.url("/duck").newBuilder().host("127.0.0.1").build()
        val provider = PublicWebProvider(case.source, JsonHttpClient(), server.url("/brave"), duck)
        val outcome = try {
            val page = provider.search(SearchRequest("kotlin,android", setOf(case.source)), null)
            SourceStatus.SUCCESS to page.items.size
        } catch (failure: SourceException) { failure.status to 0 }
        val requests = List(server.requestCount) { server.takeRequest() }
        ReliabilityResult(outcome.first, outcome.second, requests.count { it.path!!.startsWith("/duck") })
    }
}
