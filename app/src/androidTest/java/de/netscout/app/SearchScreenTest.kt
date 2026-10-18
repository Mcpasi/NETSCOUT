package de.netscout.app

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.netscout.core.*
import java.io.File
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
        screenshot("search-form.png")
        compose.onNodeWithText("Keywords").performTextInput("kotlin")
        compose.onNodeWithText("Netz durchsuchen").performScrollTo().performClick()
        compose.onNodeWithText("1 Treffer").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Kotlin Testtreffer").performScrollTo().assertIsDisplayed()
        screenshot("search-results.png")
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
        compose.onNodeWithText("Höchste Relevanz zuerst").performClick().assertIsOn()
        compose.onNodeWithText("Neueste Beiträge zuerst").assertIsOff()
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(directory, name).outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
