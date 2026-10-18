package de.netscout.data

import de.netscout.core.SourceException
import de.netscout.core.SourceStatus
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant

class JsonHttpClientTest {
    private lateinit var server: MockWebServer
    @Before fun setup() { server = MockWebServer(); server.start() }
    @After fun teardown() { server.shutdown() }
    @Test fun `rate limited host is not retried until Retry-After has expired`() = runBlocking {
        var time = Instant.parse("2026-01-01T00:00:00Z")
        val http = JsonHttpClient(now = { time })
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "120"))
        repeat(2) {
            try { http.get(server.url("/search")); fail() }
            catch (failure: SourceException) { assertEquals(SourceStatus.RATE_LIMITED, failure.status) }
        }
        assertEquals(1, server.requestCount)
        time = time.plusSeconds(121)
        server.enqueue(MockResponse().setBody("{\"ok\":true}"))
        assertTrue(http.get(server.url("/search")).flag("ok"))
        assertEquals(2, server.requestCount)
    }
    @Test fun `temporary server failure gets exactly one retry`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setBody("{\"ok\":true}"))
        assertTrue(JsonHttpClient().get(server.url("/search")).flag("ok"))
        assertEquals(2, server.requestCount)
    }
    @Test fun `unauthorized response is setup error without exposing server body`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("secret-token"))
        try { JsonHttpClient().get(server.url("/search")); fail() }
        catch (failure: SourceException) {
            assertEquals(SourceStatus.SETUP_REQUIRED, failure.status)
            assertFalse(failure.message.contains("secret-token"))
        }
        assertEquals(1, server.requestCount)
    }
    @Test fun `oversized response is rejected before JSON parsing`() = runBlocking {
        server.enqueue(MockResponse().setBody("a".repeat(2_097_153)))
        try { JsonHttpClient().get(server.url("/search")); fail() }
        catch (failure: SourceException) { assertEquals(SourceStatus.ERROR, failure.status) }
    }
}
