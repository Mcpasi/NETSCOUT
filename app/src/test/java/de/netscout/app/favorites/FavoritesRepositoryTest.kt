package de.netscout.app.favorites

import de.netscout.core.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class FavoritesRepositoryTest {
    private class Store(var entries: List<Favorite> = emptyList()) : FavoritesStore {
        var failRead = false
        var failWrite = false
        var writes = 0
        override suspend fun load(): List<Favorite> {
            if (failRead) throw IOException("read failure")
            return entries.toList()
        }
        override suspend fun save(favorites: List<Favorite>) {
            delay(1) // Exercise overlapping user actions while persistence is in progress.
            if (failWrite) throw IOException("write failure")
            entries = favorites.toList()
            writes++
        }
    }

    @Test fun `saved snapshots survive repository recreation and deletion survives another restart`() = runTest {
        val store = Store()
        val first = FavoritesRepository(store)
        first.load()
        first.toggle(favorite(1))
        val restarted = FavoritesRepository(store)
        restarted.load()
        assertEquals(listOf(favorite(1)), restarted.state.value.entries)
        restarted.remove(favorite(1).id)
        val afterDeletion = FavoritesRepository(store)
        afterDeletion.load()
        assertTrue(afterDeletion.state.value.entries.isEmpty())
        assertTrue(afterDeletion.state.value.isLoaded)
    }

    @Test fun `twenty favorites are retained and the twenty first is rejected until space is freed`() = runTest {
        val store = Store()
        val repository = FavoritesRepository(store)
        repository.load()
        (1..20).forEach { repository.toggle(favorite(it)) }
        val original = store.entries
        repository.toggle(favorite(21))
        assertEquals(original, repository.state.value.entries)
        assertEquals(original, store.entries)
        assertEquals(20, store.writes)
        assertTrue(repository.state.value.error!!.text.contains("höchstens 20"))
        repository.remove(favorite(4).id)
        repository.toggle(favorite(21))
        assertEquals(20, store.entries.size)
        assertEquals(favorite(21), store.entries.first())
        assertFalse(store.entries.any { it.id == favorite(4).id })
        assertNull(repository.state.value.error)
    }

    @Test fun `tracking variants and another source identify the same saved result`() = runTest {
        val store = Store()
        val repository = FavoritesRepository(store)
        repository.load()
        val original = favorite(1)
        repository.toggle(original)
        val variant = original.copy(item = original.item.copy(source = SearchSource.WEB,
            url = "https://www.example.org/1/?utm_source=search#section"), sources = setOf(SearchSource.WEB))
        assertEquals(original.id, variant.id)
        repository.toggle(variant)
        assertTrue(store.entries.isEmpty())
    }

    @Test fun `different meaningful URL parameters retain separate favorites`() = runTest {
        val repository = FavoritesRepository(Store())
        repository.load()
        repository.toggle(favorite(1).let { it.copy(item = it.item.copy(url = "https://youtube.com/watch?v=one")) })
        repository.toggle(favorite(2).let { it.copy(item = it.item.copy(url = "https://youtube.com/watch?v=two")) })
        assertEquals(2, repository.state.value.entries.size)
    }

    @Test fun `concurrent saves lose no entries and enforce the limit`() = runTest {
        val store = Store()
        val repository = FavoritesRepository(store)
        repository.load()
        (1..25).map { async { repository.toggle(favorite(it)) } }.awaitAll()
        assertEquals(20, repository.state.value.entries.size)
        assertEquals(repository.state.value.entries, store.entries)
        assertEquals(20, store.writes)
    }

    @Test fun `a failed add or delete leaves both the displayed and saved collection unchanged`() = runTest {
        val original = listOf(favorite(1))
        val store = Store(original).apply { failWrite = true }
        val repository = FavoritesRepository(store)
        repository.load()
        repository.toggle(favorite(2))
        assertEquals(original, repository.state.value.entries)
        assertNotNull(repository.state.value.error)
        repository.remove(favorite(1).id)
        assertEquals(original, repository.state.value.entries)
        assertEquals(original, store.entries)
        store.failWrite = false
        repository.remove(favorite(1).id)
        assertTrue(store.entries.isEmpty())
        assertNull(repository.state.value.error)
    }

    @Test fun `a failed read cannot overwrite saved favorites and can be retried`() = runTest {
        val store = Store(listOf(favorite(1))).apply { failRead = true }
        val repository = FavoritesRepository(store)
        repository.load()
        assertFalse(repository.state.value.isLoading)
        assertFalse(repository.state.value.isLoaded)
        assertNotNull(repository.state.value.error)
        repository.toggle(favorite(2))
        repository.remove(favorite(1).id)
        assertEquals(0, store.writes)
        store.failRead = false
        repository.load()
        assertEquals(listOf(favorite(1)), repository.state.value.entries)
        assertTrue(repository.state.value.isLoaded)
        assertNull(repository.state.value.error)
    }

    @Test fun `toggling an existing favorite removes it even when all twenty slots are occupied`() = runTest {
        val store = Store((1..20).map(::favorite))
        val repository = FavoritesRepository(store)
        repository.load()
        repository.toggle(favorite(10))
        assertEquals(19, store.entries.size)
        assertFalse(store.entries.contains(favorite(10)))
    }

    @Test fun `cancellation propagates instead of pretending that a write failed`() = runTest {
        val repository = FavoritesRepository(object : FavoritesStore {
            override suspend fun load() = emptyList<Favorite>()
            override suspend fun save(favorites: List<Favorite>) { throw CancellationException() }
        })
        repository.load()
        try {
            repository.toggle(favorite(1))
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            assertTrue(repository.state.value.entries.isEmpty())
            assertNull(repository.state.value.error)
        }
    }
}

internal fun favorite(index: Int) = Favorite.from(
    SearchHit(SearchItem("$index", SearchSource.GITHUB, "Kotlin Treffer $index", "https://example.org/$index",
        snippet = "Eine passende Textstelle $index", searchText = "Ein viel längerer ursprünglicher Beitrag"),
        setOf(SearchSource.GITHUB), 0.7), "kotlin",
)
