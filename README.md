# NetScout für Android

Ein erster nativer Kotlin-/Jetpack-Compose-Prototyp für eine quellenübergreifende Keyword-Suche. Android 8.0+ (API 26), deutsche Oberfläche, keine Anmeldung bei NetScout und kein eigener Backend-Dienst nötig.

- Keywords, optionale Von-/Bis-Daten und sechs auswählbare Quellen.
- Alle sechs Quellen ohne Schlüssel nutzbar. Websuche ist vorausgewählt; Reddit und YouTube nutzen ohne eigene Zugänge den öffentlichen Suchindex.
- Gemeinsames Suchthema statt bloßer Wortvorkommen: Kandidaten werden anhand zusammengehöriger Textstellen und eines lokalen mehrsprachigen Relevanzmodells bewertet. `Codex,mobile,android` ist eine beliebige Beispielanfrage; keine Begriffe sind im Produktionscode festgelegt.
- Kommas, Semikolons und Leerzeichen einschließlich kopierter Unicode-Leerzeichen trennen Keywords; Anführungszeichen gruppieren genaue Phrasen. Die Oberfläche zeigt alle erkannten, erforderlichen Begriffe.
- Neueste Beiträge oder höchste Relevanz; die Sortier-Checkboxen sind gegenseitig exklusiv.
- Echte Treffer, Links zur Originalquelle, Quellenstatus mit Abfrage-/Prüfphase und Kandidatenzahl sowie weitere Ergebnisse per Pagination. Nach dem Suchstart wird der Quellenstatus eingeblendet.
- Parallele Quellenabfragen; Fehler einzelner Plattformen beeinträchtigen die übrigen nicht.
- Optional verschlüsselt gespeicherte Zugänge, Speicher-Cache für erfolgreiche Seiten, Abbruch und API-Pausen.

## APK testen

