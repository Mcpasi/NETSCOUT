# Suchqualität 0.3.1

Untersuchung am 19.10.2026, Ausgangsstand `5ce6327a35702eceb8a23d67038763d8ae8edde9`.
Alle unten aufgeführten Fehler wurden vor Änderungen am Produktionscode durch fehlgeschlagene Tests reproduziert. Die erste gezielte Ausführung enthielt **36 Tests, davon 13 fehlgeschlagen**. Die anschließende Messung nutzte dieselben unveränderten Eingaben vor und nach der Korrektur.

## Reproduzierte Fehler und Korrekturen

| Fehlerursache | Konkrete Reproduktion und beobachteter Fehler | Korrektur | Fehlgeschlagene Fälle vorher |
| --- | --- | --- | ---: |
| Klartext und Markdown werden als HTML gelesen | GitHub-Issue/Reddit: `Use List<Widget> in Kotlin`, wobei der generische Ausdruck in Markdown-Code steht. Die HTML-Aufbereitung entfernt `<Widget>`. Dasselbe passiert bei der GitHub-Klartextbeschreibung `A Kotlin library for <Widget> collections.` Die Suche nach `Widget,kotlin` verliert passende Treffer schon vor der Modellbewertung. | GitHub-Beschreibungen bleiben Klartext; Issue-/PR- und Reddit-Texte werden zuerst als Markdown verarbeitet. CommonMark schützt literale Zeichen in Code vor der anschließenden HTML-Extraktion. Tatsächliches HTML wird weiterhin bereinigt; Absatzgrenzen bleiben erhalten. | 6 |
| Reihenfolge wiederholter URL-Parameter geht verloren | `?sort=title&sort=date` und `?sort=date&sort=title` werden gleichgesetzt. Auch mehrfaches `filter` ist betroffen. Solche Parameter können eine geordnete Sortierung oder den ersten/letzten Filterwert bestimmen. Zwei unterschiedliche Treffer werden zu einem. | Unterschiedliche Parameternamen werden weiterhin sortiert; die Reihenfolge gleichnamiger Parameter bleibt erhalten. | 2 |
| Nichtstandardports gehen verloren | `http://example.org:443/article` wird mit `https://example.org/article` zusammengelegt; entsprechend `https://example.org:80/article` mit `http://example.org/article`. Das sind unterschiedliche Endpunkte. | Nur der Standardport des jeweiligen Schemas wird entfernt: 80 bei HTTP, 443 bei HTTPS. | 2 |
| Unlesbare Ergebniszeilen gelten als erfolgreiche leere Suche | Brave liefert Ergebniscontainer, deren Titelklasse oder Link nicht mehr lesbar ist. Vorher: Erfolg mit null Treffern und kein DuckDuckGo-Aufruf. Sind auch die Zeilen des letzten Dienstes unlesbar, erscheint ebenfalls ein falscher Erfolg. | Sind Ergebniszeilen vorhanden, aber keine lesbar, wird ein Quellenfehler ausgelöst. Dadurch greift der bestehende Ausweichweg; ein vollständig unlesbarer letzter Dienst meldet einen Fehler. Gültige leere Antworten und lesbare, wegen der Quelleneinschränkung ausgeschlossene Treffer bleiben erfolgreiche Antworten. | 3 |

Die vier Ursachen werden durch `SourceTextRegressionTest`, `UrlIdentityRegressionTest` und `HtmlReliabilityRegressionTest` abgesichert. `SearchQualityModelTest` prüft zusätzlich alle 20 Textfälle mit dem echten lokalen Modell im normalen JVM-Testlauf.

CommonMark ist auf die Android-kompatible Version 0.21.0 festgelegt. Neuere Versionen benötigen zusätzliche Java-APIs; 0.24.0 ruft unter anderem `List.of` und `Map.of` auf, die auf älteren unterstützten Android-Versionen fehlen. `SearchTextCompatibilityTest` führt den tatsächlichen GitHub-Provider samt Markdown-Code auf Android aus, mit HTTP-Antworten aus einem In-Memory-Interceptor.

