package de.netscout.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class UrlIdentityRegressionTest(private val case: SearchQualityFixtures.UrlCase) {
    @Test fun `only equivalent result URLs are merged`() {
        assertEquals(case.id, if (case.same) 1 else 2, SearchQualityFixtures.mergeUrls(case).size)
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases() = SearchQualityFixtures.urlCases().map { arrayOf(it) }
    }
}
