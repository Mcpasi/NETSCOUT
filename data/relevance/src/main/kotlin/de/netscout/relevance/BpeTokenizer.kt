package de.netscout.relevance

import java.io.DataInputStream
import java.io.InputStream

/** Horizon Small's mmBERT BPE: literal-space metaspace, byte fallback and added tokens. */
class BpeTokenizer(input: InputStream) {
    private data class AddedToken(val id: Int, val stripLeft: Boolean)
    private class TokenNode {
        val children = HashMap<Char, TokenNode>()
        var token: AddedToken? = null
    }
    private val alphabet = HashMap<Int, Int>(32_768)
    private val bytes: IntArray
    private val mergePairs: LongArray
    private val mergeRanks: IntArray
    private val mergeIds: IntArray
    private val addedTokens = TokenNode()
    private val wordCache = object : LinkedHashMap<String, IntArray>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, IntArray>?) = size > 2048
    }

    init {
        DataInputStream(input.buffered()).use { stream ->
            require(String(ByteArray(5).also(stream::readFully), Charsets.US_ASCII) == "NSBP1")
            val vocabularySize = stream.readInt()
            require(vocabularySize == 256_000)
            fun tokenId(): Int = stream.readInt().also { require(it in 0 until vocabularySize) }
            val alphabetSize = stream.readInt()
            require(alphabetSize in 1..vocabularySize)
            repeat(alphabetSize) {
                val codepoint = stream.readInt()
                require(Character.isValidCodePoint(codepoint))
                alphabet[codepoint] = tokenId()
            }
            bytes = IntArray(256) { tokenId() }
            val count = stream.readInt()
            require(count in 1..1_000_000)
            mergePairs = LongArray(count)
            mergeRanks = IntArray(count)
            mergeIds = IntArray(count)
            repeat(count) { index ->
                val pair = stream.readLong()
                require(pair ushr 32 < vocabularySize.toLong() && (pair and 0xffffffffL) < vocabularySize.toLong())
                require(index == 0 || pair > mergePairs[index - 1])
                mergePairs[index] = pair
                mergeRanks[index] = stream.readInt().also { require(it in 0 until count) }
                mergeIds[index] = tokenId()
            }
            val addedCount = stream.readInt()
            require(addedCount in 1..1024)
            repeat(addedCount) {
                val length = stream.readInt()
                require(length in 1..4096)
                val text = String(ByteArray(length).also(stream::readFully), Charsets.UTF_8)
                val token = AddedToken(tokenId(), stream.readBoolean())
                var node = addedTokens
                text.forEach { character -> node = node.children.getOrPut(character) { TokenNode() } }
                node.token = token
            }
            require(stream.read() == -1)
        }
    }

    fun encode(text: String): IntArray {
        val result = mutableListOf<Int>()
        var start = 0
        var index = 0
        while (index < text.length) {
            var node = addedTokens
            var end = index
            var match: AddedToken? = null
            var matchEnd = index
            // Hugging Face added tokens use the longest match at the earliest position.
            while (end < text.length) {
                node = node.children[text[end]] ?: break
                end++
                node.token?.let { match = it; matchEnd = end }
            }
            val token = match
            if (token == null) {
                index++
            } else {
                var ordinaryEnd = index
                if (token.stripLeft) {
                    while (ordinaryEnd > start && whitespace(text[ordinaryEnd - 1])) ordinaryEnd--
                }
                encodeOrdinary(text.substring(start, ordinaryEnd), result)
                result += token.id
                index = matchEnd
                start = index
            }
        }
        encodeOrdinary(text.substring(start), result)
        return result.toIntArray()
    }

    fun pair(query: String, passage: String, maxTokens: Int = 384): LongArray {
        require(maxTokens >= 3)
        val first = encode(query).take(minOf(96, maxTokens - 3))
        val second = encode(passage).take(maxTokens - first.size - 3)
        // Horizon's template is <bos> query <eos> passage <eos>; no double separator.
        return (listOf(2) + first + listOf(1) + second + listOf(1)).map(Int::toLong).toLongArray()
    }

    private fun encodeOrdinary(text: String, result: MutableList<Int>) {
        if (text.isEmpty()) return
        val normalized = text.replace(' ', '▁').let { if (it.startsWith('▁')) it else "▁$it" }
        var start = 0
        for (index in 1 until normalized.length) {
            if (normalized[index] == '▁') {
                result.addAll(word(normalized.substring(start, index)).asList())
                start = index
            }
        }
        result.addAll(word(normalized.substring(start)).asList())
    }

    private fun word(text: String): IntArray = wordCache.getOrPut(text) {
        val ids = mutableListOf<Int>()
        var index = 0
        while (index < text.length) {
            val codepoint = text.codePointAt(index)
            val next = index + Character.charCount(codepoint)
            val id = alphabet[codepoint]
            if (id != null) ids += id
            else text.substring(index, next).toByteArray(Charsets.UTF_8).forEach { ids += bytes[it.toInt() and 255] }
            index = next
        }
        while (ids.size > 1) {
            var bestRank = Int.MAX_VALUE
            var bestPosition = -1
            var bestId = -1
            for (position in 0 until ids.lastIndex) {
                val pair = (ids[position].toLong() shl 32) or ids[position + 1].toLong()
                val merge = mergePairs.binarySearch(pair)
                if (merge >= 0 && mergeRanks[merge] < bestRank) {
                    bestRank = mergeRanks[merge]
                    bestPosition = position
                    bestId = mergeIds[merge]
                }
            }
            if (bestPosition < 0) break
            ids[bestPosition] = bestId
            ids.removeAt(bestPosition + 1)
        }
        ids.toIntArray()
    }

    private fun whitespace(character: Char) = character.isWhitespace() || character == '\u0085'

    companion object {
        const val PAD_ID = 0L
    }
}
