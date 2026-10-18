package de.netscout.core

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ResultMergerTest {
    private val request = SearchRequest("kotlin", SearchSource.entries.toSet())
    private fun item(id: String, url: String = "https://example.org/$id", date: String? = null, source: SearchSource = SearchSource.GITHUB, rank: Int = 1) =
        SearchItem(id, source, "Kotlin $id", url, publishedAt = date?.let(Instant::parse), rank = rank)

    @Test fun `tracking variants collapse and merge their source evidence`() {
        val hits = ResultMerger.merge(request, listOf(
            item("1", "https://www.example.org/post/?utm_source=github#comment"),
            item("2", "http://example.org/post?fbclid=tracking", source = SearchSource.WEB),
        ))
        assertEquals(1, hits.size)
        assertEquals(setOf(SearchSource.GITHUB, SearchSource.WEB), hits.single().sources)
    }
    @Test fun `meaningful query parameters keep different videos distinct`() {
        assertEquals(2, ResultMerger.merge(request, listOf(item("1", "https://youtube.com/watch?v=one"), item("2", "https://youtube.com/watch?v=two"))).size)
    }
    @Test fun `repeated duplicates cannot inflate relevance`() {
        val single = ResultMerger.merge(request, listOf(item("1"))).single().relevance
        val repeated = ResultMerger.merge(request, List(10) { item("1") }).single().relevance
        assertEquals(single, repeated, 0.0)
    }
    @Test fun `newest puts unknown dates last`() {
        val result = ResultMerger.merge(request.copy(sort = SortOrder.NEWEST), listOf(
            item("unknown"), item("old", date = "2025-01-01T00:00:00Z"), item("new", date = "2026-01-01T00:00:00Z"),
        ))
        assertEquals(listOf("new", "old", "unknown"), result.map { it.item.id })
    }
    @Test fun `filter removes wrong sources out of range unknown dates and unsafe links`() {
        val filtered = request.copy(sources = setOf(SearchSource.GITHUB), from = LocalDate.parse("2026-01-01"))
        val result = ResultMerger.merge(filtered, listOf(
            item("keep", date = "2026-02-01T00:00:00Z"), item("unknown"), item("old", date = "2025-01-01T00:00:00Z"),
            item("other", date = "2026-02-01T00:00:00Z", source = SearchSource.WEB), item("unsafe", "javascript:alert(1)", "2026-02-01T00:00:00Z"),
        ))
        assertEquals(listOf("keep"), result.map { it.item.id })
        assertFalse(ResultMerger.safeWebUrl("https://user:password@example.org/"))
        assertFalse(ResultMerger.safeWebUrl("intent://something"))
    }
    @Test fun `provider rank is compared without mixing raw score scales`() {
        val hits = ResultMerger.merge(request, listOf(item("low", rank = 20), item("best", source = SearchSource.HACKER_NEWS)))
        assertEquals("best", hits.first().item.id)
    }
}
