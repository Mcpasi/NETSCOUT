package de.netscout.relevance

import de.netscout.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SemanticRelevanceTest {
    private val request = SearchRequest("Codex,mobile,Android", setOf(SearchSource.GITHUB))
    private fun item(id: String, title: String, text: String = "") = SearchItem(id, SearchSource.GITHUB, title, "https://example.org/$id", searchText = text)

    @Test fun `semantic scores determine order and rejected passages cannot leak into newest results`() = runBlocking {
        val relevance = SemanticRelevance { PassageScorer { _, passages ->
            passages.map { if ("native" in it) 8.0 else -2.0 }.toDoubleArray()
        } }
        val page = relevance.assess(request, SourcePage(listOf(
            item("bad", "Codex mobile Android compatibility list"),
            item("good", "Codex mobile Android native client"),
            item("scattered", "Weekly notes", "Codex support updated. Mobile browser fixed. Android permissions changed."),
        )))
        assertEquals(listOf(false, true, false), page.items.map { it.assessment!!.accepted })
        assertEquals(listOf("good"), ResultMerger.merge(request.copy(sort = SortOrder.NEWEST), page.items).map { it.item.id })
        assertTrue(page.items.all { it.assessment!!.semantic })
    }
    @Test fun `best contiguous passage is the exact text shown to the user`() = runBlocking {
        val relevance = SemanticRelevance { PassageScorer { query, passages ->
            assertEquals("Codex mobile Android", query)
            passages.map { if ("native" in it) 9.0 else 2.0 }.toDoubleArray()
        } }
        val page = relevance.assess(request, SourcePage(listOf(item("one", "Project", "Codex mobile Android overview.\nA native Codex mobile Android client."))))
        assertEquals("A native Codex mobile Android client.", page.items.single().snippet)
        assertEquals(9.0, page.items.single().assessment!!.score, 0.0)
    }
    @Test fun `model failure is explicit and never falls back to word occurrence`() = runBlocking {
        val relevance = SemanticRelevance { error("private file/path should stay private") }
        try {
            relevance.assess(request, SourcePage(listOf(item("one", "Codex mobile Android"))))
            fail("Expected a visible relevance error")
        } catch (failure: SourceException) {
            assertEquals(SourceStatus.ERROR, failure.status)
            assertFalse(failure.message.contains("private"))
        }
    }
}
