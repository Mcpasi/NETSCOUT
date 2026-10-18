# Fester Entwicklungsschlüssel

`netscout-debug.jks` wird bewusst versioniert, damit lokale und GitHub-Actions-Debug-APKs immer mit demselben Zertifikat signiert sind. Alias: `androiddebugkey`, Store- und Key-Passwort: `android`. Er ist ausschließlich eine öffentliche **Entwicklungsidentität**, kein Produktionsschlüssel.

Nicht neu generieren oder ersetzen: Android akzeptiert Updates nur bei gleichem Application-ID/Zertifikat und nicht kleinerem `versionCode`. Application-ID ist `de.netscout.app`; Actions setzt `versionCode = 1000 + github.run_number`. Bei lokalen Updates einer CI-Installation denselben oder einen größeren Code angeben:

```sh
./gradlew :app:assembleDebug -Pnetscout.versionCode=2000
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`certificate.sha256` enthält den SHA-256-Fingerabdruck des DER-Zertifikats. CI überprüft die fertige APK mit `scripts/verify-debug-signature.py`. Die Keystore-Datei und den Fingerabdruck zusammen sichern. Für eine spätere Veröffentlichung einen separaten Release-Schlüssel und eine separate Release-Signatur einführen.
