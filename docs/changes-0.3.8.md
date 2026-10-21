# NetScout 0.3.8

Android-Version: `0.3.8`. Release-Versionscode: `9` (zuvor `0.3.7` / `8`).

## Optionale Begriffserweiterung

Das ONNX-Modell kann nur Treffer bewerten, die ein Suchdienst liefert. Der neue Schalter **Suchbegriffe erweitern (optional)** ergänzt deshalb die Originalsuche um höchstens zwei zusätzliche Synonymabfragen pro ausgewählter Quelle. Er ist beim App-Start ausgeschaltet und gilt wie die übrigen Suchfilter für die nächste Suche. Eine Vorschau zeigt die tatsächlich verwendeten Zusatzabfragen; für unbekannte oder ausschließlich zitierte Begriffe bleibt es bei der Originalabfrage.

Beispiel: `Android app` sucht zusätzlich nach `"Android" "application"` und `"Android" "anwendung"`. So können relevante Anwendungen das Modell erreichen, die bei der ursprünglichen Wortwahl fehlen. Sämtliche ursprünglichen Begriffe müssen weiterhin als Begriff oder zugelassene Variante im zusammenhängenden Beleg vorkommen. Das Modell bewertet diesen Originaltext gegen die ursprüngliche Nutzeranfrage; seine Relevanzschwelle und die Prüfung zusammengehöriger Textstellen bleiben erhalten.

- Die vollständig lokale Wortliste enthält diese Gruppen: `app / application / anwendung`, `mobile / mobil`, `smartphone / handy`, `async / asynchronous / asynchron`, `database / datenbank`, `encryption / verschlüsselung`, `authentication / authentifizierung`, `backup / datensicherung`, `privacy / datenschutz`, `ai / artificial intelligence / ki`, `self-hosted / selbstgehostet` und `open-source / opensource`.
- Jede Zusatzabfrage ersetzt genau einen unzitierten Begriff. Zuerst wird je Begriff die erste Alternative in Eingabereihenfolge berücksichtigt, danach gegebenenfalls die zweite, bis das Gesamtbudget von zwei Zusatzabfragen erreicht ist. Es entsteht kein kombinatorisches Produkt. Die Belegprüfung erlaubt nur Varianten, die in diesen tatsächlich erzeugten Abfragen vorkommen.
- Anführungszeichen schützen sowohl einzelne Wörter als auch Phrasen, einschließlich deutscher und typografischer Anführungszeichen. Unbekannte Bezeichnungen werden nicht umgeschrieben. Wortgrenzen, Unicode-Leerzeichen und die bisherige Begrenzung auf 256 Zeichen bleiben berücksichtigt. Zu lange Zusatzabfragen entfallen.
- Deutsch und Englisch bieten denselben Schalter, dieselbe Vorschau und übersetzte Hinweise. Der Sprachwechsel verändert weder die Suchbegriffe noch den Schalterzustand und löst keine Suche aus.
- Das Umschalten erhält vorhandene Ergebnisse und markiert die Filter als geändert. Nachladen ist erst wieder möglich, wenn eine Suche mit den neuen Filtern gestartet wurde.

## Modularer Suchablauf und erhaltene Funktionen

`QueryExpansion` im Modul `core/search` erstellt den begrenzten Abfrageplan und die zugehörigen Belegvarianten. `CandidateRetrieval` führt den Abruf aus. `SearchEngine` übergibt die gesammelten Kandidaten anschließend an die bestehende Relevanzprüfung. Plattformadapter benötigen keine eigene Synonymlogik; alle sechs Quellen, eigene Zugänge, SearXNG und die bisherigen Direkt-/Index-Fallbacks verwenden denselben Ablauf.

Die Originalabfrage behält ihr bisheriges Zeitbudget. Zusatzabfragen laufen parallel mit jeweils höchstens vier Sekunden Abrufzeit, begrenzt durch das Quellenzeitbudget. Abbruch beendet alle laufenden Varianten. Erfolgreiche Seiten bleiben auch dann verfügbar, wenn andere Varianten fehlschlagen oder ihr Zeitlimit erreichen. Ein übersetzter Hinweis benennt die Zahl fehlgeschlagener Abfragen. Wenn alle Varianten scheitern, bleibt die Fehlerbehandlung mit Quellenstatus und bereinigter Meldung erhalten.

Jede Variante besitzt einen eigenen undurchsichtigen Provider-Cursor einschließlich Offset und etwaigem Index-Fallback-Präfix. Nachladen setzt ausschließlich offene Varianten fort und wiederholt fehlgeschlagene Abfragen an ihrer bisherigen Position. Abgeschlossene Varianten werden nicht erneut angefragt. Ungültige zusammengesetzte Cursor werden abgewiesen. Der Cache unterscheidet aktivierte und deaktivierte Erweiterung und speichert weiterhin nur erfolgreich bewertete Seiten.

