package de.netscout.app.favorites

import de.netscout.core.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class FavoritesCodecTest {
    @Test fun `saved source metadata retains message keys across a restart`() {
        val saved = favorite(1).let { it.copy(item = it.item.copy(
            context = "Brave · öffentlicher Suchindex · example.org",
            contextMessage = SearchMessage(MessageKey.PUBLIC_CONTEXT, "Brave", "example.org"),
        )) }
        assertEquals(listOf(saved), FavoritesCodec.decode(FavoritesCodec.encode(listOf(saved))))
    }

    @Test fun `old favorites remain readable and only recognized source metadata gains a translation key`() {
        val saved = favorite(1).let { it.copy(item = it.item.copy(context = "Hacker News · 42 Punkte")) }
        val loaded = FavoritesCodec.decode(FavoritesCodec.encode(listOf(saved))).single()
        assertEquals(saved.item.title, loaded.item.title)
        assertEquals(saved.item.snippet, loaded.item.snippet)
        assertEquals(SearchMessage(MessageKey.HN_CONTEXT, "42"), loaded.item.contextMessage)
        assertEquals("Hacker News · 42 Punkte", loaded.item.context)
    }

    @Test fun `malformed metadata messages are rejected before they reach the interface`() {
        val malformed = favorite(1).let { it.copy(item = it.item.copy(
            contextMessage = SearchMessage(MessageKey.PUBLIC_CONTEXT, "missing host"),
        )) }
        assertRejected(FavoritesCodec.encode(listOf(malformed)))
    }

    @Test fun `snapshot round trip retains the displayed passage attribution date sources and original query`() {
        val passage = "Kotlin für Android – 日本語, \"Zitat\"\nZweite Zeile."
        val item = SearchItem("provider-id", SearchSource.FORUMS, "Ein Titel ⭐", "https://example.org/?id=42&lang=de",
            passage, "Autor", Instant.parse("2026-10-19T23:00:00Z"), 12, "Eine Diskussion", "CC BY-SA 4.0",
            searchText = "Langer Originalbeitrag, der nicht vollständig gespeichert werden soll.",
            assessment = RelevanceAssessment(true, 3.4, passage, true))
        val snapshot = Favorite.from(SearchHit(item, setOf(SearchSource.FORUMS, SearchSource.WEB), 3.5), "kotlin,android")
        assertEquals(passage, snapshot.item.searchText)
        assertEquals(listOf(snapshot), FavoritesCodec.decode(FavoritesCodec.encode(listOf(snapshot))))
    }

    @Test fun `empty collection and unknown date and license round trip`() {
        assertEquals(emptyList<Favorite>(), FavoritesCodec.decode(FavoritesCodec.encode(emptyList())))
        assertEquals(listOf(favorite(1)), FavoritesCodec.decode(FavoritesCodec.encode(listOf(favorite(1)))))
    }

    @Test fun `unsupported truncated and incomplete files are rejected instead of becoming an empty collection`() {
        assertRejected("{\"version\":2,\"favorites\":[]}")
        assertRejected("{\"version\":1,\"favorites\":[")
        assertRejected("{\"version\":1}")
        assertRejected("{\"version\":1,\"favorites\":[{}]}")
    }

    @Test fun `duplicates over capacity and unsafe links cannot enter saved state`() {
        assertRejected(FavoritesCodec.encode(listOf(favorite(1), favorite(1))))
        assertRejected(FavoritesCodec.encode((1..21).map(::favorite)))
        val unsafe = favorite(1).let { it.copy(item = it.item.copy(url = "javascript:alert(1)")) }
        assertRejected(FavoritesCodec.encode(listOf(unsafe)))
    }

    private fun assertRejected(text: String) {
        assertTrue("Expected invalid saved data to be rejected", runCatching { FavoritesCodec.decode(text) }.isFailure)
    }
}
