package de.netscout.app

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.netscout.core.*
import de.netscout.data.ApiSettings
import de.netscout.data.publicSearchUrl
import de.netscout.app.favorites.FavoritesScreen
import de.netscout.app.favorites.FavoritesViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun NetScoutApp(model: SearchViewModel, favoritesModel: FavoritesViewModel, languagePreferences: LanguagePreferences? = null) {
    AppLanguageHost(languagePreferences) { language, onLanguage ->
        NetScoutContent(model, favoritesModel, language, onLanguage)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NetScoutContent(model: SearchViewModel, favoritesModel: FavoritesViewModel, language: AppLanguage?, onLanguage: (AppLanguage?) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val favorites by favoritesModel.state.collectAsStateWithLifecycle()
    val favoriteIds = remember(favorites.entries) { favorites.entries.map { it.id }.toSet() }
    var showFavorites by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var dateField by rememberSaveable { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    BackHandler(enabled = showFavorites) { showFavorites = false }
    LaunchedEffect(favorites.error, context) {
        favorites.error?.let {
            snackbar.showSnackbar(context.messageText(it), withDismissAction = true)
            favoritesModel.clearError()
        }
    }
    var searchScrollRequest by remember { mutableIntStateOf(0) }
    val query = remember(state.keywords) { KeywordQuery.parse(state.keywords) }
    LaunchedEffect(searchScrollRequest, state.activeRequest, state.settingsWarning) {
        if (searchScrollRequest > 0 && state.activeRequest != null) {
            // Wait for the submitted request to be composed, including instant/cache answers.
            // The heading and search form precede the optional settings warning.
            listState.scrollToItem(if (state.settingsWarning == null) 2 else 3)
        }
    }
    val search = {
        focus.clearFocus()
        model.search()
        if (model.state.value.activeRequest != null && model.state.value.error == null) searchScrollRequest++
        Unit
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                TopAppBar(navigationIcon = {
                    if (showFavorites) IconButton(onClick = { showFavorites = false }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back_to_search))
                    }
                }, title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RadarMark()
                        Text("NETSCOUT", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }, actions = {
                    if (!showFavorites) TextButton(onClick = { showFavorites = true }) {
                        Icon(Icons.Outlined.Star, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.favorites))
                    }
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.access_title)) }
                }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
                LanguageBar(language, onLanguage)
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            if (showFavorites) {
                FavoritesScreen(favorites, onRetry = favoritesModel::retryLoad, onRemove = favoritesModel::remove)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.widthIn(max = 800.dp).fillMaxSize().imePadding().testTag("search-content"),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.search_heading), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                            Text(stringResource(R.string.search_tagline), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    item {
                        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text(stringResource(R.string.search_prompt), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                OutlinedTextField(
                                    value = state.keywords, onValueChange = model::keywords, modifier = Modifier.fillMaxWidth(),
                                    label = { Text(stringResource(R.string.keywords)) }, placeholder = { Text(stringResource(R.string.keywords_example)) },
                                    leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search() }),
                                )
                                Text(stringResource(R.string.keywords_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (query.terms.isNotEmpty() && query.balancedQuotes) Text(
                                    stringResource(R.string.required_terms, query.terms.joinToString(" · ") { if (' ' in it) context.getString(R.string.quoted_term, it) else it }),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                                )
                                Text(stringResource(R.string.relevance_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    DateField(stringResource(R.string.date_from), state.from, { dateField = "from" }, { model.from(null) }, Modifier.weight(1f))
                                    DateField(stringResource(R.string.date_until), state.until, { dateField = "until" }, { model.until(null) }, Modifier.weight(1f))
                                }
                                Text(stringResource(R.string.date_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                HorizontalDivider()
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(stringResource(R.string.sources), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text("${state.sources.size} / 6", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                                }
                                SearchSource.entries.chunked(2).forEach { pair ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        pair.forEach { source -> SourceChoice(source, source in state.sources, { model.toggle(source) }, Modifier.weight(1f)) }
                                    }
                                }
                                Text(stringResource(R.string.keyless_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                            Text(sortLabel(order), style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                    Text(stringResource(R.string.sort_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                state.error?.let { Text(messageText(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                                Button(onClick = search, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = !state.isSearching, shape = RoundedCornerShape(12.dp)) {
                                    Icon(Icons.Outlined.Search, null); Spacer(Modifier.width(8.dp))
                                    Text(if (state.isSearching) stringResource(R.string.search_running) else stringResource(R.string.search_button), fontWeight = FontWeight.Bold)
                                }
                                if (state.isSearching) TextButton(onClick = model::cancel, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(stringResource(R.string.search_cancel)) }
                            }
                        }
                    }
                    state.settingsWarning?.let { warning -> item { Text(messageText(warning), color = MaterialTheme.colorScheme.error) } }
                    if (state.activeRequest == null) {
                        item {
                            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(stringResource(R.string.intro_title), fontWeight = FontWeight.SemiBold)
                                    Text(stringResource(R.string.intro_body), style = MaterialTheme.typography.bodyMedium)
                                    TextButton(onClick = { showSettings = true }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.intro_access)) }
                                }
                            }
                        }
                    } else {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.source_status), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                                if (state.isSearching) LinearProgressIndicator(Modifier.fillMaxWidth())
                                state.activeRequest!!.sources.sortedBy { it.ordinal }.forEach { source ->
                                    SourceStatusRow(source, state.outcomes[source], state.sourceProgress[source], state.sourceCounts[source] ?: 0,
                                        state.candidateCounts[source] ?: 0, { showSettings = true },
                                        { openUrl(context, publicSearchUrl(source, state.activeRequest!!)) })
                                }
                                if (state.activeRequest!!.hasDateFilter) Text(stringResource(R.string.unknown_date_filtered), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(pluralStringResource(R.plurals.result_count, state.results.size, state.results.size), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                                Text(stringResource(R.string.results_for, state.activeRequest!!.keywords, sortLabel(state.activeRequest!!.sort)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (state.unmatchedCount > 0) Text(pluralStringResource(R.plurals.excluded_count, state.unmatchedCount, state.unmatchedCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (state.filtersChanged) Text(stringResource(R.string.filters_changed), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        items(state.results, key = { ResultMerger.canonicalUrl(it.item.url) }) { hit ->
                            ResultCard(hit.item, hit.sources, state.activeRequest!!.keywords,
                                isFavorite = ResultMerger.canonicalUrl(hit.item.url) in favoriteIds,
                                favoriteEnabled = favorites.isLoaded,
                                onToggleFavorite = { favoritesModel.toggle(hit, state.activeRequest!!.keywords) })
                        }
                        if (!state.isSearching && state.results.isEmpty()) {
                            item {
                                Text(if (state.outcomes.values.none { it.status == SourceStatus.SUCCESS }) stringResource(R.string.no_source_results)
                                else stringResource(R.string.no_matching_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (state.canLoadMore) item {
                            OutlinedButton(onClick = model::loadMore, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.load_more)) }
                        }
                    }
                    item {
                        Text(stringResource(R.string.search_footer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    if (showSettings) AccessDialog(state.settings, onDismiss = { showSettings = false }, onSave = model::saveSettings)
    dateField?.let { field ->
        val current = if (field == "from") state.from else state.until
        val picker = rememberDatePickerState(initialSelectedDateMillis = current?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli())
        val resources = rememberLanguageResources()
        DatePickerDialog(onDismissRequest = { dateField = null }, confirmButton = {
            resources.Provide {
                TextButton(onClick = {
                    val date = picker.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    if (field == "from") model.from(date) else model.until(date)
                    dateField = null
                }) { Text(stringResource(R.string.apply)) }
            }
        }, dismissButton = {
            resources.Provide { TextButton(onClick = { dateField = null }) { Text(stringResource(R.string.cancel)) } }
        }) { resources.Provide { DatePicker(picker) } }
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
                Text(date?.format(dateFormat()) ?: stringResource(R.string.any_date), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = if (date != null) clear else open) {
                    Icon(if (date != null) Icons.Outlined.Close else Icons.Outlined.DateRange, stringResource(if (date != null) R.string.clear_date else R.string.choose_date, label), modifier = Modifier.size(18.dp))
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
                Text(sourceLabel(source), style = MaterialTheme.typography.labelLarge)
                Text(sourceHint(source), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SourceStatusRow(source: SearchSource, outcome: SourceOutcome?, progress: SourceProgress?, count: Int, candidates: Int, connect: () -> Unit, searchInBrowser: () -> Unit) {
    val color = when (if (progress == null) outcome?.status else null) {
        SourceStatus.SUCCESS -> MaterialTheme.colorScheme.primary
        SourceStatus.ERROR, SourceStatus.RATE_LIMITED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(modifier = Modifier.testTag("source-status-${source.name}"), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(sourceLabel(source), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(when (progress?.phase) {
                    SourcePhase.FETCHING -> stringResource(R.string.status_fetching)
                    SourcePhase.ASSESSING -> stringResource(R.string.status_assessing)
                    null -> when (outcome?.status) {
                        SourceStatus.SUCCESS -> pluralStringResource(R.plurals.result_count, count, count).let {
                            if (outcome.cached) stringResource(R.string.status_cached, it) else it
                        }
                        SourceStatus.SETUP_REQUIRED -> stringResource(R.string.status_setup)
                        SourceStatus.RATE_LIMITED -> stringResource(R.string.status_paused)
                        SourceStatus.ERROR -> stringResource(R.string.status_error)
                        null -> stringResource(R.string.status_cancelled)
                    }
                }, color = color, style = MaterialTheme.typography.labelMedium)
            }
            val detail = when {
                progress?.phase == SourcePhase.ASSESSING -> pluralStringResource(R.plurals.candidates_loaded, progress.candidateCount, progress.candidateCount)
                progress != null -> null
                outcome?.status == SourceStatus.SUCCESS -> if (candidates == 0) stringResource(R.string.no_candidates)
                    else pluralStringResource(R.plurals.candidates_checked, candidates, candidates)
                else -> null
            }
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (progress == null) {
                val context = LocalContext.current
                val notice = outcome?.message?.let(context::messageText)
                    ?: outcome?.page?.notes?.takeIf { it.isNotEmpty() }?.joinToString("") { context.messageText(it) }
                notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (outcome?.status == SourceStatus.SETUP_REQUIRED) TextButton(onClick = connect, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.setup_access)) }
                if (outcome?.status in setOf(SourceStatus.RATE_LIMITED, SourceStatus.ERROR)) TextButton(onClick = searchInBrowser, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.browser_search)) }
            }
        }
    }
}

@Composable
private fun AccessDialog(initial: ApiSettings, onDismiss: () -> Unit, onSave: (ApiSettings) -> SearchMessage?) {
    // Credentials deliberately use remember, not rememberSaveable (no plaintext saved-instance state).
    var searxng by remember { mutableStateOf(initial.searxngUrl) }
    var youtube by remember { mutableStateOf(initial.youtubeApiKey) }
    var reddit by remember { mutableStateOf(initial.redditAccessToken) }
    var github by remember { mutableStateOf(initial.githubToken) }
    var error by remember { mutableStateOf<SearchMessage?>(null) }
    val resources = rememberLanguageResources()
    AlertDialog(onDismissRequest = onDismiss, title = { resources.Provide { Text(stringResource(R.string.access_title)) } }, text = {
        resources.Provide {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.access_help), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(searxng, { searxng = it.take(4096) }, label = { Text(stringResource(R.string.searx_url)) }, placeholder = { Text("https://suche.example.org/") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.searx_help), style = MaterialTheme.typography.bodySmall)
                SecretField(youtube, { youtube = it }, stringResource(R.string.youtube_key))
                Text(stringResource(R.string.youtube_help), style = MaterialTheme.typography.bodySmall)
                SecretField(reddit, { reddit = it }, stringResource(R.string.reddit_token))
                Text(stringResource(R.string.reddit_help), style = MaterialTheme.typography.bodySmall)
                SecretField(github, { github = it }, stringResource(R.string.github_token))
                Text(stringResource(R.string.github_help), style = MaterialTheme.typography.bodySmall)
                error?.let { Text(messageText(it), color = MaterialTheme.colorScheme.error) }
            }
        }
    }, confirmButton = {
        resources.Provide {
            TextButton(onClick = { error = onSave(ApiSettings(searxng, youtube, reddit, github)); if (error == null) onDismiss() }) { Text(stringResource(R.string.save)) }
        }
    }, dismissButton = { resources.Provide { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } } })
}

@Composable
private fun SecretField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(value, { onChange(it.take(4096)) }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
}
