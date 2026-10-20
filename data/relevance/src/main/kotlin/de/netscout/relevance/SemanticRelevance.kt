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
    // A sentence-initial personal reference can carry an immediately preceding subject.
    private val continuation = Regex("^(?:[Ii]t|[Ee]s|[Ee]r|[Ss]ie)[\\s\\p{Z}]+")

    override suspend fun assess(request: SearchRequest, page: SourcePage): SourcePage {
        if (page.items.isEmpty()) return page
        return withContext(Dispatchers.Default) {
            try {
                val passages = page.items.map { PassageSelector.semanticCandidates(request.keywordQuery, it) }
                val flattened = passages.flatten()
                val scores = mutableListOf<Double>()
                val connected = BooleanArray(flattened.size) { true }
                // One model/session and bounded batches keep CPU/memory use predictable on Android.
                mutex.withLock {
                    for (batch in flattened.chunked(4)) {
                        currentCoroutineContext().ensureActive()
                        val result = scorer.score(request.keywordQuery.terms.joinToString(" "), batch.map { it.document })
                        require(result.size == batch.size && result.all(Double::isFinite))
                        scores.addAll(result.toList())
                    }
                    val continuityCache = mutableMapOf<Pair<String, String>, Boolean>()
                    flattened.forEachIndexed { index, passage ->
                        currentCoroutineContext().ensureActive()
                        if (scores[index] >= 0.0 && passage.contextBlocks.isNotEmpty()) {
                            val blocks = passage.contextBlocks
                            connected[index] = blocks.size <= 4 && blocks.drop(1).all { block ->
                                val pair = blocks.first() to block
                                val anchorEnd = passage.text.indexOf(pair.first) + pair.first.length
                                val blockStart = passage.text.indexOf(block, anchorEnd)
                                val adjacent = blockStart >= anchorEnd && passage.text.substring(anchorEnd, blockStart).isBlank()
                                if (adjacent && continuation.containsMatchIn(block)) return@all true
                                continuityCache.getOrPut(pair) {
                                    // Query/passage encoding is asymmetric. Compare both directions;
                                    // unrelated sections can otherwise get a high whole-window score.
                                    val forward = scorer.score(pair.first, listOf(pair.second))
                                    val backward = scorer.score(pair.second, listOf(pair.first))
                                    require(forward.size == 1 && backward.size == 1 && forward[0].isFinite() && backward[0].isFinite())
                                    maxOf(forward[0], backward[0]) >= MIN_CONTINUITY_SCORE
                                }
                            }
                        }
                    }
                }
                currentCoroutineContext().ensureActive()
                var offset = 0
                page.copy(items = page.items.mapIndexed { index, item ->
                    val options = passages[index]
                    val best = options.indices.filter { connected[offset + it] }.maxByOrNull { scores[offset + it] }
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
                throw SourceException(SourceStatus.ERROR, SearchMessage(MessageKey.RELEVANCE_FAILED))
            }
        }
    }

    private companion object {
        // Empirical raw-logit cutoff for block-to-block retrieval, not a probability.
        const val MIN_CONTINUITY_SCORE = -5.0
    }
}
