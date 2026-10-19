package de.netscout.app.favorites

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.netscout.app.ResultCard

@Composable
internal fun FavoritesScreen(state: FavoritesState, onRetry: () -> Unit, onRemove: (Favorite) -> Unit) {
    LazyColumn(
        modifier = Modifier.widthIn(max = 800.dp).fillMaxSize().testTag("favorites-content"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Favoriten", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() })
                Text("${state.entries.size} / $MAX_FAVORITES gespeichert", color = MaterialTheme.colorScheme.primary)
                Text("Auf diesem Gerät gespeichert – auch nach einem Neustart. Mit dem Stern entfernst du einen Favoriten.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        when {
            state.isLoading -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            !state.isLoaded -> item {
                Column {
                    Text("Favoriten konnten nicht geladen werden.", color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text("Erneut laden") }
                }
            }
            state.entries.isEmpty() -> item {
                Text("Noch keine Favoriten. Tippe nach einer Suche auf den Stern eines Treffers.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(state.entries, key = { it.id }) { favorite ->
            ResultCard(favorite.item, favorite.sources, favorite.keywords, isFavorite = true,
                onToggleFavorite = { onRemove(favorite) })
        }
    }
}
