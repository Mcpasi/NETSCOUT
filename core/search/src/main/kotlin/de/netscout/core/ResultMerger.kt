package de.netscout.core

import java.net.URI
import java.util.Locale

data class SearchHit(val item: SearchItem, val sources: Set<SearchSource>, val relevance: Double)

object ResultMerger {
    fun merge(request: SearchRequest, items: List<SearchItem>): List<SearchHit> {
        val terms = Regex("[\\p{L}\\p{N}]+").findAll(request.keywords.lowercase(Locale.ROOT)).map { it.value }.toSet()
        val hits = items.asSequence()
            .filter { it.source in request.sources && request.includes(it.publishedAt) && safeWebUrl(it.url) }
            .groupBy { canonicalUrl(it.url) }
            .map { (_, group) ->
                val best = group.minBy { it.rank }
                // Reciprocal rank fusion avoids comparing incompatible provider scores.
                val rrf = group.groupBy { it.source }.values.sumOf { rows -> 1.0 / (60 + rows.minOf { it.rank.coerceAtLeast(1) }) }
                val title = best.title.lowercase(Locale.ROOT)
                val coverage = if (terms.isEmpty()) 0.0 else terms.count { title.contains(it) }.toDouble() / terms.size
                SearchHit(best, group.map { it.source }.toSet(), rrf * (1.0 + 0.25 * coverage))
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
        val query = uri.rawQuery?.split('&')?.filterNot {
            val name = it.substringBefore('=').lowercase(Locale.ROOT)
            name.startsWith("utm_") || name in setOf("fbclid", "gclid")
        }?.sorted()?.joinToString("&")?.takeIf { it.isNotEmpty() }
        val host = uri.host.lowercase(Locale.ROOT).removePrefix("www.")
        val port = uri.port.takeUnless { it == -1 || it == 80 || it == 443 }?.let { ":$it" }.orEmpty()
        "$host$port${uri.rawPath.orEmpty().trimEnd('/')}" + (query?.let { "?$it" } ?: "")
    }.getOrDefault(url)
}
