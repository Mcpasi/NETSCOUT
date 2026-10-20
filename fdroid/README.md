# F-Droid-Vorbereitung

Abgleich am 20.10.2026 mit der offiziellen
[Inclusion Policy](https://f-droid.org/en/docs/Inclusion_Policy/), dem
[Quick Start Guide](https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/),
der [Build-Metadatenreferenz](https://f-droid.org/en/docs/Build_Metadata_Reference/)
und den [Anti-Features](https://f-droid.org/en/docs/Anti-Features/).

## Abgleich

| Bedingung | Umsetzung / Grenze |
| --- | --- |
| Öffentlich zugänglicher, vollständiger Quellcode | `https://github.com/Mcpasi/NETSCOUT`; muss für die Einreichung ohne Anmeldung klonbar sein |
| Freie Projektlizenz | Apache-2.0, Copyright 2026 Mcpasi; `LICENSE` und `NOTICE`, auch in der APK |
| Freie Bibliotheken / vertrauenswürdige Repositories | Maven Central, Google Maven und Gradle Plugin Portal; feste Versionen, kein Firebase/GMS/Tracking-SDK; siehe `third-party/README.md` |
| Freie Toolchain | OpenJDK 17, Gradle 8.11.1, Python 3.11+, Android SDK 35 / Build-Tools 35.0.0; Android SDK und frei lizenzierte Maven-Artefakte fallen unter die dokumentierten Ausnahmen |
| Vollständiger Build auf F-Droid | `metadata/de.netscout.app.yml`, `subdir: app`, `gradle: yes`; verwendet den von F-Droid bereitgestellten Gradle-Launcher |
| Vorbereitung vor dem Build | `prebuild` lädt die zwei festen Modell-Inputs und prüft Größe/SHA-256; der Gradle-Task erzeugt daraus offline die Assets |
| Keine verschleierten Binärabhängigkeiten | Einzige `scanignore`-Ausnahme ist die exakt bezeichnete Apache-2.0-Modelldatei; kein pauschales Ignorieren von Quell- oder Bibliotheksverzeichnissen |
| Signierung | Release bleibt unsigniert, nicht debuggable; F-Droid entfernt die Debug-Signierkonfiguration und signiert selbst |
| Versionsprüfung und Updates | Literale Release-Version im Android-Block; Tags `vX.Y.Z`; Debug-Laufnummern beeinflussen Release nicht |
| Eindeutige App-ID | `de.netscout.app` bleibt erhalten; keine fremde App wird geforkt. Die Kontrolle über `netscout.de` ist damit nicht nachgewiesen; eine eigene Domain wird empfohlen, ist laut Policy aber keine allgemeine Pflicht |
| Anti-Features | `NonFreeNet` erklärt die proprietären Suchdienste. Eine eigene SearXNG-Instanz ist optional; Index-Fallbacks können weiterhin proprietäre Dienste verwenden |
| Keine nachgeladenen ausführbaren Dateien | Modell und Runtime sind bereits in der APK; keine Selbstaktualisierung oder Plug-in-Downloads |
| Lokalisierte Store-Texte | `fastlane/metadata/android/de-DE` und `en-US`, inklusive Changelog pro Versionscode; die Oberfläche ist deutsch |
| Pflege, Funktion und Reife | Suche, Relevanzbewertung und lokale Favoriten mit automatisierten Tests. Die Policy nennt mindestens drei Monate aktive Entwicklung oder eine ihrer Ausnahmen, etwa nachgewiesene Nutzer; diese Reife-/Nutzungsprüfung bleibt bei F-Droid |

Das Modell und der Tokenizer werden aus der festgelegten Hugging-Face-Revision
bezogen. Die Apache-2.0-Lizenz und Herkunft sind dokumentiert. ONNX Runtime 1.23.2
wird als MIT-lizenzierte Maven-Central-AAR einschließlich nativer Bibliotheken
bezogen. Dieses Rezept baut die Runtime und die Modellgewichte nicht neu.
F-Droid entscheidet bei der Einreichung über ihre Akzeptanz und die abschließende
Kennzeichnung. Ein bestandener Scanner ersetzt diese manuelle Prüfung nicht.

## Derselbe Build in beiden Umgebungen

Auf F-Droids Buildserver läuft das Rezept mit dessen Gradle-Launcher, OpenJDK und
SDK. `prebuild` benötigt Zugriff auf die feste Hugging-Face-Revision einschließlich
deren Download-Weiterleitungen. Der eigentliche Build benötigt die üblichen
Gradle-/Maven-Downloads, aber keine Modell-Downloads. Ein vollständig netzloser
Gradle-Build setzt zusätzlich bereits gecachte Maven-/Gradle-Abhängigkeiten voraus.

GitHub Actions installiert dieselben Toolversionen und `fdroidserver==2.4.5`.
`scripts/check-fdroid-build.py` lintet das Einreichungsrezept und führt dann
`fdroid build --test --stop --no-tarball --scan-binary --refresh-scanner` in einem neuen Arbeitsordner
aus. Nur für diesen Test werden Repo und Commit durch das lokale Git-Repo und
dessen unveränderlichen `HEAD` ersetzt. Damit prüft auch ein Pull Request seine
eigenen Änderungen. Das `sudo`-Setup entfällt dort, weil CI JDK/Python bereits
installiert. Alle weiteren Rezeptschritte bleiben erhalten, einschließlich
F-Droids Entfernen des Wrappers und der Signierung, Quellscan und APK-Scan.

Lokal mit OpenJDK 17, Android SDK, Python 3.11+, `fdroidserver==2.4.5` und
**Gradle 8.11.1 im PATH**, vom sauberen, committeten Stand aus:

```sh
python3 -m unittest discover -s scripts/tests -v
python3 scripts/check-fdroid-build.py --work-dir /tmp/netscout-fdroid
python3 scripts/verify-release-apk.py /tmp/netscout-fdroid/unsigned/netscout-release-unsigned.apk
```

Das Skript akzeptiert nur einen leeren Arbeitsordner. Es baut ausschließlich
committierte Änderungen. Die Unit-/Gerätetests laufen zusätzlich in den bisherigen
GitHub-Jobs; die Release-Prüfung kontrolliert App-ID, feste Version, fehlende
Signatur, Debuggable-Flag, das tatsächlich verpackte Modell und die Lizenzdateien.

## Einreichung und neue Versionen

1. Den Quellcode öffentlich erreichbar halten. Für jedes Release `versionName`
   und `versionCode` in `app/build.gradle.kts` erhöhen, lokalisierte Changelogs
   ergänzen und den geprüften Release-Commit mit `vX.Y.Z` taggen.
2. Dieses Rezept nach `fdroiddata/metadata/de.netscout.app.yml` kopieren und für
   `Builds.commit` die vollständige Commit-ID des Tags verwenden. Bei einer
   neuen Version die Versionsfelder und bei geändertem Modell dessen spezifische
   `scanignore`-Prüfsumme aktualisieren. Keine Debug-APK als Release referenzieren.
3. In `fdroiddata` `fdroid lint de.netscout.app` und
   `fdroid build --server --test de.netscout.app` ausführen. Der GitHub-Test
   verwendet den echten F-Droid-Prozess, jedoch keine offizielle Server-VM.
4. Echte Screenshots der aktuellen App ergänzen und einen Merge Request bei
   [fdroiddata](https://gitlab.com/fdroid/fdroiddata) einreichen. Modellherkunft,
   enge Scan-Ausnahme und `NonFreeNet` im Antrag erläutern; Reife oder eine
   zutreffende Policy-Ausnahme belegen.

Dieses Repository enthält die technische Vorbereitung. Ein F-Droid-Merge-Request,
eine offizielle Buildserver-Ausführung und die Aufnahmeentscheidung sind eigene
Schritte und werden durch das Veröffentlichen dieses Codes nicht vorweggenommen.
