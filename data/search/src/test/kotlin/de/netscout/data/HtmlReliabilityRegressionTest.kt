package de.netscout.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class HtmlReliabilityRegressionTest(private val case: SearchQualityFixtures.ReliabilityCase) {
    @Test fun `fallback distinguishes unreadable responses from valid empty pages`() = runBlocking {
        val result = SearchQualityFixtures.reliability(case)
        assertEquals(case.id, SearchQualityFixtures.ReliabilityResult(case.expected, case.expectedItems, case.expectedDuckCalls), result)
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases() = SearchQualityFixtures.reliabilityCases().map { arrayOf(it) }
    }
}