Identische Kandidaten werden vor der Modellbewertung anhand der kanonischen URL, des Titels, des Originaltexts und der Metadaten zusammengefasst. Verschiedene Originalpassagen oder Veröffentlichungsdaten derselben URL bleiben für die Bewertung verfügbar; die bestehende Ergebnissammlung übernimmt anschließend die URL-Deduplizierung. Datumsfilter, Sortierung, Quellenfortschritt, Kandidaten-/Trefferzahlen, Favoriten, Sprachwahl und verschlüsselte Zugangsdaten bleiben erhalten. Es kommen keine Bibliotheken, Cloud-Modelle, Konten oder Berechtigungen hinzu.

## Version, F-Droid und GitHub

Die aktive App-Konfiguration und die F-Droid-Felder `versionName`, `versionCode`, `CurrentVersion` und `CurrentVersionCode` verwenden übereinstimmend `0.3.8` / `9`. Die F-Droid-Rezeptur ist auf den vollständigen Implementierungscommit `93d9f4205d6a2ff2ff0112f46bfe3709d98dc224` festgesetzt; das annotierte Tag `v0.3.8` verweist auf denselben Stand. Historische Versionsdokumentation bleibt unverändert.

F-Droid baut weiterhin ohne GitHub-Variablen und ohne Signierschlüssel, prüft Modell-Downloads anhand ihrer Größe und SHA-256 und bereitet die Assets beim Gradle-Build offline vor. Die JDK-17-Voraussetzungen, Scanner-Ausnahme für die festgelegten freien Modellgewichte und die unsigned Release-APK bleiben erhalten. GitHub Actions führt weiterhin JVM-Tests, Lint, Debug-Build, Emulator-Tests und den vollständigen F-Droid-Build aus. Der erhöhte CI-Versionscode gilt weiterhin ausschließlich für Debug-Builds; der Release-Code bleibt `9`. Der permanente Debug-Signierschlüssel wird nicht geändert.

Die Änderungsdokumentation für diese Version steht ausschließlich in dieser Datei; `README.md` und `CHANGELOG.md` bleiben auf Wunsch unverändert.

## Verifikation

- 180 JVM-Tests: 59 im Suchkern, 81 für Suchdienste und Aufbereitung, 11 für die Relevanzbewertung und 29 für die App. Die neuen Tests prüfen Opt-in, geschützte Zitate, Abfragelimit und Länge, alle sechs Quellen, unabhängige Cursor, Wiederholungen, Zeitlimits, Abbruch, Cache-Trennung, Beleg-/Datumserhalt und Filterwechsel.
- Ein lokaler HTTP-Test mit dem echten Hacker-News-Adapter und dem echten ONNX-Modell bestätigt: Die unveränderte Abfrage liefert keinen Kandidaten; die Zusatzabfrage findet eine relevante Android-Anwendung. Ein iOS-Kandidat wird weiterhin ausgeschlossen. Dies belegt den Abruf-/Bewertungspfad unter kontrollierten Bedingungen und ist keine allgemeine Messung der Trefferabdeckung externer Suchdienste.
- Drei Python-Tests prüfen die unveränderte Offline-Modellvorbereitung.
- Android Lint für Debug und Release sowie Debug-, Release- und Android-Test-APK.
- Die APK-Prüfskripte bestätigen den unveränderten permanenten Debug-Signierer und die unsigned Release-APK mit Version `0.3.8`, Code `9`, festgelegtem Modell und Lizenzhinweisen. F-Droid-Metadaten bestehen `fdroid lint` und bleiben bei `fdroid rewritemeta` unverändert formatiert.
- Der isolierte F-Droid-Durchlauf mit `fdroidserver 2.4.5` besteht Quellscan, Release-Lint, Offline-Gradle-Build und APK-Scan für den festgesetzten Implementierungscommit. Auch die daraus erzeugte unsigned Release-APK besteht die Inhalts- und Versionsprüfung.
- Der neue Android-Oberflächentest prüft den standardmäßig ausgeschalteten Schalter, die erzeugten Abfragen und den Zustand beim Wechsel zwischen Deutsch und Englisch. Der bestehende Nachrichtentest prüft auch den neuen Teilfehlerhinweis in beiden Sprachen.

Die Erweiterung kann durch zusätzliche externe Anfragen mehr Kontingent, Laufzeit und lokale Modellarbeit benötigen. Die kleine Wortliste und das Abfragebudget erweitern die erreichbaren Kandidaten gezielt; sie garantieren keine vollständige Suche oder korrekte Interpretation mehrdeutiger Begriffe. Ausschalten stellt den bisherigen Abruf- und Bewertungsmodus wieder her.
