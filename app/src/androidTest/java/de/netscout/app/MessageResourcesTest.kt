package de.netscout.app

import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.netscout.core.MessageKey
import de.netscout.core.SearchMessage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class MessageResourcesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun localized(tag: String) = context.createConfigurationContext(
        Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) },
    )

    @Test fun everyStructuredMessageHasCompleteGermanAndEnglishResources() {
        val german = localized("de-DE")
        val english = localized("en-US")
        MessageKey.entries.forEach { key ->
            val count = Regex("%([0-9]+)\\\$s").findAll(key.fallback).map { it.groupValues[1].toInt() }.maxOrNull() ?: 0
            val arguments = if (key == MessageKey.PUBLIC_SOURCE_NOTE) listOf("REDDIT") else List(count) { "42" }
            val message = SearchMessage(key, arguments)
            assertEquals(key.name, message.text, german.messageText(message))
            val translated = english.messageText(message)
            assertFalse(key.name, translated.contains(Regex("%[0-9]+\\\$s")))
            if (key != MessageKey.FORUMS_NOTE) assertNotEquals(key.name, message.text, translated)
        }
    }

    @Test fun unknownDeviceLanguageUsesEnglishResourcesAndEnglishPlurals() {
        assertEquals("Web search", localized("fr-FR").getString(R.string.search_heading))
        val english = localized("en-US")
        assertEquals("1 result", english.resources.getQuantityString(R.plurals.result_count, 1, 1))
        assertEquals("2 results", english.resources.getQuantityString(R.plurals.result_count, 2, 2))
    }

    @Test fun languageChoiceIsPersistedIndependentlyOfAccessCredentials() {
        val preferences = DeviceLanguagePreferences(context)
        val previous = preferences.load()
        try {
            preferences.save(AppLanguage.ENGLISH)
            assertEquals(AppLanguage.ENGLISH, DeviceLanguagePreferences(context).load())
            preferences.save(AppLanguage.GERMAN)
            assertEquals(AppLanguage.GERMAN, DeviceLanguagePreferences(context).load())
            preferences.save(null)
            assertNull(DeviceLanguagePreferences(context).load())
        } finally {
            preferences.save(previous)
        }
    }
}
