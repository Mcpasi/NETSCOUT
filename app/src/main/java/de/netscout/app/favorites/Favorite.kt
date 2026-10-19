package de.netscout.app.favorites

import de.netscout.core.ResultMerger
import de.netscout.core.SearchHit
import de.netscout.core.SearchItem
import de.netscout.core.SearchSource

const val MAX_FAVORITES = 20

/** A display snapshot, independent of the current search and its in-memory cache. */
data class Favorite(val item: SearchItem, val sources: Set<SearchSource>, val keywords: String) {
    val id: String get() = ResultMerger.canonicalUrl(item.url)

    companion object {
        fun from(hit: SearchHit, keywords: String) = Favorite(
            // Keep the displayed passage, not the potentially much larger provider document.
            hit.item.copy(searchText = hit.item.snippet), hit.sources, keywords,
        )
    }
}
