#!/usr/bin/env bash
# Drives the app on a running emulator with adb, saves screenshots + UI dumps to tests/ui/out
# and fails if a step cannot be done, if texts overlap, or if the app crashed.
set -uo pipefail
API="${1:-0}"
cd "$(dirname "$0")"
OUT="$PWD/out"; rm -rf "$OUT"; mkdir -p "$OUT"
PKG=io.github.samaliyan.timeatlas
APK=../../app/build/outputs/apk/release/app-release.apk
FAILS=0
log() { echo "[ui] $*" | tee -a "$OUT/report.txt"; }
fail() { log "FAIL: $*"; FAILS=$((FAILS+1)); }

dump() { adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; adb exec-out cat /sdcard/ui.xml > "$OUT/.ui.xml"; }
shot() {      # shot NAME  → screenshot, UI dump and overlap check
  sleep "${2:-1.2}"
  adb exec-out screencap -p > "$OUT/$1.png"
  dump; cp "$OUT/.ui.xml" "$OUT/$1.xml"
  python3 check_layout.py "$OUT/$1.xml" "$PKG" >> "$OUT/report.txt" || fail "layout problem on $1"
  log "shot $1"
}
# find "text or content-desc" [regex]  → prints "x y" of the node centre, or nothing
nfind() { dump; python3 find_node.py "$OUT/.ui.xml" "$1" "${2:-}"; }
tap() {       # tap LABEL [regex]
  local p; p=$(nfind "$1" "${2:-}")
  if [ -z "$p" ]; then fail "cannot find '$1'"; return 1; fi
  adb shell input tap $p; sleep 0.8; return 0
}
tap_any() { for l in "$@"; do p=$(nfind "$l"); if [ -n "$p" ]; then adb shell input tap $p; sleep 1; return 0; fi; done; fail "none of: $*"; return 1; }
longpress() { local p; p=$(nfind "$1"); [ -n "$p" ] || { fail "cannot find '$1'"; return 1; }; set -- $p; adb shell input swipe $1 $2 $1 $2 900; sleep 0.8; }
expect() { [ -n "$(nfind "$1" "${2:-}")" ] && log "ok: sees '$1'" || fail "expected '$1' on screen"; }
expect_not() { [ -z "$(nfind "$1" "${2:-}")" ] && log "ok: no '$1'" || fail "did not expect '$1'"; }
back() { adb shell input keyevent KEYCODE_BACK; sleep 0.8; }
launch() { adb shell am start -W -n "$PKG/.MainActivity" > /dev/null; sleep 2; }

log "API $API"
adb install -r "$APK" || { echo "install failed"; exit 1; }
adb logcat -c
adb shell settings put system time_12_24 24

# 1. First start: default cities, live mode
launch
shot 01_main_first_start
expect "Tehran"; expect "London"; expect "New York"; expect "Melbourne"
expect "Now in Tehran" "^Now in Tehran"

# 2. Drag the ruler: switches to a chosen time
R=$(nfind "Time ruler" "^Time ruler")
if [ -n "$R" ]; then set -- $R; adb shell input swipe $(( $1 + 300 )) $2 $(( $1 - 300 )) $2 700; else fail "ruler not found"; fi
shot 02_ruler_dragged 2
expect "Converting from Tehran"; expect "Now"

# 3. Back to now
tap "Now"
shot 03_back_to_live
expect "Now in Tehran" "^Now in Tehran"

# 4. The user's example: pick a time in Melbourne
tap "Melbourne"
shot 04_time_picker
tap_any "OK" "Ok" "ok"
shot 05_from_melbourne
expect "Converting from Melbourne"

# 5. Date picker
tap "date" "^Change date"
shot 06_date_picker
back

# 6. Add a city by search
tap "Add city"
adb shell input text tokyo; sleep 1
shot 07_search_tokyo
tap "Tokyo"
shot 08_tokyo_added 2
expect "Tokyo"

# 7. Search with no result, then a country and an abbreviation
tap "Add city"
adb shell input text zzqx; sleep 1
shot 09_no_results
expect "No matching city or time zone"
for i in 1 2 3 4; do adb shell input keyevent KEYCODE_DEL; done
adb shell input text usa; sleep 1
shot 10_search_usa
expect "Los Angeles"
back; back
launch

# 8. Long press → set London as home
longpress "London"
shot 11_row_menu
tap "Set as home city"
shot 12_london_home

# 9. Edit list: reorder by dragging, then remove Tokyo
tap "More options"
tap "Edit list"
shot 13_edit_list
H=$(dump; python3 find_node.py "$OUT/.ui.xml" "Drag to reorder" "" first)
if [ -n "$H" ]; then set -- $H; adb shell input swipe $1 $2 $1 $(( $2 + 450 )) 1800; else fail "drag handle not found"; fi
shot 14_reordered 1.5
tap "Remove Tokyo"
shot 15_tokyo_removed
expect_not "Tokyo"
tap "Done"

# 10. Settings and widget settings
tap "More options"
tap "Settings"
shot 16_settings
tap "Widget cities and style"
shot 17_widget_settings 2
tap "Dark"
shot 18_widget_dark
tap "Auto"
if [ -n "$(nfind "Add widget to home screen")" ]; then
  tap "Add widget to home screen"
  shot 19_pin_widget_dialog 2
  tap_any "Add automatically" "ADD AUTOMATICALLY" "Add to home screen" "ADD TO HOME SCREEN" "Add" "ADD"
  sleep 2
  adb shell input keyevent KEYCODE_HOME
  shot 20_home_screen_widget 3
  launch
else
  log "pin widget not supported by this launcher"
fi

# 11. 12-hour format and dark theme from the app settings
launch
tap "More options"; tap "Settings"
tap "Time format"; tap "12-hour"
tap "Theme"; tap "Dark"
shot 21_settings_dark
back
shot 22_main_dark_12h 2
expect "AM|PM" '(AM|PM)$'

# 12. Large text, landscape, phone dark mode with "phone setting"
adb shell settings put system font_scale 1.3
launch
shot 23_large_text 2
adb shell settings put system font_scale 1.0
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
sleep 2
shot 24_landscape 2
adb shell settings put system user_rotation 0
sleep 1

# 13. Update check button (the test build has a huge build number → "latest version" or "could not reach")
tap "More options"; tap "Settings"
tap "Check for updates now"
shot 25_update_check 0.6
back

# 14. Crash check
adb logcat -d -b crash > "$OUT/crash.txt" 2>/dev/null
if grep -q "$PKG" "$OUT/crash.txt"; then fail "app crashed (see crash.txt)"; else log "ok: no crash"; fi
adb logcat -d | grep -iE "AndroidRuntime|$PKG|StrictMode" | tail -200 > "$OUT/logcat.txt"
rm -f "$OUT/.ui.xml"

log "failures: $FAILS"
[ "$FAILS" -eq 0 ]
