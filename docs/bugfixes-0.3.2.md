# Bugfixes 0.3.2

Ausgangsstand: `b6bc3e45abdf0e10bc4644998c5cec9aa41b7a23` (0.3.1), geprüft am 19.10.2026.
Vor jeder Produktionsänderung wurden die neuen Regressionstests gegen diesen Stand ausgeführt: **5 Tests, davon 4 fehlgeschlagen**. Nach den Korrekturen bestanden dieselben fünf Tests mit unveränderten Eingaben. Alle HTTP-Antworten stammen von lokalem MockWebServer; echte Suchdienste und Zugangsdaten sind nicht erforderlich.

## 1. HTTP 503 ignoriert eine angeforderte Pause

Reproduktion: Der Server antwortet mit `503 Service Unavailable` und `Retry-After: 120`; die nächste bereitgestellte Antwort ist HTTP 200. Obwohl der Server zwei Minuten Pause verlangt, wiederholt der Client die Anfrage nach 350 Millisekunden und liefert bereits die zweite Antwort. Dasselbe passiert bei `Retry-After: Thu, 01 Jan 2026 00:02:00 GMT`, wenn die injizierte Uhr auf `2026-01-01T00:00:00Z` steht.

Korrektur in `JsonHttpClient`: HTTP 503 mit `Retry-After` verwendet dieselbe Host-Pause wie eine Rate-Limit-Antwort. Sekunden und HTTP-Datum werden ausgewertet; der Status `RATE_LIMITED` informiert den Aufrufer über die Pause. Eine weitere Anfrage vor Ablauf erreicht den Server nicht. Genau zum Ablauf darf wieder abgefragt werden. HTTP 503 ohne Pausenanforderung wird weiterhin einmal wiederholt.

## 2. Ungültige JSON-Treffer werden zu leerem Erfolg

Reproduktion: Die HN-API liefert `{"hits":[null,"schema-changed",42,[]],"nbPages":1}`. `requireRows` prüft bisher nur, ob `hits` ein Array ist; `rows` entfernt anschließend alle Elemente, die keine JSON-Objekte sind. Der Provider meldet dadurch eine erfolgreiche leere Seite. `IndexFallbackProvider` ruft den verfügbaren Ausweichprovider kein einziges Mal auf. Eine SearXNG-Antwort mit denselben Elementen in `results` wird ebenfalls fälschlich als erfolgreicher leerer Endpunkt gemeldet.

Korrektur in `requireRows`: Eine Trefferliste muss ein Array aus JSON-Objekten sein. Strukturell ungültige Listen melden `SourceStatus.ERROR`; dadurch kann der vorhandene Ausweichweg greifen. Ist der letzte Provider betroffen, bleibt der Fehler sichtbar. Ein korrektes leeres Array `[]` bleibt erfolgreich und verursacht keinen unnötigen Ausweichaufruf.

## Nachweis und Wiederholung

| Regressionstest | Vorher | Nachher |
| --- | --- | --- |
| `JsonHttpClientTest.service unavailable with Retry-After pauses instead of retrying immediately` | Fehlgeschlagen: erneute HTTP-Anfrage statt Pause | Bestanden; inklusive Sperre und Ablaufprüfung |
| `JsonHttpClientTest.service unavailable also honors an HTTP date Retry-After` | Fehlgeschlagen: HTTP-Datum ignoriert | Bestanden |
| `ProvidersTest.non-object search rows trigger the index fallback instead of a false empty success` | Fehlgeschlagen: 0 statt 1 Ausweichaufruf | Bestanden; Ausweichtreffer erhalten |
| `ProvidersTest.unreadable final index response reports an error` | Fehlgeschlagen: Erfolg statt Fehler | Bestanden |
| `ProvidersTest.a valid empty JSON result list remains successful without fallback` | Bestanden | Bestanden |

Die gezielte Prüfung:

```sh
./gradlew :data:search:test \
  --tests '*JsonHttpClientTest.service unavailable*' \
  --tests '*ProvidersTest.non-object*' \
  --tests '*ProvidersTest.unreadable final*' \
  --tests '*ProvidersTest.a valid empty*'
```

Für die Vorher-Prüfung mit denselben Tests in einer getrennten Arbeitskopie ausschließlich die beiden Produktionsdateien zurücksetzen:

```sh
git worktree add --detach ../NETSCOUT-0.3.2-before HEAD
git -C ../NETSCOUT-0.3.2-before restore \
  --source=b6bc3e45abdf0e10bc4644998c5cec9aa41b7a23 --worktree -- \
  data/search/src/main/kotlin/de/netscout/data/JsonHttpClient.kt \
  data/search/src/main/kotlin/de/netscout/data/JsonHelpers.kt
cd ../NETSCOUT-0.3.2-before
# Hier die gezielte Prüfung oben ausführen: erwartet sind 4 fehlgeschlagene Tests.
```

## Version

Patchversion **0.3.1 → 0.3.2**, lokaler Standard-Versionscode **2 → 3**. Die bestehende Vergabe des Versionscodes in CI bleibt erhalten.

## Abschlussprüfung

Erfolgreich ausgeführt:

```sh
./gradlew :core:search:test :data:search:test :data:relevance:test \
  :app:testDebugUnitTest :app:lintDebug :app:assembleDebug \
  :app:assembleDebugAndroidTest
python3 scripts/verify-debug-signature.py app/build/outputs/apk/debug/app-debug.apk
```

**131 Tests**, keine Fehler und keine übersprungenen Tests: Core 41, Suchadapter 71, Relevanzmodell 10, App/ViewModel 9. Android Lint sowie App- und Test-APK-Build erfolgreich. Die Paketmetadaten bestätigen `versionName=0.3.2`, `versionCode=3`; das permanente Debug-Zertifikat wurde verifiziert.

Android-Gerätetests wurden lokal nicht ausgeführt; sie sind Bestandteil des bestehenden CI-Workflows.
