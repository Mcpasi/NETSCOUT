package de.netscout.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.netscout.core.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun launch() {
        val model = SearchViewModel(object : SettingsStore {
            override fun load() = SettingsRead()
            override fun save(settings: de.netscout.data.ApiSettings) = Unit
        }) {
            SearchEngine(SearchSource.entries.map { source -> object : SearchProvider {
                override val source = source
                override suspend fun search(request: SearchRequest, cursor: SearchCursor?) = SourcePage(
                    if (source == SearchSource.GITHUB) listOf(SearchItem("1", source, "Kotlin Testtreffer", "https://example.org/kotlin")) else emptyList())
            } })
        }
        compose.setContent { NetScoutTheme { NetScoutApp(model) } }
    }
    @Test fun keywordSearchShowsRealProviderResults() {
        launch()
        compose.onNodeWithText("Keywords").performTextInput("kotlin")
        compose.onNodeWithText("Netz durchsuchen").performScrollTo().performClick()
        compose.onNodeWithTag("search-content").performScrollToNode(hasText("Kotlin Testtreffer"))
        compose.onNodeWithText("Kotlin Testtreffer").assertIsDisplayed()
    }
    @Test fun emptySearchShowsValidation() {
        launch()
        compose.onNodeWithText("Netz durchsuchen").performScrollTo().performClick()
        compose.onNodeWithText("Bitte mindestens zwei Zeichen eingeben.").performScrollTo().assertIsDisplayed()
    }
    @Test fun sortingCheckboxesAreMutuallyExclusive() {
        launch()
        compose.onNodeWithText("Neueste Beiträge zuerst").performScrollTo().performClick().assertIsOn()
        compose.onNodeWithText("Höchste Relevanz zuerst").assertIsOff()
        compose.onNodeWithText("Höchste Relevanz zuerst").performScrollTo().performClick().assertIsOn()
        compose.onNodeWithText("Neueste Beiträge zuerst").assertIsOff()
    }
}
