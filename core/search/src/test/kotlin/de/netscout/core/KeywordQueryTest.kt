package de.netscout.core

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class KeywordQueryTest {
    @Test fun `user regression comma separated keywords require all three terms`() {
        val query = KeywordQuery.parse("Codex,mobile,android")
        assertEquals(listOf("Codex", "mobile", "android"), query.terms)
        assertEquals("\"Codex\" \"mobile\" \"android\"", query.remoteQuery)
        assertNotNull(query.match("Codex mobile", "Native client for Android"))
        assertNull(query.match("Codex mobile is now on iOS", "iPhone app"))
        assertNull(query.match("Android mobile development", "Unrelated coding tips"))
    }
    @Test fun `commas spaces and semicolons have the same AND semantics`() {
        val expected = listOf("codex", "mobile", "android")
        listOf("codex mobile android", "codex,mobile,android", "codex; mobile ;android", "codex, codex, mobile, android").forEach {
            assertEquals(expected, KeywordQuery.parse(it).terms)
        }
    }
    @Test fun `whole terms cannot match unrelated substrings`() {
        val query = KeywordQuery.parse("AI")
        assertNull(query.match("Gmail and email", "A chair in the railway domain"))
        assertNotNull(query.match("AI on Android", "A mobile assistant"))
    }
    @Test fun `quoted phrases require adjacent words in the same field`() {
        val query = KeywordQuery.parse("\"local llm\",android")
        assertNotNull(query.match("Local LLM for Android", ""))
        assertNull(query.match("LLM on Android", "local models"))
        assertNull(query.match("Local", "LLM for Android"))
    }
    @Test fun `programming language punctuation is literal`() {
        assertNotNull(KeywordQuery.parse("C++,Android").match("C++ on Android", ""))
        assertNull(KeywordQuery.parse("C++,Android").match("C on Android", ""))
        assertNotNull(KeywordQuery.parse("C#").match("C# development", ""))
    }
    @Test fun `matches and query serialization do not depend on device language`() {
        val old = Locale.getDefault()
        try {
            for (locale in listOf(Locale.GERMAN, Locale.ENGLISH, Locale.forLanguageTag("tr"))) {
                Locale.setDefault(locale)
                val query = KeywordQuery.parse("CODEX,mobile,android")
                assertNotNull(query.match("Codex mobile for Android", "English article"))
                assertNotNull(query.match("Codex mobile für Android", "Deutscher Beitrag"))
            }
        } finally { Locale.setDefault(old) }
        assertNotNull(KeywordQuery.parse("東京,Android").match("東京 Android", ""))
        assertNotNull(KeywordQuery.parse("東京,Android").match("東京ではAndroid対応です", ""))
        assertNotNull(KeywordQuery.parse("Android").match("支持Android客户端", ""))
    }
    @Test fun `snippet retains evidence late in long texts and across distant sections`() {
        val body = "Unrelated intro. ".repeat(100) + "Codex controls a mobile client. " + "More content. ".repeat(100) + "It runs on Android."
        val query = KeywordQuery.parse("Codex,mobile,android")
        val snippet = query.snippet(body)
        assertTrue(snippet.length <= 700)
        assertNotNull(query.match("", snippet))
        assertTrue(query.ranges(snippet).isNotEmpty())
    }
}
