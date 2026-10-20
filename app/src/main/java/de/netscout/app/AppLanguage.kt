package de.netscout.app

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.util.Locale

enum class AppLanguage(val tag: String) {
    GERMAN("de"), ENGLISH("en");

    companion object {
        fun fromTag(tag: String?): AppLanguage? = entries.firstOrNull { it.tag == tag }

        fun forDevice(locales: List<Locale>): AppLanguage =
            locales.firstNotNullOfOrNull { fromTag(it.language) } ?: ENGLISH
    }
}

interface LanguagePreferences {
    fun load(): AppLanguage?
    fun save(language: AppLanguage?)
}

/** An absent preference follows the device. Credentials use a separate encrypted store. */
internal class DeviceLanguagePreferences(context: Context) : LanguagePreferences {
    private val preferences = context.getSharedPreferences("app-language", Context.MODE_PRIVATE)
    override fun load() = AppLanguage.fromTag(preferences.getString("language", null))
    override fun save(language: AppLanguage?) {
        preferences.edit().apply {
            if (language == null) remove("language") else putString("language", language.tag)
        }.apply()
    }
}

/** Changes only this composition's resources, keeping searches and entered values alive. */
@Composable
internal fun AppLanguageHost(
    preferences: LanguagePreferences? = null,
    content: @Composable (AppLanguage?, (AppLanguage?) -> Unit) -> Unit,
) {
    val deviceContext = LocalContext.current
    val deviceConfiguration = LocalConfiguration.current
    val store = remember(preferences, deviceContext.applicationContext) {
        preferences ?: DeviceLanguagePreferences(deviceContext.applicationContext)
    }
    var selection by remember(store) { mutableStateOf(store.load()) }
    val deviceLocales = deviceConfiguration.locales.let { list -> (0 until list.size()).map(list::get) }
    val language = selection ?: AppLanguage.forDevice(deviceLocales)
    val configuration = remember(deviceConfiguration, language) {
        Configuration(deviceConfiguration).apply { setLocale(Locale.forLanguageTag(language.tag)) }
    }
    val context = remember(deviceContext, configuration) {
        ContextThemeWrapper(deviceContext, 0).apply { applyOverrideConfiguration(configuration) }
    }
    CompositionLocalProvider(
        LocalContext provides context,
        LocalConfiguration provides configuration,
        LocalLayoutDirection provides LayoutDirection.Ltr,
    ) {
        content(selection) { next ->
            store.save(next)
            selection = next
        }
    }
}

@Composable
internal fun LanguageBar(selection: AppLanguage?, onSelect: (AppLanguage?) -> Unit) {
    val currentLanguage = LocalConfiguration.current.locales[0].language
    val systemDescription = stringResource(R.string.language_system_description)
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.language_label), style = MaterialTheme.typography.labelMedium)
            FilterChip(
                selected = selection == null, onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.language_system)) },
                modifier = Modifier.heightIn(min = 48.dp).semantics {
                    contentDescription = systemDescription
                },
            )
            AppLanguage.entries.forEach { language ->
                val description = stringResource(if (language == AppLanguage.GERMAN) R.string.language_german else R.string.language_english)
                FilterChip(
                    selected = currentLanguage == language.tag,
                    onClick = { onSelect(language) },
                    label = { Text(language.tag.uppercase(Locale.ROOT)) },
                    leadingIcon = {
                        Icon(painterResource(if (language == AppLanguage.GERMAN) R.drawable.flag_de else R.drawable.flag_en),
                            contentDescription = null, modifier = Modifier.size(width = 24.dp, height = 16.dp),
                            tint = androidx.compose.ui.graphics.Color.Unspecified)
                    },
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = description },
                )
            }
        }
    }
}
