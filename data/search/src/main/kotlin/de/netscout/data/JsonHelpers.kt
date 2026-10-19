package de.netscout.data

import de.netscout.core.SourceException
import de.netscout.core.SourceStatus
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import org.jsoup.Jsoup
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

internal fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
internal fun JsonObject.number(key: String) = (get(key) as? JsonPrimitive)?.intOrNull ?: 0
internal fun JsonObject.decimal(key: String) = (get(key) as? JsonPrimitive)?.doubleOrNull
internal fun JsonObject.flag(key: String) = (get(key) as? JsonPrimitive)?.booleanOrNull ?: false
internal fun JsonObject.obj(key: String) = get(key) as? JsonObject ?: JsonObject(emptyMap())
internal fun JsonObject.rows(key: String) = (get(key) as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
internal fun JsonObject.requireRows(key: String): List<JsonObject> {
    if (get(key) !is JsonArray) throw SourceException(SourceStatus.ERROR, "Quelle liefert keine gültige Trefferliste.")
    return rows(key)
}
internal fun JsonObject.array(key: String) = get(key) as? JsonArray ?: JsonArray(emptyList())
internal fun cleanHtml(text: String) = Jsoup.parse(text).text().trim().take(700)
// Parse code spans/fences before HTML extraction so literal <Type> is preserved as text.
private val markdownParser = Parser.builder().build()
private val markdownRenderer = HtmlRenderer.builder().build()
internal fun markdownText(text: String) = fullText(markdownRenderer.render(markdownParser.parse(text)))
internal fun fullText(text: String): String {
    val document = Jsoup.parse(text)
    // Keep paragraph/list boundaries for relevance. Jsoup.text() flattened unrelated sections.
    document.select("br").append("\n")
    document.select("p,li,div,pre,h1,h2,h3,h4,blockquote").append("\n")
    return document.wholeText().lines().joinToString("\n") { it.trim() }.trim()
}
internal fun parseDate(text: String): Instant? = runCatching { Instant.parse(text) }.getOrNull()
    ?: runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull()
    ?: runCatching { LocalDate.parse(text.take(10)).atStartOfDay().toInstant(ZoneOffset.UTC) }.getOrNull()
internal fun epoch(value: Double?): Instant? = value?.let { runCatching { Instant.ofEpochSecond(it.toLong()) }.getOrNull() }
