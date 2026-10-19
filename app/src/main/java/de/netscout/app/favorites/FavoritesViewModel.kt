package de.netscout.app.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.netscout.core.SearchHit
import kotlinx.coroutines.launch

class FavoritesViewModel(private val repository: FavoritesRepository) : ViewModel() {
    val state = repository.state

    init { retryLoad() }

    fun retryLoad() { viewModelScope.launch { repository.load() } }
    fun toggle(hit: SearchHit, keywords: String) { viewModelScope.launch { repository.toggle(Favorite.from(hit, keywords)) } }
    fun remove(favorite: Favorite) { viewModelScope.launch { repository.remove(favorite.id) } }
    fun clearError() = repository.clearError()
}
