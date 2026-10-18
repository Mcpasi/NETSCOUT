#!/usr/bin/env bash
# Preserve the test exit code while collecting screenshots, including after failures.
set -uo pipefail
./gradlew :app:connectedDebugAndroidTest --stacktrace
test_status=$?
mkdir -p app/build/outputs/ui-screenshots
adb pull /sdcard/Android/data/de.netscout.app/files/screenshots/. app/build/outputs/ui-screenshots/ || true
exit "$test_status"
