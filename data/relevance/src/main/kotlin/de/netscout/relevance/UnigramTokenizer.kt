package de.netscout.relevance

import java.io.DataInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.BreakIterator
import java.util.Locale

/** XLM-R Unigram Viterbi segmentation with the model's own SentencePiece normalization table. */
class UnigramTokenizer(input: InputStream) {
    private data class Piece(val id: Int, val score: Float)
    private val vocabulary = HashMap<String, Piece>(335_000)
    private val normalizer: SentencePieceNormalizer
    private var maxLength = 0
    private var unknownScore = 0f
    private val wordCache = object : LinkedHashMap<String, IntArray>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, IntArray>?) = size > 2048
    }

    init {
        DataInputStream(input.buffered()).use { stream ->
            require(String(ByteArray(5).also(stream::readFully), Charsets.US_ASCII) == "NSTK1")
            val count = stream.readInt()
            require(count == 250_002)
            repeat(count) { id ->
                val length = stream.readInt()
                require(length in 1..256)
                val token = String(ByteArray(length).also(stream::readFully), Charsets.UTF_8)
                val score = stream.readFloat()
                vocabulary[token] = Piece(id, score)
                maxLength = maxOf(maxLength, token.length)
                unknownScore = minOf(unknownScore, score)
            }
            unknownScore -= 10f
            val length = stream.readInt()
            require(length in 1..1_000_000)
            normalizer = SentencePieceNormalizer(ByteArray(length).also(stream::readFully))
        }
    }

    fun encode(text: String): IntArray = normalizer.normalize(text).splitToSequence(Regex("\\s+"))
        .filter(String::isNotBlank).flatMap { word ->
            wordCache.getOrPut(word) { segment("▁$word") }.asSequence()
        }.toList().toIntArray()

    fun pair(query: String, passage: String, maxTokens: Int = 384): LongArray {
        val first = encode(query).take(96)
        val second = encode(passage).take((maxTokens - first.size - 4).coerceAtLeast(0))
        return (listOf(0) + first + listOf(2, 2) + second + listOf(2)).map(Int::toLong).toLongArray()
    }

    private fun segment(word: String): IntArray {
        val scores = FloatArray(word.length + 1) { Float.NEGATIVE_INFINITY }.apply { this[0] = 0f }
        val previous = IntArray(word.length + 1)
        val ids = IntArray(word.length + 1)
        var start = 0
        while (start < word.length) {
            val next = start + Character.charCount(word.codePointAt(start))
            var singleKnown = false
            var end = next
            while (end <= word.length && end - start <= maxLength) {
                val piece = vocabulary[word.substring(start, end)]
                if (piece != null) {
                    if (end == next) singleKnown = true
                    val score = scores[start] + piece.score
                    if (score > scores[end]) {
                        scores[end] = score; previous[end] = start; ids[end] = piece.id
                    }
                }
                if (end == word.length) break
                end += Character.charCount(word.codePointAt(end))
            }
            if (!singleKnown && scores[start] + unknownScore > scores[next]) {
                scores[next] = scores[start] + unknownScore; previous[next] = start; ids[next] = 3
            }
            start = next
        }
        val result = mutableListOf<Int>()
        var end = word.length
        while (end > 0) {
            val id = ids[end]
            if (id != 3 || result.lastOrNull() != 3) result += id
            end = previous[end]
        }
        return result.asReversed().toIntArray()
    }
}

/** Darts double-array lookup; matches HF's grapheme/first-prefix normalization behavior. */
private class SentencePieceNormalizer(bytes: ByteArray) {
    private val units: IntArray
    private val replacements: ByteArray
    init {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val size = buffer.int
        require(size % 4 == 0 && size <= bytes.size - 4)
        units = IntArray(size / 4) { buffer.int }
        replacements = ByteArray(buffer.remaining()).also(buffer::get)
    }
    private fun offset(unit: Int) = (unit ushr 10) shl ((unit and 512) ushr 6)
    private fun transform(text: String): String? {
        var node = offset(units[0])
        for (byte in text.toByteArray(Charsets.UTF_8)) {
            val label = byte.toInt() and 255
            if (label == 0) break
            node = node xor label
            if (node !in units.indices) return null
            val unit = units[node]
            if (unit and (Int.MIN_VALUE or 255) != label) return null
            node = node xor offset(unit)
            if (unit and 256 != 0) {
                val start = units[node] and Int.MAX_VALUE
                var end = start
                while (end < replacements.size && replacements[end] != 0.toByte()) end++
                return String(replacements, start, end - start, Charsets.UTF_8)
            }
        }
        return null
    }
    fun normalize(text: String): String = buildString {
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(text) }
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            val grapheme = text.substring(start, end)
            val replacement = if (grapheme.toByteArray(Charsets.UTF_8).size < 6) transform(grapheme) else null
            if (replacement != null) append(replacement)
            else {
                var index = 0
                while (index < grapheme.length) {
                    val next = index + Character.charCount(grapheme.codePointAt(index))
                    val character = grapheme.substring(index, next)
                    append(transform(character) ?: character)
                    index = next
                }
            }
            start = end; end = iterator.next()
        }
    }
}
