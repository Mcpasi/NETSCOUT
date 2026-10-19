package de.netscout.data

import de.netscout.core.*
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class PublicWebProviderTest {
    private val request = SearchRequest("Codex,mobile,Android", SearchSource.entries.toSet())
    private fun brave(url: String = "https://example.org/client", extra: String = "", text: String = "A mobile client for Codex. It runs on Android.") = """
        <html><body><nav>Navigation is not evidence</nav><main>
        <div class="snippet" data-type="ad"><a href="https://ads.example.org"><div class="title">Sponsored Codex mobile Android</div></a></div>
        <div class="snippet" data-type="web"><div class="result-content"><a href="$url"><div class="site-name-wrapper">Site name</div><div class="title">Codex mobile Android client</div></a>
        <div class="generic-snippet"><div class="content">$text</div></div></div>
        <div class="inline-qa"><a>Unrelated answer that must not be combined with the original passage.</a></div></div>
        $extra</main></body></html>
    """.trimIndent()
    private fun duck(query: String, extra: String = "") = """
        <html><body><div class="result"><a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.org%2Fclient%3Fa%3D1%26b%3D2">Codex mobile Android</a>
        <a class="result__snippet">A mobile client for Codex. It runs on Android.</a></div>
        <div class="result result--ad"><a class="result__a" href="https://ads.example.org">Advertising</a></div>
        $extra<form method="post" action="/html/"><input type="hidden" name="q" value='${query.replace("&", "&amp;")}'>
        <input type="hidden" name="s" value="30"><input type="hidden" name="vqd" value="opaque-state"><input type="hidden" name="dc" value="31"></form></body></html>
    """.trimIndent()
    private fun MockWebServer.respond(html: String, status: Int = 200) = enqueue(MockResponse().setResponseCode(status).setHeader("Content-Type", "text/html").setBody(html))
    private fun provider(server: MockWebServer, source: SearchSource = SearchSource.WEB, backend: PublicSearchBackend = PublicSearchBackend.BRAVE, endpoint: HttpUrl = server.url("/search"), http: JsonHttpClient = JsonHttpClient()) =
        HtmlSearchProvider(source, http, backend, endpoint)

    @Test fun `browser fallback preserves required keywords and platform restrictions`() {
        val url = publicSearchUrl(SearchSource.REDDIT, request).toHttpUrl()
        assertEquals("https", url.scheme)
        assertEquals("search.brave.com", url.host)
        assertEquals("site:reddit.com ${request.keywordQuery.remoteQuery}", url.queryParameter("q"))
        assertEquals("0", url.queryParameter("spellcheck"))
        assertEquals(request.keywordQuery.remoteQuery, publicSearchUrl(SearchSource.WEB, request).toHttpUrl().queryParameter("q"))
    }

    @Test fun `public search preserves all keywords original evidence and real continuation beyond ten pages`() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.respond(brave(extra = """<div class="pagination"><a href="/search?q=ignored&amp;offset=101">Next</a></div>"""))
            val page = provider(server).search(request, SearchCursor("100", 2000))
            assertEquals(SearchCursor("101", 2001), page.nextCursor)
            val call = server.takeRequest()
            assertEquals(request.keywordQuery.remoteQuery, call.requestUrl!!.queryParameter("q"))
            assertEquals("100", call.requestUrl!!.queryParameter("offset"))
            assertEquals("0", call.requestUrl!!.queryParameter("spellcheck"))
            assertNull(call.getHeader("Authorization"))
            assertEquals("text/html", call.getHeader("Accept"))
            assertEquals(1, page.items.size)
            assertEquals(2001, page.items.single().rank)
            assertEquals("A mobile client for Codex. It runs on Android.", page.items.single().searchText)
            assertFalse(page.items.single().searchText.contains("Unrelated answer"))
            assertNull(page.items.single().publishedAt)
            assertTrue(page.note!!.contains("Ohne Schlüssel"))
            assertEquals(1, ResultMerger.merge(request, page.items).size)
        }
    }

    @Test fun `Reddit and video searches validate destination domains and exclude profile and channel pages`() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val pages = listOf(
                "https://www.reddit.com/r/codex/comments/abc/client/",
                "https://evilreddit.com/r/codex/comments/abc/client/",
                "https://www.reddit.com/r/codex/",
                "https://www.youtube.com/watch?v=abc123",
                "https://www.youtube.com/@channel",
                "https://youtube.com.evil.example/watch?v=abc123",
            ).joinToString("") { brave(it) }
            for ((source, prefix, expected) in listOf(
                Triple(SearchSource.REDDIT, "site:reddit.com", "https://www.reddit.com/r/codex/comments/abc/client/"),
                Triple(SearchSource.YOUTUBE, "site:youtube.com/watch", "https://www.youtube.com/watch?v=abc123"),
            )) {
                server.respond(pages)
                val page = provider(server, source).search(request, null)
                assertEquals(expected, page.items.single().url)
                assertEquals("$prefix ${request.keywordQuery.remoteQuery}", server.takeRequest().requestUrl!!.queryParameter("q"))
            }
        }
    }

    @Test fun `explicit publication dates are retained while relative and unlabeled index dates remain unknown`() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            for ((label, date, expected) in listOf(
                Triple("Published:", "May 12, 2025", Instant.parse("2025-05-12T00:00:00Z")),
                Triple("Published:", "2 days ago", null), Triple("Updated:", "May 12, 2025", null),
            )) {
                server.respond(brave(text = """<p>Codex mobile Android video</p><div class="item-attributes"><div class="attr"><strong>$label</strong><span>$date</span></div></div>"""))
                assertEquals(expected, provider(server).search(request, null).items.single().publishedAt)
            }
        }
    }

    @Test fun `DuckDuckGo decodes destinations and carries returned page state without accepting another query`() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.respond(duck(request.keywordQuery.remoteQuery))
            val provider = provider(server, backend = PublicSearchBackend.DUCKDUCKGO)
            val first = provider.search(request, null)
            assertEquals("https://example.org/client?a=1&b=2", first.items.single().url)
            assertNotNull(first.nextCursor)
            server.takeRequest()
            server.respond(duck("different query"))
            val second = provider.search(request, first.nextCursor)
            val nextCall = server.takeRequest().requestUrl!!
            assertEquals("30", nextCall.queryParameter("s"))
            assertEquals("opaque-state", nextCall.queryParameter("vqd"))
            assertEquals(request.keywordQuery.remoteQuery, nextCall.queryParameter("q"))
            assertEquals("wt-wt", nextCall.queryParameter("kl"))
            assertNull(second.nextCursor)
        }
    }

    @Test fun `a captcha is a visible rate limit and is not mistaken for an empty search or immediately retried`() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.respond("<html><body><form id='challenge-form' action='/anomaly.js'>Verify</form></body></html>", 202)
            val provider = provider(server, backend = PublicSearchBackend.DUCKDUCKGO)
            repeat(2) {
                try { provider.search(request, null); fail("Expected a visible pause") }
                catch (failure: SourceException) { assertEquals(SourceStatus.RATE_LIMITED, failure.status) }
            }
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun `unrecognized markup fails visibly while an explicit empty response succeeds`() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.respond("<html><body>Unrecognized markup</body></html>")
            try { provider(server).search(request, null); fail() }
            catch (failure: SourceException) { assertEquals(SourceStatus.ERROR, failure.status) }
            server.respond("<html><main><div class='no-results'>No results found</div></main></html>")
            val page = provider(server).search(request, null)
            assertTrue(page.items.isEmpty())
            assertNull(page.nextCursor)
        }
    }

    @Test fun `fallback and subsequent pages stay on the responding service`() = runBlocking {
        MockWebServer().use { brave -> MockWebServer().use { duck ->
            brave.start(); duck.start()
            brave.respond("Blocked", 403)
            duck.respond(duck(request.keywordQuery.remoteQuery))
            val provider = PublicWebProvider(SearchSource.WEB, JsonHttpClient(), brave.url("/search"), duck.url("/html/").newBuilder().host("127.0.0.1").build())
            val first = provider.search(request, null)
            assertTrue(first.note!!.contains("Erster Suchdienst nicht verfügbar"))
            assertTrue(first.items.single().context.contains("DuckDuckGo"))
            assertTrue(first.nextCursor!!.token.startsWith("index:"))
            duck.respond("<html><body><div class='no-results'>No results found</div></body></html>")
            assertNull(provider.search(request, first.nextCursor).nextCursor)
            assertEquals(1, brave.requestCount)
            assertEquals(2, duck.requestCount)
        } }
    }

    @Test fun `pagination never follows external URLs or accepts malformed cursor state`() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.respond(brave(extra = """<div class="pagination"><a href="https://evil.example/search?offset=2">Next</a></div>"""))
            assertNull(provider(server).search(request, null).nextCursor)
            for (backend in PublicSearchBackend.entries) {
                try { provider(server, backend = backend).search(request, SearchCursor("https://evil.example")); fail() }
                catch (failure: SourceException) { assertEquals(SourceStatus.ERROR, failure.status) }
            }
            assertEquals(1, server.requestCount)
        }
    }
}
