package de.netscout.app.favorites

import de.netscout.core.ResultMerger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface FavoritesStore {
    suspend fun load(): List<Favorite>
    suspend fun save(favorites: List<Favorite>)
}

data class FavoritesState(
    val entries: List<Favorite> = emptyList(),
    val isLoading: Boolean = true,
    val isLoaded: Boolean = false,
    val error: String? = null,
)

/** Serializes mutations and only publishes changes after they have reached durable storage. */
class FavoritesRepository(private val store: FavoritesStore) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(FavoritesState())
    val state = mutableState.asStateFlow()

    suspend fun load() = mutex.withLock {
        if (mutableState.value.isLoaded) return@withLock
        mutableState.update { it.copy(isLoading = true, error = null) }
        try {
            val entries = store.load()
            require(entries.size <= MAX_FAVORITES && entries.distinctBy { it.id }.size == entries.size)
            require(entries.all { ResultMerger.safeWebUrl(it.item.url) && it.sources.isNotEmpty() })
            mutableState.value = FavoritesState(entries = entries, isLoading = false, isLoaded = true)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableState.update { it.copy(isLoading = false, error = "Favoriten konnten nicht geladen werden. Bitte erneut versuchen.") }
        }
    }

    suspend fun toggle(favorite: Favorite) = mutex.withLock {
        val current = mutableState.value
        // A failed read must never be overwritten by a seemingly empty collection.
        if (!current.isLoaded) return@withLock
        val exists = current.entries.any { it.id == favorite.id }
        if (!exists && current.entries.size >= MAX_FAVORITES) {
            mutableState.update { it.copy(error = "Du kannst höchstens $MAX_FAVORITES Favoriten speichern. Entferne zuerst einen Favoriten.") }
            return@withLock
        }
        if (!ResultMerger.safeWebUrl(favorite.item.url)) return@withLock
        persist(if (exists) current.entries.filterNot { it.id == favorite.id } else listOf(favorite) + current.entries)
    }

    suspend fun remove(id: String) = mutex.withLock {
        val current = mutableState.value
        if (!current.isLoaded || current.entries.none { it.id == id }) return@withLock
        persist(current.entries.filterNot { it.id == id })
    }

    fun clearError() { mutableState.update { it.copy(error = null) } }

    private suspend fun persist(entries: List<Favorite>) {
        try {
            store.save(entries)
            mutableState.update { it.copy(entries = entries, error = null) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableState.update { it.copy(error = "Favoriten konnten nicht gespeichert werden. Bitte erneut versuchen.") }
        }
    }
}
