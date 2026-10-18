package de.netscout.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

enum class SearchSource(val label: String, val hint: String) {
    REDDIT("Reddit", "OAuth oder Suchindex"),
    YOUTUBE("YouTube", "API-Key oder Suchindex"),
    WEB("Web", "SearXNG-Zugang"),
    GITHUB("GitHub", "Öffentliche Issues & PRs"),
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
    fun normalized() = copy(keywords = keywords.trim().replace(Regex("\\s+"), " "))
    fun validationError(): String? = when {
        keywords.trim().length < 2 -> "Bitte mindestens zwei Zeichen eingeben."
        keywords.length > 256 -> "Bitte höchstens 256 Zeichen eingeben."
        sources.isEmpty() -> "Bitte mindestens eine Quelle auswählen."
        from != null && until != null && from > until -> "Das Von-Datum muss vor oder am Bis-Datum liegen."
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
)

data class SearchCursor(val token: String, val offset: Int = 0)
data class SourcePage(val items: List<SearchItem>, val nextCursor: SearchCursor? = null, val note: String? = null)
enum class SourceStatus { SUCCESS, SETUP_REQUIRED, RATE_LIMITED, ERROR }
class SourceException(val status: SourceStatus, override val message: String) : Exception(message)
data class SourceOutcome(
    val source: SearchSource,
    val status: SourceStatus,
    val page: SourcePage = SourcePage(emptyList()),
    val message: String? = null,
    val cached: Boolean = false,
)

interface SearchProvider {
    val source: SearchSource
    suspend fun search(request: SearchRequest, cursor: SearchCursor? = null): SourcePage
}
