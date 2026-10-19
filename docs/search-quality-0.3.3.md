# Suchkorrektur 0.3.3: Umgebrochene Absätze

Ausgangsstand: `72a06791970d107f70c40bffbea1aa67453b2bfe` (0.3.2), geprüft am 19.10.2026.
Die neuen Tests und der erweiterte Prüfkorpus wurden zuerst gegen unveränderten Produktionscode ausgeführt. Danach wurden dieselben Eingaben mit der Korrektur geprüft. Alle Suchantworten stammen von lokalem MockWebServer; Zugangsdaten und öffentliche Suchdienste sind für die Messung nicht erforderlich. Das unveränderte, per Prüfsumme festgelegte ONNX-Relevanzmodell wird tatsächlich ausgeführt.

## Reproduzierter Fehler

Diese Markdown-Zeilen bilden **einen** Absatz; seine Zeilenumbrüche werden beim Rendern als Leerzeichen behandelt:

```markdown
A native Codex
mobile client for
Android.
```

Dasselbe gilt für Zeilenumbrüche innerhalb eines HTML-`<p>`-Elements. Bisher übernahm `fullText` diese Formatierungsumbrüche als harte Zeilengrenzen. Die Textstellenprüfung zerlegte dadurch den zusammenhängenden Satz. Die drei neuen positiven Provider-Fixtures für GitHub-Issues, Reddit und Hacker News wurden sowohl von der Kontextprüfung als auch vom echten Relevanzmodell verworfen.

Ein zusätzlicher negativer Fall besteht aus drei unabhängigen Markdown-Absätzen für Codex-Webintegration, Mobile-Safari-Layout und Android-Berechtigungen. Jeder Absatz ist im Quelltext nochmals umgebrochen. Diese künstlichen Teilstücke führten bisher außerdem zu einem falsch akzeptierten Modelltreffer. Nach der Korrektur erhält die bestehende Kontinuitätsprüfung die vollständigen Absätze und verwirft den Fall.

## Korrektur und Funktionserhalt

`JsonHelpers.fullText` vereinheitlicht HTML-Formatierungsleerraum in expliziten Textblöcken wie `p`, `li` und `div`, bevor die bestehenden Absatzgrenzen eingefügt werden. Markdown nutzt denselben Pfad nach dem CommonMark-Rendering. Wortlaut, Satzzeichen und Reihenfolge bleiben erhalten.

Echte Absätze, Listen, `<br>`, harte Markdown-Zeilenumbrüche und vorformatierte Codezeilen bleiben getrennt. Unmarkierte Klartext-Snippets behalten ihre bisherigen Zeilengrenzen. Fünf neue Regressionstests prüfen diese Eigenschaften. Quellen, Suchabfragen, Datumsfilter, Pagination, Ausweichwege, Rankingverfahren, Modell und Modellschwellen bleiben erhalten; keine Funktion wurde entfernt.

## Gemessene Verbesserung

Der bisherige Korpus mit 20 Textfällen bleibt vollständig enthalten. Hinzu kommen drei passende umgebrochene Absätze und ein unpassender Beitrag mit getrennten Abschnitten: insgesamt **24 Fälle**, davon 17 relevant und 7 irrelevant.

| Messung im festen Korpus | Vorher | Nachher |
| --- | ---: | ---: |
| Korrekt bewertete Textfälle | 20/24 | **24/24** |
| Richtig akzeptierte Treffer | 14 | **17** |
| Verlorene passende Treffer | 3 | **0** |
| Falsch akzeptierte Treffer | 1 | **0** |
| Precision | 93,33 % | **100 %** |
| Recall | 82,35 % | **100 %** |
| F1 | 0,875 | **1,000** |
| URL-Identität | 8/8 | 8/8 |
| Ausweich-/Fehlerverhalten, drei Durchläufe | 24/24 | 24/24 |
| Gezielte Regressionstests | 24/30 | **30/30** |

