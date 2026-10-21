package de.netscout.core

import org.junit.Assert.*
import org.junit.Test

class QueryExpansionTest {
    private fun request(keywords: String) = SearchRequest(keywords, setOf(SearchSource.WEB), expandTerms = true)

    @Test fun `expansion is opt in and every original concept still needs evidence`() {
        val expanded = request("Android app")
        assertNull(expanded.copy(expandTerms = false).evidenceQuery.match("Android application", ""))
        assertNotNull(expanded.evidenceQuery.match("Android application", ""))
        assertNotNull(expanded.evidenceQuery.match("Android Anwendung", ""))
        assertNull(expanded.evidenceQuery.match("iOS application", ""))
        assertNull(expanded.evidenceQuery.match("Android applicationsuite", ""))
        assertEquals(listOf("Android", "app"), expanded.evidenceQuery.terms)
    }

    @Test fun `quoted words phrases smart quotes and duplicate quoted terms remain literal`() {
        for (keywords in listOf("Android \"app\"", "Android „app“", "Android app \"APP\"", "\"mobile app\" Android")) {
            val expanded = request(keywords)
            assertEquals(1, expanded.expansionPlan.queries.size)
            assertNull(expanded.evidenceQuery.match("Android mobile application", ""))
        }
    }

    @Test fun `unknown identifiers and source operators receive no invented alternatives`() {
        val expanded = request("Codex C++ Kotlin site:example.org")
        assertEquals(1, expanded.expansionPlan.queries.size)
        assertEquals(expanded.keywordQuery.remoteQuery, expanded.expansionPlan.queries.single())
    }

    @Test fun `multiple expandable concepts produce bounded valid literal queries`() {
        val expanded = request("AI async app database")
        val queries = expanded.expansionPlan.queries
        assertEquals(3, queries.size)
        assertEquals(expanded.keywordQuery.remoteQuery, queries.first())
        queries.forEach { keywords ->
            val literal = expanded.copy(keywords = keywords, expandTerms = false)
            assertNull(literal.validationError())
            assertEquals(4, literal.keywordQuery.terms.size)
        }
        assertNotNull(expanded.evidenceQuery.match("Artificial intelligence asynchronous app database", ""))
        assertNull(expanded.evidenceQuery.match("Artificial intelligence asynchronous app", ""))
        assertNull(expanded.evidenceQuery.match("Artificial intelligence asynchronous application database", ""))
    }

    @Test fun `expansion never exceeds request length and skipped variants do not widen evidence`() {
        val expanded = request("${"x".repeat(251)} ai")
        assertNull(expanded.validationError())
        assertEquals(1, expanded.expansionPlan.queries.size)
        assertNull(expanded.evidenceQuery.match("${"x".repeat(251)} artificial intelligence", ""))
    }

    @Test fun `synonyms use the existing whitespace and paragraph evidence rules`() {
        val expanded = request("AI app")
        val relevant = SearchItem("1", SearchSource.WEB, "New project", "https://example.org/1",
            searchText = "An artificial\u00a0intelligence application for your device.")
        assertTrue(ContextRelevance.assessment(expanded, relevant).accepted)
        assertFalse(ContextRelevance.assessment(expanded, relevant.copy(searchText = "Artificial intelligence news.\nAn application for weather.")).accepted)
    }
}
