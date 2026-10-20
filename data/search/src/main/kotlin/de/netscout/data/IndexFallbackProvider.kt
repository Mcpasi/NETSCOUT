package de.netscout.data

import de.netscout.core.MessageKey
import de.netscout.core.SearchMessage
import de.netscout.core.SearchCursor
import de.netscout.core.SearchProvider
import de.netscout.core.SearchRequest
import de.netscout.core.SourcePage
import de.netscout.core.SourceException
import de.netscout.core.SourceStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/** Keeps direct and index cursors separate, including after a first-page fallback. */
internal class IndexFallbackProvider(
    private val primary: SearchProvider,
    private val index: SearchProvider,
    private val fallbackMessage: SearchMessage = SearchMessage(MessageKey.FALLBACK_DIRECT),
) : SearchProvider {
    init { require(primary.source == index.source) }
    override val source = primary.source
    override suspend fun search(request: SearchRequest, cursor: SearchCursor?): SourcePage {
        if (cursor?.token?.startsWith("index:") == true) return indexed(request, cursor.copy(token = cursor.token.removePrefix("index:")))
        return try {
            if (cursor != null) primary.search(request, cursor)
            else withTimeoutOrNull(4_000) { primary.search(request, null) }
                ?: throw SourceException(SourceStatus.ERROR, SearchMessage(MessageKey.DIRECT_TIMEOUT))
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            if (cursor != null) throw failure
            indexed(request, null).let { it.copy(notes = listOf(fallbackMessage) + it.notes) }
        }
    }
    private suspend fun indexed(request: SearchRequest, cursor: SearchCursor?): SourcePage {
        val page = index.search(request, cursor)
        return page.copy(nextCursor = page.nextCursor?.let { it.copy(token = "index:${it.token}") })
    }
}
