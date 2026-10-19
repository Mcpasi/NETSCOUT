package de.netscout.app.favorites

import android.util.AtomicFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.netscout.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FileFavoritesStoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun file() = AtomicFile(File(context.filesDir, "favorites-test-${UUID.randomUUID()}.json"))
    private fun snapshot() = Favorite.from(SearchHit(
        SearchItem("1", SearchSource.GITHUB, "Kotlin für Android ⭐", "https://example.org/kotlin", "Eine Textstelle"),
        setOf(SearchSource.GITHUB, SearchSource.WEB), 0.8), "kotlin android")

    @Test fun favoritesAndDeletionSurviveNewStoreInstances() = runBlocking {
        val file = file()
        try {
            assertTrue(FileFavoritesStore(file).load().isEmpty())
            val expected = listOf(snapshot())
            FileFavoritesStore(file).save(expected)
            assertEquals(expected, FileFavoritesStore(AtomicFile(file.baseFile)).load())
            FileFavoritesStore(AtomicFile(file.baseFile)).save(emptyList())
            assertTrue(FileFavoritesStore(AtomicFile(file.baseFile)).load().isEmpty())
        } finally { file.delete() }
    }

    @Test fun unfinishedAtomicWritePreservesThePreviouslySavedCollection() = runBlocking {
        val file = file()
        try {
            val expected = listOf(snapshot())
            FileFavoritesStore(file).save(expected)
            // Simulate a process stopping before finishWrite; the completed base file must survive.
            file.startWrite().use { it.write("{unfinished".toByteArray()) }
            assertEquals(expected, FileFavoritesStore(AtomicFile(file.baseFile)).load())
        } finally { file.delete() }
    }

    @Test fun corruptStorageIsReportedAndNotOverwrittenByANewFavorite() = runBlocking {
        val file = file()
        try {
            val corrupt = "{broken favorites"
            file.baseFile.writeText(corrupt)
            val repository = FavoritesRepository(FileFavoritesStore(file))
            repository.load()
            assertFalse(repository.state.value.isLoaded)
            assertNotNull(repository.state.value.error)
            repository.toggle(snapshot())
            assertEquals(corrupt, file.baseFile.readText())
        } finally { file.delete() }
    }
}
