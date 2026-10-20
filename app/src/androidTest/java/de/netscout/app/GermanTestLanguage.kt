package de.netscout.app

/** Existing German UI assertions are independent of the emulator's system language. */
internal object GermanTestLanguage : LanguagePreferences {
    override fun load() = AppLanguage.GERMAN
    override fun save(language: AppLanguage?) = Unit
}
