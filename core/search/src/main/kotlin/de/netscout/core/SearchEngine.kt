package de.netscout.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Emits each source independently. Cancelling collection cancels all outstanding providers. */
class SearchEngine(
    providers: List<SearchProvider>,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val timeoutMillis: Long = 18_000,
    private val cacheTtlMillis: Long = 60_000,
) {
    private val providers = providers.associateBy { it.source }
    private data class Key(val request: SearchRequest, val source: SearchSource, val cursor: SearchCursor?)
    private data class Cached(val at: Long, val outcome: SourceOutcome)
    private val cache = LinkedHashMap<Key, Cached>()

    init { require(this.providers.size == providers.size) { "Exactly one provider per source is required" } }

    fun search(
        input: SearchRequest,
        cursors: Map<SearchSource, SearchCursor?> = input.sources.associateWith { null },
    ): Flow<SourceOutcome> = channelFlow {
        val request = input.normalized()
        require(request.validationError() == null) { request.validationError().orEmpty() }
        cursors.filterKeys { it in request.sources }.forEach { (source, cursor) ->
            launch {
                // Expanding the selection must not spend quota again on unchanged source queries.
                val key = Key(request.copy(sources = setOf(source)), source, cursor)
                val cached = synchronized(cache) { cache[key]?.takeIf { nowMillis() - it.at < cacheTtlMillis } }
                if (cached != null) {
                    send(cached.outcome.copy(cached = true))
                    return@launch
                }
                val outcome = try {
                    val provider = providers[source] ?: throw SourceException(SourceStatus.SETUP_REQUIRED, "Quelle noch nicht eingerichtet.")
                    SourceOutcome(source, SourceStatus.SUCCESS, withTimeout(timeoutMillis) { provider.search(request, cursor) })
                } catch (_: TimeoutCancellationException) {
                    SourceOutcome(source, SourceStatus.ERROR, message = "Zeitlimit erreicht. Bitte erneut versuchen.")
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: SourceException) {
                    SourceOutcome(source, failure.status, message = failure.message)
                } catch (_: Exception) {
                    // Never expose URLs, credentials, response bodies, or internal exception messages.
                    SourceOutcome(source, SourceStatus.ERROR, message = "Quelle zurzeit nicht erreichbar. Verbindung und Zugang prüfen.")
                }
                if (outcome.status == SourceStatus.SUCCESS) synchronized(cache) {
                    cache[key] = Cached(nowMillis(), outcome)
                    while (cache.size > 64) cache.remove(cache.keys.first())
                }
                send(outcome)
            }
        }
    }
}
