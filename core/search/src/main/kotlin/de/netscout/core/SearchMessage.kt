package de.netscout.core

import java.util.Locale

/** Stable message identifiers; Android renders these using its current string resources.
 * The German fallback keeps JVM tools and diagnostics usable without Android resources. */
enum class MessageKey(val fallback: String) {
    QUERY_TOO_SHORT("Bitte mindestens zwei Zeichen eingeben."),
    QUERY_TOO_LONG("Bitte höchstens 256 Zeichen eingeben."),
    QUERY_QUOTES("Bitte die Anführungszeichen der Suchphrase schließen."),
    QUERY_TERMS("Bitte Suchbegriffe mit Buchstaben oder Zahlen eingeben."),
    QUERY_SOURCES("Bitte mindestens eine Quelle auswählen."),
    QUERY_DATES("Das Von-Datum muss vor oder am Bis-Datum liegen."),
    ACCESS_URL("Bitte eine HTTPS-Basis-URL ohne Zugangsdaten, Query oder Fragment eingeben."),
    SOURCE_SETUP("Quelle noch nicht eingerichtet."),
    SOURCE_TIMEOUT("Zeitlimit erreicht. Bitte erneut versuchen."),
    SOURCE_UNAVAILABLE("Quelle zurzeit nicht erreichbar. Verbindung und Zugang prüfen."),
    SOURCE_INVALID_LIST("Quelle liefert keine gültige Trefferliste."),
    PUBLIC_TIMEOUT("Öffentlicher Suchdienst antwortet zu langsam."),
    PUBLIC_BLOCKED("Öffentlicher Suchdienst blockiert die Anfrage vorübergehend. Bitte später erneut suchen."),
    PUBLIC_CHALLENGE("Suchdienst verlangt eine Browserprüfung. Bitte später erneut suchen oder einen eigenen Suchdienst verwenden."),
    PUBLIC_UNREADABLE("Suchdienst liefert keine lesbare Trefferliste. Bitte später erneut versuchen."),
    INVALID_CURSOR("Ungültige Suchseite. Bitte die Suche erneut starten."),
    INVALID_RESPONSE("Quelle liefert keine gültige Suchantwort."),
    SOURCE_PAUSE_SEARCH("Quelle verlangt eine Pause. Bitte später erneut suchen."),
    SOURCE_PAUSE_RETRY("Quelle verlangt eine Pause. Bitte später erneut versuchen."),
    ACCESS_REJECTED("Zugang abgelehnt oder Kontingent verbraucht. Einstellungen prüfen."),
    HTTP_REJECTED("Suchanfrage von der Quelle abgelehnt (HTTP %1\$s)."),
    RESPONSE_TOO_LARGE("Antwort der Quelle ist zu groß."),
    RELEVANCE_FAILED("Inhaltliche Trefferprüfung fehlgeschlagen. Bitte die Suche erneut starten."),
    REDDIT_SETUP("Genehmigten Reddit-OAuth-Zugang oder SearXNG in Zugänge einrichten."),
    YOUTUBE_SETUP("YouTube-API-Key oder SearXNG in Zugänge einrichten."),
    SEARX_SETUP("SearXNG-HTTPS-URL in Zugänge einrichten; JSON muss dort aktiviert sein."),
    STACK_PAUSE("Stack Exchange verlangt eine Pause oder hat die Anfrage abgelehnt."),
    DIRECT_TIMEOUT("Direktzugriff antwortet zu langsam."),
    FALLBACK_PUBLIC("Erster Suchdienst nicht verfügbar; "),
    FALLBACK_CUSTOM("Eigener Suchdienst nicht verfügbar; "),
    FALLBACK_DIRECT("Direktzugriff fehlgeschlagen; "),
    PUBLIC_NOTE("Ohne Schlüssel über %1\$s. "),
    PUBLIC_WEB_NOTE("Bewertet werden Titel und Index-Textstellen. "),
    PUBLIC_SOURCE_NOTE("Öffentlich indexierte %1\$s-Seiten; kein vollständiger Direktzugriff. "),
    PUBLIC_DATE_NOTE("Datum nur bei ausdrücklich bekanntem Veröffentlichungsdatum. Suchdienste können Anfragen begrenzen."),
    REDDIT_NOTE("Reddit-Posts; Datumsfilter lokal. Ältere Treffer können trotz Blättern fehlen."),
    SEARX_NOTE("Indexsuche über SearXNG. Datum nur bei bekanntem Veröffentlichungsdatum; keine garantierte Vollständigkeit."),
    SEARX_UNAVAILABLE(" %1\$s Suchmaschinen nicht erreichbar."),
    GITHUB_NOTE("Öffentliche Repositories, Issues und Pull Requests; alle Sprachen. Drei API-Abfragen pro Seite. "),
    GITHUB_PARTIAL("Teilabfrage fehlgeschlagen. "),
    GITHUB_INCOMPLETE("GitHub meldet unvollständige Ergebnisse. Suchbegriffe eingrenzen."),
    GITHUB_NO_CODE("Kein Code-Suchindex."),
    FORUMS_NOTE("Stack Overflow · Super User · Server Fault. "),
    FORUMS_UNAVAILABLE("%1\$s Foren nicht erreichbar. "),
    FORUMS_PAUSE("API-Pause vor nächster Seite. "),
    FORUMS_QUOTA("API-Restkontingent: %1\$s."),
    HN_NOTE("HN-Beiträge über Algolia; Link öffnet die Diskussion."),
    PUBLIC_CONTEXT("%1\$s · öffentlicher Suchindex · %2\$s"),
    SEARX_CONTEXT("Suchindex · %1\$s"),
    HN_CONTEXT("Hacker News · %1\$s Punkte"),
    ACCESS_LOAD_FAILED("Gespeicherte Zugänge konnten nicht gelesen werden. Bitte neu eingeben."),
    ACCESS_SAVE_FAILED("Zugänge konnten nicht verschlüsselt gespeichert werden. Bitte erneut versuchen."),
    FAVORITES_LOAD_FAILED("Favoriten konnten nicht geladen werden. Bitte erneut versuchen."),
    FAVORITES_SAVE_FAILED("Favoriten konnten nicht gespeichert werden. Bitte erneut versuchen."),
    FAVORITES_LIMIT("Du kannst höchstens %1\$s Favoriten speichern. Entferne zuerst einen Favoriten."),
}

data class SearchMessage(val key: MessageKey, val arguments: List<String> = emptyList()) {
    constructor(key: MessageKey, vararg arguments: String) : this(key, arguments.toList())
    val text: String get() {
        val values = if (key == MessageKey.PUBLIC_SOURCE_NOTE) listOf(SearchSource.valueOf(arguments.single()).label) else arguments
        return String.format(Locale.GERMAN, key.fallback, *values.toTypedArray())
    }
}
