package de.netscout.app.favorites

import androidx.compose.ui.res.stringResource
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
import de.netscout.app.R

@Composable
internal fun FavoritesScreen(state: FavoritesState, onRetry: () -> Unit, onRemove: (Favorite) -> Unit) {
    LazyColumn(
        modifier = Modifier.widthIn(max = 800.dp).fillMaxSize().testTag("favorites-content"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.favorites), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.favorites_count, state.entries.size, MAX_FAVORITES), color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.favorites_help),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        when {
            state.isLoading -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            !state.isLoaded -> item {
                Column {
                    Text(stringResource(R.string.favorites_load_failed), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.retry_load)) }
                }
            }
            state.entries.isEmpty() -> item {
                Text(stringResource(R.string.favorites_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(state.entries, key = { it.id }) { favorite ->
            ResultCard(favorite.item, favorite.sources, favorite.keywords, isFavorite = true,
                onToggleFavorite = { onRemove(favorite) })
        }
    }
}
