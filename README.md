# NetScout for Android

Search the web and public discussions from one native Android app. Enter your keywords, choose up to six sources, and let NetScout check whether the results discuss your search topic together.

**Android 8.0 or newer (API 26).** The app interface is currently in German. No NetScout account, payment, API key, or separate backend is required. Search results can be in any language.

## What you can do

- Search the web, Reddit, YouTube, GitHub, Hacker News, and three Stack Exchange forums.
- Narrow results by publication date and sort by newest or relevance.
- Read the original passage behind a match, expand it, and open its source.
- See each source's progress, candidate count, confirmed matches, and service errors.
- Load more results and cancel a running search.
- Save up to **20 favorites** on your device, with previews you can read offline.

NetScout uses a bundled multilingual relevance model to assess the passages returned by search services. It runs on your device and requires no paid AI API or model download in the app. Matching can still be imperfect, and NetScout can only assess the content its sources return.

## Install a test APK

1. Open **[Actions → Android tests & APKs](https://github.com/Mcpasi/NETSCOUT/actions/workflows/android.yml)** and select a successful run. GitHub may require you to sign in to download artifacts.
2. Under **Artifacts**, download `netscout-debug-<run number>`.
3. Extract the ZIP and install `netscout-debug.apk` on your Android device. Allow installation from the app you use to open the APK if Android asks.

The ZIP also contains the APK's SHA-256 and signing-certificate fingerprint. Later debug builds can update the existing test installation when their version code is at least as high. All debug builds use the same [public development certificate](debug-signing/README.md).

**F-Droid status:** the repository includes a build recipe and verification notes. This does not yet mean NetScout is available in the official F-Droid repository. The `netscout-fdroid-release-<run number>` artifact contains an unsigned release APK for verification; use the debug APK above for testing. See the [F-Droid preparation notes](fdroid/README.md) for details.

## Run your first search

1. Enter keywords, for example `kotlin,android`. Commas, semicolons, and spaces separate terms. Use quotation marks for a phrase, such as `"local llm",Android`. The app shows the required terms it recognized.
2. Choose your sources. **Web, GitHub, Forums, and Hacker News** are selected by default. Reddit and YouTube can also be used immediately.
3. Optionally select a start date, an end date, and a sort order. The newest and relevance options are mutually exclusive.
4. Start the search. NetScout brings the source status into view and shows results after checking their content.
5. Expand a preview to read the assessed passage, open its original link, or tap the star to save it. Load more pages when available.

All six sources work with empty credential fields. The gear icon opens **Optionale Suchzugänge** (optional search credentials) if you want to add your own API access or a permitted SearXNG HTTPS instance with JSON output.

## Sources and coverage

| Source | Available without setup | Main limits |
| --- | --- | --- |
| Web | Public Brave search, with DuckDuckGo as a fallback | Titles and indexed passages, rather than a full crawl of target pages. The service controls availability and result depth. |
| Reddit | Publicly indexed discussions | Index coverage rather than the complete Reddit API. Approved OAuth access is optional. |
| YouTube | Publicly indexed videos | Titles and indexed passages rather than full descriptions or transcripts. A personal Data API key is optional. |
| GitHub | Public repositories, issues, and pull requests through the REST API; index fallback | Repository names/descriptions and issue/PR titles/bodies. No comments, README search, code search, or Discussions. Anonymous API limits apply; a token can increase the allowance. |
| Hacker News | Public stories through Algolia; index fallback | Links open the HN discussion. No complete comment search. |
| Forums | Stack Overflow, Super User, and Server Fault through the Stack Exchange API; index fallback | These three sites only. API quotas and requested pauses apply. |

NetScout has no daily search quota of its own. External services can still limit requests, show a CAPTCHA, change their layout, or stop returning more pages. The app reports these conditions and respects pauses. When a source is blocked or unavailable, you can open the same keywords and platform restriction in your browser. Browser results are provided by that service and are not assessed by NetScout's local model.

Dates refer to **publication or creation in UTC**, including both selected days. Results without a known publication date are excluded when a date filter is active, so indexed searches may return fewer matches. **Newest** sorts the results already loaded; complete historical coverage is not guaranteed.

For details about matching, languages, date handling, pagination, and fallbacks, see the [search guide](docs/search-guide.md).

## Save favorites

Tap an empty star to save a result; tap the filled star to remove it. Open **Favoriten** in the top bar to view saved results without running another search. The back arrow or Android back button returns to the search.

Favorites preserve the title, original link, displayed passage, sources, known publication dates, source metadata, and search keywords used for highlighting. Up to **20 results** remain stored without an expiry date. Links that differ only in tracking parameters count as one result. When the list is full, remove a favorite before adding another.

New searches, credential changes, and app restarts keep your favorites. Previews work offline; opening an original source needs a connection. Uninstalling the app or clearing its data deletes favorites and saved credentials. Switching from a debug installation to a differently signed F-Droid installation requires uninstalling first.

## Privacy and credentials

Your keywords are sent to the selected platforms or the search index in use: Brave, DuckDuckGo on fallback, or an optional SearXNG instance. Failed direct requests can also fall back to the public index. Target pages are not fetched separately for relevance assessment; the model assesses the text already returned by the source, locally on your device.

The app has no telemetry and needs internet access, with no location, contacts, or storage permission. Search results stay in memory unless you explicitly save them as favorites. There is no search history, synchronization, or cloud backup. Forms and current results survive screen rotation, but not a process restart. Successful pages use an in-memory cache of up to 64 pages for 60 seconds.

Optional credentials are encrypted locally with AES-GCM and a non-exportable Android Keystore key. They are sent only to their respective API, never to an index fallback, logs, or saved instance state. App backups are disabled. Save an empty credential field to remove its token. The development machine's GitHub identity is never included in the APK.

## Documentation

- [Changelog](CHANGELOG.md): version history.
- [Search guide](docs/search-guide.md): matching, dates, languages, and service behavior.
- [Development guide](docs/development.md): local builds, tests, APK verification, and CI.
- [Architecture](docs/architecture.md): modules and provider extensions.
- [Local relevance model](docs/relevance-model.md): model origin, checksums, licensing, and limitations.
- [Keyless search investigation](docs/keyless-search.md): evaluated services and reproduced failures.
- [Search regressions](docs/search-regressions.md), [0.3.1 quality report](docs/search-quality-0.3.1.md), [0.3.2 bugfix report](docs/bugfixes-0.3.2.md), and [0.3.3 quality report](docs/search-quality-0.3.3.md): technical evidence and repeatable checks.
- [F-Droid preparation](fdroid/README.md): recipe, recorded verification, and submission steps.

## License

Copyright **2026 Mcpasi**. NetScout's own code, documentation, and artwork are licensed under [Apache-2.0](LICENSE). Third-party components retain their own licenses; see [NOTICE](NOTICE) and [third-party/README.md](third-party/README.md).
