package de.netscout.core

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class CandidateRetrievalTest {
    private val request = SearchRequest("Android app", setOf(SearchSource.GITHUB), expandTerms = true)
    private fun provider(action: suspend (SearchRequest, SearchCursor?) -> SourcePage) = object : SearchProvider {
        override val source = SearchSource.GITHUB
        override suspend fun search(request: SearchRequest, cursor: SearchCursor?) = action(request, cursor)
    }
    private fun item(id: String, title: String = "Android app", text: String = "") =
        SearchItem(id, SearchSource.GITHUB, title, "https://example.org/$id", searchText = text)

    @Test fun `disabled expansion and unknown terms retain a single unchanged provider request`() = runTest {
        val calls = mutableListOf<Pair<SearchRequest, SearchCursor?>>()
        val position = SearchCursor("index:opaque-next", 42)
        val engine = SearchEngine(listOf(provider { input, cursor ->
            calls.add(input to cursor)
            SourcePage(emptyList(), position)
        }))
        val literal = request.copy(expandTerms = false)
        assertEquals(position, engine.search(literal).toList().single().page.nextCursor)
        engine.search(literal, mapOf(SearchSource.GITHUB to position)).toList()
        val unknown = request.copy(keywords = "Kotlin")
        engine.search(unknown).toList()
        assertEquals(listOf(literal to null, literal to position, unknown to null), calls)
    }

    @Test fun `original and additional candidates are assessed together against the original filters`() = runTest {
        val input = request.copy(from = LocalDate.parse("2026-01-01"), sort = SortOrder.NEWEST)
        val calls = mutableListOf<SearchRequest>()
        var assessments = 0
        val engine = SearchEngine(listOf(provider { query, _ ->
            calls.add(query)
            val title = if ("application" in query.keywordQuery.terms) "Android application" else "Android app"
            SourcePage(listOf(item("shared"), item(title, title)), notes = listOf(SearchMessage(MessageKey.GITHUB_NOTE)))
        }), relevance = SearchRelevance { original, page ->
            assessments++
            assertEquals(input, original)
            assertEquals(3, page.items.size)
            ContextRelevance.assess(original, page)
        })
        val page = engine.search(input).toList().single().page
        assertEquals(3, calls.size)
        assertTrue(calls.all { it.from == input.from && it.sort == input.sort && it.sources == input.sources && !it.expandTerms })
        assertTrue(page.items.all { it.assessment!!.accepted })
        assertEquals(1, assessments)
        assertEquals(1, page.notes.size)
        assertNull(page.nextCursor)
    }

    @Test fun `each variant resumes its opaque cursor and failed queries retry without replaying finished pages`() = runTest {
        val cursors = listOf(SearchCursor("index:next-original", 90), SearchCursor("pageToken:xyz", 25))
        val calls = mutableListOf<Pair<String, SearchCursor?>>()
        var failed = false
        val engine = SearchEngine(listOf(provider { input, cursor ->
            val term = input.keywordQuery.terms.last()
            calls.add(term to cursor)
            when (term) {
                "app" -> SourcePage(listOf(item("original")), if (cursor == null) cursors[0] else null)
                "application" -> {
                    assertEquals(if (calls.count { it.first == term } == 1) null else cursors[1], cursor)
                    SourcePage(listOf(item("additional", "Android application")), if (cursor == null) cursors[1] else null)
                }
                else -> {
                    if (!failed) { failed = true; throw SourceException(SourceStatus.RATE_LIMITED, SearchMessage(MessageKey.SOURCE_PAUSE_RETRY)) }
                    SourcePage(listOf(item("retry", "Android Anwendung")), if (cursor == null) SearchCursor("last", 17) else null)
                }
            }
        }))
        val first = engine.search(request).toList().single().page
        assertEquals(setOf(0, 1, 2), first.nextCursor!!.variants!!.keys)
        assertEquals(MessageKey.EXPANSION_PARTIAL, first.notes.last().key)
        val second = engine.search(request, mapOf(SearchSource.GITHUB to first.nextCursor)).toList().single().page
        assertEquals(setOf(2), second.nextCursor!!.variants!!.keys)
        assertTrue(calls.contains("app" to cursors[0]))
        val last = engine.search(request, mapOf(SearchSource.GITHUB to second.nextCursor)).toList().single().page
        assertNull(last.nextCursor)
        assertEquals("anwendung" to SearchCursor("last", 17), calls.last())
        assertEquals(2, calls.count { it.first == "app" })
        assertEquals(2, calls.count { it.first == "application" })
    }

    @Test fun `additional query timeouts retain original candidates and offer only pending retries`() = runTest {
        val engine = SearchEngine(listOf(provider { input, _ ->
            if (input.keywordQuery.terms.last() != "app") awaitCancellation()
            SourcePage(listOf(item("original")))
        }))
        val outcome = engine.search(request).toList().single()
        assertEquals(4_000, currentTime)
        assertEquals(SourceStatus.SUCCESS, outcome.status)
        assertEquals("original", outcome.page.items.single().id)
        assertEquals(setOf(1, 2), outcome.page.nextCursor!!.variants!!.keys)
        assertEquals(listOf("2"), outcome.page.notes.last().arguments)
    }

    @Test fun `original timeout does not discard successful additional candidates`() = runTest {
        val engine = SearchEngine(listOf(provider { input, _ ->
            if (input.keywordQuery.terms.last() == "app") awaitCancellation()
            SourcePage(listOf(item("extra", "Android application")))
        }), timeoutMillis = 100)
        val outcome = engine.search(request).toList().single()
        assertEquals(SourceStatus.SUCCESS, outcome.status)
        assertEquals(setOf(0), outcome.page.nextCursor!!.variants!!.keys)
        assertTrue(outcome.page.items.single().assessment!!.accepted)
    }

    @Test fun `all failed variants preserve original failure status and never expose exception details`() = runTest {
        val engine = SearchEngine(listOf(provider { input, _ ->
            if (input.keywordQuery.terms.last() == "app") throw SourceException(SourceStatus.RATE_LIMITED, SearchMessage(MessageKey.SOURCE_PAUSE_RETRY))
            error("https://private.example/?token=secret")
        }))
        val outcome = engine.search(request).toList().single()
        assertEquals(SourceStatus.RATE_LIMITED, outcome.status)
        assertTrue(outcome.page.items.isEmpty())
        assertFalse(outcome.message!!.text.contains("secret"))
        assertNull(outcome.page.nextCursor)
    }

    @Test fun `cancelling collection cancels every outstanding query and emits nothing`() = runTest {
        var started = 0
        var cancelled = 0
        val engine = SearchEngine(listOf(provider { _, _ ->
            started++
            try { awaitCancellation() } finally { cancelled++ }
        }))
        val outcomes = mutableListOf<SourceOutcome>()
        val job = launch { engine.search(request).toList(outcomes) }
        runCurrent()
        assertEquals(3, started)
        job.cancelAndJoin()
        assertEquals(3, cancelled)
        assertTrue(outcomes.isEmpty())
    }

    @Test fun `cache separates expansion mode and caches assessed expanded pages`() = runTest {
        var calls = 0
        val engine = SearchEngine(listOf(provider { _, _ -> calls++; SourcePage(listOf(item("1"))) }))
        engine.search(request.copy(expandTerms = false)).toList()
        assertFalse(engine.search(request).toList().single().cached)
        assertTrue(engine.search(request).toList().single().cached)
        assertTrue(engine.search(request.copy(expandTerms = false)).toList().single().cached)
        assertEquals(4, calls)
    }

    @Test fun `different passages for the same URL reach assessment before deduplication`() = runTest {
        val engine = SearchEngine(listOf(provider { input, _ ->
            SourcePage(listOf(item("same", text = if (input.keywordQuery.terms.last() == "app") "brief" else "useful")))
        }), relevance = SearchRelevance { _, page ->
            assertEquals(2, page.items.size)
            page.copy(items = page.items.map { it.copy(assessment = RelevanceAssessment(it.searchText == "useful", 5.0, it.searchText, true)) })
        })
        val hits = ResultMerger.merge(request, engine.search(request).toList().single().page.items)
        assertEquals("useful", hits.single().item.snippet)
    }

    @Test fun `a cursor from another query cannot restart or corrupt variant pagination`() = runTest {
        var calls = 0
        val engine = SearchEngine(listOf(provider { _, _ -> calls++; SourcePage(emptyList()) }))
        val cursor = SearchCursor("expanded:other", variants = mapOf(0 to SearchCursor("next")))
        val outcome = engine.search(request, mapOf(SearchSource.GITHUB to cursor)).toList().single()
        assertEquals(MessageKey.INVALID_CURSOR, outcome.message!!.key)
        assertEquals(0, calls)
    }

    @Test fun `deduplication retains known dates from variants so date filtering cannot lose a hit`() = runTest {
        val published = Instant.parse("2026-01-02T12:00:00Z")
        val engine = SearchEngine(listOf(provider { input, _ ->
            SourcePage(listOf(item("same").copy(publishedAt = if (input.keywordQuery.terms.last() == "app") null else published)))
        }))
        val input = request.copy(from = LocalDate.parse("2026-01-01"))
        val page = engine.search(input).toList().single().page
        assertEquals(2, page.items.size)
        assertEquals(published, ResultMerger.merge(input, page.items).single().item.publishedAt)
    }

    @Test fun `all six sources receive expanded retrieval and keep their source identity`() = runTest {
        val calls = mutableMapOf<SearchSource, Int>()
        val engine = SearchEngine(SearchSource.entries.map { source -> object : SearchProvider {
            override val source = source
            override suspend fun search(request: SearchRequest, cursor: SearchCursor?): SourcePage {
                calls[source] = calls.getOrDefault(source, 0) + 1
                return SourcePage(listOf(SearchItem(source.name, source, "Android application", "https://example.org/${source.name}")))
            }
        } })
        val input = request.copy(sources = SearchSource.entries.toSet())
        val outcomes = engine.search(input).toList()
        assertEquals(SearchSource.entries.toSet(), outcomes.map { it.source }.toSet())
        assertTrue(calls.values.all { it == 3 })
        assertTrue(outcomes.all { it.status == SourceStatus.SUCCESS && it.page.items.single().source == it.source })
        assertEquals(6, ResultMerger.merge(input, outcomes.flatMap { it.page.items }).size)
    }
}
