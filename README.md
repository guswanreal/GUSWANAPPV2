# Guswaneditor

Android motion editor built with Kotlin and Jetpack Compose. The editing workspace includes a composition preview, layer timeline, property inspector, GPU effects, editable keyframe graphs, camera rigs and offline tracking.

## Motion editor upgrade

- Portrait keeps the timeline visible above the inspector. Landscape places the preview beside the editing controls, and larger screens use a dedicated inspector column.
- Value and signed Speed Graphs support keyframe dragging, temporal Bezier handles, numerical speed/influence controls and 12 Flow-style curves.
- Cameras can be linked to a Null. Attaching and detaching preserve the view at the current playhead; moving the Null then drives the camera.
- Camera solves interpolate rotation using quaternions. Manual camera position, rotation and zoom remain available after solving.
- Face tracking creates editable position, pitch, yaw and roll keys on a Face Null 3D using bundled ML Kit detection. It runs offline after media import.
- Chroma Key, Luma Key and Color Balance extend the GPU effect catalog. Nine effect stacks include RGB hit, cinema glow, film grain and a handheld camera rig.

Face Z is an estimate from apparent face size. It is suitable for relative attachments, not metric depth measurement or a face mesh. These controls are inspired by AM, AE and Flow; this project does not implement their complete proprietary feature sets. Existing MP4 export renders the composition without an audio mixing pipeline.

## Use tracking and rigs

Import a video, select its layer, open **Tracking > Face 3D**, then choose **Analisis wajah & buat Null 3D**. The resulting Null contains ordinary editable animation tracks. Select an attachment, link it through **Parent**, then use **Pusatkan pada parent** when it should sit on the tracked face. Keep the attachment within the tracked time range. Missing detections and analysis errors are shown explicitly.

For a camera rig, select a camera and use **Transform > Buat Null pengendali kamera**. Animate the selected Null or add the **Handheld rig** effect stack. A camera may be parented to a Null; it cannot act as a parent itself.

## Build and test in cloud

The workflow in `.github/workflows/android-cloud.yml` builds an APK and unit tests on GitHub Actions, then installs those APKs in a separate cloud emulator job. Device tests do not compile the project again. They exercise rendering, tracking, hierarchy, persistence and UI in portrait, landscape and at 200% font size. Artifacts include test logs and UI captures.

Open the pull request's **Checks**, select **Android cloud build**, and download **android-build-results** for the APK. The cloud APK uses application ID `com.example.myapplication.preview` and label **Guswaneditor Preview**, so it can be installed alongside the Android Studio version.

Cloud builds require Java 25, SDK platform `android-37.0`, build tools `36.1.0` and the committed Gradle wrapper. For a manual build on a machine with those dependencies:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest -PcloudPreview=true --no-daemon --max-workers=2 --console=plain
```

On Windows, use `gradlew.bat` with the same arguments. Local compilation and emulators are optional; the current workflow is configured to keep those jobs off the owner's laptop.

Design rationale is in [docs/editor-design.md](docs/editor-design.md). Validation evidence and supported limits are recorded in [docs/editor-validation.md](docs/editor-validation.md).
