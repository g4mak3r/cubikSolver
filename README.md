# cubikSolver

Offline Android Rubik's Cube scanner and solver for 3x3 and 5x5 cubes.

## Current version

0.10.4

## Stack

- Kotlin / Jetpack Compose
- CameraX + OpenCV
- min2phase for the reduced 3x3 stage
- C++ / JNI acceleration for 5x5 reduction
- Android 10+ (minSdk 29)
- JDK 17 / Gradle 8.9 / NDK 27.0.12077973

## Build

Open the repository root in Android Studio, use JDK 17, sync Gradle and run the `app` configuration on an arm64 Android device.

Command-line build:

```bash
gradle testDebugUnitTest assembleDebug
```

The app is designed to work offline at runtime.

Third-party attribution and license notices are in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
