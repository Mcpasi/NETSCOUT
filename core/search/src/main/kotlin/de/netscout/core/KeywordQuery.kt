package de.netscout.core

import java.util.Locale

/** Dynamic, literal candidate queries. Relevance of retrieved text is assessed separately. */
class KeywordQuery private constructor(val terms: List<String>, val balancedQuotes: Boolean) {
    private val patterns = terms.map(::pattern)
    val remoteQuery: String get() = terms.joinToString(" ") { "\"$it\"" }

    data class Match(val titleCoverage: Double, val textCoverage: Double)

    fun match(title: String, text: String): Match? {
        if (terms.isEmpty() || !balancedQuotes) return null
        var inTitle = 0; var inText = 0
        for (regex in patterns) {
            val titleMatch = regex.containsMatchIn(title)
            val textMatch = regex.containsMatchIn(text)
            if (!titleMatch && !textMatch) return null
            if (titleMatch) inTitle++
            if (textMatch) inText++
        }
        return Match(inTitle.toDouble() / terms.size, inText.toDouble() / terms.size)
    }

    fun ranges(text: String): List<IntRange> = patterns.flatMap { regex -> regex.findAll(text).take(100).map { it.range }.toList() }.sortedBy { it.first }

    /** A single original excerpt; never stitch distant keyword mentions into apparent context. */
    fun snippet(text: String, maxLength: Int = 700): String {
        if (text.length <= maxLength) return text
        val positions = patterns.mapNotNull { it.find(text)?.range }.sortedBy { it.first }
        if (positions.isEmpty()) return text.take(maxLength)
        val start = (positions.first().first - 70).coerceAtLeast(0)
        val end = (start + maxLength - 4).coerceAtMost(text.length)
        return (if (start > 0) "… " else "") + text.substring(start, end) + (if (end < text.length) " …" else "")
    }

    companion object {
        fun parse(input: String): KeywordQuery {
            val normalized = input.replace('„', '"').replace('“', '"').replace('”', '"')
            val terms = Regex("\"([^\"]+)\"|([^\\s,;\"]+)").findAll(normalized).map {
                (it.groups[1]?.value ?: it.groups[2]!!.value).trim().replace(Regex("\\s+"), " ")
            }.filter { term -> term.any { it.isLetterOrDigit() } }
                .distinctBy { it.lowercase(Locale.ROOT) }.toList()
            return KeywordQuery(terms, normalized.count { it == '"' } % 2 == 0)
        }

        private fun pattern(term: String): Regex {
            val value = term.split(' ').joinToString("\\s+") { Regex.escape(it) }
            // CJK text commonly has no spaces around words or Latin product names.
            val word = "[\\p{L}\\p{N}&&[^\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{IsHangul}]]"
            val scripts = setOf(Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA, Character.UnicodeScript.HANGUL)
            val left = if (Character.UnicodeScript.of(term.codePointAt(0)) in scripts) "" else "(?<!$word)"
            val right = if (Character.UnicodeScript.of(term.codePointBefore(term.length)) in scripts) "" else "(?!$word)"
            return Regex("$left$value$right", RegexOption.IGNORE_CASE)
        }
    }
}
