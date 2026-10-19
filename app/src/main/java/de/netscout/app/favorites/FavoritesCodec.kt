package de.netscout.app.favorites

import de.netscout.core.RelevanceAssessment
import de.netscout.core.ResultMerger
import de.netscout.core.SearchItem
import de.netscout.core.SearchSource
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
                ), sources, string("keywords"),
            )
        }.also { favorites -> require(favorites.distinctBy { it.id }.size == favorites.size) }
    }
}
