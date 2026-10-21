package de.netscout.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout

/** Retrieves literal and optional alternative queries; relevance always uses the original request. */
internal class CandidateRetrieval(private val additionalTimeoutMillis: Long = 4_000) {
    suspend fun fetch(provider: SearchProvider, request: SearchRequest, cursor: SearchCursor?, timeoutMillis: Long): SourcePage {
        val queries = if (request.expandTerms) request.expansionPlan.queries else listOf(request.keywords)
        if (queries.size == 1) {
            if (cursor?.variants != null) invalidCursor()
            return withTimeout(timeoutMillis) { provider.search(request, cursor) }
        }
        val token = "expanded:${request.keywordQuery.remoteQuery}"
        val pending = if (cursor == null) queries.indices.associateWith { null } else {
            if (cursor.token != token || cursor.variants.isNullOrEmpty() || cursor.variants.keys.any { it !in queries.indices }) invalidCursor()
            cursor.variants
        }
        // Each query has its own budget. A slow/failed additional query cannot discard a good page.
        val responses = coroutineScope {
            pending.entries.sortedBy { it.key }.map { (index, position) -> async {
                val literal = request.copy(keywords = if (index == 0) request.keywords else queries[index], expandTerms = false)
                val result = try {
                    Result.success(withTimeout(if (index == 0) timeoutMillis else minOf(timeoutMillis, additionalTimeoutMillis)) {
                        provider.search(literal, position)
                    })
                } catch (_: TimeoutCancellationException) {
                    Result.failure(SourceException(SourceStatus.ERROR, SearchMessage(MessageKey.SOURCE_TIMEOUT)))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    Result.failure(failure)
                }
                index to result
            } }.awaitAll()
        }
        val pages = responses.mapNotNull { it.second.getOrNull() }
        if (pages.isEmpty()) throw responses.first().second.exceptionOrNull()!!
        val next = buildMap {
            responses.forEach { (index, result) ->
                val page = result.getOrNull()
                if (page == null) put(index, pending[index]) // Retry only failed or unfinished queries.
                else page.nextCursor?.let { put(index, it) }
            }
        }
        val offset = cursor?.offset ?: 0
        val items = pages.flatMap { it.items }
            // Preserve different passages and metadata (especially dates used by local filters).
            .distinctBy { it.copy(id = "", url = ResultMerger.canonicalUrl(it.url), rank = 0, snippet = "") }
            .mapIndexed { index, item -> item.copy(rank = offset + index + 1) }
        return SourcePage(items, next.takeIf { it.isNotEmpty() }?.let { SearchCursor(token, offset + items.size, it) }, buildList {
            addAll(pages.flatMap { it.notes }.distinct())
            val failed = responses.count { it.second.isFailure }
            if (failed > 0) add(SearchMessage(MessageKey.EXPANSION_PARTIAL, failed.toString()))
        })
    }

    private fun invalidCursor(): Nothing = throw SourceException(SourceStatus.ERROR, SearchMessage(MessageKey.INVALID_CURSOR))
}