Vor der Änderung schlugen zwei Textaufbereitungstests, drei Provider-Fälle und der Modelltest fehl. Nachher bestehen alle 30 mit identischen Testquellen und Fixtures. Alle bisherigen 20 Textfälle bleiben korrekt. Dies ist ein diagnostischer Regressionskorpus, keine Schätzung der Trefferqualität im gesamten Web und keine Geschwindigkeitsmessung.

Maschinenlesbare Originalergebnisse:

- [Qualität vorher](search-quality/0.3.3/before.json) und [nachher](search-quality/0.3.3/after.json)
- [Tests vorher](search-quality/0.3.3/before-tests.json) und [nachher](search-quality/0.3.3/after-tests.json)

## Wiederholen

Mit der im README beschriebenen JDK-17-/Android-SDK-Umgebung im Repository-Wurzelverzeichnis:

```sh
./gradlew :data:search:test \
  --tests '*HtmlParagraphRegressionTest' \
  --tests '*SourceTextRegressionTest' \
  --tests '*SearchQualityModelTest' \
  :data:search:checkSearchQuality \
  -PsearchQualityOutput="$PWD/data/search/build/reports/search-quality-0.3.3.json" \
  -PsearchQualityLabel=0.3.3-after
```

Für die Vorher-Prüfung bleiben die neuen Tests unverändert. In einer getrennten Arbeitskopie wird ausschließlich die geänderte Produktionsdatei auf den Ausgangsstand gesetzt:

```sh
git worktree add --detach ../NETSCOUT-0.3.3-before HEAD
git -C ../NETSCOUT-0.3.3-before restore \
  --source=72a06791970d107f70c40bffbea1aa67453b2bfe --worktree -- \
  data/search/src/main/kotlin/de/netscout/data/JsonHelpers.kt
cd ../NETSCOUT-0.3.3-before
./gradlew :data:search:test \
  --tests '*HtmlParagraphRegressionTest' \
  --tests '*SourceTextRegressionTest' \
  --tests '*SearchQualityModelTest' \
  :data:search:checkSearchQuality --continue \
  -PsearchQualityOutput="$PWD/data/search/build/reports/search-quality-0.3.3-before.json" \
  -PsearchQualityLabel=72a0679-before
```

Erwartet: **6 fehlgeschlagene Tests von 30**, ein Build-Exitcode ungleich null und der trotzdem erzeugte Qualitätsbericht mit **20/24** korrekten Textfällen. `--continue` lässt die Messung trotz der erwarteten Testfehler laufen. Modell und Tokenizer werden beim ersten Build heruntergeladen und anhand der eingecheckten Prüfsummen geprüft; spätere Läufe verwenden den lokalen Cache.

## Version

Patchversion **0.3.2 → 0.3.3**, lokaler Standard-Versionscode **3 → 4**. Die bestehende fortlaufende Vergabe des Versionscodes in CI und das permanente Debug-Zertifikat bleiben erhalten.

## Abschlussprüfung

Erfolgreich ausgeführt:

```sh
./gradlew :core:search:test :data:search:test :data:relevance:test \
  :app:testDebugUnitTest :app:lintDebug :app:assembleDebug \
  :app:assembleDebugAndroidTest
python3 scripts/verify-debug-signature.py app/build/outputs/apk/debug/app-debug.apk
```

**140 Tests bestanden**, keine Fehler und keine übersprungenen Tests: Core 41, Suchadapter 80, Relevanzmodell 10, App/ViewModel 9. Android Lint sowie App- und Test-APK-Build erfolgreich. Die erzeugte App-APK bestätigt `versionName=0.3.3`, `versionCode=4`; das permanente Debug-Zertifikat wurde verifiziert.

Android-Gerätetests wurden lokal nicht ausgeführt; der bestehende CI-Workflow führt sie separat auf einem Emulator aus.
