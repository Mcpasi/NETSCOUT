package de.netscout.data

import de.netscout.core.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

/** Opt-in integration check against real public endpoints; never part of normal CI tests. */
object LiveSearchCheck {
    @JvmStatic fun main(args: Array<String>) = runBlocking {
        val request = SearchRequest(args.firstOrNull() ?: "Codex,mobile,android", setOf(SearchSource.GITHUB, SearchSource.HACKER_NEWS))
        val providers = ProviderFactory.create(ApiSettings()).filter { it.source in request.sources }
        val outcomes = SearchEngine(providers).search(request).toList()
        check(outcomes.all { it.status == SourceStatus.SUCCESS }) { outcomes.map { it.source to it.status } }
        val hits = ResultMerger.merge(request, outcomes.flatMap { it.page.items })
        check(hits.isNotEmpty()) { "No verified matches for the integration query" }
        outcomes.sortedBy { it.source.ordinal }.forEach {
            println("${it.source.label}: ${it.page.items.size} loaded, ${ResultMerger.merge(request, it.page.items).size} verified matches")
        }
        check(hits.all { request.keywordQuery.match(it.item.title, it.item.searchText) != null })
        hits.take(8).forEach { println("${it.item.source.label} · ${it.item.context} · ${it.item.title}") }
    }
}
