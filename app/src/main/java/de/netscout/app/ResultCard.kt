package de.netscout.app

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.netscout.core.KeywordQuery
import de.netscout.core.SearchItem
import de.netscout.core.SearchSource
import java.time.ZoneOffset

@Composable
internal fun ResultCard(
    row: SearchItem,
    sources: Set<SearchSource>,
    keywords: String,
    isFavorite: Boolean,
    favoriteEnabled: Boolean = true,
    onToggleFavorite: () -> Unit,
) {
    val context = LocalContext.current
    fun open(url: String) = openUrl(context, url)
    var expanded by remember(row.id, row.snippet) { mutableStateOf(false) }
    var expandable by remember(row.id, row.snippet) { mutableStateOf(false) }
    val query = remember(keywords) { KeywordQuery.parse(keywords) }
    val highlight = MaterialTheme.colorScheme.primary
    Surface(onClick = { open(row.url) }, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .4f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val sourceNames = sources.map { sourceLabel(it) }.joinToString(" + ")
                Text(sourceNames + " · " + (row.publishedAt?.atZone(ZoneOffset.UTC)?.toLocalDate()?.format(dateFormat()) ?: stringResource(R.string.date_unknown)),
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                IconToggleButton(checked = isFavorite, onCheckedChange = { onToggleFavorite() }, enabled = favoriteEnabled) {
                    Icon(if (isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                        contentDescription = if (isFavorite) stringResource(R.string.favorite_remove) else stringResource(R.string.favorite_add))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(highlighted(row.title.ifBlank { row.url }, query, highlight), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = stringResource(R.string.open_result), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (row.assessment?.semantic == true) Text(stringResource(R.string.semantic_result), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            if (row.snippet.isNotBlank()) {
                Text(highlighted(row.snippet, query, highlight), maxLines = if (expanded) Int.MAX_VALUE else 8,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    onTextLayout = { if (!expanded) expandable = it.hasVisualOverflow })
                if (expandable || expanded) TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
                    Text(if (expanded) stringResource(R.string.collapse_passage) else stringResource(R.string.expand_passage))
                }
            }
            Text(listOf(row.author, row.contextMessage?.let { messageText(it) } ?: row.context).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            row.license?.let { license ->
                Text(license, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { open(if (license.contains("4.0")) "https://creativecommons.org/licenses/by-sa/4.0/" else "https://stackoverflow.com/help/licensing") }.padding(vertical = 8.dp))
            }
        }
    }
}

private fun highlighted(text: String, query: KeywordQuery, color: Color) = buildAnnotatedString {
    append(text)
    query.ranges(text).forEach { addStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold), it.first, it.last + 1) }
}
