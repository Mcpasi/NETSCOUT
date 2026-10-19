package de.netscout.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlParagraphRegressionTest {
    @Test fun `HTML source wrapping keeps a single paragraph together`() {
        val compact = "<p>A native <b>Codex</b> mobile client for Android.</p>"
        val wrapped = "<p>\n  A native <b>Codex</b>\n  mobile client for\n  Android.\n</p>"
        assertEquals(fullText(compact), fullText(wrapped))
    }

    @Test fun `Markdown soft wrapping keeps a single paragraph together`() {
        assertEquals("A native Codex mobile client for Android.",
            markdownText("A native Codex\nmobile client for\nAndroid."))
    }

    @Test fun `explicit paragraph list and line boundaries remain separate`() {
        listOf(
            fullText("<p>Codex</p><p>mobile</p><p>Android</p>"),
            fullText("<ul><li>Codex</li><li>mobile</li><li>Android</li></ul>"),
            fullText("Codex<br>mobile<br>Android"),
            markdownText("Codex  \nmobile  \nAndroid"),
        ).forEach { text -> assertEquals(listOf("Codex", "mobile", "Android"), text.lines().filter(String::isNotBlank)) }
    }

    @Test fun `unmarked plain text retains its line boundaries`() {
        assertEquals("Codex\nmobile\nAndroid", fullText("Codex\nmobile\nAndroid"))
    }

    @Test fun `preformatted code retains its line boundaries`() {
        assertEquals("val codex = client\n.mobile()\n.android()",
            fullText("<pre><code>val codex = client\n    .mobile()\n    .android()</code></pre>"))
    }
}
