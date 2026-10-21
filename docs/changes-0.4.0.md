# NetScout 0.4.0

Android-Version: `0.4.0`. Release-Versionscode: `10` (zuvor `0.3.8` / `9`).

## Lokales Modell

NetScout verwendet jetzt [Horizon-Labs/multilingual-reranker-small v1.2](https://huggingface.co/Horizon-Labs/multilingual-reranker-small/tree/d35a2781eeb3de1d971ad328165277bdf5712e35), einen mehrsprachigen Cross-Encoder mit 141 Millionen Parametern auf Basis von mmBERT-small. Die Variante **Small** ersetzt `cross-encoder/mmarco-mMiniLMv2-L12-H384-v1`. Die Auswahl erfolgt beim Bau; installierte Geräte laden keine Modellgewichte nach und benötigen keinen KI-Dienst.

Die unveränderliche Modellrevision lautet `d35a2781eeb3de1d971ad328165277bdf5712e35`. Das Upstream-Modell verwendet quantisierte Embeddings und Float32-Rechengewichte. ONNX Runtime bleibt auf `1.23.2`; es kommt keine zusätzliche Laufzeitbibliothek hinzu. Die Apache-2.0-Modelllizenz und die MIT-Lizenz der Laufzeit samt Hinweisen bleiben in der APK enthalten. `NOTICE`, die aktive Modelldokumentation und die Architekturübersicht benennen das neue Modell.

| Bestandteil | Bytes | SHA-256 |
| --- | ---: | --- |
| `onnx/model_quantized.onnx` | 268.407.694 | `f913154682468bfe92d6a7e02ed4e994e548c112fddc74452b573b17c949046e` |
| Originales `tokenizer.json` | 34.363.439 | `7925f638b5444897efe3b4608eed56c1652f0ed39c2662c8302de1bc264c6088` |
| Kompiliertes `tokenizer.bin` (`NSBP1`) | 9.462.546 | `cb744a8fb04393ba0d46dc8e51fc0118d1b879439a6720e57551a5cb412f1c95` |

Diese Werte sind in `relevance-model.lock.json` festgelegt. Eine bewegliche Modellrevision oder die Base-Variante wird nicht verwendet.

## Tokenisierung und Bewertung

`BpeTokenizer` ersetzt den bisherigen XLM-R-Unigram-Tokenizer. Das Python-Vorbereitungsskript kompiliert die Originaltabellen ausschließlich mit der Standardbibliothek: Alphabet, UTF-8-Byte-Fallback, zusätzliche Tokens und Merge-Paare samt Rang und Ziel-ID. Primitive sortierte Arrays vermeiden Hunderttausende zusätzlicher Merge-Objekte im Android-Heap. Eine veränderte Konfiguration oder abweichende kompilierte Prüfsumme bricht den Bau ab.

Die Modell-Eingabe lautet `<bos> Anfrage <eos> Passage <eos>` mit IDs `2 … 1 … 1`. Rechts angehängtes Padding verwendet ID `0` und Attention-Maske `0`. Die Begrenzung bleibt bei 384 Tokens pro Paar, einschließlich höchstens 96 Anfrage-Tokens und drei Spezialtokens. ASCII-Leerzeichen werden gemäß Original zu `▁`; Zeilenumbrüche, Tabs, zusätzliche Tokens, kombinierte Unicode-Zeichen und unbekannte Zeichen werden entsprechend dem neuen Original-Tokenizer verarbeitet.

Das Modell liefert einen rohen Relevanzwert pro Paar. Der Ausschlusswert `0` und der zusätzliche Zusammenhangswert `−5` bleiben erhalten und werden mit den bisherigen festen Suchregressionen geprüft. Sie sind keine kalibrierten Wahrscheinlichkeiten. Originalbelege, höchstens vier Passagen je Kandidat, Vierer-Batches und die zusätzliche Zusammenhangsprüfung bleiben Bestandteil der Bewertung. Die bisherige optionale Begriffserweiterung, alle sechs Suchquellen, Datumssortierung, Favoriten und Sprachwahl bleiben erhalten.

## GitHub und F-Droid

App-Konfiguration, neue F-Droid-Rezeptur und `CurrentVersion` verwenden `0.4.0` / `10`. Die Rezeptur ist auf den geprüften Implementierungscommit `51ebd43b61a2d8e3a5b78a2e74cb8b90920e4065` festgesetzt. Die historische Rezeptur für `0.3.8` bleibt auf ihrem bisherigen Commit und Modell-Hash festgesetzt. Die neue Scanner-Ausnahme gilt ausschließlich für die neuen freien Modellgewichte im Cache. Der F-Droid-Prüflauf kontrolliert zusätzlich, dass aktuelle Versionsfelder, Modell-Hash und Offline-Gradle-Einstellungen übereinstimmen, und baut gezielt den neuesten Versionscode, ohne ältere Versionen erneut zu bauen.

`prebuild` lädt und prüft die beiden Originaldateien, bevor Gradle mit `netscout.offlineModel=true` die Assets erzeugt. Fehlende oder beschädigte Dateien werden im Offline-Bau nicht nachgeladen. Die fertige APK enthält auch `MODEL.lock.json`. Die Release-Prüfung kontrolliert Modell- und Tokenizer-Prüfsummen, Modellidentität, Dokumentation, Lizenzhinweise, Versionsangaben und den fehlenden Release-Signierer. Der erhöhte CI-Versionscode gilt weiterhin ausschließlich für Debug-Builds. Der permanente Debug-Schlüssel bleibt unverändert.

GitHub Actions behält seine drei Jobs für JVM-Tests/Lint/Debug-APK, Android-Emulator und F-Droid-Quellscan/Release-APK bei. Die Python-Verträge zur Modellvorbereitung laufen zusätzlich im JVM-/Debug-Job. Der Cache-Schlüssel hängt weiterhin vom gesamten Modell-Lock ab und trennt damit die alten und neuen Downloads automatisch.

Die Arbeiten erfolgen im Branch `feat/0.4.0-horizon-small` unter der vorhandenen GitHub- und Commit-Identität des Repository-Inhabers. Sämtliche README-Dateien bleiben unverändert. Historische Versionsberichte beschreiben weiterhin ihren damaligen Stand.

## Verifikation

Am 2026-10-21 lokal erfolgreich geprüft, mit JDK 17, Gradle 8.11.1, Android-Plattform 35 und Build-Tools 35.0.0:

- **184 JVM-Tests**, ohne Fehler oder übersprungene Tests: 59 Suchkern, 81 Suchdienste/Textaufbereitung, 15 Relevanz/Tokenizer und 29 App-Tests. Das echte neue Modell besteht auch die bisherigen Tests für mehrere Sprachen, optionale Begriffserweiterung und Zusammenhang zwischen Originalpassagen. Ein neuer Padding-Test vergleicht Einzelbewertung und Batches mit unterschiedlich langen Passagen.
- **Sechs Python-Tests** für Vorbereitung ohne Netzwerk, fehlende und beschädigte Originaldateien einschließlich unveränderter Dateigröße, zusätzliche Tokens, Byte-Fallback, Merge-Ränge, Ablehnung inkompatibler Konfigurationen und abweichende kompilierte Prüfsummen.
- **17 feste Tokenizer-Referenzvektoren** aus dem originalen JSON mit Hugging Face `tokenizers==0.22.1`, einschließlich Deutsch, Englisch, Chinesisch, Arabisch, Hindi, Zeilenumbrüchen, Tabs, kombinierter Unicode-Schreibweise, Spezialtokens und unbekannter Zeichen. Zusätzlich wurden lokal **556 Fälle ohne Abweichung** verglichen: alle 249 zusätzlichen Tokens mit Text davor/danach, sieben Whitespace-/Masken-Kombinationen und 300 deterministisch erzeugte Mischtexte mit Seed `400`. Python-Referenzpakete werden nur für diesen unabhängigen Vergleich verwendet; der Bau benötigt sie nicht.
- Der [feste Suchkorpus](search-quality/0.4.0/horizon-small.json) erreicht weiterhin **24/24 korrekte Textentscheidungen** (17 relevante, sieben irrelevante Beispiele), **8/8 URL-Fälle** und **24/24 Zuverlässigkeitsversuche**. Die bisherigen Eingaben, Labels und Relevanzschwellen sind unverändert. Das ist eine kontrollierte Regressionsprüfung ohne Live-Suchanfragen und keine allgemeine Qualitätsmessung für das offene Web.
- Debug- und Release-Lint, Debug-, unsigned Release- und Android-Test-APK. Der abschließende Gradle-Durchlauf lief mit `--offline -Pnetscout.offlineModel=true`. Die lokale Debug-APK umfasst 318.627.136 Bytes; die lokale unsigned Release-APK 311.940.222 Bytes. Größen können sich durch Build-Metadaten geringfügig unterscheiden.
- Release-Inhalts-/Versionsprüfung und unveränderter permanenter Debug-Signierer; `fdroid lint` und unveränderte Formatierung nach `fdroid rewritemeta`.
- Vollständiger isolierter F-Droid-Durchlauf mit `fdroidserver==2.4.5` für den oben festgesetzten Implementierungscommit: Quellscan, Release-Lint, Modellvorbereitung aus dem geprüften Cache, Release-Bau und APK-Scan. Die erzeugte unsigned APK wird zusätzlich mit dem Release-Prüfskript kontrolliert. Dies ist ein Bau außerhalb der offiziellen F-Droid-VM und keine Zusage einer Aufnahme in das offizielle Verzeichnis.

Android-Integrationstests prüfen zusätzlich den verpackten Horizon-Tokenizer, die Modellidentität und echte Inferenz über den produktiven Asset-Loader. Der vollständige GitHub-Status mit Emulator- und F-Droid-Job ist im [Pull Request #1](https://github.com/Mcpasi/NETSCOUT/pull/1) nachvollziehbar.

Reproduktion im Repository-Stamm, bei vorhandenem JDK/SDK und für `--offline` bereits gefüllten Abhängigkeits- und Modell-Caches:

```sh
python3 -m unittest discover -s scripts/tests -v
./gradlew :core:search:test :data:search:test :data:relevance:test :app:testDebugUnitTest \
  :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease \
  :app:assembleDebugAndroidTest --offline -Pnetscout.offlineModel=true
./gradlew :data:search:checkSearchQuality \
  -PsearchQualityOutput="$PWD/docs/search-quality/0.4.0/horizon-small.json" \
  -PsearchQualityLabel=horizon-small-0.4.0
python3 scripts/verify-debug-signature.py app/build/outputs/apk/debug/app-debug.apk
python3 scripts/verify-release-apk.py app/build/outputs/apk/release/app-release-unsigned.apk
# Zusätzliche Voraussetzung: fdroidserver 2.4.5 und Gradle 8.11.1 auf PATH.
python3 scripts/check-fdroid-build.py --work-dir /tmp/netscout-fdroid-040
python3 scripts/verify-release-apk.py /tmp/netscout-fdroid-040/unsigned/netscout-release-unsigned.apk
# Mit verbundenem Emulator/Gerät:
bash scripts/run-device-tests.sh
```

## Größe und Grenzen

Beim ersten Bau werden etwa 303 MB Originaldateien geladen. Die neuen Gewichte sind etwa 150 MB größer als die bisherigen 119 MB; Modell und kompilierter Tokenizer belegen zusammen rund 278 MB vor APK-Kompression. APK-Größe, Speicherbedarf und erste Modellinitialisierung fallen entsprechend stärker ins Gewicht. Das Modell ist neuer, aber die geprüften festen Beispiele belegen keine allgemeine Verbesserung der Suchqualität oder der Laufzeit auf allen Android-Geräten.

Suchdienste müssen weiterhin passende Kandidaten und genügend Originaltext liefern. Fehlende Indexabdeckung, mehrdeutige Begriffe, begrenzte Textfenster und Modellfehler bleiben mögliche Ursachen für fehlende oder falsche Treffer. Die neue Modellgeneration umgeht diese Grenzen nicht.
