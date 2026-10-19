package de.netscout.data

import de.netscout.core.ResultMerger
import de.netscout.relevance.OnnxPassageScorer
import de.netscout.relevance.SemanticRelevance
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.channels.FileChannel
import java.nio.file.Path

class SearchQualityModelTest {
    @Test fun `real relevance pipeline agrees with labelled provider fixtures`() = runBlocking {
        val root = Path.of(System.getProperty("netscout.modelDir"))
        OnnxPassageScorer(
            FileChannel.open(root.resolve("model.onnx")).use { it.map(FileChannel.MapMode.READ_ONLY, 0, it.size()) },
            root.resolve("tokenizer.bin").toFile().inputStream(),
        ).use { model ->
            val relevance = SemanticRelevance { model }
            for (case in SearchQualityFixtures.textCases()) {
                val page = relevance.assess(case.request, SearchQualityFixtures.retrieve(case))
                assertEquals(case.id, case.relevant, ResultMerger.merge(case.request, page.items).isNotEmpty())
            }
        }
    }
}
