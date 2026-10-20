# Reproduced search failures

The additional investigation for **0.3.1**, covering four causes, 13 regression tests that failed before the fixes, and fixed before/after measurements, is in [search-quality-0.3.1.md](search-quality-0.3.1.md). The sections below describe the earlier investigation from 2026-10-18.

Baseline: `7685087a900d3ba53ff46b94b07ac07272d16797` (2026-10-18).
The following tests were added and run against unchanged production files first. Fixes were implemented only after the tests failed.

```sh
./gradlew :core:search:test
# Before the fix: 38 tests, 6 failures.
./gradlew :data:relevance:test --tests de.netscout.relevance.SemanticRelevanceTest
# Before the fix: 4 tests, 1 failure.
```

| Reproduction | Observation before the fix | Fix |
| --- | --- | --- |
| `Codex\u00a0mobile\u2003Android` with pasted Unicode spaces | The three words are treated as one term; relevant titles are missed. | Normalize Unicode spaces before parsing and request normalization. |
| `"local\u00a0llm",Android`, text `Local\u2003LLM for Android` | The phrase is not recognized despite equivalent whitespace. | Phrases accept Unicode whitespace in original text too. |
| `Control Codex from a mobile client. It runs on Android.` | No single sentence contains all three words; their shared reference is not assessed. | Two neighboring sentences within the same paragraph can form original evidence. |
| A long sentence with `Codex`, followed roughly 560 characters later by `mobile Android` | Fixed windows start 200 characters before each occurrence and cut off other required words, although all fit within 700 characters. | A sliding complete-match window follows actual term positions. |
| 120 earlier separate occurrences of each term, followed by a complete passage | Highlighting capped at 100 occurrences is used for search; the later evidence is missed. | Passage search has its own iterators over all occurrences; only the number of stored excerpts is limited. |
| Matching title, generic first paragraph, informative later paragraph | An early return for a matching title hides later body evidence. | Body passages are selected even with a fully matching title and prioritized by keyword coverage. |
| Two short complete mentions before a longer explanation rated positively by the scorer | The two shortest passages fill both slots; the explanation is never assessed. | Assess up to four passages without restricting selection to the shortest length. |

Examples use the existing sample query and other arbitrary terms; the implementation has no hard-coded product names.

## Additional paragraph case from live comparison

After the initial fixes, several relevant HN posts were still rejected before model assessment: the project introduction names the agent, while a later platform paragraph mentions Android. The new model test `real model retains a project introduction followed by its platform paragraph` also failed before this further fix. Its reduced reproduction is:

```text
An open source interface for Codex and other coding agents.

The mobile app runs on iOS and Android.
```

When sentence-level evidence is absent, semantic assessment now receives complete original windows of up to 1,000 characters and additionally checks continuity between required sections. The same model test confirms a relationship between `rust`, `async`, and `database` with an immediate back-reference. Unrelated release notes remain excluded even when their combined text receives a high query/passage score. Another test, which failed before the safeguard, prevents back-references from skipping an intervening topic change. The preview can be fully expanded.

## HN and Forums

Real HTTP requests before the fixes confirmed reachable endpoints: Algolia returned six story candidates for `Codex,mobile,android`. Stack Exchange returned questions for `kotlin,android` and no candidates for a combination with an invented second term. A general failure of these providers to start could therefore not be confirmed.

Previously, the engine provided its only visible source status after complete content assessment. A completed page remained visible as successful while the next page was being requested. Fetching and content assessment are now shown separately, including loaded candidates and successful empty responses. Starting a search scrolls to source status. Unchecked candidates are still never displayed as matches.

Additional tests cover parallel startup of GitHub, HN, and Forums, assessment in progress without premature matches, pagination, cancellation, caching, and candidate/match counts. A real model test confirms continuity across two sentences. Existing negative tests for missing terms, separate paragraphs, and unrelated release-note sections remain. The optional live check now covers all three public sources:

```sh
./gradlew :data:search:checkLiveSearch -PsearchKeywords=Codex,mobile,android
```

## Final verification

77 JVM/ViewModel tests, including real model inference, passed after the fixes. Android Lint, the debug APK, Android test APK, and permanent signing-certificate verification also passed. The final live check on 2026-10-18 confirmed successful responses from all three sources: GitHub 90 loaded/72 confirmed candidates, HN 6/3, and Forums 3/0. This snapshot does not establish complete platform coverage. UI checks additionally cover individual source phases and Unicode input on the emulator.

The first CI emulator run failed before app tests started: `FATAL | Not enough space to create userdata partition. Available: 7026.18 MB ... need 7372.80 MB.` The subsequent repeated `adb: device 'emulator-5554' not found` was thereby reproduced. Even with the supported AVD setting `disk-size: 2048M`, the supplied system image still requested 7372.80 MB on the next run. The workflow therefore frees space before emulator startup by removing unused .NET/CodeQL tools from the temporary CI machine. The emulator startup timeout is 180 rather than 600 seconds.

The next run (`39172314571`, commit `cc51302`) successfully started the emulator and reproduced a UI bug before its fix: `everySelectedSourceShowsItsCurrentSearchPhase` failed at `assertIsDisplayed()` for HN's fetching status. The scroll target did not count the separate heading, so it jumped to the input form rather than source status. The target index now accounts for the heading, form, and optional credential notice. The UI check requires visible phases for all three sources without additional scrolling and also checks the variant with the credential notice.