Im Repository unter **[Actions → Android tests & debug APK](https://github.com/Mcpasi/NETSCOUT/actions/workflows/android.yml)** einen Lauf öffnen und unter **Artifacts** `netscout-debug-<Laufnummer>` herunterladen. Das ZIP enthält `netscout-debug.apk`, deren SHA-256 und den Zertifikats-Fingerabdruck. APK auf Android installieren und bei späteren Läufen über die bestehende Version aktualisieren.

Alle Debug-Builds verwenden den eingecheckten [Entwicklungsschlüssel](debug-signing/README.md). Er darf nicht rotiert werden, solange die Testinstallation aktualisierbar bleiben soll. Android benötigt zusätzlich einen mindestens gleich hohen Versionscode. Für CI steigt dieser mit jeder neuen Workflow-Ausführung.

## Welche Daten sind sofort verfügbar?

| Quelle | Zugriff im Prototyp | Grenzen |
| --- | --- | --- |
| Web | Öffentliche Brave-Suche, DuckDuckGo als zweiter Dienst; ohne Einrichtung | Titel und Index-Textstellen, kein Volltextcrawl. Keine eigenen Tages-/Monatskontingente. Verfügbarkeit, Sperren und Seitentiefe bestimmt der Suchdienst. |
| Reddit | Öffentliche Indexsuche nach Diskussionen, ohne Token | Indexierte Beiträge statt vollständiger Reddit-API. Reddit selbst blockiert anonyme API-Abfragen. Genehmigter OAuth-Zugang bleibt optional. |
| YouTube | Öffentliche Indexsuche nach Videos, ohne API-Key | Titel und Index-Textstellen statt vollständiger Videobeschreibung. Explizite Veröffentlichungsdaten werden übernommen, relative Datumsangaben bleiben unbekannt. Eigener Data-API-Key bleibt optional. |
| GitHub | Öffentliche Repositories, Issues und Pull Requests über die REST API, ohne Token; Index bei Ausfall | Drei API-Abfragen pro Seite. Name/Beschreibung bzw. Titel/Text, keine Kommentare und kein Code-Suchindex. Anonyme Suche limitiert; optionaler Token erhöht das Kontingent. |
| Hacker News | Öffentliche Story-Beiträge über Algolia, ohne Schlüssel; Index bei Ausfall | Link öffnet die HN-Diskussion. Keine vollständige Kommentarsuche. |
| Foren | Fragen auf Stack Overflow, Super User und Server Fault über die Stack Exchange API; Index bei Ausfall | Kein universeller Forenindex. API-Kontingente und `backoff` werden beachtet; Indexsuche bleibt auf dieselben drei Foren beschränkt. |

Einfach Keywords eingeben und suchen. Web, GitHub, Foren und HN sind vorausgewählt; Reddit und YouTube lassen sich direkt hinzuwählen. Über das Zahnrad **Optionale Suchzugänge** können vorhandene eigene API-Zugänge oder eine eigene/erlaubte SearXNG-HTTPS-Instanz mit JSON-Ausgabe ergänzt werden. Alle Felder können leer bleiben. Die GitHub-Identität des Entwicklungsrechners wird niemals in die APK übernommen.

NetScout verlangt weder Konto noch Bezahlung noch ein eigenes tägliches Suchkontingent. Eine garantierte unbegrenzte Suche bei fremden Diensten gibt es trotzdem nicht. Die App liest öffentlich abrufbare Suchergebnisseiten, erkennt Sperr-/CAPTCHA-Seiten, respektiert Pausen und verwendet keine Demo-Treffer. API-Zugangsdaten werden niemals an den Suchindex weitergegeben. Ausgefallene Direktzugänge und optionale eigene Suchdienste wechseln auf der ersten Seite automatisch zum gekennzeichneten öffentlichen Index; Pagination bleibt beim antwortenden Dienst. Ein hängender Erstzugriff lässt nach vier Sekunden Zeit für den Ausweichweg. Öffentliche Websuche folgt angebotenen Fortsetzungen ohne festes App-Seitenlimit. Bei Sperren/Ausfällen lassen sich dieselben Keywords samt Plattformbeschränkung ohne Schlüssel im normalen Browser öffnen.

Geprüfte Alternativen, echte Abfragen und reproduzierte Fehler vor der Änderung: [docs/keyless-search.md](docs/keyless-search.md).

### Datumsfilter und Sortierung

Die Datumsgrenzen beziehen sich auf **Veröffentlichung/Erstellung in UTC**, einschließlich beider ausgewählter Tage. Soweit die API es unterstützt, werden Grenzen bereits an die Quelle übergeben; danach wird zentral erneut exakt gefiltert. Reddit und Suchindizes werden lokal gefiltert. Treffer ohne bekanntes Datum werden bei aktivem Datumsfilter ausgeschlossen; Indexsuche kann deshalb deutlich weniger Treffer zeigen. Crawl-/Änderungsdaten oder relative Angaben werden nicht als Veröffentlichungsdatum ausgegeben. Weitere Seiten können nötig sein; eine vollständige historische Suche ist nicht garantiert. „Neueste“ sortiert die **geladenen** Treffer, unbekannte Daten stehen am Ende.

Seit 0.2.0 genügt ein Vorkommen aller Wörter irgendwo im Beitrag nicht mehr: Titel und eine zusammenhängende Textstelle müssen die Keywords gemeinsam belegen. Eine Textstelle kann auch zwei benachbarte Sätze desselben Absatzes umfassen. Fehlt dort ein vollständiger Beleg, prüft das lokale Modell außerdem Originalstellen bis 1.000 Zeichen über Absatzgrenzen hinweg, mit einer zusätzlichen Prüfung des Bezugs zwischen den benötigten Abschnitten. Drei unabhängige Release-Note-Abschnitte für „Codex“, „mobile“ und „Android“ werden weiterhin verworfen. Bei langen Texten werden vollständige Trefferfenster über alle Vorkommen gesucht; die aufklappbare Vorschau zeigt die bewertete Originalstelle mit Hervorhebung. Bis zu vier Textstellen pro Kandidat verhindern, dass zwei kurze Erwähnungen eine spätere Erklärung verdrängen. Ein passender Titel verdeckt dabei keine stärkeren Textbelege. Der Quellenstatus unterscheidet laufende Abfragen, die Inhaltsprüfung geladener Kandidaten und bestätigte Treffer. Auch eine erfolgreiche Antwort ohne Kandidaten wird ausdrücklich angezeigt. Verworfenes bleibt bei der Pagination berücksichtigt, damit nach einer leeren gefilterten Seite weitergesucht werden kann.

Die Relevanzsortierung nutzt primär den gelernten Modellwert; Reciprocal Rank Fusion (`1 / (60 + Rang)`) dient mit kleinem Gewicht als Tie-Breaker. Inkompatible Popularitätswerte verschiedener Plattformen werden nicht vermischt. Identische URLs werden über Quellen und Seiten zusammengeführt, Trackingparameter und Fragmente ignoriert. Bedeutungsvolle Parameter wie YouTube-Video-IDs bleiben erhalten.

Die Suche berücksichtigt alle Sprachen unabhängig von der deutschen App-Oberfläche. GitHub/HN erhalten keine Deutsch- oder Länder-Vorgabe; SearXNG wird explizit mit `language=all` angesprochen. Die Sprache der Keywords und die Abdeckung der Plattformen beeinflussen den Sprachmix weiterhin. Das Modell wurde auf 14 Sprachen trainiert. Die Kandidatenabfragen bleiben wörtlich, ohne automatische Übersetzung oder Synonymerweiterung: Der semantische Reranker kann nur bewerten, was die Plattform liefert. `AI` trifft beispielsweise nicht allein auf `Gmail` zu. HN erhält zusätzlich deaktivierte Tippfehler-/Präfixsuche und darf fehlende Wörter nicht automatisch weglassen.

Das Modell läuft vollständig auf dem Gerät, ohne kostenpflichtige KI-API, Backend oder Modell-Download in der App. Es vergrößert die APK und benötigt zusätzliche Rechenzeit. Inhaltliche Bewertungen können falsch sein; kurze mehrdeutige Anfragen bleiben mehrdeutig. Die konservative Textstellenprüfung kann passende Erklärungen über mehrere Absätze auslassen. Modell, Lizenz, feste Prüfsummen, Datenschutz und Qualitätsgrenzen: [docs/relevance-model.md](docs/relevance-model.md).

## Lokal bauen

Voraussetzungen: JDK 17, Python 3.11+, Android SDK mit Plattform 35 und Build-Tools 35.0.0. In Android Studio öffnen oder `ANDROID_HOME` setzen; alternativ eine lokale `local.properties` mit `sdk.dir=/pfad/zum/sdk` erstellen. Gradle 8.11.1 samt SHA-256-Prüfung ist als Wrapper enthalten. Der erste Build lädt etwa 136 MB Modell-/Tokenizerdateien aus einer festen Hugging-Face-Revision und prüft deren SHA-256; weitere Builds nutzen den lokalen Cache. Keine Python-Zusatzpakete nötig.

```sh
./gradlew :core:search:test :data:search:test :data:relevance:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
python3 scripts/verify-debug-signature.py app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Geräte-/Oberflächentests bei laufendem Emulator oder angeschlossenem Android-Gerät:

```sh
./gradlew :app:connectedDebugAndroidTest
```

GitHub Actions führt Unit-Tests einschließlich echter Modellinferenz und Tokenizer-Referenzvektoren, Android Lint, den signierten Debug-Build und in einem separaten Job Emulator-Tests auf API 29 aus. Ein Gerätetest lädt das verpackte Modell über den Produktionspfad. Der Workflow ist zusätzlich über `workflow_dispatch` manuell startbar. Test- und Lint-Berichte werden als Artefakte bereitgestellt. Plattformtests verwenden lokale HTTP-Fixtures/Fake-Provider; sie verbrauchen keine Plattformkontingente und benötigen keine Secrets.

Nach den Emulator-Tests wird außerdem die tatsächliche APK installiert und über ihre Launcher-Activity gestartet. Eine Bildschirmaufnahme dieses Startchecks liegt im Artefakt `android-ui-tests`.

Ein optionaler Integrationstest prüft alle sechs Quellen ohne Zugangsdaten durch dasselbe lokale Modell und gibt Abfragephasen, Diensthinweise, Kandidatenzahlen und bestätigte Treffer aus. Er nutzt öffentliche Dienste und läuft deshalb nicht automatisch in CI:

```sh
./gradlew :data:search:checkLiveSearch -PsearchKeywords=Codex,mobile,android
# Optional zwei echte Ergebnisseiten nur der Web-/Reddit-/Videosuche:
./gradlew :data:search:checkLiveSearch -PsearchKeywords=kotlin,android -PsearchSources=WEB,REDDIT,YOUTUBE -PsearchPages=2
```

Reproduktion der vor der Korrektur nachgewiesenen Suchfehler: [docs/search-regressions.md](docs/search-regressions.md).

Die [Qualitätsmessung für 0.3.1](docs/search-quality-0.3.1.md) dokumentiert vier weitere reproduzierte Fehlerursachen, die Korrekturen und maschinenlesbare Vorher-/Nachher-Ergebnisse. Der feste Prüfkorpus umfasst Textaufbereitung mit echtem lokalem Relevanzmodell, URL-Deduplizierung und Ausweichwege bei unlesbaren Suchantworten. Er lässt sich ohne Abfragen öffentlicher Suchdienste wiederholen:

```sh
./gradlew :data:search:checkSearchQuality
# Bericht: data/search/build/reports/search-quality.json
```

## Architektur und Erweiterung

```text
app/           Compose-UI, ViewModel, Android-Keystore, App-Konfiguration
core/search/   Modelle, Validierung, Provider-Vertrag, parallele Engine, Ranking/Dedup
data/search/   HTTP-Transport, Provider und Fallback-Verknüpfung
data/relevance/ Lokaler ONNX-Cross-Encoder, Tokenizer, Modelltests
```

Die drei Bibliotheksmodule sind reine Kotlin/JVM-Module. Neue Plattformen implementieren `SearchProvider`, liefern `SourcePage` mit Treffern und optionalem `SearchCursor` und werden in `ProviderFactory` registriert. `SearchRelevance` trennt die Trefferbewertung von den Plattformadaptern und erlaubt spätere alternative Modelle. `SourceException` unterscheidet fehlende Einrichtung, Rate-Limit und Ausfälle. Neue Werte in `SearchSource` erscheinen automatisch in der Quellenauswahl. Details und nächste Ausbauschritte: [docs/architecture.md](docs/architecture.md).

## Datenschutz und Zugangsdaten

Suchbegriffe gehen an die ausgewählten Plattformen oder den verwendeten Suchindex (Brave, bei Ausfall DuckDuckGo, optional eigene SearXNG-Instanz). Bei Direktzugriffsfehlern können sie an den Index-Ausweichweg gehen. Zielseiten werden zur Bewertung nicht zusätzlich abgerufen. Die App braucht Internetzugriff, aber keine Standort-, Kontakt- oder Speicherberechtigung. Sie enthält keine Telemetrie. Suchergebnisse werden nur im Arbeitsspeicher gehalten (max. 64 Seiten, 60 Sekunden Cache); es gibt keine Suchhistorie. Formulare und aktuelle Treffer überleben eine Bildschirmdrehung, aber keinen Prozessneustart.

Zugänge werden mit AES-GCM und einem nicht exportierbaren Android-Keystore-Schlüssel lokal verschlüsselt. App-Backups sind deaktiviert. Die Zugänge werden nicht im Saved-Instance-State oder in Logs gespeichert und nur an die jeweilige API gesendet. Deinstallation entfernt die lokalen Zugänge. Leere Zugangsfelder speichern entfernt die entsprechenden Tokens. Für größere Verteilung sind ein eigener Backend-Dienst und geregelte OAuth-/Schlüsselverwaltung vorgesehen.

## Primärquellen zum Datenzugriff

Geprüft am 19.10.2026; Vorgaben und Kontingente können sich ändern:

- [GitHub Search API, Limits und unvollständige Ergebnisse](https://docs.github.com/en/rest/search/search)
- [Hacker-News-Suche über Algolia](https://hn.algolia.com/api)
- [Stack Exchange Advanced Search](https://api.stackexchange.com/docs/advanced-search) und [Rate Limits](https://api.stackexchange.com/docs/throttle)
- [YouTube Search API](https://developers.google.com/youtube/v3/docs/search/list) und [Kontingente](https://developers.google.com/youtube/v3/determine_quota_cost)
- [Reddit Data API und OAuth-Anforderungen](https://support.reddithelp.com/hc/en-us/articles/16160319875092-Reddit-Data-API-Wiki)
- [SearXNG Search API und JSON-Konfiguration](https://docs.searxng.org/dev/search_api.html)
- [DuckDuckGo HTML-/Lite-Suche](https://duckduckgo.com/duckduckgo-help-pages/features/non-javascript) und [Brave-Suchoberfläche](https://search.brave.com/)
