# Editor upgrade validation

PASS: cloud build, recorded device workflows, full-device visual review and the scoped antislop DURING gate completed on 15 October 2026 (Asia/Makassar).

## Changes

The editor keeps timeline context during property editing, gives graphs their own working area, supports numerical temporal handles and 12 curve presets, links cameras to Nulls, and creates editable Face Null 3D tracks. Three GPU treatments extend the existing 62-effect catalog; nine preset stacks provide editable combinations. Counts come from the source enums and preset lists.

Movement fixes cover interpolated solved-camera poses, view-preserving camera reparenting, signed graph velocity, duplicate loop times, stable fitted axes during pointer dragging, editing at the visible snapped frame, and footage trim/source time consistency. The renderer skips zero-sized layout frames during keyboard transitions.

## Evidence

Tested code commit: `3104ecf7d7362d11b8f5a4270ff52f0656a41f36`. The documentation commit following it changes Markdown only. [Android cloud build run 38605343436](https://github.com/guswanreal/GUSWANAPPV2/actions/runs/38605343436) completed successfully for both build and device-tests jobs. Builds and device tests ran on GitHub Actions.

Build artifact: `11914824137`, `android-build-results`. Device evidence: `11915468642`, `android-device-test-results`. The preview APK has application ID `com.example.myapplication.preview`, version `1.0-cloud-preview`, minimum Android API 35 (Android 15), and size 247,297,883 bytes. SHA-256: `022A3EBDF549E922A23D08DA1E7BF3CE4E2777F7705BE257388D7746251EF5E6`.

- JVM: 7 tests, including six curve, velocity and quaternion checks.
- Android API 35 emulator: 38 distinct instrumentation tests.
- Additional UI runs: four tests in actual landscape and four at 200% font size. The test checks activity orientation and captured bitmap orientation.
- Graph UI: all 12 presets, value/speed modes, fitted/full ranges, key selection, influence entry, numeric validation, and pointer movement.
- Editor UI: Null creation, offline motion creation, camera rig creation, camera detach, handheld effect stack, RGB hit, three new effects, face/camera tracking tabs and a visible missing-face error.
- Engine: hierarchy/cycles, reparent/detach compensation, 2D parallax compatibility, solved-camera controls, persistence/undo, media recovery and MP4 export.
- ML Kit: real detection on the licensed Grace Hopper sample and a sequence of translated sample frames; blank frames are rejected. This does not establish accuracy on every real moving-head video.
- GPU: runtime shader compilation and decoded Chroma Key export pixels. Green is removed and the blue composition background remains in the encoded frame.

The test runner reports `OK (38 tests)`, `OK (4 tests)` and `OK (4 tests)`: 46 device test executions, 38 distinct tests. The two JVM XML reports contain 1 and 6 tests with zero failures or errors. These are the latest run's results.

### Recorded click-through

| Action | Recorded result |
| --- | --- |
| Add layer > Null; Parent selection | Null appears and valid parenting changes the hierarchy; invalid cycles are rejected. |
| Buat Motion | Offline motion editing opens through the actual workspace action. |
| Graph > each of 12 Flow curves | Selected segment updates; Value/Speed and Fit/Full controls change the plotted state. |
| Graph key menu, numeric influence, pointer drag | Key selection opens; influence is applied; a two-sample drag moves the key by the expected time delta. |
| Numeric dialog > NaN; Escape | Apply rejects non-finite input; Escape dismisses the dialog without committing it. |
| Effects > RGB hit; Handheld rig | The chosen stack adds editable effects to an eligible layer. |
| Effect search > Color Balance, Chroma Key, Luma Key | Search narrows real results, keyboard closes, and tapping each result adds the effect. |
| Camera > Buat Null pengendali kamera; Parent > Tidak ada | A Null rig is created; camera selection supports detaching while preserving the current view. |
| Tracking > Face 3D; Camera Solve | Both modes open; blank footage produces a visible missing-face error. Real detector and persistence behavior are verified separately with the licensed fixture. |

These paths were exercised in portrait, landscape and 200% type by `EditorWorkspaceUiTest`, with Null/Motion paths additionally covered by `NullObjectUiTest`. Engine/export tests supply the associated state and pixel assertions. This is a scoped check of the changed workspace, not an accessibility certification of the entire application.

## Visual and contrast review

Reviewed nine complete-device captures of editor, graph and effects in portrait, landscape and 200% font size from the run above. Status/navigation bars are clear, graph canvases and axes are visible, and searched effect results remain reachable in landscape. Tests assert minimum graph height, configuration and screenshot orientation. Source and device workflows cover numeric input, visible focus, Escape dismissal and 44 dp controls. Horizontal toolbar/category strips scroll as navigation controls. Large-type and short-landscape inspectors scroll to their remaining controls.

Contrast uses the WCAG relative-luminance formula. Verified pairings:

| Pair | Ratio |
| --- | ---: |
| EDEDEF / 141416 | 15.74:1 |
| AAAAB5 / 27272C | 6.46:1 |
| C0C0CA / 27272C | 8.23:1 |
| FF6A2B / 27272C | 5.20:1 |
| 7CA7F7 / 27272C | 6.18:1 |
| 777782 / 27272C, control boundary | 3.36:1 |
| 0A0A0B / FF6A2B, primary control text | 6.92:1 |
| EDEDEF / 333333, worst-case white image beneath the 80% black scrim | 10.81:1 |

## Antislop DURING delivery gate

Scope: the motion editor workspace and controls changed by this upgrade. The owner's direction is documented in `editor-design.md`.

| Item | Status | Evidence |
| --- | --- | --- |
| R-02 | PASS | UI text scan contains no em dash. |
| R-03 | PASS | Complete-device captures and orientation/graph-height assertions verify the supported viewports. |
| R-17 | PASS | Effect counts, pose coverage, analysis progress and time values derive from actual state. |
| R-18 | PASS | No testimonials or invented customer content. |
| R-23 | PASS | Existing identity retained; the added photograph is a licensed test-only ML Kit fixture. |
| R-24 | PASS | Layer/panel/key menus route to actual editor state. |
| R-25 | PASS | Contrast calculations above cover editor text, readouts, boundaries and the image scrim. |
| R-26 | PASS | State handlers audited; changed controls exercised through recorded device workflows. |
| R-27 | PASS | Empty effects/graphs, analysis progress, import errors and tracking failures have visible text states. |
| R-28 | PASS | No FAQ is present in this workspace. |
| R-32 | PASS | Native menus/buttons, focus borders, numerical alternatives, shortcut handlers and tested Escape dismissal. |
| R-33 | PASS | Features were edited through source patches. |
| R-34 | PASS | The fixed creative-tool dark workspace uses one coherent theme. |
| R-35 | PASS | Cloud build, unit tests, device click-through, gesture verification and full-device captures. |
| R-36 | PASS | No physical-device performance, security certification or full AE parity claim. |
| R-37 | PASS | Owner references and ENERGY 2 / RHYTHM 2 / MOTION 1 were set before implementation. |
| R-38 | PASS | Catalog entries, controls and tracking results are implemented source behavior. |
| R-01 | PASS | Curve fill communicates the value area; glows/gradients in samples are the effects being previewed. |
| R-04 | PASS | Native action icons match their functions; star thumbnails depict an actual star shape. |
| R-06 | PASS | Native sans controls; compact tabular metadata, numerical values and timeline labels use monospace. |
| R-07 | PASS | Grids mark measured graph coordinates or explicitly enabled composition guides. |
| R-08 | PASS | Directional icons perform navigation or reordering. |
| R-09 | PASS | Selected chips express editing modes and filters; no decorative marketing badge. |
| R-10 | PASS | Opaque workspace and panels, with no glass stack. |
| R-12 | PASS | Elevation is reserved for floating actions and modal separation. |
| R-13 | PASS | Highlight marks the active control/playhead; glow belongs to image treatments. |
| R-14 | PASS | Preview, transport, timeline, graph and inspector have different content-driven sizes. |
| R-19 | PASS | Short transitions and press/selection feedback support editing; sample thumbnails remain still. |
| R-22 | PASS | Thumbnails render the supported shapes/models/effects; no unrelated decorative illustration. |
| Liveliness: dials | PASS | ENERGY 2 / RHYTHM 2 / MOTION 1 recorded in the design direction. |
| Liveliness: consistency | PASS | Dense editing surfaces with restrained transitions and one orange action accent. |
| Liveliness: focal point | PASS | Composition, graph or selected effect results lead their respective editing states. |
| Liveliness: spacing | PASS | Chrome, timeline and property groups form the hierarchy. |
| Liveliness: accent | PASS | Orange identifies selection, primary actions, handles and playhead. |
| Liveliness: motif | PASS | Repeated keyframe/curve vocabulary comes from animation editing. |
| Liveliness: design read | PASS | Mobile motion-design workspace direction recorded before the redesign. |
| C-1 | PASS | Major choices have reasons in `editor-design.md`. |
| C-2 | PASS | Changed actions have working state handlers and device evidence. |
| C-3 | PASS | Every workspace region serves previewing, timing, selection or property editing. |
| C-4 | PASS | Portrait, actual landscape, 200% type, keyboard and error states are exercised. |
| C-5 | PASS | Only actual catalog/state/test measurements are reported. |
| R-05 | PASS | Editing workspace composition follows task hierarchy. |
| R-11 | PASS | Preview, compact controls, graph and panels use different corner treatments. |
| R-15 | PASS | Action labels describe their result: tracking, adding effects, creating a rig or exporting. |
| R-16 | PASS | UI text scan contains no generic AI marketing claims. |
| R-20 | PASS | The owner's orange identity and animation controls remain recognizable. |
| R-21 | PASS | Dark surroundings support judging footage in a creative editor. |
| R-29 | PASS | Graphite/white with orange; blue/danger colors encode control types or errors, and footage supplies content color. |
| R-30 | PASS | Reference-inspired workflow uses the project's existing identity and native Android controls. |
| R-31 | PASS | Visual reasons are documented for palette, type, spacing, corners, icons and motion. |

## Supported limits

Face tracking is imported-video pose tracking with relative depth inferred from face size. It is not a metric reconstruction, live-camera pipeline or dense face mesh. Tracking footage and face attachments use camera-ignore mode; keep attachments within the solved range. The 3D compositor supports flat image/text/shape planes and camera rigs. Reparenting preserves the current playhead view, rather than baking the old parent's full animation. MP4 export does not mix audio. The catalog is inspired by AM/AE workflows and does not reproduce every proprietary effect or plug-in.

Testing covers the cloud emulator and documented fixtures, not every physical device, footage type, model or GPU. No guaranteed playback FPS or claim that every possible bug is eliminated is made.
