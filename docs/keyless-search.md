# Suche ohne Einrichtung

Stand der Prüfung: 19.10.2026. Ausgangspunkt: `3d27b638895386254e51ca28c75a7afa9596d661`.

Alle sechs Quellen können mit leeren Zugangsfeldern gesucht werden. Web, GitHub, Foren und Hacker News sind vorausgewählt; Reddit und YouTube benötigen nur die Auswahl in der Oberfläche. Es gibt kein NetScout-Konto, keine gemeinsamen versteckten API-Schlüssel und kein eigenes tägliches Suchkontingent. Fremde Dienste können Aufrufe und Ergebnistiefe trotzdem begrenzen. Indexsuche bewertet die gelieferten Titel/Textstellen, nicht den vollständigen Inhalt jeder Zielseite.

## Prüfung vor der Umsetzung

| Geprüfter Weg | Tatsächliche Beobachtung | Entscheidung |
| --- | --- | --- |
| Brave-Suchoberfläche, `"kotlin" "android"` | HTTP 200; passende öffentliche Web-Ergebnisse; echte zweite Seite mit neuen Ergebnissen. Reddit- und YouTube-Site-Suche ebenfalls HTTP 200 mit passenden Diskussionen/Videos. | Standard für Web und die schlüsselfreien Reddit-/YouTube-Adapter. |
| DuckDuckGo HTML und Lite | Anfangs HTTP 200, acht passende Web-Ergebnisse und zehn Reddit-Kandidaten. Spätere Abfragen/Fortsetzung lieferten HTTP 202 mit `challenge-form`/`anomaly-modal`. | Zweiter Dienst; Sperren werden erkannt und respektiert. Keine CAPTCHA-Lösung oder Wiederholung derselben Sperre. |
| Bing RSS | HTTP 200, aber Web- und `site:reddit.com`-Abfrage lieferten dieselben allgemeinen Kotlin-Seiten statt Reddit-Diskussionen. | Nicht als Suchbackend verwendet. Erfolgreiches HTTP ist kein Beleg für eine korrekt ausgeführte Suche. |
| Weitere unabhängige Dienste | Bing-HTML ignorierte ebenfalls die Reddit-/YouTube-Einschränkungen. Yahoo lieferte HTTP 500, Mojeek eine CAPTCHA-Seite und Google eine JavaScript-Zwischenseite; die Mwmbl-API überschritt das Zeitlimit. | Keine zusätzlichen unbestätigten Standardadapter. |
| Anonyme Reddit-JSON-Suche | HTTP 403. Reddit dokumentiert die OAuth-Pflicht und Sperrung nicht authentifizierter API-Aufrufe. | Öffentliche Indexsuche statt Abhängigkeit von einem anonymen JSON-Endpunkt. |
| YouTube-Suchseite | In dieser Ausführungsumgebung am Proxy mit HTTP 403 abgelehnt. | Kein ungetesteter direkter HTML-/interner API-Adapter; die geprüfte öffentliche Video-Indexsuche funktioniert ohne Google-Key. |
| GitHub, HN, Stack Exchange | Vorhandene öffentliche APIs sind bereits ohne Schlüssel nutzbar; dokumentierte Limits bleiben bestehen. | APIs beibehalten, öffentlicher Index als Erstseiten-Ausweichweg. |
| Öffentliche SearXNG-Instanzen | Die SearXNG-Dokumentation erklärt, dass viele öffentliche Instanzen JSON deaktivieren und dann HTTP 403 liefern. | Keine Einrichtung und kein zufälliger Server nötig. Ein eigener/erlaubter Dienst bleibt optional. |

Die beschriebenen HTTP-Beobachtungen sind Momentaufnahmen, keine Verfügbarkeitsgarantie. Produktionsabfragen verwenden dieselben wörtlichen Keywords und die bestehende lokale semantische Bewertung. Der Suchindex darf Wörter ignorieren; NetScout übernimmt solche Ergebnisse nur, wenn der vorhandene Beleg alle Begriffe und den Modelltest besteht. Plattformadapter kontrollieren Ergebnis-Host und -Pfad zusätzlich selbst.

