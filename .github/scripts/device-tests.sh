#!/usr/bin/env bash
set -euo pipefail
app_id=com.example.myapplication.preview
mkdir -p work-ui
adb shell settings put secure show_ime_with_hard_keyboard 1
adb shell settings put system font_scale 1.0
adb shell wm user-rotation lock 0
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
result=0
run_tests() {
  local label="$1"
  shift
  adb shell am force-stop "$app_id"
  sleep 1
  timeout 420 adb shell am instrument -w -r -e timeout_msec 120000 "$@" "$app_id.test/androidx.test.runner.AndroidJUnitRunner" | tee "work-ui/instrumentation-$label.log"
  grep -Eq '^OK \([0-9]+ tests?\)' "work-ui/instrumentation-$label.log"
}
run_tests portrait || result=$?
adb shell settings put system accelerometer_rotation 0
adb shell wm user-rotation lock 1
run_tests landscape -e class com.example.myapplication.EditorWorkspaceUiTest || result=$?
adb shell wm user-rotation lock 0
adb shell settings put system font_scale 2.0
run_tests large-type -e class com.example.myapplication.EditorWorkspaceUiTest || result=$?
for screen in editor graph; do
  for viewport in portrait landscape; do
    for type in default large-type; do
      name="$screen-$viewport-$type.png"
      if adb shell run-as "$app_id" ls "files/qa/$name" > /dev/null 2>&1; then
        adb exec-out run-as "$app_id" cat "files/qa/$name" > "work-ui/$name"
      fi
    done
  done
done
adb logcat -d -v threadtime -s AndroidRuntime:E GuswanFx:E EditorUiQa:I TestRunner:I > work-ui/runtime-log.txt
exit "$result"

