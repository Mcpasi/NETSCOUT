package de.netscout.data

import de.netscout.core.SearchCursor
import de.netscout.core.SearchProvider
import de.netscout.core.SearchRequest
import de.netscout.core.SourcePage
import kotlinx.coroutines.CancellationException

/** Keeps direct and index cursors separate, including after a first-page fallback. */
internal class IndexFallbackProvider(private val primary: SearchProvider, private val index: SearchProvider) : SearchProvider {
    init { require(primary.source == index.source) }
    override val source = primary.source
    override suspend fun search(request: SearchRequest, cursor: SearchCursor?): SourcePage {
        if (cursor?.token?.startsWith("index:") == true) return indexed(request, cursor.copy(token = cursor.token.removePrefix("index:")))
        return try { primary.search(request, cursor) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            if (cursor != null) throw failure
            indexed(request, null).let { it.copy(note = "Direktzugriff fehlgeschlagen; " + it.note.orEmpty()) }
        }
    }
    private suspend fun indexed(request: SearchRequest, cursor: SearchCursor?): SourcePage {
        val page = index.search(request, cursor)
        return page.copy(nextCursor = page.nextCursor?.let { it.copy(token = "index:${it.token}") })
    }
}
