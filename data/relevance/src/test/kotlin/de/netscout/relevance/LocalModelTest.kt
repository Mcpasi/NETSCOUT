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

    @Test fun `token IDs match the original HF tokenizer across languages and normalization`() {
        val tokenizer = UnigramTokenizer(root.resolve("tokenizer.bin").toFile().inputStream())
        val cases = mapOf(
            "Codex mobile Android" to intArrayOf(28864, 425, 14288, 3952),
            "Codex für unterwegs: Android-App zur Fernsteuerung." to intArrayOf(28864, 425, 643, 101893, 12, 3952, 9, 39179, 2957, 80621, 56357, 1619, 5),
            "支持Android的Codex手机客户端。" to intArrayOf(6, 7499, 57934, 43, 114211, 425, 14754, 199229, 30),
            "Cafe\u0301 — ① 𝕏" to intArrayOf(61427, 292, 106, 1193),
            "👨‍💻 🦄\u200btest" to intArrayOf(6, 244314, 6, 246382, 6, 247874, 3034),
            "one\ntwo\tthree" to intArrayOf(1632, 6626, 17262),
        )
        cases.forEach { (text, expected) -> assertArrayEquals(text, expected, tokenizer.encode(text)) }
        assertArrayEquals(longArrayOf(0, 28864, 425, 2, 2, 3952, 2), tokenizer.pair("Codex", "Android"))
    }
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
            assertEquals(10.891, scores[0], 0.03) // Reference ONNX/Python inference with the pinned tokenizer.
            val arbitrary = model.score("rust async database", listOf(
                "An asynchronous database client written in Rust", "A recipe for tomato soup",
            ))
            assertTrue(arbitrary[0] > arbitrary[1] + 5.0)
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
}
