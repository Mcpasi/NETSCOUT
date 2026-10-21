package de.netscout.relevance

import de.netscout.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SemanticRelevanceTest {
    private val request = SearchRequest("Codex,mobile,Android", setOf(SearchSource.GITHUB))
    private fun item(id: String, title: String, text: String = "") = SearchItem(id, SearchSource.GITHUB, title, "https://example.org/$id", searchText = text)

    @Test fun `expanded evidence reaches the model with original intent and still needs semantic acceptance`() = runBlocking {
        val queries = mutableListOf<String>()
        val relevance = SemanticRelevance { PassageScorer { query, passages ->
            queries.add(query)
            passages.map { if ("useful" in it) 8.0 else -2.0 }.toDoubleArray()
        } }
        val expanded = request.copy(keywords = "Android app", expandTerms = true)
        val page = relevance.assess(expanded, SourcePage(listOf(
            item("good", "Android application", "A useful client for the device."),
            item("bad", "Android Anwendung", "An irrelevant compatibility list."),
            item("partial", "iOS application", "A useful client for the device."),
        )))
        assertTrue(queries.isNotEmpty())
        assertTrue(queries.all { it == "Android app" })
        assertEquals(listOf(true, false, false), page.items.map { it.assessment!!.accepted })
        assertTrue(page.items.all { it.assessment!!.semantic })
        assertEquals("good", ResultMerger.merge(expanded, page.items).single().item.id)
    }

    @Test fun `wider context needs continuity even when the complete window scores highly`() = runBlocking {
        val relevance = SemanticRelevance { PassageScorer { query, passages ->
            DoubleArray(passages.size) { if (query == "Codex mobile Android") 8.0 else -8.0 }
        } }
        val page = relevance.assess(request, SourcePage(listOf(
            item("notes", "Weekly notes", "Codex support for web.\nMobile Safari layout fixes.\nAndroid permissions changed."),
            item("client", "New client", "A mobile client for Codex.\n\nIt runs on Android."),
            item("unrelated-reference", "Mobile updates", "Codex supports web.\nThe alarm on a phone failed.\nIt runs on Android."),
        )))
        assertFalse(page.items.first().assessment!!.accepted)
        assertTrue(page.items[1].assessment!!.accepted)
        assertEquals("A mobile client for Codex.\n\nIt runs on Android.", page.items[1].snippet)
        assertFalse(page.items.last().assessment!!.accepted)
    }

    @Test fun `a useful later passage is assessed alongside short keyword mentions`() = runBlocking {
        val relevance = SemanticRelevance { PassageScorer { _, passages ->
            passages.map { if ("native" in it) 8.0 else -2.0 }.toDoubleArray()
        } }
        val text = "Codex mobile Android overview.\nCodex mobile Android comparison.\nA native Codex mobile Android client lets you control the coding agent from your phone."
        val page = relevance.assess(request, SourcePage(listOf(item("client", "Project", text))))
        assertTrue(page.items.single().assessment!!.accepted)
        assertTrue(page.items.single().snippet.contains("native"))
    }

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
