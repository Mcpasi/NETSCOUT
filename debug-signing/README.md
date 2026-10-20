# Permanent development key

`netscout-debug.jks` is intentionally versioned so local and GitHub Actions debug APKs always use the same certificate. Alias: `androiddebugkey`; store and key password: `android`. This is solely a public **development identity**, not a production key.

Do not regenerate or replace it: Android accepts updates only with the same application ID/certificate and a `versionCode` that is not lower. The application ID is `de.netscout.app`; Actions sets `versionCode = 1000 + github.run_number`. When updating a CI installation locally, specify the same or a higher code:

```sh
./gradlew :app:assembleDebug -Pnetscout.versionCode=2000
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`certificate.sha256` contains the DER certificate's SHA-256 fingerprint. CI checks the finished APK with `scripts/verify-debug-signature.py`. Back up the keystore file and fingerprint together. Use a separate release key and signature for production distribution; F-Droid handles signing for its own builds, as described in the [F-Droid notes](../fdroid/README.md).
