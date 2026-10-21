package de.netscout.relevance

import de.netscout.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.nio.channels.FileChannel
import java.nio.file.Path

class LocalModelTest {
    private val root = Path.of(System.getProperty("netscout.modelDir"))
    private fun scorer() = OnnxPassageScorer(
        FileChannel.open(root.resolve("model.onnx")).use { it.map(FileChannel.MapMode.READ_ONLY, 0, it.size()) },
        root.resolve("tokenizer.bin").toFile().inputStream(),
    )

    @Test fun `real quantized model scores related passages above unrelated documents in multiple languages`() {
        scorer().use { model ->
            val scores = model.score("Codex mobile Android", listOf(
                "Codex mobile for Android. A native Android app to use the OpenAI Codex coding agent from your phone.",
                "Codex für unterwegs: Android-App zur Fernsteuerung des Coding-Agenten.",
                "支持Android的Codex手机客户端。",
                "Gardening tips for growing tomatoes on a balcony.",
            ))
            assertTrue(scores.all(Double::isFinite))
            assertTrue(scores.take(3).all { it > scores.last() + 5.0 })
            // Quantized logits vary with CPU kernels and padding/batch composition. Assert the
            // relevance decision and ordering; exact inputs are covered by the token-ID fixtures.
            assertTrue(scores.take(3).all { it > 0.0 })
            val arbitrary = model.score("rust async database", listOf(
                "An asynchronous database client written in Rust", "A recipe for tomato soup",
            ))
            assertTrue(arbitrary[0] > arbitrary[1] + 5.0)
        }
    }

    @Test fun `mixed length batches retain the same relevance decision with Horizon right padding`() {
        scorer().use { model ->
            val query = "rust async database"
            val passage = "An asynchronous database client written in Rust."
            val alone = model.score(query, listOf(passage)).single()
            val batched = model.score(query, listOf(passage, "Gardening and tomato recipes. ".repeat(80)))
            assertEquals(2, batched.size)
            assertTrue(alone > 0.0)
            assertTrue(batched[0] > 0.0)
            assertTrue(batched[1] < 0.0)
            assertEquals(alone, batched[0], 0.1)
            assertArrayEquals(doubleArrayOf(), model.score(query, emptyList()), 0.0)
        }
    }
    @Test fun `full pipeline rejects disconnected mentions and keeps a meaningful original sentence`() = runBlocking {
        scorer().use { model ->
            val relevance = SemanticRelevance { model }
            val request = SearchRequest("Codex,mobile,Android", setOf(SearchSource.GITHUB))
            val page = relevance.assess(request, SourcePage(listOf(
                SearchItem("notes", SearchSource.GITHUB, "General release notes", "https://example.org/notes", searchText = "Codex support for web.\nMobile Safari layout fixes.\nAndroid notification permissions updated."),
                SearchItem("app", SearchSource.GITHUB, "Codex mobile for Android", "https://example.org/app", searchText = "Use the OpenAI Codex coding agent from your phone."),
            )))
            assertFalse(page.items[0].assessment!!.accepted)
            assertTrue(page.items[1].assessment!!.accepted)
            assertEquals("app", ResultMerger.merge(request, page.items).single().item.id)
        }
    }

    @Test fun `real model confirms a common topic explained over adjacent sentences`() = runBlocking {
        scorer().use { model ->
            val request = SearchRequest("Codex,mobile,Android", setOf(SearchSource.HACKER_NEWS))
            val text = "Control Codex from a mobile client. It runs on Android."
            val page = SemanticRelevance { model }.assess(request, SourcePage(listOf(
                SearchItem("client", SearchSource.HACKER_NEWS, "New coding agent client", "https://example.org/client", searchText = text),
                SearchItem("partial", SearchSource.HACKER_NEWS, "Codex mobile client for iOS", "https://example.org/partial"),
            )))
            assertTrue(page.items.first().assessment!!.accepted)
            assertEquals(text, page.items.first().snippet)
            assertFalse(page.items.last().assessment!!.accepted)
            assertEquals("client", ResultMerger.merge(request, page.items).single().item.id)
        }
    }

    @Test fun `real model retains a project introduction followed by its platform paragraph`() = runBlocking {
        scorer().use { model ->
            val request = SearchRequest("Codex,mobile,Android", setOf(SearchSource.HACKER_NEWS))
            val text = "An open source interface for Codex and other coding agents.\n\nThe mobile app runs on iOS and Android."
            val notes = "Codex support for web.\nMobile Safari layout fixes.\nAndroid notification permissions updated."
            val page = SemanticRelevance { model }.assess(request, SourcePage(listOf(
                SearchItem("client", SearchSource.HACKER_NEWS, "Remote coding agent client", "https://example.org/client", searchText = text),
                SearchItem("notes", SearchSource.HACKER_NEWS, "General release notes", "https://example.org/notes", searchText = notes),
            )))
            assertTrue(page.items.first().assessment!!.accepted)
            assertTrue(text.contains(page.items.first().snippet))
            assertFalse(page.items.last().assessment!!.accepted)
            val other = SemanticRelevance { model }.assess(request.copy(keywords = "rust,async,database"), SourcePage(listOf(
                SearchItem("rust", SearchSource.HACKER_NEWS, "New client library", "https://example.org/rust",
                    searchText = "An async client written in Rust.\n\nIt connects to a database without blocking."),
            )))
            assertTrue(other.items.single().assessment!!.accepted)
        }
    }
}
