package de.netscout.relevance

import de.netscout.core.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

fun interface PassageScorer {
    fun score(query: String, passages: List<String>): DoubleArray
}

/** Neural query/passage cross-encoding, after a cheap coherent-evidence gate. No cloud API. */
class SemanticRelevance(private val scorerFactory: () -> PassageScorer) : SearchRelevance {
    private val mutex = Mutex()
    private val scorer by lazy(scorerFactory)

    override suspend fun assess(request: SearchRequest, page: SourcePage): SourcePage {
        if (page.items.isEmpty()) return page
        return withContext(Dispatchers.Default) {
            try {
                val passages = page.items.map { PassageSelector.select(request.keywordQuery, it) }
                val flattened = passages.flatten()
                val scores = mutableListOf<Double>()
                // One model/session and bounded batches keep CPU/memory use predictable on Android.
                mutex.withLock {
                    for (batch in flattened.chunked(4)) {
                        currentCoroutineContext().ensureActive()
                        val result = scorer.score(request.keywordQuery.terms.joinToString(" "), batch.map { it.document })
                        require(result.size == batch.size && result.all(Double::isFinite))
                        scores.addAll(result.toList())
                    }
                }
                currentCoroutineContext().ensureActive()
                var offset = 0
                page.copy(items = page.items.mapIndexed { index, item ->
                    val options = passages[index]
                    val best = options.indices.maxByOrNull { scores[offset + it] }
                    val score = best?.let { scores[offset + it] } ?: Double.NEGATIVE_INFINITY
                    offset += options.size
                    val evidence = best?.let { options[it].text }.orEmpty()
                    // Logits are ranking scores, not calibrated probabilities. Zero is a conservative
                    // irrelevant-passage cutoff; evidence must ALSO pass the independent context gate.
                    val assessment = RelevanceAssessment(best != null && score >= 0.0, score, evidence, semantic = true)
                    item.copy(assessment = assessment, snippet = evidence)
                })
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                // Never silently revert to the old word-presence list or expose model/file details.
                throw SourceException(SourceStatus.ERROR, "Inhaltliche Trefferprüfung fehlgeschlagen. Bitte die Suche erneut starten.")
            }
        }
    }
}
