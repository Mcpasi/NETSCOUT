# Search fix 0.3.3: wrapped paragraphs

Baseline: `72a06791970d107f70c40bffbea1aa67453b2bfe` (0.3.2), reviewed on 2026-10-19.
The new tests and expanded corpus were first run against unchanged production code. The same inputs were then checked with the fix. All search responses come from local MockWebServer; credentials and public search services are not required for measurement. The unchanged, checksum-pinned ONNX relevance model is actually executed.

## Reproduced failure

These Markdown lines form **one** paragraph; their line breaks render as spaces:

```markdown
A native Codex
mobile client for
Android.
```

The same applies to line breaks inside an HTML `<p>` element. Previously, `fullText` retained those formatting breaks as hard line boundaries. Passage checking therefore split the connected sentence. The three new positive provider fixtures for GitHub issues, Reddit, and Hacker News were rejected by both context checking and the real relevance model.

An additional negative case contains three unrelated Markdown paragraphs about Codex web integration, Mobile Safari layout, and Android permissions. Each paragraph is also wrapped in the source text. These artificial fragments previously caused a falsely accepted model match. After the fix, the existing continuity check receives complete paragraphs and rejects the case.

## Fix and preserved behavior

`JsonHelpers.fullText` normalizes HTML formatting whitespace in explicit text blocks such as `p`, `li`, and `div` before inserting the existing paragraph boundaries. Markdown follows the same path after CommonMark rendering. Wording, punctuation, and order are preserved.

Actual paragraphs, lists, `<br>`, hard Markdown line breaks, and preformatted code lines stay separate. Unmarked plain-text snippets retain their existing line boundaries. Five new regression tests cover these properties. Sources, queries, date filters, pagination, fallbacks, ranking method, model, and model thresholds are preserved; no feature was removed.

## Measured improvement

The previous corpus of 20 text cases is retained in full. Three relevant wrapped paragraphs and one irrelevant post with separate sections are added: **24 cases** total, with 17 relevant and 7 irrelevant.

| Measurement in the fixed corpus | Before | After |
| --- | ---: | ---: |
| Correctly assessed text cases | 20/24 | **24/24** |
| Correctly accepted matches | 14 | **17** |
| Missed relevant matches | 3 | **0** |
| Falsely accepted matches | 1 | **0** |
| Precision | 93.33% | **100%** |
| Recall | 82.35% | **100%** |
| F1 | 0.875 | **1.000** |
| URL identity | 8/8 | 8/8 |
| Fallback/error handling, three runs | 24/24 | 24/24 |
| Targeted regression tests | 24/30 | **30/30** |

Before the change, two text-preparation tests, three provider cases, and the model test failed. Afterward, all 30 pass with identical test sources and fixtures. All previous 20 text cases stay correct. This is a diagnostic regression corpus, not an estimate of search quality across the web or a speed measurement.

Original machine-readable results:

- [Quality before](search-quality/0.3.3/before.json) and [after](search-quality/0.3.3/after.json)
- [Tests before](search-quality/0.3.3/before-tests.json) and [after](search-quality/0.3.3/after-tests.json)

## Repeat the measurements

From the repository root, using the JDK 17/Android SDK environment described in the [development guide](development.md):

```sh
./gradlew :data:search:test \
  --tests '*HtmlParagraphRegressionTest' \
  --tests '*SourceTextRegressionTest' \
  --tests '*SearchQualityModelTest' \
  :data:search:checkSearchQuality \
  -PsearchQualityOutput="$PWD/data/search/build/reports/search-quality-0.3.3.json" \
  -PsearchQualityLabel=0.3.3-after
```

For the before check, the new tests remain unchanged. In a separate worktree, restore only the modified production file to the baseline:

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

Expected: **6 failures out of 30 tests**, a nonzero build exit code, and a quality report still generated with **20/24** correct text cases. `--continue` lets measurement run despite the expected test failures. The model and tokenizer are downloaded on the first build and verified against checked-in checksums; later runs use the local cache.

Version-number changes are recorded in the [changelog](../CHANGELOG.md#033).

## Final verification

Successfully executed for this release:

```sh
./gradlew :core:search:test :data:search:test :data:relevance:test \
  :app:testDebugUnitTest :app:lintDebug :app:assembleDebug \
  :app:assembleDebugAndroidTest
python3 scripts/verify-debug-signature.py app/build/outputs/apk/debug/app-debug.apk
```

**140 tests passed**, with no failures or skipped tests: Core 41, search adapters 80, relevance model 10, app/ViewModel 9. Android Lint and app/test APK builds passed. The generated app APK confirms `versionName=0.3.3`, `versionCode=4`; the permanent debug certificate was verified.

Android device tests were not run locally; the existing CI workflow runs them separately on an emulator.
