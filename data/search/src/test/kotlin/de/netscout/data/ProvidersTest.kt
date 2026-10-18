package de.netscout.data

import de.netscout.core.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ProvidersTest {
    private lateinit var server: MockWebServer
    private lateinit var http: JsonHttpClient
    private val request = SearchRequest("kotlin & android", SearchSource.entries.toSet(), LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-02"), SortOrder.NEWEST)
    @Before fun setup() { server = MockWebServer(); server.start(); http = JsonHttpClient() }
    @After fun teardown() { server.shutdown() }
    private fun respond(body: String) { server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(body)) }

    @Test fun `HN uses date search UTC range encoded keywords and pagination`() = runBlocking {
        respond("""{"hits":[{"objectID":"123","title":"Kotlin &amp; Android","author":"alice","created_at_i":1767225600,"points":5}],"nbPages":2}""")
        val page = HackerNewsProvider(http, server.url("/api/v1/")).search(request, null)
        val url = server.takeRequest().requestUrl!!
        assertEquals("/api/v1/search_by_date", url.encodedPath)
        assertEquals("\"kotlin\" \"android\"", url.queryParameter("query"))
        assertEquals("false", url.queryParameter("typoTolerance"))
        assertEquals("prefixNone", url.queryParameter("queryType"))
        assertEquals("none", url.queryParameter("removeWordsIfNoResults"))
        assertEquals("title,story_text", url.queryParameter("restrictSearchableAttributes"))
        assertEquals("created_at_i>=1767225600,created_at_i<1767398400", url.queryParameter("numericFilters"))
        assertEquals("Kotlin & Android", page.items.single().title)
        assertEquals(SearchCursor("1", 1), page.nextCursor)
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), page.items.single().publishedAt)
    }
    @Test fun `GitHub searches public posts with created date and optional scoped token`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = when {
                    request.requestUrl!!.encodedPath == "/search/repositories" -> """{"items":[{"id":2,"full_name":"sample/kotlin-android","html_url":"https://github.com/sample/kotlin-android","description":"Kotlin Android app","owner":{"login":"sample"},"created_at":"2026-01-01T00:00:00Z"}],"total_count":1}"""
                    request.requestUrl!!.queryParameter("q")!!.contains("is:issue") -> """{"items":[{"id":1,"title":"Kotlin","html_url":"https://github.com/a/b/issues/1","body":"<b>Test</b>","user":{"login":"alice"},"created_at":"2026-01-01T00:00:00Z"}],"total_count":31,"incomplete_results":true}"""
                    else -> """{"items":[],"total_count":0,"incomplete_results":false}"""
                }
                return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
            }
        }
        val page = GitHubProvider(http, "test-token", server.url("/")).search(request, null)
        val calls = List(3) { server.takeRequest() }
        assertTrue(calls.all { it.getHeader("Authorization") == "Bearer test-token" })
        val keywords = "\"kotlin\" \"android\" is:public "
        assertEquals(setOf(keywords + "in:name,description created:2026-01-01..2026-01-02", keywords + "is:issue in:title,body created:2026-01-01..2026-01-02", keywords + "is:pull-request in:title,body created:2026-01-01..2026-01-02"), calls.map { it.requestUrl!!.queryParameter("q") }.toSet())
        assertTrue(calls.all { it.requestUrl!!.queryParameter("sort") == if (it.requestUrl!!.encodedPath.endsWith("repositories")) "updated" else "created" })
        assertEquals("Test", page.items.single { it.context == "Issue" }.snippet)
        assertEquals("sample/kotlin-android", page.items.single { it.context == "Repository" }.title)
        assertEquals("2", page.nextCursor!!.token)
        assertTrue(page.note!!.contains("unvollständige"))
    }
    @Test fun `YouTube sends key in header and preserves backend page tokens`() = runBlocking {
        respond("""{"items":[{"id":{"videoId":"abc_123"},"snippet":{"title":"Kotlin &amp; Android","description":"Demo","channelTitle":"Channel","publishedAt":"2026-01-01T00:00:00Z"}}],"nextPageToken":"next-token"}""")
        val page = YouTubeProvider(http, "test-key", server.url("/youtube/v3/")).search(request, SearchCursor("current-token", 25))
        val recorded = server.takeRequest()
        assertEquals("test-key", recorded.getHeader("X-Goog-Api-Key"))
        assertEquals("\"kotlin\" \"android\"", recorded.requestUrl!!.queryParameter("q"))
        assertNull(recorded.requestUrl!!.queryParameter("key"))
        assertEquals("current-token", recorded.requestUrl!!.queryParameter("pageToken"))
        assertEquals("2026-01-03T00:00:00Z", recorded.requestUrl!!.queryParameter("publishedBefore"))
        assertEquals("2025-12-31T23:59:59.999999999Z", recorded.requestUrl!!.queryParameter("publishedAfter"))
        assertEquals(SearchCursor("next-token", 26), page.nextCursor)
        assertEquals(26, page.items.single().rank)
    }
    @Test fun `Reddit uses official OAuth endpoint and after cursor`() = runBlocking {
        respond("""{"data":{"children":[{"data":{"name":"t3_one","title":"Kotlin","permalink":"/r/kotlin/comments/one/demo/","selftext":"Body","author":"bob","created_utc":1767225600,"subreddit_name_prefixed":"r/kotlin"}}],"after":"t3_two"}}""")
        val page = RedditProvider(http, "test-oauth", server.url("/")).search(request, SearchCursor("t3_before", 25))
        val recorded = server.takeRequest()
        assertEquals("Bearer test-oauth", recorded.getHeader("Authorization"))
        assertEquals("\"kotlin\" AND \"android\"", recorded.requestUrl!!.queryParameter("q"))
        assertEquals("t3_before", recorded.requestUrl!!.queryParameter("after"))
        assertEquals("new", recorded.requestUrl!!.queryParameter("sort"))
        assertEquals(SearchCursor("t3_two", 26), page.nextCursor)
        assertTrue(page.note!!.contains("lokal"))
    }
    @Test fun `forums search three sites preserve author and license and report partial failure`() = runBlocking {
        repeat(2) { respond("""{"items":[{"question_id":1,"title":"Kotlin &amp; Android","link":"https://stackoverflow.com/q/1","body":"<p>Example</p>","owner":{"display_name":"Alice"},"creation_date":1767225600,"content_license":"CC BY-SA 4.0"}],"quota_remaining":250,"has_more":true}""") }
        server.enqueue(MockResponse().setResponseCode(403))
        val page = ForumsProvider(http, server.url("/2.3/")).search(request, null)
        val calls = List(3) { server.takeRequest().requestUrl!! }
        assertEquals(setOf("stackoverflow", "superuser", "serverfault"), calls.map { it.queryParameter("site") }.toSet())
        assertTrue(calls.all { it.queryParameter("todate") == "1767398399" })
        assertTrue(calls.all { it.queryParameter("q") == "\"kotlin\" \"android\"" })
        assertEquals(2, page.items.size)
        assertEquals("Alice", page.items.first().author)
        assertEquals("CC BY-SA 4.0", page.items.first().license)
        assertTrue(page.note!!.contains("1 Foren nicht erreichbar"))
        assertNotNull(page.nextCursor)
    }
    @Test fun `search index enforces source domain despite engine ignoring site operator`() = runBlocking {
        respond("""{"results":[{"url":"https://evilreddit.com/post","title":"Wrong"},{"url":"https://www.reddit.com/r/kotlin/comments/one/","title":"Right","content":"<b>Kotlin</b>","publishedDate":"2026-01-02"}],"unresponsive_engines":[["engine","timeout"]]}""")
        val page = SearxngProvider(SearchSource.REDDIT, http, server.url("/").toString()).search(request, null)
        assertEquals(1, page.items.size)
        assertEquals("Right", page.items.single().title)
        assertEquals(Instant.parse("2026-01-02T00:00:00Z"), page.items.single().publishedAt)
        assertTrue(page.note!!.contains("1 Suchmaschinen"))
        val recorded = server.takeRequest()
        assertEquals("site:reddit.com \"kotlin\" \"android\"", recorded.requestUrl!!.queryParameter("q"))
        assertEquals("all", recorded.requestUrl!!.queryParameter("language"))
        assertNull(recorded.getHeader("Accept-Language"))
    }
    @Test fun `missing credentials request no network access`() = runBlocking {
        try { YouTubeProvider(http, "").search(request, null); fail() }
        catch (failure: SourceException) { assertEquals(SourceStatus.SETUP_REQUIRED, failure.status) }
        try { RedditProvider(http, "").search(request, null); fail() }
        catch (failure: SourceException) { assertEquals(SourceStatus.SETUP_REQUIRED, failure.status) }
        assertEquals(0, server.requestCount)
    }
    @Test fun `settings accept HTTPS subpaths and reject credentials cleartext and query`() {
        assertNull(ApiSettings(searxngUrl = "https://example.org/searx/").validationError())
        listOf("http://example.org", "https://user:password@example.org", "https://example.org?key=secret", "not a url").forEach {
            assertNotNull(ApiSettings(searxngUrl = it).validationError())
        }
        assertFalse(ApiSettings(youtubeApiKey = "secret-key").toString().contains("secret-key"))
    }
    @Test fun `invalid API shape is an explicit error rather than a false empty result`() = runBlocking {
        respond("""{"unexpected":"schema-changed"}""")
        try { HackerNewsProvider(http, server.url("/")).search(request, null); fail() }
        catch (failure: SourceException) { assertEquals(SourceStatus.ERROR, failure.status) }
    }
    @Test fun `user comma query is sent as three required words and finds body evidence after 700 characters`() = runBlocking {
        val text = "Intro. ".repeat(200) + "Codex mobile client for Android"
        respond("""{"hits":[{"objectID":"123","title":"Release notes","story_text":"$text","created_at_i":1767225600}],"nbPages":1}""")
        val input = request.copy(keywords = "Codex,mobile,android")
        val page = HackerNewsProvider(http, server.url("/api/v1/")).search(input, null)
        assertEquals("\"Codex\" \"mobile\" \"android\"", server.takeRequest().requestUrl!!.queryParameter("query"))
        assertTrue(page.items.single().searchText.length > 700)
        assertTrue(page.items.single().snippet.contains("Codex mobile client for Android"))
        assertEquals(1, ResultMerger.merge(input, page.items).size)
    }
}
