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

    @Test fun `source progress distinguishes retrieval from assessment without exposing unchecked items`() = runTest {
        val fetched = CompletableDeferred<SourcePage>()
        val assessed = CompletableDeferred<Unit>()
        val progress = mutableListOf<SourceProgress>()
        val outcomes = mutableListOf<SourceOutcome>()
        val page = SourcePage(listOf(SearchItem("1", SearchSource.HACKER_NEWS, "Kotlin", "https://example.org/1")))
        val engine = SearchEngine(listOf(provider(SearchSource.HACKER_NEWS) { fetched.await() }), relevance = SearchRelevance { _, result ->
            assessed.await()
            result
        })
        val input = request.copy(sources = setOf(SearchSource.HACKER_NEWS))
        val job = launch { engine.search(input, onProgress = progress::add).toList(outcomes) }
        runCurrent()
        assertEquals(listOf(SourceProgress(SearchSource.HACKER_NEWS, SourcePhase.FETCHING)), progress)
        fetched.complete(page); runCurrent()
        assertEquals(SourceProgress(SearchSource.HACKER_NEWS, SourcePhase.ASSESSING, 1), progress.last())
        assertTrue(outcomes.isEmpty())
        assessed.complete(Unit); advanceUntilIdle(); job.join()
        assertEquals(SourceStatus.SUCCESS, outcomes.single().status)
        progress.clear()
        assertTrue(engine.search(input, onProgress = progress::add).toList().single().cached)
        assertTrue(progress.isEmpty())
    }

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
    @Test fun `expanding source selection reuses unchanged source pages`() = runTest {
        var githubCalls = 0; var hnCalls = 0
        val engine = SearchEngine(listOf(
            provider(SearchSource.GITHUB) { githubCalls++; SourcePage(emptyList()) },
            provider(SearchSource.HACKER_NEWS) { hnCalls++; SourcePage(emptyList()) },
        ))
        engine.search(request.copy(sources = setOf(SearchSource.GITHUB))).toList()
        val expanded = engine.search(request).toList()
        assertEquals(1, githubCalls)
        assertEquals(1, hnCalls)
        assertTrue(expanded.single { it.source == SearchSource.GITHUB }.cached)
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
    @Test fun `evaluated pages are cached but failed relevance never exposes unchecked results`() = runTest {
        var evaluations = 0
        val input = request.copy(sources = setOf(SearchSource.GITHUB))
        val page = SourcePage(listOf(SearchItem("1", SearchSource.GITHUB, "Kotlin", "https://example.org/1")))
        val engine = SearchEngine(listOf(provider(SearchSource.GITHUB) { page }), relevance = SearchRelevance { _, result ->
            evaluations++
            if (evaluations == 1) throw SourceException(SourceStatus.ERROR, "Relevance unavailable")
            result.copy(items = result.items.map { it.copy(assessment = RelevanceAssessment(true, 7.0, "Evidence", true)) })
        })
        val failed = engine.search(input).toList().single()
        assertEquals(SourceStatus.ERROR, failed.status)
        assertTrue(failed.page.items.isEmpty())
        assertTrue(engine.search(input).toList().single().page.items.single().assessment!!.semantic)
        assertTrue(engine.search(input).toList().single().cached)
        assertEquals(2, evaluations)
    }
    @Test fun `cancelling during relevance cancels evaluator and emits no unchecked page`() = runTest {
        var cancelled = false
        val engine = SearchEngine(listOf(provider(SearchSource.GITHUB) { SourcePage(emptyList()) }), relevance = SearchRelevance { _, _ ->
            try { awaitCancellation() } finally { cancelled = true }
        })
        val results = mutableListOf<SourceOutcome>()
        val job = launch { engine.search(request.copy(sources = setOf(SearchSource.GITHUB))).toList(results) }
        runCurrent(); job.cancelAndJoin()
        assertTrue(cancelled)
        assertTrue(results.isEmpty())
    }
}
