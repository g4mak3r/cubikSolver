#!/usr/bin/env bash
# Preserve captures when an assertion fails so the failing layout can be inspected.
gradle --no-daemon -Pemulator connectedDebugAndroidTest
design_status=$?
adb pull /sdcard/Android/data/com.cubecraft.solver/files/ui-captures ui-captures
exit "$design_status"
