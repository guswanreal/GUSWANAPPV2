# Cloud execution

The owner asked to keep heavy builds and emulators off the laptop. Source edits remain in the Windows checkout; GitHub Actions performs compilation and device tests using `.github/workflows/android-cloud.yml`. No separate Codex Cloud chat has been created.

Repository: https://github.com/guswanreal/GUSWANAPPV2

Upgrade branch: `codex/editor-motion-upgrade`; draft review: https://github.com/guswanreal/GUSWANAPPV2/pull/1

The owner selected antislop DURING. Design rationale is in `docs/editor-design.md`; validation and feature limits are in `docs/editor-validation.md`. Continue from the actual source and latest workflow artifacts. Do not reuse an older build's results as evidence for a newer commit.

Do not start a local emulator or heavy Gradle build. Use Java 25, SDK `android-37.0` and build tools `36.1.0` in the cloud. The cloud preview's separate application ID allows installation alongside the owner's existing app.
