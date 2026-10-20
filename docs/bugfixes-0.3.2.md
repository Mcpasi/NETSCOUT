# Bugfixes 0.3.2

Baseline: `b6bc3e45abdf0e10bc4644998c5cec9aa41b7a23` (0.3.1), reviewed on 2026-10-19.
Before any production changes, the new regression tests were run against this baseline: **5 tests, 4 failures**. After the fixes, the same five tests passed with unchanged inputs. All HTTP responses come from local MockWebServer; real search services and credentials are not required.

## 1. HTTP 503 ignores a requested pause

Reproduction: the server responds with `503 Service Unavailable` and `Retry-After: 120`; the next queued response is HTTP 200. Despite the requested two-minute pause, the client retries after 350 milliseconds and returns the second response. The same happens with `Retry-After: Thu, 01 Jan 2026 00:02:00 GMT` when the injected clock is at `2026-01-01T00:00:00Z`.

Fix in `JsonHttpClient`: HTTP 503 with `Retry-After` uses the same host pause as a rate-limit response. Both seconds and HTTP dates are parsed; `RATE_LIMITED` informs the caller about the pause. Another request before expiry does not reach the server. Requests are allowed again exactly at expiry. HTTP 503 without a pause request is still retried once.

## 2. Invalid JSON results become empty success

Reproduction: the HN API returns `{"hits":[null,"schema-changed",42,[]],"nbPages":1}`. Previously, `requireRows` only checked that `hits` was an array; `rows` then removed every element that was not a JSON object. The provider reported a successful empty page. `IndexFallbackProvider` never called the available fallback provider. A SearXNG response with the same elements in `results` was also incorrectly reported as a successful empty endpoint.

Fix in `requireRows`: a result list must be an array of JSON objects. Structurally invalid lists report `SourceStatus.ERROR`, allowing the existing fallback to run. If the final provider is affected, the error stays visible. A valid empty array `[]` remains successful and does not trigger an unnecessary fallback request.

## Evidence and reproduction

| Regression test | Before | After |
| --- | --- | --- |
| `JsonHttpClientTest.service unavailable with Retry-After pauses instead of retrying immediately` | Failed: another HTTP request instead of a pause | Passed, including blocking and expiry checks |
| `JsonHttpClientTest.service unavailable also honors an HTTP date Retry-After` | Failed: HTTP date ignored | Passed |
| `ProvidersTest.non-object search rows trigger the index fallback instead of a false empty success` | Failed: 0 instead of 1 fallback calls | Passed; fallback results retained |
| `ProvidersTest.unreadable final index response reports an error` | Failed: success instead of error | Passed |
| `ProvidersTest.a valid empty JSON result list remains successful without fallback` | Passed | Passed |

Targeted check:

```sh
./gradlew :data:search:test \
  --tests '*JsonHttpClientTest.service unavailable*' \
  --tests '*ProvidersTest.non-object*' \
  --tests '*ProvidersTest.unreadable final*' \
  --tests '*ProvidersTest.a valid empty*'
```

To repeat the before check with the same tests, restore only the two production files in a separate worktree:

```sh
git worktree add --detach ../NETSCOUT-0.3.2-before HEAD
git -C ../NETSCOUT-0.3.2-before restore \
  --source=b6bc3e45abdf0e10bc4644998c5cec9aa41b7a23 --worktree -- \
  data/search/src/main/kotlin/de/netscout/data/JsonHttpClient.kt \
  data/search/src/main/kotlin/de/netscout/data/JsonHelpers.kt
cd ../NETSCOUT-0.3.2-before
# Run the targeted check above here: 4 failing tests are expected.
```

Version-number changes are recorded in the [changelog](../CHANGELOG.md#032).

## Final verification

Successfully executed for this release:

```sh
./gradlew :core:search:test :data:search:test :data:relevance:test \
  :app:testDebugUnitTest :app:lintDebug :app:assembleDebug \
  :app:assembleDebugAndroidTest
python3 scripts/verify-debug-signature.py app/build/outputs/apk/debug/app-debug.apk
```

**131 tests**, no failures or skipped tests: Core 41, search adapters 71, relevance model 10, app/ViewModel 9. Android Lint and app/test APK builds passed. Package metadata confirms `versionName=0.3.2`, `versionCode=3`; the permanent debug certificate was verified.

Android device tests were not run locally; they are part of the existing CI workflow.
