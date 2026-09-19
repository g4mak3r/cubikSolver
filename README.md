# cubikSolver

Offline Android Rubik's Cube scanner and solver for 3x3 and 5x5 cubes.

## Current version

0.11.0

## Stack

- Kotlin / Jetpack Compose
- CameraX + OpenCV
- min2phase for the reduced 3x3 stage
- Constructive three-cycle reduction for 5x5 (no native search or large table pack)
- Android 10+ (minSdk 29)
- JDK 17 / Gradle 8.9

## Build

Open the repository root in Android Studio, use JDK 17, sync Gradle and run the `app` configuration on an arm64 Android device.

Command-line build:

```bash
gradle testDebugUnitTest assembleDebug
```

The 5x5 solver works from scanned stickers, validates both centre orbits and oriented
wings, and verifies every returned solution by replay. It prioritizes reliable,
fast offline solving over short solutions (typically several hundred moves).
Its three small setup tables are built once and shared between solves.

The app is designed to work offline at runtime.

Third-party attribution and license notices are in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
