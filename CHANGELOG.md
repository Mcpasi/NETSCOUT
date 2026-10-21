# Changelog

Version history moved from the README. Detailed investigation reports remain linked below.

## 0.3.7

- Add a bar for switching between English and German languages, using the German and British flags.

- Full translation of the interface, with device language detection; if no device language is detected, it remains in English by default. 

## 0.3.5

- Added the F-Droid build recipe, release verification in GitHub CI, a free project license, and localized store metadata.
- Separated model preparation from the actual build. Release version codes remain independent of CI run numbers.
- Fixed release version: `0.3.5`, version code `6`.
- Recorded successful source scanning, release lint, build, and APK scanning through the F-Droid tooling outside an official F-Droid VM. See [F-Droid preparation](fdroid/README.md) for the exact revision, environment, and remaining submission steps.

## 0.3.4

- Added persistent local favorites, limited to 20 results, with a dedicated screen and removal controls.
- Added JVM coverage for storage, restart recovery, URL identity, concurrent changes, capacity, and failures.
- Added Android coverage for file storage and the stars in search and favorites.

## 0.3.3

- Fixed formatting line breaks inside HTML and Markdown paragraphs hiding valid results.
- Preserved actual paragraph, list, and code boundaries.
- Improved correct decisions in the fixed corpus using the real relevance model from **20/24 to 24/24**.
- Updated the local default version code from `3` to `4` (patch version `0.3.2` to `0.3.3`). The existing sequential CI version codes and permanent debug certificate were retained.
- Before/after reports and reproduction: [search quality 0.3.3](docs/search-quality-0.3.3.md).

## 0.3.2

- Honored `Retry-After` pauses requested with HTTP 503 responses.
- Reported invalid JSON result lists as source errors so the existing fallback can run.
- Updated the local default version code from `2` to `3` (patch version `0.3.1` to `0.3.2`). The existing CI version-code assignment was retained.
- Reproductions and regression tests: [bugfixes 0.3.2](docs/bugfixes-0.3.2.md).

## 0.3.1

- Fixed plain text and Markdown being interpreted as HTML and losing literal generic expressions.
- Preserved repeated URL-parameter order and non-default ports during deduplication.
- Reported unreadable search-result rows as errors, enabling fallback instead of a false empty success.
- Documented four reproduced causes, 13 initially failing regression tests, and machine-readable before/after measurements.
- Updated the local default version code from `1` to `2` (patch version `0.3.0` to `0.3.1`). The existing CI version-code assignment and permanent debug certificate were retained.
- Evidence and reproduction: [search quality 0.3.1](docs/search-quality-0.3.1.md).

## 0.2.0

- Required a shared topic supported by the title and a connected original passage, rather than accepting words scattered anywhere in a post.
- Introduced on-device multilingual relevance assessment. See the [model documentation](docs/relevance-model.md) and [search regression investigation](docs/search-regressions.md) for the approach and subsequent refinements.
