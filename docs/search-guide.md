# Search guide

For installation and a first search, start with the [README](../README.md). The app interface supports German and English; this guide explains the search behavior in English.

## Keywords and matching

Commas, semicolons, and spaces, including pasted Unicode spaces, separate keywords. Quotation marks group exact phrases. The interface displays all recognized required terms. `Codex,mobile,android` is just an example query; no product names are hard-coded into production matching.

The title and a connected passage must jointly support all required terms. A passage can include two neighboring sentences in the same paragraph. When there is no complete local passage match, the semantic model also examines unchanged original windows of up to 1,000 characters across paragraph boundaries, with an additional continuity check between the required sections. Independent release-note sections mentioning separate terms remain excluded.

Long texts are searched for complete matching windows across all occurrences. Up to four passages per candidate keep short mentions from crowding out a later explanation. A matching title does not hide stronger body evidence. Expand the preview to see the full assessed original passage, with the query terms highlighted. Previews are not generated text or model explanations.

Relevance sorting primarily uses the learned model score. Reciprocal Rank Fusion (`1 / (60 + rank)`) has a small weight as a tie-breaker. Incompatible popularity measures from different platforms are not mixed. Sorting by newest still requires the content to pass the same relevance checks.

Identical URLs are merged across sources and pages. Tracking parameters and fragments are ignored; meaningful parameters, such as YouTube video IDs, are retained.

## Languages and model limits

Searches can return any language, independently of the interface language. GitHub and Hacker News receive no German-language or country restriction; SearXNG explicitly receives `language=all`. Keyword language and platform coverage still influence the languages returned. The bundled Horizon Small v1.2 reranker supports 40+ languages, including German and English.

Candidate queries are literal by default. The optional term-expansion switch adds up to two dictionary-based synonym queries per source and shows the additional queries before searching; see [0.3.8 changes](changes-0.3.8.md). The model does not generate translations or rewrite queries. The model can only assess what a platform returns. `AI`, for example, does not match merely because it appears inside `Gmail`. HN also has typo and prefix matching disabled and may not automatically drop missing words.

The model runs entirely on the device, with no paid AI API, backend, or model download in the app. It increases APK size and processing time. Content assessments can be wrong; short ambiguous queries remain ambiguous, and conservative continuity checks can miss relevant explanations spread over several paragraphs. Model origin, fixed checksums, licensing, privacy, and quality limits are documented in [relevance-model.md](relevance-model.md).

## Dates and sorting

Date bounds refer to **publication or creation in UTC**, including both selected days. Bounds are sent to the source when its API supports them, then applied precisely again in the shared filtering layer. Reddit and search-index results are filtered locally.

Results without a known publication date are excluded while a date filter is active. Indexed searches can therefore return substantially fewer results. Crawl dates, modification dates, and relative labels are not presented as publication dates. YouTube index results only receive a date when it is explicitly identified as the publication date.

You may need additional pages to find dated matches. Complete historical search is not guaranteed. **Newest** sorts the results already loaded, placing unknown dates last when no date filter excludes them. The newest and relevance checkboxes are mutually exclusive.

## Progress and more results

Source status distinguishes requests in progress, assessment of downloaded candidates, and confirmed matches. Successful responses with no candidates are also shown explicitly. Unassessed candidates are not displayed as results. Rejected candidates remain accounted for during pagination, allowing you to continue after a page whose results were all filtered out.

Source requests run in parallel. A platform failure does not stop the others. Successful assessed pages use a memory cache; cancellation and API-requested pauses are supported.

## Access without credentials

All six sources work with empty credential fields. Web, GitHub, Forums, and Hacker News are selected by default; Reddit and YouTube can be enabled directly. The gear icon opens **Optionale Suchzugänge**, where you can add personal API credentials or your own/permitted SearXNG HTTPS instance with JSON output. Every field can remain empty.

Web search uses public Brave results, with DuckDuckGo as the second service. Reddit and YouTube use the public index without personal credentials. GitHub makes three API requests per page for repositories, issues, and pull requests; anonymous API limits apply. Forums covers Stack Overflow, Super User, and Server Fault, including on index fallback. HN searches stories through Algolia.

NetScout requires no account or payment and imposes no daily search allowance of its own. External services can still limit requests and result depth. The app reads publicly accessible search-result pages, detects blocked/CAPTCHA pages, respects pauses, and never supplies demo matches.

Failed direct access and optional search services automatically fall back to the labeled public index on the first page. An unresponsive first attempt yields after four seconds to leave time for fallback. Pagination stays with the responding service. Public web search follows the continuations actually offered, without a fixed app page limit. Credentials are never forwarded to the search index.

If a source is blocked or unavailable, the app can open the same keywords and platform restriction in a normal browser without an API key. This view uses the provider's search and is not a locally assessed NetScout result list.

Evaluated alternatives, recorded live requests, and failures reproduced before the implementation are documented in [keyless-search.md](keyless-search.md).

## Primary sources for data access

The original review was performed on **2026-10-19**. Service requirements and quotas can change.

- [GitHub Search API, limits, and incomplete results](https://docs.github.com/en/rest/search/search)
- [Hacker News search through Algolia](https://hn.algolia.com/api)
- [Stack Exchange Advanced Search](https://api.stackexchange.com/docs/advanced-search) and [rate limits](https://api.stackexchange.com/docs/throttle)
- [YouTube Search API](https://developers.google.com/youtube/v3/docs/search/list) and [quotas](https://developers.google.com/youtube/v3/determine_quota_cost)
- [Reddit Data API and OAuth requirements](https://support.reddithelp.com/hc/en-us/articles/16160319875092-Reddit-Data-API-Wiki)
- [SearXNG Search API and JSON configuration](https://docs.searxng.org/dev/search_api.html)
- [DuckDuckGo HTML/Lite search](https://duckduckgo.com/duckduckgo-help-pages/features/non-javascript) and [Brave search](https://search.brave.com/)
