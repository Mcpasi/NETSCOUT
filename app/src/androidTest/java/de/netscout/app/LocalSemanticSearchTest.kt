package de.netscout.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.netscout.core.*
import de.netscout.relevance.BpeTokenizer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalSemanticSearchTest {
    @Test fun packagedTokenizerUsesHorizonSmallPairIds() {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        val tokenizer = BpeTokenizer(assets.open("netscout-relevance/tokenizer.bin"))
        assertArrayEquals(longArrayOf(2, 127937, 1, 9989, 1), tokenizer.pair("Codex", "Android"))
        val lock = assets.open("netscout-relevance/MODEL.lock.json").bufferedReader().use { it.readText() }
        assertTrue(lock.contains("Horizon-Labs/multilingual-reranker-small"))
    }

    @Test fun packagedModelRunsThroughTheProductionAndroidAssetLoader() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val relevance = LocalSemanticSearch.relevance(context)
        val request = SearchRequest("Codex,mobile,Android", setOf(SearchSource.GITHUB))
        val page = relevance.assess(request, SourcePage(listOf(
            SearchItem("app", SearchSource.GITHUB, "Codex mobile for Android", "https://example.org/app", searchText = "A native client for the coding agent."),
            SearchItem("notes", SearchSource.GITHUB, "Weekly notes", "https://example.org/notes", searchText = "Codex support updated. Mobile browser fixed. Android permissions changed."),
        )))
        assertTrue(page.items[0].assessment!!.semantic)
        assertTrue(page.items[0].assessment!!.accepted)
        assertFalse(page.items[1].assessment!!.accepted)
        assertEquals("app", ResultMerger.merge(request, page.items).single().item.id)
    }
}
