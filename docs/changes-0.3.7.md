# NetScout 0.3.7

Android version name: `0.3.7`. Release version code: `8` (previously `6`).

## German and English interface

- The app initially follows the device's language preferences, selecting the first supported German or English locale. Regional variants such as `de-CH` and `en-GB` are recognized. English is the fallback when neither language is available.
- A compact bar above the search and favorites screens provides German and British flags, `DE`/`EN` labels, and a **System** option. The active language is highlighted; the controls have translated accessibility labels and remain scrollable on narrow screens.
- A manual selection is stored on the device and survives restarts. **System** clears the override and resumes automatic selection.
- Search forms, settings, favorites, result controls, source status, validation and error messages use local Android string resources. Result counts use plurals; date fields and the date picker follow the selected interface language.
- Dialog content explicitly retains the app's resource configuration, so settings, validation, buttons and native date-picker labels use the selected language even when the device uses the other language.
- Changing the language immediately updates the interface while retaining search terms, filters, results and favorites. It does not issue another search or translate titles, passages, authors, URLs or the user's search terms.
- Providers emit structured message identifiers and arguments so cached notices and errors can be rendered in either language. JVM diagnostics retain German fallback text. Saved favorites preserve source metadata identifiers; existing version-1 favorites remain readable, including recognized legacy index and Hacker News metadata.

## Build compatibility

Android Lint identified one required build configuration change: language splitting is disabled for App Bundles so every installation contains both translations for runtime switching. This uses the existing Android Gradle plugin and adds no dependency. APK build scripts, the Gradle wrapper, GitHub Actions workflows, model preparation, SDK requirements and signing configuration remain unchanged.

The app version and the corresponding F-Droid release metadata are updated. The F-Droid recipe is pinned to the implementation commit; release APKs remain unsigned, and CI's increasing version-code override remains limited to debug builds.

## Verification

The checks for this release cover:

- 159 passing JVM tests: 41 core search, 80 search-provider, 10 relevance/model and 28 app tests. This includes language selection/fallback and favorite-format compatibility.
- Three passing Python tests for offline model preparation.
- Debug and release Android Lint, including confirmation that the runtime-language/App-Bundle warning is resolved.
- A signed debug APK and a compiled Android test APK. The permanent debug signing certificate is unchanged.
- An unsigned `0.3.7` (`8`) release APK built with offline model preparation and checked by `scripts/verify-release-apk.py`.
- F-Droid metadata validation with `fdroid lint`.

The Android UI regression tests cover automatic language detection, persistence/reset, switching with existing search results and favorites, original source content, bilingual messages/plurals, settings validation and date-picker labels. They explicitly test German dialogs and native date-picker labels on an English device. Existing tests scroll to form controls and settings help text so their visibility checks work with the additional language bar.

All **30 emulator tests passed** in [GitHub Actions run 39519842043](https://github.com/Mcpasi/NETSCOUT/actions/runs/39519842043). The same run passed the unit-test/lint/debug job and the full F-Droid source scan, offline release build, APK scan and release-content/version verification. The installed launcher activity was also started successfully, and its captured initial screen was visually checked.
