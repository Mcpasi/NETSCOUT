# Architektur des Prototyps

## Verantwortlichkeiten

`app` hält die Compose-Oberfläche, einen screenweiten `SearchViewModel` und den `SettingsStore`. Nur hier existieren Android-Abhängigkeiten. Die `StateFlow`-Oberfläche zeigt Teilergebnisse und hält Filteränderungen getrennt vom aktiven Suchauftrag. Eine neue Suche oder geänderte Zugangsdaten bricht alte Anfragen ab; alte Treffer können keine neue Suche überschreiben.

`core:search` definiert `SearchRequest`, `SearchItem`, `SearchCursor`, `SourcePage`, `SourceOutcome` und `SearchProvider`. `KeywordQuery` zerlegt Kommas/Semikolons/Leerzeichen und zitierte Phrasen, erzeugt literale Provider-Abfragen, prüft alle Begriffe an Unicode-Wortgrenzen und ermittelt passende Vorschau-/Hervorhebungsstellen. Die Engine emittiert Quellen unabhängig als Flow. Erfolgreiche Seiten werden für 60 Sekunden in einem auf 64 Einträge begrenzten Speicher-Cache gehalten. Fehler werden nicht gecacht. Jede Quelle hat ein Zeitlimit von 18 Sekunden. Abbruch der Collection propagiert an alle Provider. Der Merger übernimmt die verbindliche Keyword-Prüfung, Datumsprüfung, URL-Sicherheitsprüfung, Zusammenführung und Sortierung. Eine Plattform-Rangposition kann die Keyword-Prüfung nicht umgehen.

`SearchItem.searchText` enthält den gelieferten Text vor der Kürzung, `snippet` die passende Vorschau. Autorenname, Quellennamen und Tracking-URLs dürfen nicht allein einen Keyword-Treffer begründen. Die Oberfläche nutzt die aktive Suchanfrage zur Hervorhebung und zählt je Quelle nur bestätigte Treffer.

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
- GitHub durchsucht Repository-Namen/-Beschreibungen und Issue-/PR-Titel/-Texte in drei getrennten Abfragen. Das unterstützt auch GitHub-App-Nutzertokens. README, Code und Discussions benötigen eigene Adapter; vollständige README-Abfragen pro Repository werden zur Schonung anonymer Kontingente nicht nachgeladen.
- Ein fehlgeschlagener GitHub-Teil wird gemeldet; der nächste Seitenabruf kann bei diesem Teil Treffer auslassen, analog zu den Foren-Batches. Bei neuester Sortierung werden Repository-Kandidaten nach API-Aktualisierung geladen, anschließend zentral nach Erstellung der geladenen Treffer sortiert.
- „Foren“ umfasst zunächst drei Stack-Exchange-Sites. Weitere Discourse-/Forumadapter können folgen. Der Oberflächenhinweis bleibt so konkret wie die tatsächliche Abdeckung.
- Pagination eines Foren-Batches kann bei Ausfall einer einzelnen Site auf dieser Seite Treffer auslassen; der Quellenhinweis meldet den Teilausfall. Ein späterer Cursor pro Subadapter verbessert gezielte Wiederholungen.
- Ranking ist transparent und deterministisch: alle Keywords erforderlich, Titel-Abdeckung vor Text-Abdeckung und Plattformrang. Die Suche arbeitet wörtlich, ohne automatische Übersetzung oder semantisches Modell. GitHub/HN haben keine Sprachvorgabe; SearXNG nutzt `language=all`. Die Sprache der Keywords kann den tatsächlichen Sprachmix trotzdem beeinflussen.
- Ergebnisse bleiben im Speicher. Persistente Offline-Suche, Suchhistorie, Bookmarks und Hintergrundjobs sind getrennte spätere Funktionen.
- App-Texte sind zunächst deutsch. Vor weiterer Lokalisierung in Android-Stringressourcen auslagern.

## Verifikation

JVM-Tests prüfen inklusive UTC-Datumsgrenzen, Validierung, Keyword-UND-Suche, Komma-Eingaben, Phrasen, Wortgrenzen, internationale Texte, Quellenauswahl, URL-Dedup, Ranking, Cache, Timeout und Abbruch. MockWebServer-Tests prüfen echte HTTP-Requests und Antworten aller Adapter, Anmeldedaten, Pagination, Rate-Limits, Wiederholungen und Teilfehler. Die Nutzermeldung `Codex,mobile,android` ist ein Regressionstest über Parser, HN-Request, langen Beitragstext, Merger, ViewModel-Zähler und Compose-Oberfläche. Der optionale Task `:data:search:checkLiveSearch` prüft dieselbe Anfrage gegen die realen öffentlichen GitHub-/HN-Endpunkte. Der Keystore-Test prüft verschlüsselte Persistenz auf Android. Der Debug-Workflow verifiziert zusätzlich das Zertifikat der fertigen APK und startet die verpackte App.
