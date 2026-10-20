# F-Droid preparation

Reviewed on 2026-10-20 against the official
[Inclusion Policy](https://f-droid.org/en/docs/Inclusion_Policy/),
[Quick Start Guide](https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/),
[Build Metadata Reference](https://f-droid.org/en/docs/Build_Metadata_Reference/),
and [Anti-Features](https://f-droid.org/en/docs/Anti-Features/).

## Requirements mapping

| Requirement | Implementation / limitation |
| --- | --- |
| Publicly accessible, complete source code | `https://github.com/Mcpasi/NETSCOUT`; must be cloneable without signing in for submission |
| Free project license | Apache-2.0, copyright 2026 Mcpasi; `LICENSE` and `NOTICE`, also included in the APK |
| Free libraries / trusted repositories | Maven Central, Google Maven, and Gradle Plugin Portal; fixed versions, no Firebase/GMS/tracking SDK; see `third-party/README.md` |
| Free toolchain | OpenJDK 17, Gradle 8.11.1, Python 3.11+, Android SDK 35 / Build-Tools 35.0.0; Android SDK and freely licensed Maven artifacts fall under the documented exceptions |
| Complete build on F-Droid | `metadata/de.netscout.app.yml`, `subdir: app`, `gradle: yes`; uses F-Droid's provided Gradle launcher |
| Preparation before the build | `prebuild` downloads the two fixed model inputs and verifies size/SHA-256; the Gradle task generates assets from them offline |
| No concealed binary dependencies | The only `scanignore` exception is the precisely identified Apache-2.0 model file; source/library directories are not broadly ignored |
| Signing | Release stays unsigned and non-debuggable; F-Droid removes debug signing configuration and signs the APK itself |
| Version checks and updates | Literal release version in the Android block; `vX.Y.Z` tags; debug run numbers do not affect release builds |
| Unique app ID | `de.netscout.app` is retained; no other app is forked. This does not prove control of `netscout.de`; a personal domain is recommended but is not a general policy requirement |
| Anti-features | `NonFreeNet` explains the proprietary search services. A personal SearXNG instance is optional; index fallbacks can still use proprietary services |
| No downloaded executable files | Model and runtime are already in the APK; no self-updates or plugin downloads |
| Localized store text | `fastlane/metadata/android/de-DE` and `en-US`, including a changelog per version code; the interface is German |
| Maintenance, functionality, and maturity | Search, relevance assessment, and local favorites have automated tests. The policy specifies at least three months of active development or one of its exceptions, such as demonstrated users; F-Droid retains responsibility for this maturity/usage review |

The model and tokenizer come from the pinned Hugging Face revision. Their
Apache-2.0 license and provenance are documented. ONNX Runtime 1.23.2 comes as an
MIT-licensed Maven Central AAR including native libraries. This recipe does not
rebuild the runtime or model weights. F-Droid decides their acceptance and final
labeling during submission. A passing scanner does not replace manual review.

## The same build in both environments

On F-Droid's build server, the recipe uses its Gradle launcher, OpenJDK, and SDK.
`prebuild` needs access to the pinned Hugging Face revision, including download
redirects. The build itself needs the usual Gradle/Maven downloads, but no model
downloads. A completely network-free Gradle build additionally requires cached
Maven/Gradle dependencies.

GitHub Actions installs the same tool versions and `fdroidserver==2.4.5`.
`scripts/check-fdroid-build.py` lints the submission recipe, obtains the source
checkout through F-Droid's own VCS layer, and runs
`fdroid build --test --stop --no-tarball --scan-binary --refresh-scanner` in a new
working directory. For this test only, the repository and commit are replaced
with the local Git repository and its immutable `HEAD`. This also lets pull
requests check their own changes. The `sudo` setup is omitted there because CI
already installs JDK/Python. All other recipe steps remain, including F-Droid's
removal of the wrapper and signing configuration, source scanning, and APK scanning.

Locally, with OpenJDK 17, Android SDK, Python 3.11+, `fdroidserver==2.4.5`, and
**Gradle 8.11.1 in PATH**, start from a clean, committed checkout:

```sh
python3 -m unittest discover -s scripts/tests -v
python3 scripts/check-fdroid-build.py --work-dir /tmp/netscout-fdroid
python3 scripts/verify-release-apk.py /tmp/netscout-fdroid/unsigned/netscout-release-unsigned.apk
```

The script accepts only an empty working directory. It builds committed changes
only. Unit/device tests also run in the existing GitHub jobs; release verification
checks app ID, fixed version, absence of a signature, the debuggable flag, the
model actually packaged, and license files.

Locally verified on 2026-10-20 for 0.3.5: 153 JVM/app unit tests and three Python
tests passed, debug/release lint passed, and both APKs were built. The debug build
accepts the requested version code 2000 and retains its certificate; release
stays at version code 6. The complete F-Droid run from
`5cb6b2693fdd3beec1ba3828a2d8d2b15a3ce9d0`, with refreshed scanner rules, passes
source scanning, release lint, build, and APK scanning. This check ran with
OpenJDK 17 on Debian with Android SDK 35, outside an official F-Droid VM.

## Submission and new versions

1. Keep source code publicly accessible. For each release, increase `versionName`
   and `versionCode` in `app/build.gradle.kts`, add localized changelogs, and tag
   the verified release commit as `vX.Y.Z`.
2. Copy this recipe to `fdroiddata/metadata/de.netscout.app.yml` and use the tag's
   full commit ID for `Builds.commit`. For a new version, update version fields
   and, if the model changes, its specific `scanignore` checksum. Do not reference
   a debug APK as a release.
3. In `fdroiddata`, run `fdroid lint de.netscout.app` and
   `fdroid build --server --test de.netscout.app`. The GitHub test uses the real
   F-Droid process, but not an official server VM.
4. Add actual screenshots of the current app and submit a merge request to
   [fdroiddata](https://gitlab.com/fdroid/fdroiddata). Explain model provenance,
   the narrow scan exception, and `NonFreeNet`; document maturity or an applicable
   policy exception.

This repository contains the technical preparation. A F-Droid merge request, an
official build-server run, and the inclusion decision are separate steps; publishing
this code does not establish their completion.
