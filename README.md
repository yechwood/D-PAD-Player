# D-PAD Player 🎵

D-PAD Player is a lightweight, functional Android media player demo built with Kotlin and ExoPlayer.

## LG Exalt VN220 compatibility

The current branch contains the Android 6.0 compatibility work for the LG Exalt LTE VN220, including a lower target SDK and removal of newer foreground-service manifest requirements that are not applicable to Android 6.

Builds are produced by the GitHub Actions Android CI workflow.

## Build and Install

```bash
./gradlew clean assembleDebug
```

APK output:

`app/build/outputs/apk/debug/dpadmp3.apk`
