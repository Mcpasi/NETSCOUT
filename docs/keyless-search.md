# Search without setup

Review date: 2026-10-19. Baseline: `3d27b638895386254e51ca28c75a7afa9596d661`.

All six sources can be searched with empty credential fields. Web, GitHub, Forums, and Hacker News are selected by default; Reddit and YouTube only need selecting in the UI. There is no NetScout account, hidden shared API key, or daily app search quota. External services can still limit requests and result depth. Index search assesses returned titles/passages, rather than the complete content of every target page.

## Investigation before implementation

| Evaluated route | Actual observation | Decision |
| --- | --- | --- |
| Brave search, `"kotlin" "android"` | HTTP 200; relevant public web results; a real second page with new results. Reddit and YouTube site searches also returned HTTP 200 with relevant discussions/videos. | Default for web and the keyless Reddit/YouTube adapters. |
| DuckDuckGo HTML and Lite | Initially HTTP 200, eight relevant web results, and ten Reddit candidates. Later queries/continuation returned HTTP 202 with `challenge-form`/`anomaly-modal`. | Second service; blocks are detected and respected. No CAPTCHA solving or repeated attempts against the same block. |
| Bing RSS | HTTP 200, but web and `site:reddit.com` queries returned the same general Kotlin pages rather than Reddit discussions. | Not used as a search backend. Successful HTTP does not establish correct search execution. |
| Other independent services | Bing HTML also ignored Reddit/YouTube restrictions. Yahoo returned HTTP 500, Mojeek a CAPTCHA page, and Google a JavaScript intermediary; the Mwmbl API timed out. | No additional unverified default adapters. |
| Anonymous Reddit JSON search | HTTP 403. Reddit documents mandatory OAuth and blocking of unauthenticated API calls. | Public index search instead of dependence on an anonymous JSON endpoint. |
| YouTube search page | Rejected by the proxy with HTTP 403 in this execution environment. | No untested direct HTML/internal API adapter; the verified public video index search works without a Google key. |
| GitHub, HN, Stack Exchange | Existing public APIs already work without keys; documented limits remain. | Keep APIs, with the public index as a first-page fallback. |
| Public SearXNG instances | SearXNG documentation explains that many public instances disable JSON and return HTTP 403. | No setup or random server required. A personal/permitted service remains optional. |

These HTTP observations are snapshots, not availability guarantees. Production requests use the same literal keywords and existing local semantic assessment. An index may ignore words; NetScout accepts such results only when the available evidence supports every term and passes model assessment. Platform adapters also check result hosts and paths themselves.

The first real production check with `kotlin,android` and empty credentials passed for all six sources: Web 20 loaded/20 model-confirmed candidates, Reddit 20/20, YouTube 19/19, GitHub 90/89, Forums 21/16, and HN 30/29. A subsequent additional-page check was already limited by the public services on page one for Web/Reddit/YouTube (`RATE_LIMITED`); it is not reported as a successful live pagination test. The earlier direct Brave check had returned a real second page; HTTP fixtures additionally verify the continuation protocol. When blocked or unavailable, the app therefore offers the same keywords and platform restriction in a normal browser, without requiring an API key. That view uses the provider's search and is not presented as locally assessed results.

## Failures reproduced before fixes

`ProviderFactoryTest.empty settings make every source searchable without credentials` was first run against the unchanged factory. Result: one failing test with `[(REDDIT, SETUP_REQUIRED), (YOUTUBE, SETUP_REQUIRED), (WEB, SETUP_REQUIRED)]`. The keyless factory was implemented only afterward.

Another test, `IndexFallbackProviderTest.an unresponsive first page leaves time for the public fallback`, stalled the primary adapter. Before the fix, the entire 18-second budget expired without calling fallback (four tests, one failure). Each first-page stage now limits its primary attempt to four seconds. Fallback remains reachable within the existing engine budget; actual user cancellation still propagates.

The first Android CI run of keyless search (`39182453688`, commit `7634e2b`) reproduced another bug: ten of eleven tests passed; `blockedPublicSearchOffersTheSameKeywordsInTheBrowserWithoutRequestingAKey` failed because the pause notice was not visible. With very fast responses, search could finish before the UI started scrolling. Scrolling depended on `isSearching` and could be skipped entirely. It now runs after every valid search submission in a `LaunchedEffect`, once the active request is composed, including immediate failures or cached responses. The failing device test is rerun unchanged.

## Continuation and limits

- Follow continuations actually offered, without a fixed app page limit. A service can refuse more pages or end the search.
- Cursors retain the responding service. A pagination failure does not silently restart a different result list.
- Ads, navigation data, related links, and other posts' answer cards are not appended to a result passage.
- Accept publication dates only when the index explicitly identifies them as such. Relative labels and general index/crawl dates stay unknown. An active date filter continues to exclude undated results.
- Respect HTTP 429/`Retry-After` and block/challenge pages. Challenge pages cause a ten-minute host pause; unknown layouts produce errors.
- During a failure or pause, search can continue in a normal browser with the same keywords. No API key is required.
- Personal keys and tokens go only to their respective API. Public fallback works without those credentials even when a key is rejected or a personal SearXNG service is unavailable.

## Repeat the checks

```sh
./gradlew :core:search:test :data:search:test :data:relevance:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew :data:search:checkLiveSearch -PsearchKeywords=kotlin,android
./gradlew :data:search:checkLiveSearch -PsearchKeywords=kotlin,android -PsearchSources=WEB,REDDIT,YOUTUBE -PsearchPages=2
```

Live checks use no credentials, run through the real local model, and are not run automatically in CI because of external limits. HTTP fixtures verify query preservation, original text, platform separation, continuations beyond page ten, blocked pages, errors, dates, and credential isolation. Android tests verify the preselected web source and selection/search of all six sources without entering credentials.

Primary sources: [DuckDuckGo HTML/Lite](https://duckduckgo.com/duckduckgo-help-pages/features/non-javascript), [Brave search](https://search.brave.com/), [Reddit OAuth requirements](https://support.reddithelp.com/hc/en-us/articles/16160319875092-Reddit-Data-API-Wiki), [GitHub search limits](https://docs.github.com/en/rest/search/search), [HN API](https://hn.algolia.com/api), [Stack Exchange limits](https://api.stackexchange.com/docs/throttle), [YouTube Data API](https://developers.google.com/youtube/v3/docs/search/list), [SearXNG formats](https://docs.searxng.org/dev/search_api.html).
