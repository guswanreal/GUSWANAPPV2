#!/usr/bin/env bash
set -uo pipefail
result=0
./gradlew :app:connectedDebugAndroidTest --no-daemon --max-workers=2 --console=plain || result=$?
if [ "$result" -eq 0 ]; then
  adb shell settings put system accelerometer_rotation 0
  adb shell settings put system user_rotation 1
  ./gradlew :app:connectedDebugAndroidTest --no-daemon --max-workers=2 --console=plain -Pandroid.testInstrumentationRunnerArguments.class=com.example.myapplication.EditorWorkspaceUiTest || result=$?
  adb shell settings put system user_rotation 0
  adb shell settings put system font_scale 2.0
  ./gradlew :app:connectedDebugAndroidTest --no-daemon --max-workers=2 --console=plain -Pandroid.testInstrumentationRunnerArguments.class=com.example.myapplication.EditorWorkspaceUiTest || result=$?
fi
mkdir -p work-ui
for screen in editor graph; do
  for viewport in portrait landscape; do
    for type in default large-type; do
      name="$screen-$viewport-$type.png"
      adb exec-out run-as com.example.myapplication cat "files/qa/$name" > "work-ui/$name" 2>/dev/null || rm -f "work-ui/$name"
    done
  done
done
adb logcat -d -s AndroidRuntime:E GuswanFx:E > work-ui/runtime-errors.txt
exit "$result"

