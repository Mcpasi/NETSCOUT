package de.netscout.core

import java.net.URI
import java.util.Locale

data class SearchHit(val item: SearchItem, val sources: Set<SearchSource>, val relevance: Double)

object ResultMerger {
    private data class Candidate(val item: SearchItem, val assessment: RelevanceAssessment)
    fun merge(request: SearchRequest, items: List<SearchItem>): List<SearchHit> {
        val hits = items.asSequence()
            .filter { it.source in request.sources && request.includes(it.publishedAt) && safeWebUrl(it.url) }
            .mapNotNull { item ->
                val assessment = item.assessment ?: ContextRelevance.assessment(request, item)
                if (assessment.accepted) Candidate(item.copy(snippet = assessment.evidence), assessment) else null
            }
            .groupBy { canonicalUrl(it.item.url) }
            .map { (_, group) ->
                val candidate = group.sortedWith(compareByDescending<Candidate> { it.assessment.score }.thenBy { it.item.rank }).first()
                val best = candidate.item
                // Reciprocal rank fusion avoids comparing incompatible provider scores.
                val rrf = group.groupBy { it.item.source }.values.sumOf { rows -> 1.0 / (60 + rows.minOf { it.item.rank.coerceAtLeast(1) }) }
                val merged = best.copy(publishedAt = best.publishedAt ?: group.mapNotNull { it.item.publishedAt }.minOrNull())
                SearchHit(merged, group.map { it.item.source }.toSet(), candidate.assessment.score + rrf * 0.01)
            }
        return when (request.sort) {
            SortOrder.NEWEST -> hits.sortedWith(compareByDescending<SearchHit> { it.item.publishedAt }.thenByDescending { it.relevance }.thenBy { it.item.url })
            SortOrder.RELEVANCE -> hits.sortedWith(compareByDescending<SearchHit> { it.relevance }.thenByDescending { it.item.publishedAt }.thenBy { it.item.url })
        }
    }

    fun safeWebUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme?.lowercase(Locale.ROOT) in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)

    fun canonicalUrl(url: String): String = runCatching {
        val uri = URI(url)
        // Independent keys may be reordered, but repeated keys can have positional semantics.
        val query = uri.rawQuery?.split('&')?.filterNot {
            val name = it.substringBefore('=').lowercase(Locale.ROOT)
            name.startsWith("utm_") || name in setOf("fbclid", "gclid")
        }?.sortedBy { it.substringBefore('=') }?.joinToString("&")?.takeIf { it.isNotEmpty() }
        val host = uri.host.lowercase(Locale.ROOT).removePrefix("www.")
        val defaultPort = when (uri.scheme?.lowercase(Locale.ROOT)) { "http" -> 80; "https" -> 443; else -> -1 }
        val port = uri.port.takeUnless { it == -1 || it == defaultPort }?.let { ":$it" }.orEmpty()
        "$host$port${uri.rawPath.orEmpty().trimEnd('/')}" + (query?.let { "?$it" } ?: "")
    }.getOrDefault(url)
}
