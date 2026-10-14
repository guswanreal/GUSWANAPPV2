# Android editor continuation

Status: work in progress, not a verified release.

Owner request (Indonesian): improve the UI substantially, make the effects and editing workflow closer to Alight Motion and After Effects, fix movement glitches, add face 3D tracking, connect a Null to a 3D camera, and make the graph editor work like After Effects and Flow.

The owner explicitly selected antislop DURING. References were the existing editor, Alight Motion, After Effects and Flow. Design decisions are in docs/editor-design.md. Preserve the existing product identity. Skills requested: antislop-ui, antislop-code, antislop-human, antislop-layoutmobile, frontend-design and skill-creator. Skill-creator was read; the owner did not ask for an additional global skill, so none was created.

Latest constraint: the laptop became slow. The owner asked whether cloud can be used. Do not launch local emulators or heavy local builds while preparing the cloud continuation. The Gradle daemon was stopped and no emulator process remained at the last check. Do not mistake the original Windows file paths for paths available in a cloud environment.

Original project is a Windows Android Studio checkout. The owner supplied https://github.com/guswanreal/GUSWANAPPV2.git for cloud work. Existing source is imported to main; draft changes are on codex/editor-motion-upgrade. GitHub Actions handles builds and emulator tests while source edits remain lightweight on the laptop. No separate Codex Cloud chat has been started.

## Existing application

Kotlin / Jetpack Compose Android application com.example.myapplication, minSdk 35, compileSdk 37, targetSdk 37. OpenCV 4.12.0, Compose BOM 2025.12.00, Espresso 3.7.0. Main files: engine/Timeline.kt, Scene3D.kt, LayerHierarchy.kt, CompositionRenderer.kt, Effects.kt, ProjectStore.kt, ui/editor/EditorState.kt, GraphEditor.kt, Panels.kt, VideoEditorScreen.kt. There are existing Android instrumentation tests for hierarchy, persistence/export, camera solve and UI. The existing GPU effects catalog already contains many effects; inspect it before adding duplicate effects.

## Draft changes already saved

- Camera layers may be parented to a Null, with existing 3D compensation. A createCameraRig state operation and Transform panel button were added.
- CameraSolve3D now interpolates translation and quaternion rotation between solved poses, rather than jumping to the nearest pose.
- Added RotationInterpolation.kt and CurvePresets.kt. Flow curves bake into existing keyframe influence/speed storage; negative scalar velocity is retained in the Speed Graph.
- Added draggable temporal handles, fitted keyframe range and CurveControls.kt with numeric editing and 12 Flow-style presets.
- Added bundled com.google.mlkit:face-detection:16.1.7, FaceTracker3D.kt, FaceTrackingPanel.kt, persisted face solve data and creation of a tracked Face Null 3D. Z is relative depth estimated from face size; do not claim metric reconstruction or a face mesh.
- Changed position/anchor inspector readouts to use actual project dimensions. Added finite-value checks, a zero-period loop guard and source-time adjustment on footage trim.
- Raised secondary text and control-border contrast. Full adaptive workspace redesign and visual verification are still pending.

## Required follow-through

1. Compile all drafts and repair errors before claiming any feature works. The only build attempt in this run FAILED with unresolved COMPOSITION_TEXT_CACHE and drawComposition in VideoExporter.kt. It occurred while source work was in progress; root cause was not established. No final APK from this run exists.
2. Verify the new TrackingPanel route in VideoEditorScreen. Keyboard imports were staged in VideoEditorScreen but the corresponding layout/shortcut code was not implemented.
3. Finish the portrait, landscape and tablet workspace layout; keep timeline context visible when editing properties, provide IME-safe scrolling, visible keyboard focus and 44 dp targets. Prefer source changes through normal patches, not source-rewriting helper scripts.
4. Verify ML Kit cancellation and serialization. Task suspension now waits for detector completion before bitmap recycling or detector closing; cancellation must stop before the next frame.
5. Test camera reparent/attach/detach at nonidentity transforms, camera zoom and camera solve rigs, direct dragging at snapped playhead time, signed speeds, overshoot, dense tracked keys, trim/source times and round-trip persistence. Check graph handles coincident with keys and cancellation of frozen axis ranges. Validate the 2D camera fallback and 3D plane renderer use compatible projections when attaching/detaching a rig.
6. Review estimated face Z/XY consistency when relative depth is clamped, identity switching and missing-face gaps. Do not hide tracking failure or cover missing frames with a fabricated result. Test with real face footage plus blank footage and cancellation.
7. Implement requested effect/preset improvements with real rendering and layer-type eligibility. Preserve GPU shader compile tests and verify decoded export pixels, not just successful compilation. A complete clone of every proprietary AE/AM effect has not been implemented.
8. Build debug APK and run meaningful instrumentation on an emulator in cloud if available; record anything the cloud environment cannot validate. Do not claim testing on the user's physical phone unless it actually happened.

Validation command once the cloud JDK and Android SDK are configured: ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest --console=plain. Use cloud emulator/device infrastructure for instrumentation; never silently fall back to the owner's overloaded laptop.

The source package omits build outputs, .gradle, .idea, local.properties, local logs, backups and user media. It preserves source, tests, bundled resources, docs, Gradle configuration and wrapper.

