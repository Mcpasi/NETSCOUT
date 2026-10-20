package de.netscout.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class AppLanguageTest {
    @Test fun `German and English regional variants select the supported language`() {
        assertEquals(AppLanguage.GERMAN, AppLanguage.forDevice(listOf(Locale.forLanguageTag("de-CH"))))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.forDevice(listOf(Locale.forLanguageTag("en-GB"))))
    }

    @Test fun `device preference order wins and unsupported languages fall back to English`() {
        assertEquals(AppLanguage.GERMAN, AppLanguage.forDevice(listOf(Locale.FRENCH, Locale.GERMAN, Locale.ENGLISH)))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.forDevice(listOf(Locale.ENGLISH, Locale.GERMAN)))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.forDevice(listOf(Locale.JAPANESE)))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.forDevice(emptyList()))
    }

    @Test fun `missing and obsolete preferences follow the device`() {
        assertNull(AppLanguage.fromTag(null))
        assertNull(AppLanguage.fromTag("fr"))
        assertEquals(AppLanguage.GERMAN, AppLanguage.fromTag("de"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en"))
    }
}