## Messverfahren

- **Textqualität:** 20 vorab beschriftete Provider-Fixtures, davon 14 relevante und 6 irrelevante Kandidaten. Eingaben laufen durch die tatsächlichen GitHub-, Reddit-, HN- oder YouTube-Adapter, `SemanticRelevance` mit dem festgelegten ONNX-Modell und `ResultMerger`. Gemessen wird die Entscheidung, ob ein Treffer angezeigt wird. Positive Kontrollen enthalten normalen Text, echtes HTML und korrekt maskierte Generics; negative Kontrollen enthalten fehlende Begriffe, Teilworttreffer und unverbundene Absätze.
- **URL-Identität:** acht unabhängige Paare, davon vier gleichwertig und vier verschieden. Geprüft wird die Anzahl nach der tatsächlichen Deduplizierung. Tracking, Fragmente, unabhängige Parameterreihenfolge und korrekte Standardports dienen als Kontrollen.
- **Zuverlässigkeit:** acht feste HTTP-Szenarien, je dreimal mit frischem Transport und ohne Engine-Cache. Ein Versuch gilt nur als korrekt, wenn Status, Trefferzahl und Zahl der Ausweichaufrufe stimmen. Explizit leere Antworten zählen als korrekt; ein unlesbares letztes Backend muss als Fehler sichtbar werden.

Die HTTP-Antworten kommen ausschließlich von lokalem MockWebServer. Die Messung benötigt keine Suchzugänge und verbraucht keine Plattformkontingente. Modell und Tokenizer bleiben unverändert; es wurden weder ein Schwellwert noch die Labels an die Ergebnisse angepasst.

| Kennzahl | Vorher | Nachher |
| --- | ---: | ---: |
| Text: richtige Entscheidungen / 20 | 14 / 20 | 20 / 20 |
| Text: Genauigkeit `(TP + TN) / N` | 70,0 % | 100,0 % |
| Text: Präzision `TP / (TP + FP)` | 100,0 % | 100,0 % |
| Text: Recall `TP / (TP + FN)` | 57,1 % | 100,0 % |
| Text: F1 `2TP / (2TP + FP + FN)` | 72,7 % | 100,0 % |
| Text: TP / FP / FN / TN | 8 / 0 / 6 / 6 | 14 / 0 / 0 / 6 |
| URL-Identität: korrekte Paare / 8 | 4 / 8 (50,0 %) | 8 / 8 (100,0 %) |
| URL-Treffer, Summe je Paar; erwartet 12 | 8 | 12 |
| Zuverlässigkeit: korrekt behandelte Versuche / 24 | 15 / 24 (62,5 %) | 24 / 24 (100,0 %) |

Maschinenlesbare Ergebnisse einschließlich jeder einzelnen Entscheidung: [vorher](search-quality/0.3.1/before.json), [nachher](search-quality/0.3.1/after.json).

Das ist ein gezielter **Diagnostikkorpus**, keine repräsentative Stichprobe des Webs. Die Werte belegen die Korrektur dieser Fehlerfälle; sie schätzen weder globale Suchpräzision noch Verfügbarkeit öffentlicher Dienste. Dreimal dieselben Ausfallszenarien zu prüfen ist eine Wiederholbarkeitsprüfung, keine unabhängige Stichprobe. Die URL-Fälle prüfen die Identitätslogik separat vom neuronalen Ranking.

Festgelegte Eingaben:

- Textkorpus: `data/search/src/test/resources/search-quality/text-cases.json`, SHA-256 `dda93c7718e43b97efaa3a74887ebb123017aeb79cd5330e80ea5fe4d1da05c6`.
- URL-/HTTP-Fälle: `SearchQualityFixtures.kt`.
- Unveränderte Modellsperrdatei: `relevance-model.lock.json`, SHA-256 `c1afd5df6c29d40f2a2f658b08937df6bcc81f235dbd73886473c465aa7cf4a9`.

