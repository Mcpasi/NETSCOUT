package de.netscout.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class ApiSettings(
    val searxngUrl: String = "",
    val youtubeApiKey: String = "",
    val redditAccessToken: String = "",
    val githubToken: String = "",
) {
    fun validationError(): String? {
        if (searxngUrl.isBlank()) return null
        val url = searxngUrl.toHttpUrlOrNull()
        return if (url == null || !url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty() || url.query != null || url.fragment != null) {
            "Bitte eine HTTPS-Basis-URL ohne Zugangsdaten, Query oder Fragment eingeben."
        } else null
    }

    // Keep settings out of accidental logs and crash messages.
    override fun toString() = "ApiSettings(redacted)"
}
