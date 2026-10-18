package de.netscout.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.netscout.data.ApiSettings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsStoreTest {
    @Test fun credentialsSurviveEncryptedRoundTripWithoutPlaintextPreferences() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = EncryptedSettingsStore(context)
        val settings = ApiSettings("https://example.org/", "test-youtube-secret", "test-reddit-secret", "test-github-secret")
        try {
            store.save(settings)
            assertEquals(settings, EncryptedSettingsStore(context).load().settings)
            val values = context.getSharedPreferences("source-access", 0).all.values.joinToString()
            assertFalse(values.contains("test-youtube-secret"))
            assertFalse(values.contains("test-reddit-secret"))
        } finally { store.save(ApiSettings()) }
    }
}
