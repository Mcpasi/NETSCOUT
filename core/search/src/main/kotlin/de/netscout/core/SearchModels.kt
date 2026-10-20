package de.netscout.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

enum class SearchSource(val label: String, val hint: String) {
    REDDIT("Reddit", "Ohne Schlüssel · Index"),
    YOUTUBE("YouTube", "Ohne Schlüssel · Index"),
    WEB("Web", "Ohne Schlüssel"),
    GITHUB("GitHub", "Repos, Issues & PRs"),
    FORUMS("Foren", "3 Stack-Exchange-Foren"),
    HACKER_NEWS("Hacker News", "Öffentliche Beiträge"),
}

enum class SortOrder(val label: String) {
    NEWEST("Neueste Beiträge zuerst"), RELEVANCE("Höchste Relevanz zuerst")
}

data class SearchRequest(
    val keywords: String,
    val sources: Set<SearchSource>,
    val from: LocalDate? = null,
    val until: LocalDate? = null,
    val sort: SortOrder = SortOrder.RELEVANCE,
) {
    val keywordQuery: KeywordQuery by lazy { KeywordQuery.parse(keywords) }
    fun normalized() = copy(keywords = KeywordQuery.normalizeWhitespace(keywords))
    fun validationError(): SearchMessage? = when {
        keywords.trim().length < 2 -> SearchMessage(MessageKey.QUERY_TOO_SHORT)
        keywords.length > 256 -> SearchMessage(MessageKey.QUERY_TOO_LONG)
        !keywordQuery.balancedQuotes -> SearchMessage(MessageKey.QUERY_QUOTES)
        keywordQuery.terms.isEmpty() -> SearchMessage(MessageKey.QUERY_TERMS)
        sources.isEmpty() -> SearchMessage(MessageKey.QUERY_SOURCES)
        from != null && until != null && from > until -> SearchMessage(MessageKey.QUERY_DATES)
        else -> null
    }
    val hasDateFilter get() = from != null || until != null
    val startInstant: Instant? get() = from?.atStartOfDay()?.toInstant(ZoneOffset.UTC)
    val endExclusive: Instant? get() = until?.plusDays(1)?.atStartOfDay()?.toInstant(ZoneOffset.UTC)
    fun includes(date: Instant?): Boolean {
        if (!hasDateFilter) return true
        if (date == null) return false
        return (startInstant == null || date >= startInstant) && (endExclusive == null || date < endExclusive)
    }
}

data class SearchItem(
    val id: String,
    val source: SearchSource,
    val title: String,
    val url: String,
    val snippet: String = "",
    val author: String = "",
    val publishedAt: Instant? = null,
    val rank: Int = 1,
    val context: String = "",
    val license: String? = null,
    val searchText: String = snippet,
    val assessment: RelevanceAssessment? = null,
    val contextMessage: SearchMessage? = null,
)

data class SearchCursor(val token: String, val offset: Int = 0)
data class SourcePage(val items: List<SearchItem>, val nextCursor: SearchCursor? = null, val notes: List<SearchMessage> = emptyList()) {
    val note: String? get() = notes.takeIf { it.isNotEmpty() }?.joinToString("") { it.text }
}
enum class SourcePhase { FETCHING, ASSESSING }
data class SourceProgress(val source: SearchSource, val phase: SourcePhase, val candidateCount: Int = 0)
enum class SourceStatus { SUCCESS, SETUP_REQUIRED, RATE_LIMITED, ERROR }
class SourceException(val status: SourceStatus, val userMessage: SearchMessage) : Exception(userMessage.text) {
    override val message: String get() = userMessage.text
}
data class SourceOutcome(
    val source: SearchSource,
    val status: SourceStatus,
    val page: SourcePage = SourcePage(emptyList()),
    val message: SearchMessage? = null,
    val cached: Boolean = false,
)

interface SearchProvider {
    val source: SearchSource
    suspend fun search(request: SearchRequest, cursor: SearchCursor? = null): SourcePage
}
