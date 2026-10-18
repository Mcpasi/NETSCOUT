package de.netscout.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.netscout.core.*
import de.netscout.data.ApiSettings
import de.netscout.data.ProviderFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class SearchUiState(
    val keywords: String = "",
    val sources: Set<SearchSource> = setOf(SearchSource.GITHUB, SearchSource.FORUMS, SearchSource.HACKER_NEWS),
    val from: LocalDate? = null,
    val until: LocalDate? = null,
    val sort: SortOrder = SortOrder.RELEVANCE,
    val activeRequest: SearchRequest? = null,
    val results: List<SearchHit> = emptyList(),
    val outcomes: Map<SearchSource, SourceOutcome> = emptyMap(),
    val sourceCounts: Map<SearchSource, Int> = emptyMap(),
    val isSearching: Boolean = false,
    val error: String? = null,
    val settings: ApiSettings = ApiSettings(),
    val settingsWarning: String? = null,
) {
    fun request() = SearchRequest(keywords, sources, from, until, sort).normalized()
    val filtersChanged get() = activeRequest != null && request() != activeRequest
    val canLoadMore get() = !isSearching && !filtersChanged && outcomes.values.any { it.page.nextCursor != null }
}

class SearchViewModel(
    private val store: SettingsStore,
    private val engineFactory: (ApiSettings) -> SearchEngine = { SearchEngine(ProviderFactory.create(it)) },
) : ViewModel() {
    private val initial = store.load()
    private val mutableState = MutableStateFlow(SearchUiState(settings = initial.settings, settingsWarning = initial.warning))
    val state = mutableState.asStateFlow()
    private var engine = engineFactory(initial.settings)
    private var job: Job? = null
    private var generation = 0
    private val items = mutableMapOf<SearchSource, List<SearchItem>>()

    fun keywords(value: String) { mutableState.update { it.copy(keywords = value, error = null) } }
    fun from(value: LocalDate?) { mutableState.update { it.copy(from = value, error = null) } }
    fun until(value: LocalDate?) { mutableState.update { it.copy(until = value, error = null) } }
    fun sort(value: SortOrder) { mutableState.update { it.copy(sort = value, error = null) } }
    fun toggle(source: SearchSource) { mutableState.update {
        it.copy(sources = if (source in it.sources) it.sources - source else it.sources + source, error = null)
    } }

    fun search() {
        val request = mutableState.value.request()
        request.validationError()?.let { message -> mutableState.update { it.copy(error = message) }; return }
        cancel()
        items.clear()
        mutableState.update { it.copy(activeRequest = request, results = emptyList(), outcomes = emptyMap(), sourceCounts = emptyMap(), error = null) }
        execute(request, request.sources.associateWith { null })
    }

    fun loadMore() {
        val current = mutableState.value
        if (!current.canLoadMore) return
        val cursors = current.outcomes.mapNotNull { (source, outcome) -> outcome.page.nextCursor?.let { source to it } }.toMap()
        execute(current.activeRequest ?: return, cursors)
    }

    fun cancel() {
        generation++
        job?.cancel()
        mutableState.update { it.copy(isSearching = false) }
    }

    private fun execute(request: SearchRequest, cursors: Map<SearchSource, SearchCursor?>) {
        val currentGeneration = ++generation
        mutableState.update { it.copy(isSearching = true) }
        job = viewModelScope.launch {
            try {
                engine.search(request, cursors).collect { outcome ->
                    if (currentGeneration != generation) return@collect
                    if (outcome.status == SourceStatus.SUCCESS) {
                        items[outcome.source] = (items[outcome.source].orEmpty() + outcome.page.items).distinctBy { it.id to it.url }
                    }
                    mutableState.update { current ->
                        // Preserve failed page cursors so the user can retry without losing prior results.
                        val saved = if (outcome.status != SourceStatus.SUCCESS) outcome.copy(page = current.outcomes[outcome.source]?.page ?: outcome.page) else outcome
                        current.copy(
                            outcomes = current.outcomes + (outcome.source to saved),
                            sourceCounts = items.mapValues { (_, rows) -> rows.count { request.includes(it.publishedAt) } },
                            results = ResultMerger.merge(request, items.values.flatten()),
                        )
                    }
                }
            } finally {
                if (currentGeneration == generation) mutableState.update { it.copy(isSearching = false) }
            }
        }
    }

    fun saveSettings(value: ApiSettings): String? {
        val settings = value.copy(searxngUrl = value.searxngUrl.trim(), youtubeApiKey = value.youtubeApiKey.trim(),
            redditAccessToken = value.redditAccessToken.trim(), githubToken = value.githubToken.trim())
        settings.validationError()?.let { return it }
        return try {
            store.save(settings)
            cancel()
            engine = engineFactory(settings)
            items.clear()
            mutableState.update { it.copy(settings = settings, settingsWarning = null, activeRequest = null, results = emptyList(), outcomes = emptyMap(), sourceCounts = emptyMap()) }
            null
        } catch (_: Exception) { "Zugänge konnten nicht verschlüsselt gespeichert werden. Bitte erneut versuchen." }
    }
}
