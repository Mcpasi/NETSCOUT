package de.netscout.core

import java.util.Locale

data class ExpansionPlan(val queries: List<String>, val evidenceQuery: KeywordQuery)

/** Small, local vocabulary; no network, model, query operators or combinatorial query growth. */
object QueryExpansion {
    const val MAX_ADDITIONAL_QUERIES = 2

    private val vocabulary = listOf(
        listOf("app", "application", "anwendung"),
        listOf("mobile", "mobil"),
        listOf("smartphone", "handy"),
        listOf("async", "asynchronous", "asynchron"),
        listOf("database", "datenbank"),
        listOf("encryption", "verschlüsselung"),
        listOf("authentication", "authentifizierung"),
        listOf("backup", "datensicherung"),
        listOf("privacy", "datenschutz"),
        listOf("ai", "artificial intelligence", "ki"),
        listOf("self-hosted", "selbstgehostet"),
        listOf("open-source", "opensource"),
    ).flatMap { group -> group.map { it to group } }.toMap()

    fun plan(query: KeywordQuery): ExpansionPlan {
        val selections = mutableListOf(query.terms)
        val queries = mutableListOf(query.remoteQuery)
        if (!query.balancedQuotes || query.terms.isEmpty()) return ExpansionPlan(queries, query)
        val alternatives = query.terms.map { term ->
            val synonyms = if (query.isQuoted(term)) emptyList() else vocabulary[term.lowercase(Locale.ROOT)].orEmpty()
            listOf(term) + synonyms.filterNot { it.equals(term, ignoreCase = true) }
        }
        variants@ for (round in 1..MAX_ADDITIONAL_QUERIES) {
            for (index in alternatives.indices) {
                val alternative = alternatives[index].getOrNull(round) ?: continue
                // Change one concept at a time so other original terms keep their retrieval power.
                val selection = query.terms.toMutableList().apply { this[index] = alternative }
                val keywords = selection.joinToString(" ") { "\"$it\"" }
                // Generated requests must obey the same length limit as user requests.
                if (keywords.length <= 256 && keywords !in queries) {
                    selections.add(selection)
                    queries.add(keywords)
                    if (queries.size == MAX_ADDITIONAL_QUERIES + 1) break@variants
                }
            }
        }
        val evidence = query.withAlternatives(query.terms.indices.map { index ->
            selections.map { it[index] }.distinctBy { it.lowercase(Locale.ROOT) }
        })
        return ExpansionPlan(queries, evidence)
    }
}
