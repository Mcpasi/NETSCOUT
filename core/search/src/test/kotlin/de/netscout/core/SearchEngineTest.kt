package de.netscout.core

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchEngineTest {
    private fun provider(source: SearchSource, action: suspend () -> SourcePage) = object : SearchProvider {
        override val source = source
        override suspend fun search(request: SearchRequest, cursor: SearchCursor?) = action()
    }
    private val request = SearchRequest("kotlin", setOf(SearchSource.GITHUB, SearchSource.HACKER_NEWS))

    @Test fun `source failure does not cancel other sources and results arrive independently`() = runTest {
        val engine = SearchEngine(listOf(
            provider(SearchSource.GITHUB) { delay(2_000); SourcePage(emptyList()) },
            provider(SearchSource.HACKER_NEWS) { throw SourceException(SourceStatus.RATE_LIMITED, "Pause") },
        ))
        val results = mutableListOf<SourceOutcome>()
        val job = launch { engine.search(request).toList(results) }
        runCurrent()
        assertEquals(SearchSource.HACKER_NEWS, results.single().source)
        assertEquals(SourceStatus.RATE_LIMITED, results.single().status)
        advanceUntilIdle(); job.join()
        assertEquals(SourceStatus.SUCCESS, results.last().status)
        assertEquals(2_000, currentTime)
    }
    @Test fun `successful pages are cached normalized and expire`() = runTest {
        var calls = 0; var clock = 0L
        val engine = SearchEngine(listOf(provider(SearchSource.GITHUB) { calls++; SourcePage(emptyList()) }), nowMillis = { clock })
        val input = request.copy(sources = setOf(SearchSource.GITHUB))
        assertFalse(engine.search(input).toList().single().cached)
        assertTrue(engine.search(input.copy(keywords = "  kotlin  ")).toList().single().cached)
        assertEquals(1, calls)
        clock = 60_001
        assertFalse(engine.search(input).toList().single().cached)
        assertEquals(2, calls)
    }
    @Test fun `errors are not cached and exception details are redacted`() = runTest {
        var calls = 0
        val engine = SearchEngine(listOf(provider(SearchSource.GITHUB) { calls++; error("https://secret?key=private") }))
        repeat(2) {
            val outcome = engine.search(request.copy(sources = setOf(SearchSource.GITHUB))).toList().single()
            assertEquals(SourceStatus.ERROR, outcome.status)
            assertFalse(outcome.message!!.contains("private"))
        }
        assertEquals(2, calls)
    }
    @Test fun `cancelling search cancels provider work`() = runTest {
        var cancelled = false
        val engine = SearchEngine(listOf(provider(SearchSource.GITHUB) {
            try { awaitCancellation() } finally { cancelled = true }
        }))
        val job = launch { engine.search(request.copy(sources = setOf(SearchSource.GITHUB))).toList() }
        runCurrent(); job.cancelAndJoin()
        assertTrue(cancelled)
    }
    @Test fun `provider timeout becomes an isolated source error`() = runTest {
        val engine = SearchEngine(listOf(provider(SearchSource.GITHUB) { delay(10_000); SourcePage(emptyList()) }), timeoutMillis = 100)
        assertEquals(SourceStatus.ERROR, engine.search(request.copy(sources = setOf(SearchSource.GITHUB))).toList().single().status)
    }
}
