package de.netscout.data

import de.netscout.core.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import de.netscout.relevance.OnnxPassageScorer
import de.netscout.relevance.SemanticRelevance
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.util.Locale

/** Opt-in integration check against real public endpoints; never part of normal CI tests. */
object LiveSearchCheck {
    @JvmStatic fun main(args: Array<String>) = runBlocking {
        val request = SearchRequest(args.firstOrNull() ?: "Codex,mobile,android", setOf(SearchSource.GITHUB, SearchSource.HACKER_NEWS, SearchSource.FORUMS))
        val providers = ProviderFactory.create(ApiSettings()).filter { it.source in request.sources }
        val root = Path.of(System.getProperty("netscout.modelDir"))
        val model = OnnxPassageScorer(FileChannel.open(root.resolve("model.onnx")).use { it.map(FileChannel.MapMode.READ_ONLY, 0, it.size()) }, root.resolve("tokenizer.bin").toFile().inputStream())
        val outcomes = model.use { SearchEngine(providers, relevance = SemanticRelevance { it }).search(request, onProgress = { progress ->
            println("${progress.source.label}: ${progress.phase}" + if (progress.phase == SourcePhase.ASSESSING) " (${progress.candidateCount} candidates)" else "")
        }).toList() }
        check(outcomes.all { it.status == SourceStatus.SUCCESS }) { outcomes.map { it.source to it.status } }
        val hits = ResultMerger.merge(request, outcomes.flatMap { it.page.items })
        check(hits.isNotEmpty()) { "No verified matches for the integration query" }
        outcomes.sortedBy { it.source.ordinal }.forEach {
            println("${it.source.label}: ${it.page.items.size} loaded, ${ResultMerger.merge(request, it.page.items).size} verified matches")
        }
        check(hits.all { it.item.assessment?.let { value -> value.accepted && value.semantic } == true })
        hits.take(8).forEach {
            println("${it.item.source.label} · ${it.item.context} · ${String.format(Locale.ROOT, "%.2f", it.relevance)} · ${it.item.title}")
            println("  ${it.item.snippet.take(220)}")
        }
    }
}
