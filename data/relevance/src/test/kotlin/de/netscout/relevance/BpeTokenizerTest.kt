package de.netscout.relevance

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Path

class BpeTokenizerTest {
    private fun tokenizer() = BpeTokenizer(
        Path.of(System.getProperty("netscout.modelDir"), "tokenizer.bin").toFile().inputStream(),
    )

    @Test fun `token IDs match Hugging Face tokenizers 0_22_1 on the pinned Horizon Small tokenizer`() {
        val tokenizer = tokenizer()
        // Generated independently with Tokenizer.from_file on the locked original JSON,
        // add_special_tokens=false, no padding/truncation. Keep literal whitespace intact.
        val cases = mapOf(
            "Codex mobile Android" to intArrayOf(127937, 7635, 9989),
            "Codex für unterwegs: Android-App zur Fernsteuerung." to intArrayOf(127937, 2943, 108806, 235292, 9989, 235290, 2101, 6573, 8966, 95108, 93488, 235265),
            "支持Android的Codex手机客户端。" to intArrayOf(155233, 18933, 235370, 3010, 235297, 26102, 75418, 235362),
            "Cafe\u0301 — ① 𝕏" to intArrayOf(34882, 236703, 2062, 90853, 235248, 255966),
            "👨‍💻 🦄\u200btest" to intArrayOf(235248, 241568, 235879, 242482, 235248, 243743, 235421, 2195),
            "one\ntwo\tthree" to intArrayOf(974, 108, 1378, 226, 16183),
            "hello   world" to intArrayOf(25612, 235248, 235248, 2134),
            "hello<bos>world" to intArrayOf(25612, 2, 2134),
            "a  <mask> b" to intArrayOf(476, 4, 518),
            "x\uD83E\uDEE9\u0378\uD840\uDC00" to intArrayOf(1141, 457, 376, 388, 386, 251643, 457, 377, 345, 345),
            "مرحبا بالعالم" to intArrayOf(89187, 19833, 192740, 12842),
            "नमस्ते दुनिया" to intArrayOf(7578, 235579, 45884, 235483, 211586),
            "  hello  " to intArrayOf(235248, 25612, 235248, 235248),
            "x\n\n\ny\t\tz" to intArrayOf(1141, 110, 597, 255969, 868),
            "abc\u00a0def\r\nx" to intArrayOf(20079, 236338, 1293, 235316, 108, 1141),
            "<start_of_turn>user\n<end_of_turn>" to intArrayOf(106, 2425, 108, 107),
            "" to intArrayOf(),
        )
        cases.forEach { (text, expected) -> assertArrayEquals(text, expected, tokenizer.encode(text)) }
    }

    @Test fun `pair template and padding IDs belong to Horizon rather than XLM-R`() {
        val tokenizer = tokenizer()
        assertArrayEquals(longArrayOf(2, 127937, 1, 9989, 1), tokenizer.pair("Codex", "Android"))
        assertArrayEquals(longArrayOf(2, 1, 1), tokenizer.pair("", ""))
        assertEquals(0L, BpeTokenizer.PAD_ID)
    }

    @Test fun `pair truncation reserves the separators and caps query tokens at 96`() {
        val tokenizer = tokenizer()
        val pair = tokenizer.pair("Codex ".repeat(200), "Android ".repeat(500))
        assertEquals(384, pair.size)
        assertEquals(2L, pair.first())
        assertEquals(1L, pair[97])
        assertTrue(pair.sliceArray(1..96).all { it == 127937L })
        assertEquals(1L, pair.last())
        assertArrayEquals(longArrayOf(2, 1, 1), tokenizer.pair("long query", "long passage", maxTokens = 3))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `previous tokenizer format fails explicitly`() {
        BpeTokenizer("NSTK1".byteInputStream())
    }
}
