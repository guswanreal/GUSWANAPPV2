#!/usr/bin/env bash
set -euo pipefail
app_id=com.example.myapplication.preview
mkdir -p work-ui
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
result=0
run_tests() {
  local label="$1"
  shift
  timeout 420 adb shell am instrument -w -r -e timeout_msec 120000 "$@" "$app_id.test/androidx.test.runner.AndroidJUnitRunner" | tee "work-ui/instrumentation-$label.log"
  grep -Eq '^OK \([0-9]+ tests?\)' "work-ui/instrumentation-$label.log"
}
run_tests portrait || result=$?
if [ "$result" -eq 0 ]; then
  adb shell settings put system accelerometer_rotation 0
  adb shell settings put system user_rotation 1
  run_tests landscape -e class com.example.myapplication.EditorWorkspaceUiTest || result=$?
  adb shell settings put system user_rotation 0
  adb shell settings put system font_scale 2.0
  run_tests large-type -e class com.example.myapplication.EditorWorkspaceUiTest || result=$?
fi
for screen in editor graph; do
  for viewport in portrait landscape; do
    for type in default large-type; do
      name="$screen-$viewport-$type.png"
      adb exec-out run-as "$app_id" cat "files/qa/$name" > "work-ui/$name" 2>/dev/null || rm -f "work-ui/$name"
    done
  done
done
adb logcat -d -v threadtime -s AndroidRuntime:E GuswanFx:E EditorUiQa:I TestRunner:I > work-ui/runtime-log.txt
exit "$result"

