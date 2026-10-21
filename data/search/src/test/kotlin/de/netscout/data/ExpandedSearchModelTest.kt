package de.netscout.data

import de.netscout.core.*
import de.netscout.relevance.OnnxPassageScorer
import de.netscout.relevance.SemanticRelevance
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue

class ExpandedSearchModelTest {
    @Test fun `real HN retrieval delivers an otherwise missing synonym candidate to the real local model`() = runBlocking {
        MockWebServer().use { server ->
            val queries = ConcurrentLinkedQueue<String>()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val query = request.requestUrl!!.queryParameter("query")!!
                    queries.add(query)
                    val hits = if (query == "\"Android\" \"application\"") """
                        [{"objectID":"123","title":"Android application for web search",
                          "story_text":"An Android application to search public websites directly from your smartphone."},
                         {"objectID":"456","title":"iOS application for web search",
                          "story_text":"An iOS application to search websites from your iPhone."}]
                    """ else "[]"
                    return MockResponse().setHeader("Content-Type", "application/json").setBody("""{"hits":$hits,"nbPages":1}""")
                }
            }
            server.start()
            val root = Path.of(System.getProperty("netscout.modelDir"))
            OnnxPassageScorer(
                FileChannel.open(root.resolve("model.onnx")).use { it.map(FileChannel.MapMode.READ_ONLY, 0, it.size()) },
                root.resolve("tokenizer.bin").toFile().inputStream(),
            ).use { model ->
                val engine = SearchEngine(listOf(HackerNewsProvider(JsonHttpClient(), server.url("/api/v1/"))), relevance = SemanticRelevance { model })
                val request = SearchRequest("Android app", setOf(SearchSource.HACKER_NEWS))
                val literal = engine.search(request).toList().single()
                assertEquals(SourceStatus.SUCCESS, literal.status)
                assertTrue(literal.page.items.isEmpty())
                assertEquals(listOf("\"Android\" \"app\""), queries.toList())
                val expanded = request.copy(expandTerms = true)
                val outcome = engine.search(expanded).toList().single()
                assertEquals(SourceStatus.SUCCESS, outcome.status)
                assertEquals(4, queries.size)
                assertTrue(outcome.page.items.all { it.assessment!!.semantic })
                assertEquals("123", ResultMerger.merge(expanded, outcome.page.items).single().item.id)
                assertFalse(outcome.page.items.single { it.id == "456" }.assessment!!.accepted)
            }
        }
    }
}
