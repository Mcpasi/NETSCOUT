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
        val sources = args.getOrNull(1)?.split(',')?.map { SearchSource.valueOf(it.trim().uppercase(Locale.ROOT)) }?.toSet() ?: SearchSource.entries.toSet()
        val request = SearchRequest(args.firstOrNull() ?: "Codex,mobile,android", sources)
        val providers = ProviderFactory.create(ApiSettings()).filter { it.source in request.sources }
        val root = Path.of(System.getProperty("netscout.modelDir"))
        val model = OnnxPassageScorer(FileChannel.open(root.resolve("model.onnx")).use { it.map(FileChannel.MapMode.READ_ONLY, 0, it.size()) }, root.resolve("tokenizer.bin").toFile().inputStream())
        val outcomes = model.use {
            val engine = SearchEngine(providers, relevance = SemanticRelevance { it })
            val loaded = mutableListOf<SourceOutcome>()
            var cursors = request.sources.associateWith<SearchSource, SearchCursor?> { null }
            repeat(args.getOrNull(2)?.toIntOrNull() ?: 1) { page ->
                if (cursors.isEmpty()) return@repeat
                val outcomes = engine.search(request, cursors, onProgress = { progress ->
                    println("${progress.source.label}: page ${page + 1} · ${progress.phase}" + if (progress.phase == SourcePhase.ASSESSING) " (${progress.candidateCount} candidates)" else "")
                }).toList()
                loaded += outcomes
                cursors = outcomes.mapNotNull { outcome -> outcome.page.nextCursor?.let { outcome.source to it } }.toMap()
            }
            loaded
        }
        check(outcomes.all { it.status == SourceStatus.SUCCESS }) { outcomes.map { it.source to it.status } }
        val hits = ResultMerger.merge(request, outcomes.flatMap { it.page.items })
        check(hits.isNotEmpty()) { "No verified matches for the integration query" }
        outcomes.sortedBy { it.source.ordinal }.forEach {
            println("${it.source.label}: ${it.page.items.size} loaded, ${ResultMerger.merge(request, it.page.items).size} verified matches")
            it.page.note?.let { note -> println("  $note") }
        }
        check(hits.all { it.item.assessment?.let { value -> value.accepted && value.semantic } == true })
        hits.take(8).forEach {
            println("${it.item.source.label} · ${it.item.context} · ${String.format(Locale.ROOT, "%.2f", it.relevance)} · ${it.item.title}")
            println("  ${it.item.snippet.take(220)}")
        }
    }
}
