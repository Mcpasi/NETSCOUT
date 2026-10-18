# NetScout für Android

Ein erster nativer Kotlin-/Jetpack-Compose-Prototyp für eine quellenübergreifende Keyword-Suche. Android 8.0+ (API 26), deutsche Oberfläche, keine Anmeldung bei NetScout und kein eigener Backend-Dienst nötig.

- Keywords, optionale Von-/Bis-Daten und sechs auswählbare Quellen.
- Neueste Beiträge oder höchste Relevanz; die Sortier-Checkboxen sind gegenseitig exklusiv.
- Echte Treffer, Links zur Originalquelle, Quellenstatus und weitere Ergebnisse per Pagination.
- Parallele Quellenabfragen; Fehler einzelner Plattformen beeinträchtigen die übrigen nicht.
- Optional verschlüsselt gespeicherte Zugänge, Speicher-Cache für erfolgreiche Seiten, Abbruch und API-Pausen.

## APK testen

Im Repository unter **[Actions → Android tests & debug APK](https://github.com/Mcpasi/NETSCOUT/actions/workflows/android.yml)** einen Lauf öffnen und unter **Artifacts** `netscout-debug-<Laufnummer>` herunterladen. Das ZIP enthält `netscout-debug.apk`, deren SHA-256 und den Zertifikats-Fingerabdruck. APK auf Android installieren und bei späteren Läufen über die bestehende Version aktualisieren.

Alle Debug-Builds verwenden den eingecheckten [Entwicklungsschlüssel](debug-signing/README.md). Er darf nicht rotiert werden, solange die Testinstallation aktualisierbar bleiben soll. Android benötigt zusätzlich einen mindestens gleich hohen Versionscode. Für CI steigt dieser mit jeder neuen Workflow-Ausführung.

## Welche Daten sind sofort verfügbar?

| Quelle | Zugriff im Prototyp | Grenzen |
| --- | --- | --- |
| GitHub | Öffentliche Issues und Pull Requests über die offizielle REST API, ohne Token | Zwei Abfragen pro Seite; keine Repository-/Code-/Discussion-Suche. Anonyme Suche stärker limitiert; optionaler Token erhöht das Kontingent. |
| Hacker News | Öffentliche Story-Beiträge über die Algolia-HN-Suche, ohne Schlüssel | Drittanbieterindex; Link öffnet die HN-Diskussion. Keine Kommentare im Prototyp. |
| Foren | Fragen auf Stack Overflow, Super User und Server Fault über die Stack Exchange API | Kein universeller Forenindex; Kontingente und `backoff` beachten. Autor, Original-Link und Lizenz werden angezeigt. |
| YouTube | Offizielle Data API v3 mit eigenem API-Key; alternativ SearXNG-Indexsuche | API im Google-Projekt aktivieren, Suchkontingent begrenzt. Indexsuche bietet geringere Abdeckung/Datumsqualität. |
| Reddit | Offizieller OAuth-API-Zugang; alternativ SearXNG-Indexsuche | Genehmigung durch Reddit erforderlich. Access-Token im Prototyp manuell eintragen/erneuern. Ältere Beiträge sind nicht vollständig abrufbar. |
| Web | Eigene/erlaubte SearXNG-HTTPS-Instanz mit aktiviertem JSON-Format | Kein vorgegebener öffentlicher Server, kein Gratis-/Verfügbarkeitsversprechen. Betrieb und Suchmaschinenlimits hängen von der Instanz ab. |

In der App über das Zahnrad **Quellen verbinden** öffnen. Alle Felder sind optional. Die drei sofort nutzbaren Quellen sind vorausgewählt. Die GitHub-Identität des Entwicklungsrechners wird niemals in die APK übernommen.

Es gibt keinen verlässlichen, unbegrenzt kostenlosen Vollzugriff auf alle Plattformen. Deshalb nutzt NetScout öffentliche APIs, klar erkennbare Index-Fallbacks und explizite Hinweise bei fehlenden Zugängen. Keine stillen Demo-Daten, keine CAPTCHA-Umgehung und kein Scraping von HTML-Suchergebnisseiten. Ein ausgefallener Direktzugang wechselt nur bei eingerichteter SearXNG-Instanz und nur auf der ersten Seite zum gekennzeichneten Index; Pagination bleibt beim ursprünglichen Backend.

### Datumsfilter und Sortierung

Die Datumsgrenzen beziehen sich auf **Veröffentlichung/Erstellung in UTC**, einschließlich beider ausgewählter Tage. Soweit die API es unterstützt, werden Grenzen bereits an die Quelle übergeben; danach wird zentral erneut exakt gefiltert. Reddit und SearXNG werden lokal gefiltert. Treffer ohne bekanntes Datum werden bei aktivem Datumsfilter ausgeschlossen. Weitere Seiten können dann nötig sein; eine vollständige historische Suche ist nicht garantiert. „Neueste“ sortiert die **geladenen** Treffer, unbekannte Daten stehen am Ende.

Relevanz kombiniert pro Quelle die Rangposition mit Reciprocal Rank Fusion (`1 / (60 + Rang)`) und einer kleinen Gewichtung für Keywords im Titel. Inkompatible Popularitätswerte verschiedener Plattformen werden nicht vermischt. Identische URLs werden über Quellen und Seiten zusammengeführt, Trackingparameter und Fragmente ignoriert. Bedeutungsvolle Parameter wie YouTube-Video-IDs bleiben erhalten.

## Lokal bauen

Voraussetzungen: JDK 17, Android SDK mit Plattform 35 und Build-Tools 35.0.0. In Android Studio öffnen oder `ANDROID_HOME` setzen; alternativ eine lokale `local.properties` mit `sdk.dir=/pfad/zum/sdk` erstellen. Gradle 8.11.1 samt SHA-256-Prüfung ist als Wrapper enthalten.

```sh
./gradlew :core:search:test :data:search:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
python3 scripts/verify-debug-signature.py app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Geräte-/Oberflächentests bei laufendem Emulator oder angeschlossenem Android-Gerät:

```sh
./gradlew :app:connectedDebugAndroidTest
```

GitHub Actions führt Unit-Tests, Android Lint, den signierten Debug-Build und in einem separaten Job Emulator-Tests auf API 29 aus. Der Workflow ist zusätzlich über `workflow_dispatch` manuell startbar. Test- und Lint-Berichte werden als Artefakte bereitgestellt. Tests verwenden lokale HTTP-Fixtures/Fake-Provider; sie verbrauchen keine Plattformkontingente und benötigen keine Secrets.

Nach den Emulator-Tests wird außerdem die tatsächliche APK installiert und über ihre Launcher-Activity gestartet. Eine Bildschirmaufnahme dieses Startchecks liegt im Artefakt `android-ui-tests`.

## Architektur und Erweiterung

```text
app/           Compose-UI, ViewModel, Android-Keystore, App-Konfiguration
core/search/   Modelle, Validierung, Provider-Vertrag, parallele Engine, Ranking/Dedup
data/search/   HTTP-Transport, Provider und Fallback-Verknüpfung
```

Die beiden Suchmodule sind reine Kotlin/JVM-Module. Neue Plattformen implementieren `SearchProvider`, liefern `SourcePage` mit Treffern und optionalem `SearchCursor` und werden in `ProviderFactory` registriert. `SourceException` unterscheidet fehlende Einrichtung, Rate-Limit und Ausfälle. Neue Werte in `SearchSource` erscheinen automatisch in der Quellenauswahl. Details und nächste Ausbauschritte: [docs/architecture.md](docs/architecture.md).

## Datenschutz und Zugangsdaten

Suchbegriffe gehen ausschließlich an die ausgewählten Plattformen bzw. die konfigurierte SearXNG-Instanz und deren Suchmaschinen. Die App braucht Internetzugriff, aber keine Standort-, Kontakt- oder Speicherberechtigung. Sie enthält keine Telemetrie. Suchergebnisse werden nur im Arbeitsspeicher gehalten (max. 64 Seiten, 60 Sekunden Cache); es gibt keine Suchhistorie. Formulare und aktuelle Treffer überleben eine Bildschirmdrehung, aber keinen Prozessneustart.

Zugänge werden mit AES-GCM und einem nicht exportierbaren Android-Keystore-Schlüssel lokal verschlüsselt. App-Backups sind deaktiviert. Die Zugänge werden nicht im Saved-Instance-State oder in Logs gespeichert und nur an die jeweilige API gesendet. Deinstallation entfernt die lokalen Zugänge. Leere Zugangsfelder speichern entfernt die entsprechenden Tokens. Für größere Verteilung sind ein eigener Backend-Dienst und geregelte OAuth-/Schlüsselverwaltung vorgesehen.

## Primärquellen zum Datenzugriff

Geprüft am 18.10.2026; Vorgaben und Kontingente können sich ändern:

- [GitHub Search API, Limits und unvollständige Ergebnisse](https://docs.github.com/en/rest/search/search)
- [Hacker-News-Suche über Algolia](https://hn.algolia.com/api)
- [Stack Exchange Advanced Search](https://api.stackexchange.com/docs/advanced-search) und [Rate Limits](https://api.stackexchange.com/docs/throttle)
- [YouTube Search API](https://developers.google.com/youtube/v3/docs/search/list) und [Kontingente](https://developers.google.com/youtube/v3/determine_quota_cost)
- [Reddit Data API und OAuth-Anforderungen](https://support.reddithelp.com/hc/en-us/articles/16160319875092-Reddit-Data-API-Wiki)
- [SearXNG Search API und JSON-Konfiguration](https://docs.searxng.org/dev/search_api.html)
