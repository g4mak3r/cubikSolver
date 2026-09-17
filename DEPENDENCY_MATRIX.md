# CUBECRAFT v0.2.2 - Android compatibility matrix

This build intentionally targets a conservative Android 15 / API 35 toolchain
so it can be built with JDK 17 and installed on Android 10+ devices.

- JDK: 17
- Android Gradle Plugin: 8.7.3
- Gradle: 8.9
- compileSdk: 35
- targetSdk: 35
- minSdk: 29
- Kotlin: 2.0.21
- Compose BOM: 2024.12.01
- Activity Compose: 1.10.1
- AndroidX Core KTX: 1.15.0
- CameraX: 1.4.2
- Lifecycle: 2.8.7
- OpenCV: 4.13.0
- min2phase: 0.19.2

Why the downgrade from the earlier v0.2.1 dependency set:
the 2026 Compose / Activity / CameraX artifacts require compileSdk 36-37
and newer Android Gradle Plugin releases. v0.2.2 pins a mutually compatible
API-35-era stack instead of forcing the project onto preview/newer SDK tooling.
