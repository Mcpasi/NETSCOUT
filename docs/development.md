# Development and verification

For installation and usage, see the [README](../README.md). Release history is in the [changelog](../CHANGELOG.md).

## Build locally

Requirements: JDK 17, Python 3.11+, and an Android SDK with platform 35 and Build-Tools 35.0.0. Open the project in Android Studio, set `ANDROID_HOME`, or create a local `local.properties` with `sdk.dir=/path/to/sdk`. Gradle 8.11.1 is included as a wrapper with SHA-256 verification.

The first build downloads approximately 136 MB of model/tokenizer files from a fixed Hugging Face revision and checks their SHA-256. Later builds use the local cache. No additional Python packages are required for model preparation.

```sh
./gradlew :core:search:test :data:search:test :data:relevance:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
python3 scripts/verify-debug-signature.py app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Device/UI tests require a running emulator or connected Android device:

```sh
./gradlew :app:connectedDebugAndroidTest
```

All debug builds use the checked-in [development key](../debug-signing/README.md). Keep it unchanged to preserve updates for test installations. Android also requires a version code at least as high as the installed one; the CI debug code increases with each workflow run.

## GitHub Actions

[Android tests & APKs](https://github.com/Mcpasi/NETSCOUT/actions/workflows/android.yml) runs unit tests, including real model inference and tokenizer reference vectors, Android Lint, a signed debug build, and emulator tests on API 29 in a separate job. A device test loads the packaged model through the production path. The workflow also supports manual `workflow_dispatch` runs. Test and lint reports are uploaded as artifacts.

Platform tests use local HTTP fixtures/fake providers. They consume no platform quotas and require no secrets. After emulator tests, CI also installs the actual APK and launches its launcher activity. A screen recording of this launch check is included in `android-ui-tests`.

## Release and F-Droid

The additional **F-Droid source scan & unsigned release APK** job runs the [build recipe](../fdroid/metadata/de.netscout.app.yml) with `fdroid build`. F-Droid removes the Gradle wrapper and debug signing configuration and scans sources and the APK for known non-free components. `prebuild` downloads model inputs and verifies their size/SHA-256. The Gradle build must prepare these assets offline. No GitHub environment variables, tokens, or signing keys are required.

The `netscout-fdroid-release-<run number>` artifact contains an **unsigned** release APK and its SHA-256. F-Droid handles production signing. The release version is fixed in source; `netscout.versionCode` affects debug builds only. See the [changelog](../CHANGELOG.md) for recorded version values.

```sh
python3 scripts/prepare-relevance-model.py --download-only --cache-dir .model-cache
./gradlew :app:lintRelease :app:assembleRelease \
  -Pnetscout.modelCache=.model-cache -Pnetscout.offlineModel=true
python3 scripts/verify-release-apk.py app/build/outputs/apk/release/app-release-unsigned.apk
```

A debug installation and an F-Droid-signed installation have different certificates. Switching requires uninstalling, which deletes local favorites and credentials. The debug signature remains unchanged for updates within existing test installations.

Policy mapping, the full verification command, and submission steps are in [fdroid/README.md](../fdroid/README.md). Preparation and a successful build do not establish acceptance into the official repository.

## Live search checks

An optional integration check sends all six sources through the same local model without credentials and reports request phases, service notices, candidate counts, and confirmed matches. It uses public services, so it does not run automatically in CI:

```sh
./gradlew :data:search:checkLiveSearch -PsearchKeywords=Codex,mobile,android
# Optionally request two real pages from web, Reddit, and video search only:
./gradlew :data:search:checkLiveSearch -PsearchKeywords=kotlin,android -PsearchSources=WEB,REDDIT,YOUTUBE -PsearchPages=2
```

Earlier reproduced failures are in [search-regressions.md](search-regressions.md). The fixed quality corpus covers text preparation with the real local model, URL deduplication, and fallback for unreadable responses. Repeat it without querying public search services:

```sh
./gradlew :data:search:checkSearchQuality
# Report: data/search/build/reports/search-quality.json
```

See the [0.3.1 quality report](search-quality-0.3.1.md) and [0.3.3 quality report](search-quality-0.3.3.md) for inputs, before/after results, and limits of those measurements.

## Modules and extensions

| Path | Responsibility |
| --- | --- |
| `app/` | Compose UI, ViewModel, Android Keystore, app configuration |
| `core/search/` | Models, validation, provider contract, parallel engine, ranking, deduplication |
| `data/search/` | HTTP transport, providers, fallback composition |
| `data/relevance/` | Local ONNX cross-encoder, tokenizer, model tests |

The three library modules are Kotlin/JVM modules. New platforms implement `SearchProvider`, return a `SourcePage` with results and an optional `SearchCursor`, and register in `ProviderFactory`. `SearchRelevance` separates assessment from platform adapters and permits alternative models. `SourceException` distinguishes missing setup, rate limits, and failures. New `SearchSource` values appear automatically in source selection. See [architecture.md](architecture.md) for details and future work.

For wider distribution, a dedicated backend and managed OAuth/key handling are planned. The current app supports direct access and locally encrypted optional credentials.
