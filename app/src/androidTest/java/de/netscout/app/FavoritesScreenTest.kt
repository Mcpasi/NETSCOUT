package de.netscout.app

import android.util.AtomicFile
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.netscout.app.favorites.*
import de.netscout.core.*
import de.netscout.data.ApiSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FavoritesScreenTest {
    @get:Rule val compose = createComposeRule()
    private val file = AtomicFile(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
        "favorites-screen-${UUID.randomUUID()}.json"))
    private fun store() = FileFavoritesStore(AtomicFile(file.baseFile))
    private val favoritesModel = mutableStateOf<FavoritesViewModel?>(null)
    private val searchModel = mutableStateOf<SearchViewModel?>(null)

    @After fun cleanup() { file.delete() }

    private fun result(index: Int) = SearchItem("$index", SearchSource.GITHUB, "Kotlin Treffer $index", "https://example.org/$index",
        snippet = "Kotlin auf Android", author = "Testautor", context = "Gespeicherte Diskussion")

    private fun model(index: Int) = SearchViewModel(object : SettingsStore {
        override fun load() = SettingsRead()
        override fun save(settings: ApiSettings) = Unit
    }) {
        SearchEngine(SearchSource.entries.map { source -> object : SearchProvider {
            override val source = source
            override suspend fun search(request: SearchRequest, cursor: SearchCursor?) =
                SourcePage(if (source == SearchSource.GITHUB) listOf(result(index)) else emptyList())
        } })
    }

    private fun launch(index: Int = 1, saved: Int = 0) {
        runBlocking {
            store().save((1..saved).map { Favorite.from(SearchHit(result(it), setOf(SearchSource.GITHUB), 0.7), "kotlin") })
        }
        searchModel.value = model(index)
        favoritesModel.value = FavoritesViewModel(FavoritesRepository(store()))
        compose.setContent { NetScoutTheme { NetScoutApp(searchModel.value!!, favoritesModel.value!!, GermanTestLanguage) } }
        waitForFavorites(saved)
    }

    private fun waitForFavorites(count: Int) {
        compose.waitUntil(5_000) {
            favoritesModel.value!!.state.value.let { it.isLoaded && it.entries.size == count }
        }
    }

    private fun search(index: Int = 1) {
        compose.onNodeWithText("Keywords").performTextInput("kotlin")
        compose.onNodeWithText("Netz durchsuchen").performScrollTo().performClick()
        compose.onNodeWithTag("search-content").performScrollToNode(hasText("Kotlin Treffer $index"))
    }

    @Test fun favoritesAreAccessibleBeforeAnySearch() {
        launch()
        compose.onNodeWithText("Favoriten").performClick()
        compose.onNodeWithText("0 / 20 gespeichert").assertIsDisplayed()
        compose.onNodeWithText("Noch keine Favoriten. Tippe nach einer Suche auf den Stern eines Treffers.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Zur Suche").performClick()
        compose.onNodeWithText("Keywords").assertIsDisplayed()
    }

    @Test fun starSavesTheResultAndRemovalInFavoritesUpdatesTheSearchStar() {
        launch()
        search()
        compose.onNodeWithContentDescription("Zu Favoriten hinzufügen").assertIsOff().performClick()
        waitForFavorites(1)
        compose.onNodeWithContentDescription("Aus Favoriten entfernen").assertIsOn()
        compose.onNodeWithText("Favoriten").performClick()
        compose.onNodeWithText("1 / 20 gespeichert").assertIsDisplayed()
        compose.onNodeWithText("Kotlin Treffer 1").assertIsDisplayed()
        compose.onNodeWithText("Testautor · Gespeicherte Diskussion").assertIsDisplayed()
        compose.onNodeWithContentDescription("Aus Favoriten entfernen").performClick()
        waitForFavorites(0)
        compose.onNodeWithText("0 / 20 gespeichert").assertIsDisplayed()
        compose.onNodeWithContentDescription("Zur Suche").performClick()
        compose.onNodeWithContentDescription("Zu Favoriten hinzufügen").assertIsOff()
    }

    @Test fun pressingTheFilledSearchStarAgainDeletesTheFavorite() {
        launch()
        search()
        compose.onNodeWithContentDescription("Zu Favoriten hinzufügen").performClick()
        waitForFavorites(1)
        compose.onNodeWithContentDescription("Aus Favoriten entfernen").performClick()
        waitForFavorites(0)
        compose.onNodeWithContentDescription("Zu Favoriten hinzufügen").assertIsOff()
    }

    @Test fun anotherSearchAndChangedAccessSettingsKeepTheSavedResult() {
        launch()
        search()
        compose.onNodeWithContentDescription("Zu Favoriten hinzufügen").performClick()
        waitForFavorites(1)
        compose.onNodeWithText("Keywords").performScrollTo().performTextReplacement("android")
        compose.onNodeWithText("Netz durchsuchen").performScrollTo().performClick()
        compose.onNodeWithTag("search-content").performScrollToNode(hasText("Kotlin Treffer 1"))
        compose.onNodeWithContentDescription("Aus Favoriten entfernen").assertIsOn()
        compose.onNodeWithContentDescription("Optionale Suchzugänge").performClick()
        compose.onNodeWithText("Speichern").performClick()
        compose.onNodeWithText("Favoriten").performClick()
        compose.onNodeWithText("1 / 20 gespeichert").assertIsDisplayed()
        compose.onNodeWithText("Kotlin Treffer 1").assertIsDisplayed()
    }

    @Test fun savedResultsSurviveNewViewModelsWithoutAnotherSearchAndDeletionStaysDeleted() {
        launch()
        search()
        compose.onNodeWithContentDescription("Zu Favoriten hinzufügen").performClick()
        waitForFavorites(1)
        compose.onNodeWithText("Favoriten").performClick()
        compose.runOnIdle {
            searchModel.value = model(1)
            favoritesModel.value = FavoritesViewModel(FavoritesRepository(store()))
        }
        waitForFavorites(1)
        compose.onNodeWithText("Kotlin Treffer 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("Aus Favoriten entfernen").performClick()
        waitForFavorites(0)
        compose.runOnIdle { favoritesModel.value = FavoritesViewModel(FavoritesRepository(store())) }
        waitForFavorites(0)
        compose.onNodeWithText("0 / 20 gespeichert").assertIsDisplayed()
        compose.onNodeWithContentDescription("Zur Suche").performClick()
        compose.onNodeWithText("Keywords").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Kotlin Treffer 1").assertDoesNotExist()
    }

    @Test fun twentyFirstFavoriteShowsALimitMessageAndRetainsAllSavedResults() {
        launch(index = 21, saved = 20)
        search(21)
        compose.onNodeWithContentDescription("Zu Favoriten hinzufügen").performClick()
        compose.onNodeWithText("Du kannst höchstens 20 Favoriten speichern. Entferne zuerst einen Favoriten.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Zu Favoriten hinzufügen").assertIsOff()
        compose.onNodeWithText("Favoriten").performClick()
        compose.onNodeWithText("20 / 20 gespeichert").assertIsDisplayed()
        compose.onNodeWithText("Kotlin Treffer 21").assertDoesNotExist()
        compose.onNodeWithText("Kotlin Treffer 1").assertIsDisplayed()
    }
}
