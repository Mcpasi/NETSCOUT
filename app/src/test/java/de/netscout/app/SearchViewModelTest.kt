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
            SourcePage(listOf(SearchItem("$calls", SearchSource.GITHUB, "Hit $calls", "https://example.org/$calls")),
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
}
