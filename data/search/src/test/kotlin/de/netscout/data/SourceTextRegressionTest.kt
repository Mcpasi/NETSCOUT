package de.netscout.data

import de.netscout.core.ResultMerger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class SourceTextRegressionTest(private val case: SearchQualityFixtures.TextCase) {
    @Test fun `provider preserves required text and paragraph boundaries`() = runBlocking {
        val page = SearchQualityFixtures.retrieve(case)
        assertEquals(case.id, case.relevant, ResultMerger.merge(case.request, page.items).isNotEmpty())
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases() = SearchQualityFixtures.textCases().map { arrayOf(it) }
    }
}
