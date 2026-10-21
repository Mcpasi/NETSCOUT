package de.netscout.app

import de.netscout.core.*
import de.netscout.data.ApiSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }
    private class Store : SettingsStore {
        var settings = ApiSettings()
        override fun load() = SettingsRead(settings)
        override fun save(settings: ApiSettings) { this.settings = settings }
    }
    private fun model(store: Store = Store(), action: suspend (SearchRequest, SearchCursor?) -> SourcePage): SearchViewModel = SearchViewModel(store) {
        SearchEngine(SearchSource.entries.map { source -> object : SearchProvider {
            override val source = source
            override suspend fun search(request: SearchRequest, cursor: SearchCursor?) =
                if (source == SearchSource.GITHUB) action(request, cursor) else SourcePage(emptyList())
        } })
    }

    @Test fun `Web HN forums and GitHub start by default and report retrieval assessment and confirmed counts`() = runTest(dispatcher) {
        val sources = setOf(SearchSource.WEB, SearchSource.GITHUB, SearchSource.HACKER_NEWS, SearchSource.FORUMS)
        val pages = sources.associateWith { CompletableDeferred<SourcePage>() }
        val calls = mutableSetOf<SearchSource>()
        val assessment = CompletableDeferred<Unit>()
        val model = SearchViewModel(Store()) {
            SearchEngine(sources.map { source -> object : SearchProvider {
                override val source = source
                override suspend fun search(request: SearchRequest, cursor: SearchCursor?): SourcePage {
                    calls.add(source)
                    return pages.getValue(source).await()
                }
            } }, relevance = SearchRelevance { request, page ->
                if (page.items.isNotEmpty()) assessment.await()
                ContextRelevance.assess(request, page)
            })
        }
        model.keywords("Codex,mobile,Android"); model.search(); runCurrent()
        assertEquals(sources, calls)
        assertEquals(sources, model.state.value.sourceProgress.keys)
        assertTrue(model.state.value.sourceProgress.values.all { it.phase == SourcePhase.FETCHING })
        pages.getValue(SearchSource.HACKER_NEWS).complete(SourcePage(listOf(
            SearchItem("good", SearchSource.HACKER_NEWS, "Codex mobile Android", "https://example.org/good"),
            SearchItem("partial", SearchSource.HACKER_NEWS, "Codex mobile iOS", "https://example.org/partial"),
        )))
        runCurrent()
        assertEquals(SourceProgress(SearchSource.HACKER_NEWS, SourcePhase.ASSESSING, 2), model.state.value.sourceProgress[SearchSource.HACKER_NEWS])
        assertEquals(SourcePhase.FETCHING, model.state.value.sourceProgress[SearchSource.FORUMS]?.phase)
        assertNull(model.state.value.outcomes[SearchSource.HACKER_NEWS])
        assertTrue(model.state.value.results.isEmpty())
        pages.getValue(SearchSource.GITHUB).complete(SourcePage(emptyList()))
        pages.getValue(SearchSource.FORUMS).complete(SourcePage(emptyList()))
        assessment.complete(Unit); advanceUntilIdle()
        assertEquals(sources, model.state.value.outcomes.keys)
        assertTrue(model.state.value.sourceProgress.isEmpty())
        assertEquals(2, model.state.value.candidateCounts[SearchSource.HACKER_NEWS])
        assertEquals(1, model.state.value.sourceCounts[SearchSource.HACKER_NEWS])
        assertEquals("good", model.state.value.results.single().item.id)
    }

    @Test fun `loading another HN and forums page exposes progress despite prior success and cancellation clears it`() = runTest(dispatcher) {
        val nextPage = CompletableDeferred<Unit>()
        val sources = setOf(SearchSource.HACKER_NEWS, SearchSource.FORUMS)
        val model = SearchViewModel(Store()) {
            SearchEngine(sources.map { source -> object : SearchProvider {
                override val source = source
                override suspend fun search(request: SearchRequest, cursor: SearchCursor?): SourcePage {
                    if (cursor != null) nextPage.await()
                    return SourcePage(listOf(SearchItem(source.name, source, "Kotlin", "https://example.org/${source.name}")), SearchCursor("2"))
                }
            } })
        }
        model.toggle(SearchSource.GITHUB)
        model.toggle(SearchSource.WEB)
        model.keywords("kotlin"); model.search(); advanceUntilIdle()
        assertEquals(2, model.state.value.results.size)
        model.loadMore(); runCurrent()
        assertEquals(sources, model.state.value.sourceProgress.keys)
        assertTrue(model.state.value.sourceProgress.values.all { it.phase == SourcePhase.FETCHING })
        assertTrue(model.state.value.outcomes.values.all { it.status == SourceStatus.SUCCESS })
        model.cancel(); advanceUntilIdle()
        assertTrue(model.state.value.sourceProgress.isEmpty())
        assertFalse(model.state.value.isSearching)
        assertEquals(2, model.state.value.results.size)
        assertTrue(model.state.value.canLoadMore)
    }
    @Test fun `invalid request performs no provider calls`() = runTest(dispatcher) {
        var calls = 0
        val model = model { _, _ -> calls++; SourcePage(emptyList()) }
        model.search(); advanceUntilIdle()
        assertNotNull(model.state.value.error)
        assertEquals(0, calls)
    }
    @Test fun `load more retains results and prevents stale filter pagination`() = runTest(dispatcher) {
        var calls = 0
        val model = model { _, cursor ->
            calls++
            SourcePage(listOf(SearchItem("$calls", SearchSource.GITHUB, "Kotlin Hit $calls", "https://example.org/$calls")),
                if (cursor == null) SearchCursor("2", 1) else null)
        }
        model.keywords("kotlin"); model.search(); advanceUntilIdle()
        assertEquals(1, model.state.value.results.size)
        model.loadMore(); advanceUntilIdle()
        assertEquals(2, model.state.value.results.size)
        assertFalse(model.state.value.canLoadMore)
        model.keywords("android")
        assertTrue(model.state.value.filtersChanged)
        model.loadMore(); advanceUntilIdle()
        assertEquals(2, calls)
    }
    @Test fun `new search cancels old search and cannot receive its stale results`() = runTest(dispatcher) {
        val model = model { request, _ ->
            if (request.keywords == "old") delay(5_000)
            SourcePage(listOf(SearchItem(request.keywords, SearchSource.GITHUB, request.keywords, "https://example.org/${request.keywords}")))
        }
        model.keywords("old"); model.search(); runCurrent()
        model.keywords("new"); model.search(); advanceUntilIdle()
        assertEquals("new", model.state.value.results.single().item.title)
        assertFalse(model.state.value.isSearching)
    }
    @Test fun `saving settings trims values and clears prior results and cache`() = runTest(dispatcher) {
        val store = Store()
        val model = model(store) { _, _ -> SourcePage(emptyList()) }
        model.keywords("kotlin"); model.search(); advanceUntilIdle()
        assertNull(model.saveSettings(ApiSettings(youtubeApiKey = " test-key ")))
        assertEquals("test-key", store.settings.youtubeApiKey)
        assertNull(model.state.value.activeRequest)
        assertNotNull(model.saveSettings(ApiSettings(searxngUrl = "http://not-https.example.org")))
    }
    @Test fun `sort choice always selects exactly one ordering`() {
        val model = model { _, _ -> SourcePage(emptyList()) }
        model.sort(SortOrder.NEWEST)
        assertEquals(SortOrder.NEWEST, model.state.value.sort)
        model.sort(SortOrder.RELEVANCE)
        assertEquals(SortOrder.RELEVANCE, model.state.value.sort)
    }
    @Test fun `changing expansion keeps results until a new search and blocks stale pagination`() = runTest(dispatcher) {
        var calls = 0
        val model = model { request, _ ->
            calls++
            val title = if ("application" in request.keywordQuery.terms) "Android application" else "Android app"
            SourcePage(listOf(SearchItem(title, SearchSource.GITHUB, title, "https://example.org/${if ("application" in title) "extra" else "original"}")), SearchCursor("next"))
        }
        assertFalse(model.state.value.expandTerms)
        model.keywords("Android app"); model.search(); advanceUntilIdle()
        assertEquals(1, model.state.value.results.size)
        model.expandTerms(true)
        assertTrue(model.state.value.filtersChanged)
        assertEquals(1, model.state.value.results.size)
        model.loadMore(); advanceUntilIdle()
        assertEquals(1, calls)
        model.search(); advanceUntilIdle()
        assertTrue(model.state.value.activeRequest!!.expandTerms)
        assertFalse(model.state.value.filtersChanged)
        assertEquals(4, calls)
        assertEquals(2, model.state.value.results.size)
        model.expandTerms(false)
        assertTrue(model.state.value.filtersChanged)
        assertFalse(model.state.value.canLoadMore)
    }
    @Test fun `user query filters source counts and still loads later matching pages`() = runTest(dispatcher) {
        val model = model { _, cursor ->
            if (cursor == null) SourcePage(listOf(SearchItem("partial", SearchSource.GITHUB, "Codex mobile iOS", "https://example.org/partial")), SearchCursor("2"))
            else SourcePage(listOf(SearchItem("good", SearchSource.GITHUB, "Codex mobile Android", "https://example.org/good")))
        }
        model.keywords("Codex,mobile,android"); model.search(); advanceUntilIdle()
        assertEquals(0, model.state.value.results.size)
        assertEquals(0, model.state.value.sourceCounts[SearchSource.GITHUB])
        assertEquals(1, model.state.value.unmatchedCount)
        assertTrue(model.state.value.canLoadMore)
        model.loadMore(); advanceUntilIdle()
        assertEquals("good", model.state.value.results.single().item.id)
        assertEquals(1, model.state.value.sourceCounts[SearchSource.GITHUB])
    }
    @Test fun `source counts and exclusion notice honor semantic rejection despite all keywords being present`() = runTest(dispatcher) {
        // Test the view state with an explicitly rejecting evaluator, as used by the production model.
        val semanticModel = SearchViewModel(Store()) {
            SearchEngine(listOf(object : SearchProvider {
                override val source = SearchSource.GITHUB
                override suspend fun search(request: SearchRequest, cursor: SearchCursor?) = SourcePage(listOf(
                    SearchItem("bad", source, "Codex mobile Android", "https://example.org/bad"),
                ))
            }), relevance = SearchRelevance { _, page -> page.copy(items = page.items.map { row ->
                row.copy(assessment = RelevanceAssessment(false, -3.0, "", true))
            }) })
        }
        semanticModel.keywords("Codex,mobile,Android"); semanticModel.search(); advanceUntilIdle()
        assertTrue(semanticModel.state.value.results.isEmpty())
        assertEquals(0, semanticModel.state.value.sourceCounts[SearchSource.GITHUB])
        assertEquals(1, semanticModel.state.value.unmatchedCount)
    }
}
