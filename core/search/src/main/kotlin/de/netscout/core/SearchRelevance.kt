package de.netscout.core

/** Separate retrieval from relevance: finding the words is only candidate generation. */
fun interface SearchRelevance {
    suspend fun assess(request: SearchRequest, page: SourcePage): SourcePage
}

data class RelevanceAssessment(val accepted: Boolean, val score: Double, val evidence: String, val semantic: Boolean = false)

/** A real, contiguous passage. Never join unrelated matching fragments into apparent evidence. */
data class SearchPassage(val title: String, val text: String, val contextBlocks: List<String> = emptyList()) {
    val document: String get() = listOf(title, text).filter { it.isNotBlank() }.joinToString("\n")
}

object PassageSelector {
    private val sentenceBreak = Regex("(?<=[.!?])[\\s\\p{Z}\\u0085]+|(?<=[。！？])")

    fun select(query: KeywordQuery, item: SearchItem, limit: Int = 4): List<SearchPassage> {
        if (limit <= 0) return emptyList()
        val candidates = mutableSetOf<String>()
        fun add(text: String) {
            if (text.length <= 700) {
                if (query.match(item.title, text) != null) candidates.add(text)
            } else candidates.addAll(query.windows(item.title, text, limit = limit))
        }
        // Adjacent sentences may explain one another, but paragraph/list boundaries remain hard limits.
        item.searchText.splitToSequence(Regex("[\\r\\n\\u2028\\u2029]+"))
            .map(String::trim).filter(String::isNotBlank).forEach { paragraph ->
                val ranges = mutableListOf<IntRange>()
                var start = 0
                sentenceBreak.findAll(paragraph).forEach { separator ->
                    if (separator.range.first > start) ranges.add(start until separator.range.first)
                    start = separator.range.last + 1
                }
                if (start < paragraph.length) ranges.add(start until paragraph.length)
                ranges.forEach { add(paragraph.substring(it).trim()) }
                ranges.zipWithNext().forEach { (first, second) ->
                    // Keep already-complete sentences concise; widen only when the evidence needs both.
                    if (query.match(item.title, paragraph.substring(first)) == null &&
                        query.match(item.title, paragraph.substring(second)) == null) {
                        add(paragraph.substring(first.first, second.last + 1).trim())
                    }
                }
            }
        if (candidates.isEmpty() && query.match(item.title, "") != null) candidates.add("")
        // Body evidence beats a generic introduction even when the title already contains every term.
        return candidates.sortedByDescending { query.match(item.title, it)!!.textCoverage }.take(limit)
            .map { SearchPassage(item.title, it) }
    }

    /** Wider original context requires an additional semantic continuity check, never a lexical fallback. */
    fun semanticCandidates(query: KeywordQuery, item: SearchItem): List<SearchPassage> {
        val local = select(query, item)
        if (local.isNotEmpty()) return local
        return query.windows(item.title, item.searchText, maxLength = 1000).map { text ->
            SearchPassage(item.title, text, query.contextBlocks(item.title, text))
        }
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