## Wiederholen

```sh
./gradlew :data:search:checkSearchQuality \
  -PsearchQualityOutput=/tmp/netscout-after.json \
  -PsearchQualityLabel=0.3.1

./gradlew :data:search:test \
  --tests de.netscout.data.SourceTextRegressionTest \
  --tests de.netscout.data.UrlIdentityRegressionTest \
  --tests de.netscout.data.HtmlReliabilityRegressionTest
```

Der erste Befehl schreibt Messwerte, auch wenn der untersuchte Stand schlechtere Ergebnisse liefert. Der zweite verlangt die erwarteten Entscheidungen. Modell-/Bibliotheksdownloads beim ersten Build benötigen Netzwerkzugriff.

Für die erneute Vorher-Messung bleiben Messprogramm und Fixtures auf dem Stand dieser Änderung; ausschließlich die fünf korrigierten Produktionsdateien werden in einer separaten Arbeitskopie zurückgesetzt:

```sh
git worktree add --detach ../NetScout-quality-before HEAD
git -C ../NetScout-quality-before restore --source=5ce6327 --worktree -- \
  core/search/src/main/kotlin/de/netscout/core/ResultMerger.kt \
  data/search/src/main/kotlin/de/netscout/data/JsonHelpers.kt \
  data/search/src/main/kotlin/de/netscout/data/GitHubProvider.kt \
  data/search/src/main/kotlin/de/netscout/data/RedditProvider.kt \
  data/search/src/main/kotlin/de/netscout/data/PublicWebProvider.kt
cd ../NetScout-quality-before
./gradlew :data:search:test \
  --tests de.netscout.data.SourceTextRegressionTest \
  --tests de.netscout.data.UrlIdentityRegressionTest \
  --tests de.netscout.data.HtmlReliabilityRegressionTest \
  :data:search:checkSearchQuality \
  -PsearchQualityOutput=/tmp/netscout-before.json \
  -PsearchQualityLabel=5ce6327 --continue
# Erwartet: 36 Tests, 13 fehlgeschlagen; Messdatei wird trotzdem geschrieben.
```

## Version und Abschlussprüfung

Patch-Bump von **0.3.0 auf 0.3.1**, lokaler Standard-Versionscode von 1 auf 2. Die bestehende CI-Vergabe des Versionscodes und das permanente Debug-Signaturzertifikat bleiben erhalten.

Die Abschlussprüfung mit CommonMark 0.21.0 bestätigte dieselben Messwerte und Modellwerte wie die erste Nachher-Messung. Lokal erfolgreich:

- **126 JVM-/ViewModel-Tests**, keine Fehler oder übersprungenen Tests: Core 41, Suchadapter 66, Relevanz 10, App 9.
- Android Lint: 0 Fehler, 27 Warnungen, überwiegend Hinweise auf neuere Bibliotheksversionen. Die Android-kompatible CommonMark-Version bleibt bewusst festgelegt.
- Debug-APK und Android-Test-APK gebaut; Paketmetadaten der App bestätigen `versionName=0.3.1`, `versionCode=2`.
- Permanentes Debug-Zertifikat mit `scripts/verify-debug-signature.py` geprüft: `a5d9e8eadeb82bc5f5bb35e9f74a945b879349defae394d548c22eab57a0f8ea`.

```sh
./gradlew :core:search:test :data:search:test :data:relevance:test \
  :app:testDebugUnitTest :app:lintDebug :app:assembleDebug \
  :app:assembleDebugAndroidTest :data:search:checkSearchQuality
python3 scripts/verify-debug-signature.py app/build/outputs/apk/debug/app-debug.apk
```

Die Android-Gerätetests sind zusätzlich Teil des bestehenden CI-Jobs auf API 29; lokal wurden die Test-APK gebaut und die JVM-Prüfungen ausgeführt.
