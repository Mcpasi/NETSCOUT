# Search quality 0.3.1

Investigation on 2026-10-19, baseline `5ce6327a35702eceb8a23d67038763d8ae8edde9`.
All failures below were reproduced through failing tests before production code was changed. The first targeted run included **36 tests, 13 failures**. Subsequent measurements used the same unchanged inputs before and after the fixes.

## Reproduced failures and fixes

| Cause | Concrete reproduction and observed failure | Fix | Previously failing cases |
| --- | --- | --- | ---: |
| Plain text and Markdown interpreted as HTML | GitHub issue/Reddit: `Use List<Widget> in Kotlin`, with the generic expression inside Markdown code. HTML preparation removes `<Widget>`. The same happens to the plain-text GitHub description `A Kotlin library for <Widget> collections.` Searching `Widget,kotlin` loses relevant results before model assessment. | GitHub descriptions stay plain text; issue/PR and Reddit bodies are processed as Markdown first. CommonMark protects literal code characters before HTML extraction. Actual HTML is still cleaned; paragraph boundaries remain. | 6 |
| Repeated URL-parameter order lost | `?sort=title&sort=date` and `?sort=date&sort=title` are treated as equivalent. Repeated `filter` is also affected. Such parameters can define ordered sorting or first/last filter values. Two different results become one. | Different parameter names are still sorted; the order of parameters sharing a name is preserved. | 2 |
| Non-default ports lost | `http://example.org:443/article` is merged with `https://example.org/article`; likewise `https://example.org:80/article` with `http://example.org/article`. These are different endpoints. | Remove only the default port for the respective scheme: 80 for HTTP, 443 for HTTPS. | 2 |
| Unreadable result rows treated as a successful empty search | Brave returns result containers whose title class or link can no longer be read. Previously: success with zero results and no DuckDuckGo request. Unreadable rows from the final service also produce false success. | If rows exist but none are readable, report a source error. The existing fallback then runs; a completely unreadable final service reports an error. Valid empty responses and readable results excluded by source restrictions remain successful responses. | 3 |

The four causes are covered by `SourceTextRegressionTest`, `UrlIdentityRegressionTest`, and `HtmlReliabilityRegressionTest`. `SearchQualityModelTest` additionally checks all 20 text cases with the real local model during the normal JVM test run.

CommonMark is pinned to Android-compatible version 0.21.0. Newer versions require additional Java APIs; 0.24.0 calls `List.of` and `Map.of`, among others, which are absent on older supported Android versions. `SearchTextCompatibilityTest` runs the actual GitHub provider and Markdown code on Android with HTTP responses from an in-memory interceptor.

## Measurement method

- **Text quality:** 20 pre-labeled provider fixtures, with 14 relevant and 6 irrelevant candidates. Inputs pass through the actual GitHub, Reddit, HN, or YouTube adapters, `SemanticRelevance` with the pinned ONNX model, and `ResultMerger`. The measurement is the decision to display a match. Positive controls include ordinary text, actual HTML, and correctly escaped generics; negative controls include missing terms, substring matches, and unrelated paragraphs.
- **URL identity:** eight independent pairs, four equivalent and four different. The count after actual deduplication is checked. Tracking, fragments, independent parameter order, and correct default ports serve as controls.
- **Reliability:** eight fixed HTTP scenarios, each run three times with a fresh transport and no engine cache. A trial is correct only when status, result count, and fallback-call count all match. Explicit empty responses count as correct; an unreadable final backend must visibly report an error.

HTTP responses come exclusively from local MockWebServer. Measurement needs no search credentials and consumes no platform quotas. Model and tokenizer remain unchanged; neither a threshold nor the labels were adjusted to fit results.

| Metric | Before | After |
| --- | ---: | ---: |
| Text: correct decisions / 20 | 14 / 20 | 20 / 20 |
| Text: accuracy `(TP + TN) / N` | 70.0% | 100.0% |
| Text: precision `TP / (TP + FP)` | 100.0% | 100.0% |
| Text: recall `TP / (TP + FN)` | 57.1% | 100.0% |
| Text: F1 `2TP / (2TP + FP + FN)` | 72.7% | 100.0% |
| Text: TP / FP / FN / TN | 8 / 0 / 6 / 6 | 14 / 0 / 0 / 6 |
| URL identity: correct pairs / 8 | 4 / 8 (50.0%) | 8 / 8 (100.0%) |
| URL results, sum per pair; expected 12 | 8 | 12 |
| Reliability: correctly handled trials / 24 | 15 / 24 (62.5%) | 24 / 24 (100.0%) |

