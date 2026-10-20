package de.netscout.data

import de.netscout.core.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.*
import org.junit.Test

class IndexFallbackProviderTest {
    private val request = SearchRequest("kotlin", setOf(SearchSource.REDDIT))
    private fun provider(action: suspend (SearchCursor?) -> SourcePage) = object : SearchProvider {
        override val source = SearchSource.REDDIT
        override suspend fun search(request: SearchRequest, cursor: SearchCursor?) = action(cursor)
    }
    @Test fun `an unresponsive first page leaves time for the public fallback`() = runTest {
        var indexCalls = 0
        val fallback = IndexFallbackProvider(
            provider { delay(30_000); SourcePage(emptyList()) },
            provider { indexCalls++; SourcePage(emptyList(), notes = listOf(SearchMessage(MessageKey.SEARX_NOTE))) },
        )
        val page = withTimeoutOrNull(18_000) { fallback.search(request, null) }
        assertNotNull("The primary must leave time for the fallback within the engine deadline", page)
        assertEquals(1, indexCalls)
    }
    @Test fun `fallback cursors return to index and never reach the direct provider`() = runBlocking {
        var directCalls = 0
        val indexCursors = mutableListOf<SearchCursor?>()
        val fallback = IndexFallbackProvider(
            provider { directCalls++; throw SourceException(SourceStatus.RATE_LIMITED, SearchMessage(MessageKey.SOURCE_PAUSE_RETRY)) },
            provider { cursor -> indexCursors += cursor; SourcePage(emptyList(), if (cursor == null) SearchCursor("2", 20) else null, listOf(SearchMessage(MessageKey.SEARX_NOTE))) },
        )
        val first = fallback.search(request, null)
        assertTrue(first.note!!.contains("Direktzugriff fehlgeschlagen"))
        assertEquals(SearchCursor("index:2", 20), first.nextCursor)
        val second = fallback.search(request, first.nextCursor)
        assertNull(second.nextCursor)
        assertEquals(1, directCalls)
        assertEquals(listOf(null, SearchCursor("2", 20)), indexCursors)
    }
    @Test fun `a failed direct continuation never switches to an unrelated index page`() = runBlocking {
        var indexCalls = 0
        val fallback = IndexFallbackProvider(provider { error("failure") }, provider { indexCalls++; SourcePage(emptyList()) })
        try { fallback.search(request, SearchCursor("reddit-after", 25)); fail() }
        catch (_: IllegalStateException) { assertEquals(0, indexCalls) }
    }
    @Test fun `cancellation propagates without spending a fallback request`() = runBlocking {
        var indexCalls = 0
        val fallback = IndexFallbackProvider(provider { throw CancellationException() }, provider { indexCalls++; SourcePage(emptyList()) })
        try { fallback.search(request, null); fail() }
        catch (_: CancellationException) { assertEquals(0, indexCalls) }
    }
}
