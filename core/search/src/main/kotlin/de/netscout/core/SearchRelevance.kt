package de.netscout.core

/** Separate retrieval from relevance: finding the words is only candidate generation. */
fun interface SearchRelevance {
    suspend fun assess(request: SearchRequest, page: SourcePage): SourcePage
}

data class RelevanceAssessment(val accepted: Boolean, val score: Double, val evidence: String, val semantic: Boolean = false)

/** A real, contiguous passage. Never join unrelated matching fragments into apparent evidence. */
data class SearchPassage(val title: String, val text: String) {
    val document: String get() = listOf(title, text).filter { it.isNotBlank() }.joinToString("\n")
}

object PassageSelector {
    fun select(query: KeywordQuery, item: SearchItem, limit: Int = 2): List<SearchPassage> {
        if (query.match(item.title, "") != null) {
            return listOf(SearchPassage(item.title, item.searchText.lineSequence().firstOrNull().orEmpty().take(600)))
        }
        // Evaluate the title together with each sentence, not all sections of the entire post.
        return item.searchText.splitToSequence(Regex("(?<=[.!?。！？])\\s+|[\\r\\n]+"))
            .map(String::trim).filter(String::isNotBlank)
            .flatMap { sentence ->
                if (sentence.length <= 700) sequenceOf(sentence)
                else query.ranges(sentence).asSequence().map { range ->
                    val start = (range.first - 200).coerceAtLeast(0)
                    sentence.substring(start, (start + 700).coerceAtMost(sentence.length))
                }.distinct().take(100)
            }
            .filter { query.match(item.title, it) != null }
            .distinct().sortedBy { it.length }.take(limit)
            .map { SearchPassage(item.title, it) }.toList()
    }
}

/** Deterministic context gate for the core library; the Android app also injects a neural reranker. */
object ContextRelevance : SearchRelevance {
    fun assessment(request: SearchRequest, item: SearchItem): RelevanceAssessment {
        val passage = PassageSelector.select(request.keywordQuery, item, 1).firstOrNull()
        val titleCoverage = request.keywordQuery.match(item.title, passage?.text.orEmpty())?.titleCoverage ?: 0.0
        return RelevanceAssessment(passage != null, titleCoverage, passage?.text.orEmpty())
    }
    override suspend fun assess(request: SearchRequest, page: SourcePage) = page.copy(items = page.items.map { item ->
        val assessment = assessment(request, item)
        item.copy(assessment = assessment, snippet = assessment.evidence)
    })
}