Machine-readable results, including each decision: [before](search-quality/0.3.1/before.json), [after](search-quality/0.3.1/after.json).

This is a targeted **diagnostic corpus**, not a representative sample of the web. Values establish fixes for these failure cases; they estimate neither global search precision nor public-service availability. Repeating the same failure scenarios three times checks repeatability, not independent sampling. URL cases test identity logic separately from neural ranking.

Fixed inputs:

- Text corpus: `data/search/src/test/resources/search-quality/text-cases.json`, SHA-256 `dda93c7718e43b97efaa3a74887ebb123017aeb79cd5330e80ea5fe4d1da05c6`.
- URL/HTTP cases: `SearchQualityFixtures.kt`.
- Unchanged model lock file: `relevance-model.lock.json`, SHA-256 `c1afd5df6c29d40f2a2f658b08937df6bcc81f235dbd73886473c465aa7cf4a9`.

## Repeat the measurements

```sh
./gradlew :data:search:checkSearchQuality \
  -PsearchQualityOutput=/tmp/netscout-after.json \
  -PsearchQualityLabel=0.3.1

./gradlew :data:search:test \
  --tests de.netscout.data.SourceTextRegressionTest \
  --tests de.netscout.data.UrlIdentityRegressionTest \
  --tests de.netscout.data.HtmlReliabilityRegressionTest
```

The first command writes measurements even if the examined revision performs worse. The second requires the expected decisions. Model/library downloads on the first build need network access.

To repeat the before measurement, keep the measurement program and fixtures at this change's revision; restore only the five corrected production files in a separate worktree:

```sh
git worktree add --detach ../NetScout-quality-before HEAD
git -C ../NetScout-quality-before restore --source=5ce6327 --worktree -- \
  core/search/src/main/kotlin/de/netscout/core/ResultMerger.kt \
  data/search/src/main/kotlin/de/netscout/data/JsonHelpers.kt \
  data/search/src/main/kotlin/de/netscout/data/GitHubProvider.kt \
  data/search/src/main/kotlin/de/netscout/data/RedditProvider.kt \
  data/search/src/main/kotlin/de/netscout/data/PublicWebProvider.kt
cd ../NetScout-quality-before
./gradlew :data:search:test \
  --tests de.netscout.data.SourceTextRegressionTest \
  --tests de.netscout.data.UrlIdentityRegressionTest \
  --tests de.netscout.data.HtmlReliabilityRegressionTest \
  :data:search:checkSearchQuality \
  -PsearchQualityOutput=/tmp/netscout-before.json \
  -PsearchQualityLabel=5ce6327 --continue
# Expected: 36 tests, 13 failures; the measurement file is still written.
```

## Version and final verification

Version-number changes are recorded in the [changelog](../CHANGELOG.md#031).

Final verification with CommonMark 0.21.0 confirmed the same measurements and model scores as the first after measurement. Successfully checked locally for this release:

- **126 JVM/ViewModel tests**, no failures or skipped tests: Core 41, search adapters 66, relevance 10, app 9.
- Android Lint: 0 errors, 27 warnings, mainly notices about newer library versions. The Android-compatible CommonMark version remains deliberately pinned.
- Debug APK and Android test APK built; app package metadata confirms `versionName=0.3.1`, `versionCode=2`.
- Permanent debug certificate checked with `scripts/verify-debug-signature.py`: `a5d9e8eadeb82bc5f5bb35e9f74a945b879349defae394d548c22eab57a0f8ea`.

```sh
./gradlew :core:search:test :data:search:test :data:relevance:test \
  :app:testDebugUnitTest :app:lintDebug :app:assembleDebug \
  :app:assembleDebugAndroidTest :data:search:checkSearchQuality
python3 scripts/verify-debug-signature.py app/build/outputs/apk/debug/app-debug.apk
```

Android device tests are additionally part of the existing API 29 CI job; locally, the test APK was built and JVM checks were run.