Die erste echte Produktionsprüfung mit `kotlin,android` und leeren Zugangsdaten bestand für alle sechs Quellen: Web 20 geladene/20 vom Modell bestätigte Kandidaten, Reddit 20/20, YouTube 19/19, GitHub 90/89, Foren 21/16 und HN 30/29. Der anschließende zusätzliche Seitentest wurde bereits auf der ersten Seite für Web/Reddit/YouTube von den öffentlichen Diensten begrenzt (`RATE_LIMITED`); dies wird nicht als erfolgreicher Live-Paginationstest ausgegeben. Die frühe direkte Brave-Prüfung hatte eine echte zweite Seite geliefert; HTTP-Fixtures prüfen das Fortsetzungsprotokoll zusätzlich. Bei Sperren oder Ausfällen bietet die App deshalb die gleichen Keywords samt Plattformbeschränkung im normalen Browser an, ohne einen API-Schlüssel zu verlangen. Diese Browseransicht unterliegt der Suche des Anbieters und wird nicht als lokal bewertete Trefferliste ausgegeben.

## Vor der Behebung reproduzierte Fehler

`ProviderFactoryTest.empty settings make every source searchable without credentials` wurde zuerst gegen die unveränderte Factory ausgeführt. Ergebnis: ein fehlgeschlagener Test mit `[(REDDIT, SETUP_REQUIRED), (YOUTUBE, SETUP_REQUIRED), (WEB, SETUP_REQUIRED)]`. Erst danach wurde die schlüsselfreie Factory umgesetzt.

Ein zusätzlicher Test `IndexFallbackProviderTest.an unresponsive first page leaves time for the public fallback` ließ den primären Adapter hängen. Vor der Korrektur lief das gesamte 18-Sekunden-Budget ab, ohne den Ausweichweg aufzurufen (vier Tests, einer fehlgeschlagen). Jetzt begrenzt jede Erstseitenstufe ihren primären Versuch auf vier Sekunden. Der Ausweichweg bleibt innerhalb des bestehenden Engine-Budgets erreichbar; ein echter Nutzerabbruch wird weiterhin weitergereicht.

## Fortsetzung und Grenzen

- Ohne feste App-Seitengrenze den tatsächlich angebotenen Fortsetzungen folgen. Ein Dienst kann weitere Seiten verweigern oder die Suche beenden.
- Cursor behalten den antwortenden Dienst bei. Ein Fehler beim Weiterblättern startet nicht still eine neue Ergebnisliste.
- Werbung, Navigationsdaten, verwandte Links und Antwortkarten anderer Beiträge werden nicht an eine Ergebnistextstelle angehängt.
- Veröffentlichungsdaten nur übernehmen, wenn der Index sie ausdrücklich als solche bezeichnet. Relative Angaben und allgemeine Index-/Crawldaten bleiben unbekannt. Ein aktiver Datumsfilter blendet undatierte Treffer weiter aus.
- HTTP 429/`Retry-After` sowie Sperr-/Prüfseiten werden beachtet. Bei Prüfseiten zehn Minuten Host-Pause; unbekanntes Layout wird als Fehler gemeldet.
- Bei einem Ausfall oder einer Pause kann die Suche mit denselben Keywords im normalen Browser fortgesetzt werden. Ein API-Key wird dafür nicht benötigt.
- Eigene Schlüssel und Tokens gehen ausschließlich an die zugehörige API. Auch bei abgelehntem Schlüssel und ausgefallenem eigenem SearXNG funktioniert der öffentliche Ausweichweg ohne diese Anmeldedaten.

## Nachprüfen

```sh
./gradlew :core:search:test :data:search:test :data:relevance:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew :data:search:checkLiveSearch -PsearchKeywords=kotlin,android
./gradlew :data:search:checkLiveSearch -PsearchKeywords=kotlin,android -PsearchSources=WEB,REDDIT,YOUTUBE -PsearchPages=2
```

Die Live-Prüfungen verwenden keine Zugangsdaten, laufen durch das echte lokale Modell und werden wegen externer Limits nicht automatisch in CI ausgeführt. HTTP-Fixtures prüfen Query-Erhalt, Originaltext, Plattformtrennung, Fortsetzungen über Seite zehn, Sperrseiten, Fehler, Datum und Zugangsdatenisolation. Android-Tests prüfen die vorausgewählte Websuche sowie Auswahl/Suche aller sechs Quellen ohne Zugangseingabe.

Primärquellen: [DuckDuckGo HTML/Lite](https://duckduckgo.com/duckduckgo-help-pages/features/non-javascript), [Brave-Suchoberfläche](https://search.brave.com/), [Reddit OAuth-Pflicht](https://support.reddithelp.com/hc/en-us/articles/16160319875092-Reddit-Data-API-Wiki), [GitHub-Suchlimits](https://docs.github.com/en/rest/search/search), [HN-API](https://hn.algolia.com/api), [Stack-Exchange-Limits](https://api.stackexchange.com/docs/throttle), [YouTube Data API](https://developers.google.com/youtube/v3/docs/search/list), [SearXNG-Formate](https://docs.searxng.org/dev/search_api.html).
