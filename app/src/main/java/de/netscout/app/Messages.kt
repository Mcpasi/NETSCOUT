package de.netscout.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import de.netscout.core.MessageKey
import de.netscout.core.SearchMessage
import de.netscout.core.SearchSource
import de.netscout.core.SortOrder
import java.time.format.DateTimeFormatter

internal fun Context.messageText(message: SearchMessage): String {
    val resource = when (message.key) {
        MessageKey.QUERY_TOO_SHORT -> R.string.message_query_too_short
        MessageKey.QUERY_TOO_LONG -> R.string.message_query_too_long
        MessageKey.QUERY_QUOTES -> R.string.message_query_quotes
        MessageKey.QUERY_TERMS -> R.string.message_query_terms
        MessageKey.QUERY_SOURCES -> R.string.message_query_sources
        MessageKey.QUERY_DATES -> R.string.message_query_dates
        MessageKey.ACCESS_URL -> R.string.message_access_url
        MessageKey.SOURCE_SETUP -> R.string.message_source_setup
        MessageKey.SOURCE_TIMEOUT -> R.string.message_source_timeout
        MessageKey.SOURCE_UNAVAILABLE -> R.string.message_source_unavailable
        MessageKey.SOURCE_INVALID_LIST -> R.string.message_source_invalid_list
        MessageKey.PUBLIC_TIMEOUT -> R.string.message_public_timeout
        MessageKey.PUBLIC_BLOCKED -> R.string.message_public_blocked
        MessageKey.PUBLIC_CHALLENGE -> R.string.message_public_challenge
        MessageKey.PUBLIC_UNREADABLE -> R.string.message_public_unreadable
        MessageKey.INVALID_CURSOR -> R.string.message_invalid_cursor
        MessageKey.INVALID_RESPONSE -> R.string.message_invalid_response
        MessageKey.SOURCE_PAUSE_SEARCH -> R.string.message_source_pause_search
        MessageKey.SOURCE_PAUSE_RETRY -> R.string.message_source_pause_retry
        MessageKey.ACCESS_REJECTED -> R.string.message_access_rejected
        MessageKey.HTTP_REJECTED -> R.string.message_http_rejected
        MessageKey.RESPONSE_TOO_LARGE -> R.string.message_response_too_large
        MessageKey.RELEVANCE_FAILED -> R.string.message_relevance_failed
        MessageKey.EXPANSION_PARTIAL -> R.string.message_expansion_partial
        MessageKey.REDDIT_SETUP -> R.string.message_reddit_setup
        MessageKey.YOUTUBE_SETUP -> R.string.message_youtube_setup
        MessageKey.SEARX_SETUP -> R.string.message_searx_setup
        MessageKey.STACK_PAUSE -> R.string.message_stack_pause
        MessageKey.DIRECT_TIMEOUT -> R.string.message_direct_timeout
        MessageKey.FALLBACK_PUBLIC -> R.string.message_fallback_public
        MessageKey.FALLBACK_CUSTOM -> R.string.message_fallback_custom
        MessageKey.FALLBACK_DIRECT -> R.string.message_fallback_direct
        MessageKey.PUBLIC_NOTE -> R.string.message_public_note
        MessageKey.PUBLIC_WEB_NOTE -> R.string.message_public_web_note
        MessageKey.PUBLIC_SOURCE_NOTE -> R.string.message_public_source_note
        MessageKey.PUBLIC_DATE_NOTE -> R.string.message_public_date_note
        MessageKey.REDDIT_NOTE -> R.string.message_reddit_note
        MessageKey.SEARX_NOTE -> R.string.message_searx_note
        MessageKey.SEARX_UNAVAILABLE -> R.string.message_searx_unavailable
        MessageKey.GITHUB_NOTE -> R.string.message_github_note
        MessageKey.GITHUB_PARTIAL -> R.string.message_github_partial
        MessageKey.GITHUB_INCOMPLETE -> R.string.message_github_incomplete
        MessageKey.GITHUB_NO_CODE -> R.string.message_github_no_code
        MessageKey.FORUMS_NOTE -> R.string.message_forums_note
        MessageKey.FORUMS_UNAVAILABLE -> R.string.message_forums_unavailable
        MessageKey.FORUMS_PAUSE -> R.string.message_forums_pause
        MessageKey.FORUMS_QUOTA -> R.string.message_forums_quota
        MessageKey.HN_NOTE -> R.string.message_hn_note
        MessageKey.PUBLIC_CONTEXT -> R.string.message_public_context
        MessageKey.SEARX_CONTEXT -> R.string.message_searx_context
        MessageKey.HN_CONTEXT -> R.string.message_hn_context
        MessageKey.ACCESS_LOAD_FAILED -> R.string.message_access_load_failed
        MessageKey.ACCESS_SAVE_FAILED -> R.string.message_access_save_failed
        MessageKey.FAVORITES_LOAD_FAILED -> R.string.message_favorites_load_failed
        MessageKey.FAVORITES_SAVE_FAILED -> R.string.message_favorites_save_failed
        MessageKey.FAVORITES_LIMIT -> R.string.message_favorites_limit
    }
    val arguments = if (message.key == MessageKey.PUBLIC_SOURCE_NOTE) {
        val source = SearchSource.valueOf(message.arguments.single())
        listOf(if (source == SearchSource.FORUMS) getString(R.string.source_forums) else source.label)
    } else message.arguments
    return getString(resource, *arguments.toTypedArray())
}

@Composable
internal fun messageText(message: SearchMessage): String = LocalContext.current.messageText(message)

@Composable
internal fun sourceLabel(source: SearchSource): String =
    if (source == SearchSource.FORUMS) stringResource(R.string.source_forums) else source.label

@Composable
internal fun sourceHint(source: SearchSource): String = stringResource(when (source) {
    SearchSource.REDDIT, SearchSource.YOUTUBE -> R.string.source_keyless_index
    SearchSource.WEB -> R.string.source_keyless
    SearchSource.GITHUB -> R.string.source_github_hint
    SearchSource.FORUMS -> R.string.source_forums_hint
    SearchSource.HACKER_NEWS -> R.string.source_hn_hint
})

@Composable
internal fun sortLabel(order: SortOrder): String = stringResource(when (order) {
    SortOrder.NEWEST -> R.string.sort_newest
    SortOrder.RELEVANCE -> R.string.sort_relevance
})

@Composable
internal fun dateFormat(): DateTimeFormatter {
    val locale = LocalConfiguration.current.locales[0]
    return DateTimeFormatter.ofPattern(if (locale.language == "de") "dd.MM.yyyy" else "MM/dd/yyyy", locale)
}
