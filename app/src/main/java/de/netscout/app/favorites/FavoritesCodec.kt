package de.netscout.app.favorites

import de.netscout.core.RelevanceAssessment
import de.netscout.core.ResultMerger
import de.netscout.core.SearchItem
import de.netscout.core.SearchSource
import de.netscout.core.MessageKey
import de.netscout.core.SearchMessage
import kotlinx.serialization.json.*
import java.time.Instant

/** Versioned format containing only the information required to display saved results. */
internal object FavoritesCodec {
    fun encode(favorites: List<Favorite>): String = buildJsonObject {
        put("version", 1)
        putJsonArray("favorites") {
            favorites.forEach { favorite ->
                val item = favorite.item
                add(buildJsonObject {
                    put("id", item.id)
                    put("source", item.source.name)
                    put("title", item.title)
                    put("url", item.url)
                    put("snippet", item.snippet)
                    put("author", item.author)
                    item.publishedAt?.let { put("publishedAt", it.toString()) }
                    put("rank", item.rank)
                    put("context", item.context)
                    item.contextMessage?.let { message ->
                        putJsonObject("contextMessage") {
                            put("key", message.key.name)
                            putJsonArray("arguments") { message.arguments.forEach { add(it) } }
                        }
                    }
                    item.license?.let { put("license", it) }
                    item.assessment?.let { assessment ->
                        putJsonObject("assessment") {
                            put("accepted", assessment.accepted)
                            put("score", assessment.score)
                            put("evidence", assessment.evidence)
                            put("semantic", assessment.semantic)
                        }
                    }
                    putJsonArray("sources") { favorite.sources.sortedBy { it.ordinal }.forEach { add(it.name) } }
                    put("keywords", favorite.keywords)
                })
            }
        }
    }.toString()

    fun decode(text: String): List<Favorite> {
        val root = Json.parseToJsonElement(text).jsonObject
        require(root.getValue("version").jsonPrimitive.int == 1) { "Unsupported favorites format" }
        val entries = root.getValue("favorites").jsonArray
        require(entries.size <= MAX_FAVORITES)
        return entries.map { element ->
            val row = element.jsonObject
            fun string(key: String) = row.getValue(key).jsonPrimitive.content
            val url = string("url")
            require(ResultMerger.safeWebUrl(url))
            val sources = row.getValue("sources").jsonArray.map { SearchSource.valueOf(it.jsonPrimitive.content) }.toSet()
            require(sources.isNotEmpty())
            val assessment = row["assessment"]?.jsonObject?.let {
                RelevanceAssessment(it.getValue("accepted").jsonPrimitive.boolean, it.getValue("score").jsonPrimitive.double,
                    it.getValue("evidence").jsonPrimitive.content, it.getValue("semantic").jsonPrimitive.boolean)
            }
            Favorite(
                SearchItem(
                    id = string("id"), source = SearchSource.valueOf(string("source")), title = string("title"), url = url,
                    snippet = string("snippet"), author = string("author"),
                    publishedAt = row["publishedAt"]?.jsonPrimitive?.content?.let(Instant::parse),
                    rank = row.getValue("rank").jsonPrimitive.int, context = string("context"),
                    license = row["license"]?.jsonPrimitive?.content, assessment = assessment,
                    contextMessage = row["contextMessage"]?.jsonObject?.let { message ->
                        val key = MessageKey.valueOf(message.getValue("key").jsonPrimitive.content)
                        val arguments = message.getValue("arguments").jsonArray.map { it.jsonPrimitive.content }
                        val expected = when (key) {
                            MessageKey.PUBLIC_CONTEXT -> 2
                            MessageKey.SEARX_CONTEXT, MessageKey.HN_CONTEXT -> 1
                            else -> error("Unsupported source metadata message")
                        }
                        require(arguments.size == expected)
                        SearchMessage(key, arguments)
                    } ?: legacyContext(string("context")),
                ), sources, string("keywords"),
            )
        }.also { favorites -> require(favorites.distinctBy { it.id }.size == favorites.size) }
    }

    /** Recognize only app-generated metadata from favorites saved before localization. */
    private fun legacyContext(context: String): SearchMessage? {
        Regex("^(Brave|DuckDuckGo) · öffentlicher Suchindex · ([a-zA-Z0-9.:-]+)$").matchEntire(context)?.let {
            return SearchMessage(MessageKey.PUBLIC_CONTEXT, it.groupValues[1], it.groupValues[2])
        }
        Regex("^Suchindex · ([a-zA-Z0-9.:-]+)$").matchEntire(context)?.let {
            return SearchMessage(MessageKey.SEARX_CONTEXT, it.groupValues[1])
        }
        Regex("^Hacker News · ([0-9]+) Punkte$").matchEntire(context)?.let {
            return SearchMessage(MessageKey.HN_CONTEXT, it.groupValues[1])
        }
        return null
    }
}
