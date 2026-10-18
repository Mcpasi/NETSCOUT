package de.netscout.core

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SearchRequestTest {
    private val request = SearchRequest("Kotlin", setOf(SearchSource.GITHUB), LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-02"))

    @Test fun `date range includes both entire UTC days`() {
        assertFalse(request.includes(Instant.parse("2025-12-31T23:59:59Z")))
        assertTrue(request.includes(Instant.parse("2026-01-01T00:00:00Z")))
        assertTrue(request.includes(Instant.parse("2026-01-02T23:59:59.999999999Z")))
        assertFalse(request.includes(Instant.parse("2026-01-03T00:00:00Z")))
    }
    @Test fun `unknown dates are excluded only with active date range`() {
        assertFalse(request.includes(null))
        assertTrue(request.copy(from = null, until = null).includes(null))
        assertFalse(request.copy(until = null).includes(Instant.parse("2025-12-31T00:00:00Z")))
    }
    @Test fun `invalid input is explained before network work`() {
        assertNotNull(request.copy(keywords = " ").validationError())
        assertNotNull(request.copy(keywords = "a".repeat(257)).validationError())
        assertNotNull(request.copy(sources = emptySet()).validationError())
        assertNotNull(request.copy(from = LocalDate.parse("2026-01-03")).validationError())
        assertNull(request.copy(from = request.until).validationError())
    }
    @Test fun `normalization keeps keywords and quoted phrases`() {
        assertEquals("\"Kotlin Android\" privacy", request.copy(keywords = "  \"Kotlin Android\"   privacy \n").normalized().keywords)
    }
    @Test fun `punctuation only and unclosed phrases cannot produce unfiltered queries`() {
        assertNotNull(request.copy(keywords = ",,;;&&").validationError())
        assertNotNull(request.copy(keywords = "\"codex android").validationError())
        assertNull(request.copy(keywords = "Codex,mobile,android").validationError())
    }
}
