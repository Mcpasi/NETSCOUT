package de.netscout.core

import org.junit.Assert.*
import org.junit.Test

class PassageSelectorTest {
    private fun item(title: String, text: String) = SearchItem("1", SearchSource.GITHUB, title, "https://example.org/1", searchText = text)

    @Test fun `related adjacent sentences retain every keyword in the original paragraph`() {
        val text = "Control Codex from a mobile client. It runs on Android."
        val passages = PassageSelector.select(KeywordQuery.parse("Codex,mobile,Android"), item("New project", text))
        assertTrue(passages.any { it.text == text })
    }

    @Test fun `a complete 700 character window is found even when matches are far apart`() {
        val text = "Introduction ".repeat(25) + "Codex " + "detail ".repeat(80) + "mobile Android support is available. " + "trailing ".repeat(40)
        val query = KeywordQuery.parse("Codex,mobile,Android")
        val passages = PassageSelector.select(query, item("New project", text))
        assertTrue(passages.isNotEmpty())
        assertTrue(passages.all { it.text.length <= 700 && text.contains(it.text) && query.match(it.title, it.text) != null })
    }

    @Test fun `repeated earlier keywords cannot hide a later complete passage`() {
        val text = listOf("Codex", "mobile", "Android").joinToString("") { "$it ".repeat(120) + "other ".repeat(150) } + "Codex mobile Android client"
        val query = KeywordQuery.parse("Codex,mobile,Android")
        assertTrue(PassageSelector.select(query, item("New project", text)).isNotEmpty())
    }

    @Test fun `a matching title must not hide more useful body evidence`() {
        val text = "Unrelated introduction.\nControl Codex from a mobile client on Android."
        val passages = PassageSelector.select(KeywordQuery.parse("Codex,mobile,Android"), item("Codex mobile Android", text))
        assertTrue(passages.any { it.text.contains("Control Codex") })
    }

    @Test fun `neighbouring sentences never cross paragraphs or bridge distant mentions`() {
        val query = KeywordQuery.parse("Codex,mobile,Android")
        listOf(
            "Control Codex from a mobile client.\nIt runs on Android.",
            "Codex " + "unrelated ".repeat(100) + "mobile Android",
        ).forEach { text -> assertTrue(PassageSelector.select(query, item("New project", text)).isEmpty()) }
    }

    @Test fun `adjacent CJK sentences retain their original punctuation and every keyword`() {
        val text = "使用Codex的mobile客户端。支持Android。"
        val passage = PassageSelector.select(KeywordQuery.parse("Codex,mobile,Android"), item("新项目", text)).single()
        assertEquals(text, passage.text)
    }

    @Test fun `three words in unrelated changelog sections do not establish a common topic`() {
        val query = KeywordQuery.parse("Codex,mobile,Android")
        val post = item("Weekly release notes", "Codex web integration updated.\nMobile Safari layout fixed.\nAndroid notification permission changed.")
        assertNotNull(query.match(post.title, post.searchText)) // The old whole-post gate accepted it.
        assertTrue(PassageSelector.select(query, post).isEmpty())
        assertTrue(ResultMerger.merge(SearchRequest("Codex,mobile,Android", setOf(SearchSource.GITHUB)), listOf(post)).isEmpty())
    }
    @Test fun `related original passage can be found late in the post without stitching fragments`() {
        val text = "Unrelated section.\n".repeat(100) + "Control Codex from a mobile client on Android.\nOther unrelated section."
        val passage = PassageSelector.select(KeywordQuery.parse("Codex,mobile,Android"), item("New project", text)).single()
        assertEquals("Control Codex from a mobile client on Android.", passage.text)
        assertTrue(text.contains(passage.text))
    }
    @Test fun `the same contextual gate works for arbitrary keywords and languages`() {
        listOf(
            Triple("rust,async,database", "Rust async database client", "A new project"),
            Triple("python,linux,security", "New project", "Python security scanner for Linux servers."),
            Triple("Kotlin,Akku,Android", "Kotlin auf Android", "Akku-Verbrauch messen."),
            Triple("Codex,Android", "新项目", "支持Android的Codex客户端。"),
        ).forEach { (keywords, title, text) ->
            assertFalse(keywords, PassageSelector.select(KeywordQuery.parse(keywords), item(title, text)).isEmpty())
        }
    }
    @Test fun `matching title does not allow a phrase to cross unrelated fields`() {
        assertTrue(PassageSelector.select(KeywordQuery.parse("\"local llm\",android"), item("Local", "LLM for Android.")).isEmpty())
    }
}
