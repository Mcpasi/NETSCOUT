package de.netscout.core

import java.util.Locale
import java.util.PriorityQueue

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

    /** Find complete, bounded evidence without limiting how far into the original text we search. */
    internal fun windows(title: String, text: String, maxLength: Int = 700, limit: Int = 4): List<String> {
        if (limit <= 0) return emptyList()
        val missing = patterns.filterNot { it.containsMatchIn(title) }
        val required = if (missing.isEmpty()) patterns.filter { it.containsMatchIn(text) } else missing
        if (required.isEmpty()) return if (match(title, "") != null) listOf(text.take(maxLength)) else emptyList()
        val iterators = required.map { it.findAll(text).iterator() }
        if (iterators.any { !it.hasNext() }) return emptyList()
        val current = iterators.map { it.next().range }.toMutableList()
        val queue = PriorityQueue<Int>(compareBy { current[it].first }).apply { addAll(current.indices) }
        var last = current.maxOf { it.last }
        // Keep only a few compact excerpts, while still examining every occurrence.
        val selected = mutableMapOf<IntRange, Int>()
        while (true) {
            val term = queue.remove()
            val first = current[term].first
            val span = last - first + 1
            if (span <= maxLength) {
                val padding = minOf(160, (maxLength - span) / 2)
                val start = (first - padding).coerceAtLeast(0)
                val range = start until minOf(text.length, start + maxLength)
                selected[range] = minOf(selected[range] ?: Int.MAX_VALUE, span)
                if (selected.size > limit) {
                    selected.remove(selected.keys.maxWith(compareBy<IntRange> { selected.getValue(it) }.thenBy { it.first }))
                }
            }
            if (!iterators[term].hasNext()) break
            current[term] = iterators[term].next().range
            last = maxOf(last, current[term].last)
            queue.add(term)
        }
        return selected.keys.sortedBy { it.first }.map { text.substring(it).trim() }.filter { match(title, it) != null }
    }

    internal fun contextBlocks(title: String, text: String): List<String> {
        val missing = patterns.filterNot { it.containsMatchIn(title) }
        return text.splitToSequence(Regex("[\\r\\n\\u2028\\u2029]+|(?<=[.!?])[\\s\\p{Z}\\u0085]+|(?<=[。！？])"))
            .map(String::trim).filter { block -> missing.any { it.containsMatchIn(block) } }.distinct().toList()
    }

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
        private const val WHITESPACE = "[\\s\\p{Z}\\u0085]+"
        private val whitespace = Regex(WHITESPACE)
        internal fun normalizeWhitespace(input: String): String = input.replace(whitespace, " ").trim()

        fun parse(input: String): KeywordQuery {
            val normalized = normalizeWhitespace(input).replace('„', '"').replace('“', '"').replace('”', '"')
            val terms = Regex("\"([^\"]+)\"|([^\\s,;\"]+)").findAll(normalized).map {
                (it.groups[1]?.value ?: it.groups[2]!!.value).trim()
            }.filter { term -> term.any { it.isLetterOrDigit() } }
                .distinctBy { it.lowercase(Locale.ROOT) }.toList()
            return KeywordQuery(terms, normalized.count { it == '"' } % 2 == 0)
        }

        private fun pattern(term: String): Regex {
            val value = term.split(' ').joinToString(WHITESPACE) { Regex.escape(it) }
            // CJK text commonly has no spaces around words or Latin product names.
            val word = "[\\p{L}\\p{N}&&[^\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{IsHangul}]]"
            val scripts = setOf(Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA, Character.UnicodeScript.HANGUL)
            val left = if (Character.UnicodeScript.of(term.codePointAt(0)) in scripts) "" else "(?<!$word)"
            val right = if (Character.UnicodeScript.of(term.codePointBefore(term.length)) in scripts) "" else "(?!$word)"
            return Regex("$left$value$right", RegexOption.IGNORE_CASE)
        }
    }
}
