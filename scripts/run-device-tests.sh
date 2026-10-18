#!/usr/bin/env bash
# Verify the installed APK's actual entry point after the isolated Compose/Keystore tests.
set -uo pipefail
./gradlew :app:connectedDebugAndroidTest --stacktrace
test_status=$?
if [ "$test_status" -eq 0 ]; then
    mkdir -p app/build/outputs/ui-screenshots
    adb install -r app/build/outputs/apk/debug/app-debug.apk || exit 1
    adb shell am start -W -n de.netscout.app/.MainActivity || exit 1
    adb shell pidof de.netscout.app > /dev/null || exit 1
    adb shell screencap -p /data/local/tmp/netscout-start.png || exit 1
    adb pull /data/local/tmp/netscout-start.png app/build/outputs/ui-screenshots/netscout-start.png || exit 1
fi
exit "$test_status"
