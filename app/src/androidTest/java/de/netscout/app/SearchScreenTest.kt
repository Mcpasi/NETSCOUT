package de.netscout.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.netscout.core.*
import de.netscout.app.favorites.*
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun launch(queries: CompletableDeferred<Unit>? = null, assessment: CompletableDeferred<Unit>? = null, warning: SearchMessage? = null, unavailableSource: SearchSource? = null) {
        val model = SearchViewModel(object : SettingsStore {
            override fun load() = SettingsRead(warning = warning)
            override fun save(settings: de.netscout.data.ApiSettings) = Unit
        }) {
            SearchEngine(SearchSource.entries.map { source -> object : SearchProvider {
                override val source = source
                override suspend fun search(request: SearchRequest, cursor: SearchCursor?): SourcePage {
                    if (source == unavailableSource) throw SourceException(SourceStatus.RATE_LIMITED, SearchMessage(MessageKey.SOURCE_PAUSE_SEARCH))
                    if (source != SearchSource.GITHUB) queries?.await()
                    return SourcePage(
                    if (source != SearchSource.GITHUB) emptyList()
                    else if (request.keywords.contains("Codex", ignoreCase = true)) listOf(
                        SearchItem("good", source, "Codex mobile for Android", "https://example.org/good"),
                        SearchItem("partial", source, "Codex mobile for iOS", "https://example.org/partial"),
                        SearchItem("wrong", source, "Unrelated topic", "https://example.org/wrong"),
                        SearchItem("scattered", source, "Weekly notes", "https://example.org/scattered", searchText = "Codex support updated. Mobile browser fixed. Android permissions changed."),
                    ) else listOf(SearchItem("1", source, "Kotlin Testtreffer", "https://example.org/kotlin")))
                }
            } }, relevance = SearchRelevance { request, page ->
                if (page.items.isNotEmpty()) assessment?.await()
                ContextRelevance.assess(request, page)
            })
        }
        val favorites = FavoritesViewModel(FavoritesRepository(object : FavoritesStore {
            override suspend fun load() = emptyList<Favorite>()
            override suspend fun save(favorites: List<Favorite>) = Unit
        }))
        compose.setContent { NetScoutTheme { NetScoutApp(model, favorites, GermanTestLanguage) } }
    }

    @Test fun blockedPublicSearchOffersTheSameKeywordsInTheBrowserWithoutRequestingAKey() {
        launch(unavailableSource = SearchSource.WEB)
        compose.onNodeWithText("Keywords").performTextInput("kotlin")
        compose.onNodeWithText("Netz durchsuchen").performScrollTo().performClick()
        compose.onNodeWithText("Quelle verlangt eine Pause. Bitte später erneut suchen.").assertIsDisplayed()
        compose.onNodeWithText("Keywords im Browser suchen").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText("Zugang nötig").assertDoesNotExist()
    }

    @Test fun everySelectedSourceShowsItsCurrentSearchPhase() = assertVisibleSourceProgress()

    @Test fun sourceProgressIsVisibleWithASettingsWarning() =
        assertVisibleSourceProgress(SearchMessage(MessageKey.ACCESS_LOAD_FAILED))

    private fun assertVisibleSourceProgress(warning: SearchMessage? = null) {
        val queries = CompletableDeferred<Unit>()
        val assessment = CompletableDeferred<Unit>()
        launch(queries, assessment, warning)
        compose.onNodeWithText("Keywords").performTextInput("kotlin")
        compose.onNodeWithText("Netz durchsuchen").performScrollTo().performClick()
        fun inSource(source: SearchSource, text: String) = hasText(text) and hasAnyAncestor(hasTestTag("source-status-${source.name}"))
        compose.onNode(inSource(SearchSource.WEB, "Abfrage läuft …")).assertIsDisplayed()
        compose.onNode(inSource(SearchSource.HACKER_NEWS, "Abfrage läuft …")).assertIsDisplayed()
        compose.onNode(inSource(SearchSource.FORUMS, "Abfrage läuft …")).assertIsDisplayed()
        compose.onNode(inSource(SearchSource.GITHUB, "Inhalt wird geprüft …")).assertIsDisplayed()
        compose.onNode(inSource(SearchSource.GITHUB, "1 Kandidat von der Quelle geladen.")).assertIsDisplayed()
        queries.complete(Unit)
        compose.onNode(inSource(SearchSource.HACKER_NEWS, "Quelle hat geantwortet: keine Kandidaten für diese Anfrage.")).assertIsDisplayed()
        compose.onNode(inSource(SearchSource.FORUMS, "0 Treffer")).assertIsDisplayed()
        assessment.complete(Unit)
        compose.onNodeWithTag("search-content").performScrollToNode(hasText("Kotlin Testtreffer"))
        compose.onNodeWithText("Kotlin Testtreffer").assertIsDisplayed()
    }

    @Test fun searchSettingsAreOptionalAndAllSixSourcesCanBeSelectedWithoutCredentials() {
        launch()
        compose.onNodeWithContentDescription("Optionale Suchzugänge").performClick()
        compose.onNodeWithText("Die Suche funktioniert ohne Einrichtung. Nur ausfüllen, wenn du eigene Zugänge verwenden möchtest.").assertIsDisplayed()
        compose.onNodeWithText("Abbrechen").performClick()
        compose.onNodeWithText("Reddit").performScrollTo().performClick()
        compose.onNodeWithText("YouTube").performScrollTo().performClick()
        compose.onNodeWithText("6 / 6").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Keywords").performScrollTo().performTextInput("kotlin")
        compose.onNodeWithText("Netz durchsuchen").performScrollTo().performClick()
        compose.onNodeWithTag("search-content").performScrollToNode(hasText("Kotlin Testtreffer"))
        compose.onNodeWithText("Kotlin Testtreffer").assertIsDisplayed()
        compose.onNodeWithText("Zugang nötig").assertDoesNotExist()
    }

    @Test fun pastedUnicodeSpacesShowEveryRequiredKeywordAndFindTheMatchingResult() {
        launch()
        compose.onNodeWithText("Keywords").performTextInput("Codex\u00a0mobile\u2003android")
        compose.onNodeWithText("Alle Begriffe erforderlich: Codex · mobile · android").assertIsDisplayed()
        compose.onNodeWithText("Netz durchsuchen").performScrollTo().performClick()
        compose.onNodeWithTag("search-content").performScrollToNode(hasText("Codex mobile for Android"))
        compose.onNodeWithText("Codex mobile for Android").assertIsDisplayed()
        compose.onNodeWithText("Codex mobile for iOS").assertDoesNotExist()
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
    @Test fun commaSeparatedKeywordsRequireACommonPassage() {
        launch()
        compose.onNodeWithText("Keywords").performTextInput("Codex,mobile,android")
        compose.onNodeWithText("Netz durchsuchen").performScrollTo().performClick()
        compose.onNodeWithTag("search-content").performScrollToNode(hasText("3 Ergebnisse ohne ausreichenden Themenbezug ausgeblendet."))
        compose.onNodeWithText("3 Ergebnisse ohne ausreichenden Themenbezug ausgeblendet.").assertIsDisplayed()
        compose.onNodeWithTag("search-content").performScrollToNode(hasText("Codex mobile for Android"))
        compose.onNodeWithText("Codex mobile for Android").assertIsDisplayed()
        compose.onNodeWithText("Codex mobile for iOS").assertDoesNotExist()
        compose.onNodeWithText("Unrelated topic").assertDoesNotExist()
        compose.onNodeWithText("Weekly notes").assertDoesNotExist()
    }
}
