#!/usr/bin/env bash
# Preserve captures when an assertion fails so the failing layout can be inspected.
timeout --signal=TERM --kill-after=30s 6m gradle --no-daemon -Pemulator connectedDebugAndroidTest
design_status=$?
adb pull /sdcard/Android/data/com.cubecraft.solver/files/ui-captures ui-captures
if [ "$design_status" -ne 0 ]; then
  adb logcat -d -t 500 > design-logcat.txt
fi
exit "$design_status"
