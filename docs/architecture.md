# Architektur des Prototyps

## Verantwortlichkeiten

`app` hält die Compose-Oberfläche, einen screenweiten `SearchViewModel` und den `SettingsStore`. Nur hier existieren Android-Abhängigkeiten. Die `StateFlow`-Oberfläche zeigt Teilergebnisse und hält Filteränderungen getrennt vom aktiven Suchauftrag. Eine neue Suche oder geänderte Zugangsdaten bricht alte Anfragen ab; alte Treffer können keine neue Suche überschreiben.

`core:search` definiert `SearchRequest`, `SearchItem`, `SearchCursor`, `SourcePage`, `SourceOutcome` und `SearchProvider`. Die Engine emittiert Quellen unabhängig als Flow. Erfolgreiche Seiten werden für 60 Sekunden in einem auf 64 Einträge begrenzten Speicher-Cache gehalten. Fehler werden nicht gecacht. Jede Quelle hat ein Zeitlimit von 18 Sekunden. Abbruch der Collection propagiert an alle Provider. Der Merger übernimmt die zentrale Datumsprüfung, URL-Sicherheitsprüfung, Zusammenführung und Sortierung.

`data:search` kapselt OkHttp und alle Plattformdetails. Der Transport setzt Verbindungs-, Lese- und Aufrufzeitlimits, begrenzt Antworten auf 2 MiB, bricht HTTP-Calls bei Coroutine-Abbruch ab und wiederholt temporäre Netzwerk-/5xx-Fehler einmal. Rate-Limits führen zu einer Pause anhand `Retry-After`/`X-RateLimit-Reset`; Stack-Exchange-`backoff` wird ebenfalls beachtet. Keine automatischen HTTP-Redirects, keine HTTP-Logs und kein Disk-Cache.

## Provider erweitern

1. Eine neue `SearchSource` mit verständlichem Label und Einrichtungshinweis ergänzen.
2. `SearchProvider` im Datenmodul implementieren. Pro Plattform genau eine Instanz registrieren. Ergebnisse müssen Original-URLs, einen stabilen Identifier und die globale Rangposition über alle Seiten liefern.
3. Datum nur setzen, wenn es ein Veröffentlichungs-/Erstellungsdatum ist. Fehlende Datumsangaben als `null` weiterreichen, niemals die aktuelle Zeit einsetzen.
4. Backend-Cursor und kumulierten Rangoffset als `SearchCursor` zurückgeben. Bei Ende `nextCursor = null` liefern. Plattformgrenzen respektieren; keine automatische Vollabfrage aller Seiten.
5. Nutzerverständliche Fehler über `SourceException` liefern. Keine Rohantworten, Tokens oder Request-URLs in Fehlermeldungen aufnehmen.
6. Provider in `ProviderFactory` registrieren und HTTP-Fixtures für Query, Datum, Paging und Fehlerbehandlung ergänzen.

Direkt- und Index-Cursor sind getrennt. Fallback-Cursor tragen intern das Präfix `index:`; die Factory entfernt dieses vor dem Aufruf des Index-Providers. Ein Fehler auf Seite 2 wechselt nicht heimlich den Backend-Cursor.

## Bewusste Grenzen und nächste Schritte

- Websuche braucht eine erlaubte SearXNG-Instanz. Es werden keine wechselnden öffentlichen Instanzen hart codiert. Sinnvoll ist ein selbst kontrollierter Suchdienst mit Monitoring, begrenzter Parallelität und einem geregelten Betrieb.
- Reddit benötigt genehmigten Zugriff und derzeit eine manuelle Access-Token-Eingabe; kein Client-Secret wird in einer APK versteckt. Ein späterer OAuth-Flow gehört hinter eine passende Provider-/Backend-Schnittstelle.
- YouTube nutzt einen manuell eingerichteten API-Key. Für eine verteilte App sind Kontingentverwaltung und ein Backend sinnvoll, weil ein APK-Schlüssel grundsätzlich extrahierbar ist.
- GitHub durchsucht Issues und PRs. Repository-, Code- und Discussion-Suche sollten getrennte Subadapter mit klaren Datums-/Authentifizierungsregeln bekommen.
- GitHub verwendet getrennte Issue-/PR-Abfragen, damit auch GitHub-App-Nutzertokens unterstützt werden. Ein fehlgeschlagener Teil wird gemeldet; der nächste Seitenabruf kann bei diesem Teil Treffer auslassen, analog zu den Foren-Batches.
- „Foren“ umfasst zunächst drei Stack-Exchange-Sites. Weitere Discourse-/Forumadapter können folgen. Der Oberflächenhinweis bleibt so konkret wie die tatsächliche Abdeckung.
- Pagination eines Foren-Batches kann bei Ausfall einer einzelnen Site auf dieser Seite Treffer auslassen; der Quellenhinweis meldet den Teilausfall. Ein späterer Cursor pro Subadapter verbessert gezielte Wiederholungen.
- Ranking ist transparent und deterministisch, keine trainierte Relevanzbewertung. Für die nächste Stufe eignen sich Qualitätsmetriken mit einem kuratierten Satz realer Suchaufgaben.
- Ergebnisse bleiben im Speicher. Persistente Offline-Suche, Suchhistorie, Bookmarks und Hintergrundjobs sind getrennte spätere Funktionen.
- App-Texte sind zunächst deutsch. Vor weiterer Lokalisierung in Android-Stringressourcen auslagern.

## Verifikation

JVM-Tests prüfen inklusive UTC-Datumsgrenzen, Validierung, Quellenauswahl, URL-Dedup, Ranking, Cache, Timeout und Abbruch. MockWebServer-Tests prüfen echte HTTP-Requests und Antworten aller Adapter, Anmeldedaten, Pagination, Rate-Limits, Wiederholungen und Teilfehler. ViewModel-Tests prüfen Nutzerzustand und veraltete Suchaufträge. Instrumentierte Compose-Tests prüfen Suche, Validierung und exklusive Sortierung; der Keystore-Test prüft verschlüsselte Persistenz auf Android. Der Debug-Workflow verifiziert zusätzlich das Zertifikat der fertigen APK.
