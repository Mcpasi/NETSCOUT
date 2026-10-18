package de.netscout.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.netscout.core.*
import de.netscout.data.ApiSettings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val DateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetScoutApp(model: SearchViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var dateField by rememberSaveable { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    val search = { focus.clearFocus(); model.search() }
    Scaffold(
        topBar = {
            TopAppBar(title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RadarMark()
                    Text("NETSCOUT", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("PROTOTYP", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                }
            }, actions = {
                IconButton(onClick = { showSettings = true }) { Icon(Icons.Outlined.Settings, contentDescription = "Zugänge einrichten") }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 800.dp).fillMaxSize().imePadding().testTag("search-content"),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Netzsuche", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                        Text("Ein Suchauftrag. Mehr Perspektiven.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item {
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("Wonach suchst du?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(
                                value = state.keywords, onValueChange = model::keywords, modifier = Modifier.fillMaxWidth(),
                                label = { Text("Keywords") }, placeholder = { Text("z. B. selbst gehostete KI") },
                                leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search() }),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                DateField("Von", state.from, { dateField = "from" }, { model.from(null) }, Modifier.weight(1f))
                                DateField("Bis", state.until, { dateField = "until" }, { model.until(null) }, Modifier.weight(1f))
                            }
                            Text("Optional · Veröffentlichungsdatum · inklusive beider Tage (UTC)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            HorizontalDivider()
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Quellen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("${state.sources.size} / 6", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                            }
                            SearchSource.entries.chunked(2).forEach { pair ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    pair.forEach { source -> SourceChoice(source, source in state.sources, { model.toggle(source) }, Modifier.weight(1f)) }
                                }
                            }
                            Text("GitHub, Foren und Hacker News starten ohne Schlüssel.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            HorizontalDivider()
                            Column {
                                SortOrder.entries.forEach { order ->
                                    Row(
                                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                            .toggleable(state.sort == order, role = Role.Checkbox, onValueChange = { model.sort(order) }),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Checkbox(state.sort == order, onCheckedChange = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(order.label, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                                Text("Eine Sortierung ist aktiv.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                            Button(onClick = search, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = !state.isSearching, shape = RoundedCornerShape(12.dp)) {
                                Icon(Icons.Outlined.Search, null); Spacer(Modifier.width(8.dp))
                                Text(if (state.isSearching) "Quellen werden durchsucht …" else "Netz durchsuchen", fontWeight = FontWeight.Bold)
                            }
                            if (state.isSearching) TextButton(onClick = model::cancel, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Suche abbrechen") }
                        }
                    }
                }
                state.settingsWarning?.let { warning -> item { Text(warning, color = MaterialTheme.colorScheme.error) } }
                if (state.activeRequest == null) {
                    item {
                        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Dein Radar fürs Netz", fontWeight = FontWeight.SemiBold)
                                Text("Finde Beiträge und Diskussionen. Verbinde weitere Quellen für Videos, Reddit und die Websuche.", style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = { showSettings = true }, contentPadding = PaddingValues(0.dp)) { Text("Weitere Quellen verbinden →") }
                            }
                        }
                    }
                } else {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Quellenstatus", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                            if (state.isSearching) LinearProgressIndicator(Modifier.fillMaxWidth())
                            state.activeRequest!!.sources.sortedBy { it.ordinal }.forEach { source ->
                                SourceStatusRow(source, state.outcomes[source], state.sourceCounts[source] ?: 0, state.isSearching, { showSettings = true })
                            }
                            if (state.activeRequest!!.hasDateFilter) Text("Treffer ohne bekanntes Veröffentlichungsdatum werden ausgeblendet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${state.results.size} Treffer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                            Text("Für „${state.activeRequest!!.keywords}“ · ${state.activeRequest!!.sort.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (state.filtersChanged) Text("Filter geändert. Starte die Suche erneut.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    items(state.results, key = { ResultMerger.canonicalUrl(it.item.url) }) { hit -> ResultCard(hit) }
                    if (!state.isSearching && state.results.isEmpty()) {
                        item {
                            Text(if (state.outcomes.values.none { it.status == SourceStatus.SUCCESS }) "Noch keine Quelle hat Ergebnisse geliefert. Prüfe die Hinweise oben und versuche es erneut."
                            else "Keine passenden Treffer in den geladenen Seiten. Probiere andere Keywords, einen größeren Zeitraum oder weitere Seiten.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (state.canLoadMore) item {
                        OutlinedButton(onClick = model::loadMore, modifier = Modifier.fillMaxWidth()) { Text("Weitere Treffer laden") }
                    }
                }
                item {
                    Text("Live-Quellen · Abdeckung und API-Limits unterscheiden sich je Plattform. Keine vollständige Erfassung des Netzes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (showSettings) AccessDialog(state.settings, onDismiss = { showSettings = false }, onSave = model::saveSettings)
    dateField?.let { field ->
        val current = if (field == "from") state.from else state.until
        val picker = rememberDatePickerState(initialSelectedDateMillis = current?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli())
        DatePickerDialog(onDismissRequest = { dateField = null }, confirmButton = {
            TextButton(onClick = {
                val date = picker.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                if (field == "from") model.from(date) else model.until(date)
                dateField = null
            }) { Text("Übernehmen") }
        }, dismissButton = { TextButton(onClick = { dateField = null }) { Text("Abbrechen") } }) { DatePicker(picker) }
    }
}

@Composable
private fun RadarMark() {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(30.dp)) {
        drawCircle(color, radius = size.minDimension * .43f, style = Stroke(2.dp.toPx()))
        drawCircle(color.copy(alpha = .45f), radius = size.minDimension * .25f, style = Stroke(1.dp.toPx()))
        drawLine(color, center, Offset(size.width * .85f, size.height * .15f), 2.dp.toPx())
        drawCircle(color, radius = 2.dp.toPx())
    }
}

@Composable
private fun DateField(label: String, date: LocalDate?, open: () -> Unit, clear: () -> Unit, modifier: Modifier) {
    Surface(onClick = open, modifier = modifier, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(date?.format(DateFormat) ?: "Beliebig", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = if (date != null) clear else open) {
                    Icon(if (date != null) Icons.Outlined.Close else Icons.Outlined.DateRange, if (date != null) "$label-Datum löschen" else "$label-Datum wählen", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun SourceChoice(source: SearchSource, checked: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp),
        color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (checked) MaterialTheme.colorScheme.primary.copy(alpha = .6f) else MaterialTheme.colorScheme.outline.copy(alpha = .5f))) {
        Row(Modifier.toggleable(checked, role = Role.Checkbox, onValueChange = { onClick() }).padding(10.dp).heightIn(min = 54.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(checked, onCheckedChange = null, modifier = Modifier.size(22.dp))
            Column {
                Text(source.label, style = MaterialTheme.typography.labelLarge)
                Text(source.hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SourceStatusRow(source: SearchSource, outcome: SourceOutcome?, count: Int, running: Boolean, connect: () -> Unit) {
    val color = when (outcome?.status) {
        SourceStatus.SUCCESS -> MaterialTheme.colorScheme.primary
        SourceStatus.ERROR, SourceStatus.RATE_LIMITED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(source.label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(when (outcome?.status) {
                    SourceStatus.SUCCESS -> "$count Treffer" + if (outcome.cached) " · Cache" else ""
                    SourceStatus.SETUP_REQUIRED -> "Zugang nötig"
                    SourceStatus.RATE_LIMITED -> "API-Pause"
                    SourceStatus.ERROR -> "Nicht erreichbar"
                    null -> if (running) "Suche läuft …" else "Abgebrochen"
                }, color = color, style = MaterialTheme.typography.labelMedium)
            }
            (outcome?.message ?: outcome?.page?.note)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (outcome?.status == SourceStatus.SETUP_REQUIRED) TextButton(onClick = connect, contentPadding = PaddingValues(0.dp)) { Text("Zugang einrichten") }
        }
    }
}

@Composable
private fun ResultCard(hit: SearchHit) {
    val context = LocalContext.current
    fun open(url: String) {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: ActivityNotFoundException) { Toast.makeText(context, "Keine App zum Öffnen des Links gefunden.", Toast.LENGTH_SHORT).show() }
    }
    val row = hit.item
    Surface(onClick = { open(row.url) }, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .4f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(hit.sources.joinToString(" + ") { it.label } + " · " + (row.publishedAt?.atZone(ZoneOffset.UTC)?.toLocalDate()?.format(DateFormat) ?: "Datum unbekannt"),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(row.title.ifBlank { row.url }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = "Treffer im Browser öffnen", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (row.snippet.isNotBlank()) Text(row.snippet, maxLines = 4, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(listOf(row.author, row.context).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            row.license?.let { license ->
                Text(license, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { open(if (license.contains("4.0")) "https://creativecommons.org/licenses/by-sa/4.0/" else "https://stackoverflow.com/help/licensing") }.padding(vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun AccessDialog(initial: ApiSettings, onDismiss: () -> Unit, onSave: (ApiSettings) -> String?) {
    // Credentials deliberately use remember, not rememberSaveable (no plaintext saved-instance state).
    var searxng by remember { mutableStateOf(initial.searxngUrl) }
    var youtube by remember { mutableStateOf(initial.youtubeApiKey) }
    var reddit by remember { mutableStateOf(initial.redditAccessToken) }
    var github by remember { mutableStateOf(initial.githubToken) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Quellen verbinden") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Optional. GitHub, Hacker News und drei Stack-Exchange-Foren funktionieren ohne Schlüssel.", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(searxng, { searxng = it.take(4096) }, label = { Text("SearXNG-Basis-URL") }, placeholder = { Text("https://suche.example.org/") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
            Text("Für Websuche sowie Reddit-/YouTube-Indexsuche. Eigene oder erlaubte HTTPS-Instanz verwenden; JSON-Ausgabe muss aktiviert sein.", style = MaterialTheme.typography.bodySmall)
            SecretField(youtube, { youtube = it }, "YouTube Data API-Key")
            Text("YouTube Data API v3 im Google-Projekt aktivieren. Das Suchkontingent ist begrenzt.", style = MaterialTheme.typography.bodySmall)
            SecretField(reddit, { reddit = it }, "Reddit OAuth Access-Token")
            Text("Benötigt genehmigten API-Zugang. Access-Tokens laufen ab und müssen im Prototyp manuell ersetzt werden.", style = MaterialTheme.typography.bodySmall)
            SecretField(github, { github = it }, "GitHub-Token (optional)")
            Text("Erhöht das API-Limit. Nur öffentliche Inhalte werden gesucht. Alle Zugänge werden lokal verschlüsselt gespeichert.", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = {
        TextButton(onClick = { error = onSave(ApiSettings(searxng, youtube, reddit, github)); if (error == null) onDismiss() }) { Text("Speichern") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } })
}

@Composable
private fun SecretField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(value, { onChange(it.take(4096)) }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
}
