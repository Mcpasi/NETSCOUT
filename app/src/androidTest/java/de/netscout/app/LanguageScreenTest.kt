package de.netscout.app

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.netscout.app.favorites.*
import de.netscout.core.*
import de.netscout.data.ApiSettings
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class LanguageScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val device = mutableStateOf(configuration("de-CH"))
    private val generation = mutableIntStateOf(0)
    private val calls = AtomicInteger()
    private val preferences = object : LanguagePreferences {
        var selected: AppLanguage? = null
        override fun load() = selected
        override fun save(language: AppLanguage?) { selected = language }
    }

    private fun configuration(tag: String) = Configuration(context.resources.configuration).apply {
        setLocale(Locale.forLanguageTag(tag))
    }

    private fun launch() {
        val model = SearchViewModel(object : SettingsStore {
            override fun load() = SettingsRead()
            override fun save(settings: ApiSettings) = Unit
        }) {
            SearchEngine(SearchSource.entries.map { source -> object : SearchProvider {
                override val source = source
                override suspend fun search(request: SearchRequest, cursor: SearchCursor?): SourcePage {
                    calls.incrementAndGet()
                    return SourcePage(if (source == SearchSource.GITHUB) listOf(
                        SearchItem("1", source, "Kotlin Gerät", "https://example.org/kotlin", "kotlin Ursprung", "Autor",
                            context = "Brave · öffentlicher Suchindex · example.org",
                            contextMessage = SearchMessage(MessageKey.PUBLIC_CONTEXT, "Brave", "example.org")),
                    ) else emptyList(), notes = listOf(SearchMessage(MessageKey.PUBLIC_NOTE, "Brave")))
                }
            } })
        }
        val favorites = FavoritesViewModel(FavoritesRepository(object : FavoritesStore {
            override suspend fun load() = emptyList<Favorite>()
            override suspend fun save(favorites: List<Favorite>) = Unit
        }))
        compose.setContent {
            CompositionLocalProvider(LocalConfiguration provides device.value) {
                key(generation.intValue) { NetScoutTheme { NetScoutApp(model, favorites, preferences) } }
            }
        }
        compose.waitUntil(5_000) { favorites.state.value.isLoaded }
    }

    @Test fun automaticLanguageFollowsTheDeviceAndFallsBackToEnglish() {
        launch()
        compose.onNodeWithText("Netzsuche").assertIsDisplayed()
        compose.runOnIdle { device.value = configuration("en-GB") }
        compose.onNodeWithText("Web search").assertIsDisplayed()
        compose.runOnIdle { device.value = configuration("fr-FR") }
        compose.onNodeWithText("Web search").assertIsDisplayed()
        assertNull(preferences.selected)
    }

    @Test fun explicitChoiceSurvivesANewCompositionAndSystemRestoresAutomaticSelection() {
        launch()
        compose.onNodeWithText("Keywords").performTextInput("kotlin Gerät")
        compose.onNodeWithContentDescription("Englisch verwenden").performClick()
        compose.onNodeWithText("Web search").assertIsDisplayed()
        compose.onNodeWithText("kotlin Gerät").assertIsDisplayed()
        compose.runOnIdle { generation.intValue++ }
        compose.onNodeWithText("Web search").assertIsDisplayed()
        assertEquals(AppLanguage.ENGLISH, preferences.selected)
        compose.onNodeWithContentDescription("Use device language").performClick()
        compose.onNodeWithText("Netzsuche").assertIsDisplayed()
        assertNull(preferences.selected)
    }

    @Test fun switchingLanguageRetainsResultsFavoritesOriginalContentAndProviderCallCount() {
        launch()
        compose.onNodeWithText("Keywords").performTextInput("kotlin")
        compose.onNodeWithText("Netz durchsuchen").performScrollTo().performClick()
        compose.onNodeWithTag("search-content").performScrollToNode(hasText("Kotlin Gerät"))
        compose.onNodeWithContentDescription("Zu Favoriten hinzufügen").performClick()
        compose.onNodeWithContentDescription("Aus Favoriten entfernen").assertIsOn()
        val callsBefore = calls.get()
        compose.onNodeWithContentDescription("Englisch verwenden").performClick()
        compose.onNodeWithText("Kotlin Gerät").assertIsDisplayed()
        compose.onNodeWithText("Autor · Brave · public search index · example.org").assertIsDisplayed()
        compose.onNodeWithContentDescription("Remove from favorites").assertIsOn()
        compose.onNodeWithText("Favorites").performClick()
        compose.onNodeWithText("1 / 20 saved").assertIsDisplayed()
        compose.onNodeWithText("kotlin Ursprung").assertIsDisplayed()
        compose.onNodeWithContentDescription("Use German").performClick()
        compose.onNodeWithText("1 / 20 gespeichert").assertIsDisplayed()
        compose.onNodeWithText("Kotlin Gerät").assertIsDisplayed()
        assertEquals(callsBefore, calls.get())
    }

    @Test fun validationMessagesSwitchWithTheInterfaceWithoutIssuingRequests() {
        launch()
        compose.onNodeWithContentDescription("Englisch verwenden").performClick()
        compose.onNodeWithText("Search the web").performScrollTo().performClick()
        compose.onNodeWithText("Please enter at least two characters.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Use German").performClick()
        compose.onNodeWithText("Bitte mindestens zwei Zeichen eingeben.").assertIsDisplayed()
        assertEquals(0, calls.get())
    }

    @Test fun englishAccessSettingsAndDatePickerUseLocalizedLabels() {
        launch()
        compose.onNodeWithContentDescription("Englisch verwenden").performClick()
        compose.onNodeWithContentDescription("Optional search access").performClick()
        compose.onNodeWithText("SearXNG URL (optional)").performTextInput("http://example.org")
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Please enter an HTTPS base URL without credentials, a query or a fragment.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithContentDescription("Choose From date").performScrollTo().performClick()
        compose.onNodeWithText("Apply").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test fun germanDialogsOverrideTheEnglishDeviceLanguageIncludingNativeDatePickerLabels() {
        device.value = configuration("en-US")
        preferences.selected = AppLanguage.GERMAN
        launch()
        compose.onNodeWithContentDescription("Optionale Suchzugänge").performClick()
        compose.onNodeWithText("SearXNG-URL (optional)").performTextInput("http://example.org")
        compose.onNodeWithText("Speichern").performClick()
        compose.onNodeWithText("Bitte eine HTTPS-Basis-URL ohne Zugangsdaten, Query oder Fragment eingeben.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Abbrechen").performClick()
        compose.onNodeWithContentDescription("Von-Datum wählen").performScrollTo().performClick()
        compose.onNodeWithText("Datum auswählen").assertIsDisplayed()
        compose.onNodeWithText("Übernehmen").assertIsDisplayed()
        compose.onNodeWithText("Apply").assertDoesNotExist()
    }
}
