package de.netscout.data

import de.netscout.core.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test

class ProviderFactoryTest {
    @Test fun `rejected optional API credentials and own index fall back without sending credentials to search engines`() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = if (request.getHeader("X-Test-Host") == "search.brave.com") {
                    MockResponse().setBody("""<html><main>
                        <div class="snippet" data-type="web"><a href="https://github.com/example/client"><div class="title">Kotlin Android app</div></a></div>
                        <div class="snippet" data-type="web"><a href="https://www.reddit.com/r/kotlin/comments/abc/client/"><div class="title">Kotlin Android discussion</div></a></div>
                        <div class="snippet" data-type="web"><a href="https://www.youtube.com/watch?v=abc123"><div class="title">Kotlin Android course</div></a></div>
                        </main></html>""")
                } else MockResponse().setResponseCode(401).setBody("private upstream failure")
            }
            server.start()
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                val original = chain.request()
                val local = original.url.newBuilder().scheme("http").host(server.hostName).port(server.port).build()
                chain.proceed(original.newBuilder().url(local).header("X-Test-Host", original.url.host).build())
            }.build()
            val settings = ApiSettings("https://own.example/", "private-youtube", "private-reddit", "private-github")
            val sources = setOf(SearchSource.REDDIT, SearchSource.YOUTUBE, SearchSource.GITHUB)
            val outcomes = SearchEngine(ProviderFactory.create(settings, JsonHttpClient(client))).search(SearchRequest("kotlin android", sources)).toList()
            assertTrue(outcomes.all { it.status == SourceStatus.SUCCESS && it.page.items.size == 1 })
            assertTrue(outcomes.all { it.page.note!!.contains("Eigener Suchdienst nicht verfügbar") })
            List(server.requestCount) { server.takeRequest() }.forEach { request ->
                val host = request.getHeader("X-Test-Host")!!
                if (host in setOf("own.example", "search.brave.com")) {
                    assertNull(request.getHeader("Authorization"))
                    assertNull(request.getHeader("X-Goog-Api-Key"))
                }
                assertFalse(request.path!!.contains("private-"))
            }
        }
    }

    @Test fun `empty settings make every source searchable without credentials`() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = MockResponse().setBody(when (request.getHeader("X-Test-Host")) {
                    "api.github.com" -> """{"items":[],"total_count":0}"""
                    "api.stackexchange.com" -> """{"items":[],"has_more":false,"quota_remaining":299}"""
                    "hn.algolia.com" -> """{"hits":[],"nbPages":0}"""
                    else -> """<html><body>
                        <div class="snippet" data-type="web"><a href="https://example.org/kotlin"><div class="title">Kotlin Android guide</div></a><div class="generic-snippet"><div class="content">Develop Android apps in Kotlin.</div></div></div>
                        <div class="snippet" data-type="web"><a href="https://www.reddit.com/r/kotlin/comments/abc/guide/"><div class="title">Kotlin Android discussion</div></a><div class="inline-qa-question">Develop Android apps in Kotlin.</div></div>
                        <div class="snippet" data-type="web"><a href="https://www.youtube.com/watch?v=abc123"><div class="title">Kotlin Android course</div></a><div class="video-snippet"><div class="content">Develop Android apps in Kotlin.</div></div></div>
                        </body></html>"""
                })
            }
            server.start()
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                val original = chain.request()
                val local = original.url.newBuilder().scheme("http").host(server.hostName).port(server.port).build()
                chain.proceed(original.newBuilder().url(local).header("X-Test-Host", original.url.host).build())
            }.build()
            val outcomes = SearchEngine(ProviderFactory.create(ApiSettings(), JsonHttpClient(client))).search(
                SearchRequest("kotlin android", SearchSource.entries.toSet()),
            ).toList()
            assertEquals(SearchSource.entries.toSet(), outcomes.map { it.source }.toSet())
            assertEquals(emptyList<Pair<SearchSource, SourceStatus>>(), outcomes.filter { it.status != SourceStatus.SUCCESS }.map { it.source to it.status })
            List(server.requestCount) { server.takeRequest() }.forEach { request ->
                assertNull(request.getHeader("Authorization"))
                assertNull(request.getHeader("X-Goog-Api-Key"))
            }
        }
    }
}
