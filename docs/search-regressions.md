# Reproduzierte Suchfehler

Ausgangspunkt: `7685087a900d3ba53ff46b94b07ac07272d16797` (18.10.2026).
Die folgenden Tests wurden zuerst ergänzt und gegen unveränderte Produktionsdateien ausgeführt. Erst nach ihrem Fehlschlag wurden die Korrekturen implementiert.

```sh
./gradlew :core:search:test
# Vor der Korrektur: 38 Tests, 6 fehlgeschlagen.
./gradlew :data:relevance:test --tests de.netscout.relevance.SemanticRelevanceTest
# Vor der Korrektur: 4 Tests, 1 fehlgeschlagen.
```

| Reproduktion | Beobachtung vor der Korrektur | Korrektur |
| --- | --- | --- |
| `Codex\u00a0mobile\u2003Android` mit kopierten Unicode-Leerzeichen | Die drei Wörter werden als ein einzelner Begriff behandelt; passende Titel fehlen. | Unicode-Leerzeichen werden vor Zerlegung und Request-Normalisierung vereinheitlicht. |
| `"local\u00a0llm",Android`, Text `Local\u2003LLM for Android` | Die Phrase wird trotz gleichwertiger Leerzeichen nicht erkannt. | Phrasen akzeptieren Unicode-Leerzeichen auch im Originaltext. |
| `Control Codex from a mobile client. It runs on Android.` | Kein einzelner Satz enthält alle drei Wörter; der gemeinsame Bezug wird nicht bewertet. | Zwei benachbarte Sätze innerhalb desselben Absatzes können einen Originalbeleg bilden. |
| Ein langer Satz mit `Codex` und rund 560 Zeichen später `mobile Android` | Starre Fenster beginnen jeweils 200 Zeichen vor einem Vorkommen und schneiden andere benötigte Wörter ab, obwohl alle in 700 Zeichen passen. | Ein gleitendes vollständiges Trefferfenster richtet sich nach den tatsächlichen Begriffspositionen. |
| Je 120 frühere, getrennte Vorkommen der drei Begriffe, danach eine vollständige Textstelle | Die auf 100 Vorkommen begrenzte Hervorhebung wird zur Suche verwendet; der späte Beleg fehlt. | Die Textstellensuche hat eigene Iteratoren über sämtliche Vorkommen; nur die Zahl gespeicherter Ausschnitte ist begrenzt. |
| Passender Titel, generischer erster Absatz, aussagekräftiger späterer Absatz | Der frühe Rücksprung beim passenden Titel verdeckt den späteren Textbeleg. | Textbelege werden auch bei vollständig passendem Titel ausgewählt und nach Keyword-Abdeckung priorisiert. |
| Zwei kurze vollständige Erwähnungen vor einer längeren, vom Scorer positiv bewerteten Erklärung | Die zwei kürzesten Stellen belegen beide Plätze; die Erklärung wird nie bewertet. | Bis zu vier Stellen werden geprüft, ohne die Auswahl nach der kürzesten Länge zu beschränken. |

Die Beispiele verwenden die vorhandene Beispielanfrage und weitere beliebige Begriffe; die Implementierung enthält keine festgelegten Produktnamen.

## Zusätzlicher Absatzfall aus dem Live-Vergleich

Nach den ersten Korrekturen wurden bei HN mehrere passende Beiträge weiterhin vor der Modellprüfung verworfen: Die Projekteinleitung nennt den Agenten, ein späterer Plattformabsatz Android. Der neue Modelltest `real model retains a project introduction followed by its platform paragraph` schlug vor dieser weiteren Korrektur ebenfalls fehl. Seine reduzierte Reproduktion lautet:

```text
An open source interface for Codex and other coding agents.

The mobile app runs on iOS and Android.
```

Die semantische Prüfung erhält jetzt bei fehlendem Satzbeleg vollständige Originalfenster bis 1.000 Zeichen und prüft die Verbindung der benötigten Abschnitte zusätzlich. Der gleiche Modelltest bestätigt einen Zusammenhang zwischen `rust`, `async` und `database` mit unmittelbarem Rückverweis. Unabhängige Release-Notes bleiben ausgeschlossen, obwohl ihr gemeinsamer Text ebenfalls einen hohen Anfrage-/Textstellenwert erhält. Ein weiterer, vor der Absicherung fehlgeschlagener Test verhindert, dass Rückverweise über einen zwischenliegenden Themenwechsel hinweg verwendet werden. Die Vorschau lässt sich vollständig aufklappen.

## HN und Foren

Echte HTTP-Abfragen vor der Korrektur bestätigten erreichbare Endpunkte: Algolia lieferte für `Codex,mobile,android` sechs Story-Kandidaten. Stack Exchange lieferte Fragen für `kotlin,android` und keine Kandidaten für eine Kombination mit einem erfundenen zweiten Begriff. Ein genereller Nichtstart dieser Provider ließ sich damit nicht bestätigen.

Die bisherige Engine lieferte ihren einzigen sichtbaren Quellenstatus erst nach der vollständigen Inhaltsprüfung. Eine abgeschlossene Seite blieb während der nächsten Seitenabfrage als Erfolg sichtbar. Jetzt werden Abfrage und Inhaltsprüfung separat angezeigt, einschließlich geladener Kandidaten und erfolgreicher leerer Antworten. Nach Suchstart wird zum Quellenstatus gescrollt. Ungeprüfte Kandidaten werden weiterhin nicht als Treffer ausgegeben.

Zusätzliche Tests prüfen den parallelen Start von GitHub, HN und Foren, laufende Prüfung ohne verfrühte Treffer, Pagination, Abbruch, Cache und Kandidaten-/Trefferzahlen. Ein echter Modelltest bestätigt den Zusammenhang über zwei Sätze. Bestehende Negativtests für fehlende Begriffe, getrennte Absätze und unabhängige Release-Note-Abschnitte bleiben erhalten. Der optionale Live-Check deckt jetzt alle drei öffentlichen Quellen ab:

```sh
./gradlew :data:search:checkLiveSearch -PsearchKeywords=Codex,mobile,android
```

## Abschlussprüfung

77 JVM-/ViewModel-Tests einschließlich echter Modellinferenz bestanden nach der Korrektur. Android Lint, Debug-APK, Android-Test-APK und Prüfung des permanenten Signaturzertifikats waren ebenfalls erfolgreich. Der letzte Live-Check am 18.10.2026 bestätigte erfolgreiche Antworten aller drei Quellen: GitHub 90 geladene/72 bestätigte Kandidaten, HN 6/3 und Foren 3/0. Diese Momentaufnahme ist kein Anspruch auf vollständige Plattformabdeckung. Oberflächenprüfungen decken die einzelnen Quellenphasen und Unicode-Eingaben zusätzlich auf dem Emulator ab.
