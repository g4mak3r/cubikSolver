# CUBECRAFT v0.4.2

## v0.4.3 scanner scale fix

The fixed white NxN guide is now the authoritative capture region. OpenCV AR tracking is only a visual assist and can no longer resize or move the cells used for color sampling. Tiny quadrilaterals are rejected by area, side-length and center-position guards before they can become an AR face. This specifically prevents one sticker/cubie from being mistaken for the entire cube face.


## v0.4.2 compile hotfix / QA pass

Fixed the malformed Kotlin `when` branches in the AR corner-accent renderer (`else -> -1f`).
Before packaging this archive, every Kotlin source file was parsed with `kotlinc` and the dependency-free core/scanner-model/5x5-history solver subset was compiled successfully.


Android 10+ offline Rubik's Cube scanner / digital twin / solver assistant.

## v0.4 scanner reliability + live AR pass

This release fixes the real multi-face scanner bug found on-device in v0.3 and removes contour recognition from the capture path completely.

### Critical second-face fix
`PreviewView` / `AndroidView.factory` is long-lived. In v0.3 its analyzer captured the callback from the first Compose scan step. After FACE 1 advanced, frames could continue writing into the old FACE 1 state, leaving FACE 2 with no current observation and an unusable Capture button.

v0.4 routes analyzer results through `rememberUpdatedState`, so the same camera stream always calls the current scan-step callback. The camera is not unnecessarily rebound between faces.

### Capture has no recognition lock
- CAPTURE is never enabled/disabled by contour detection.
- If a fresh frame already exists, tapping CAPTURE uses it immediately.
- If the analyzer has not delivered a fresh frame yet, the tap is queued and the very next frame is captured automatically.
- A short 280 ms step guard prevents the previous face's stale frame being consumed after advancing.
- Recognition/tracking is now only a visual and geometric enhancement.

### Live AR scanner
- The detected face quadrilateral is temporally smoothed so the frame visually sticks to the cube instead of jittering.
- Brief contour loss is bridged for ~0.5 s, useful through reflections and motion blur.
- The N×N grid is perspective-aware and follows the tracked face.
- Every sticker gets a live color chip and provisional W/Y/R/O/G/B label.
- Sticker color chips are drawn from the actual sampled camera pixels.
- Cells with low uniformity/exposure/color confidence are highlighted yellow or red before capture.
- Final classification still uses the six real center colors, so provisional live labels do not replace center calibration.
- When no contour is found, the same live colors/uncertainty overlay falls back to the fixed guide square and CAPTURE still works.

### Camera reliability
- CameraX `PreviewView.ImplementationMode.COMPATIBLE` remains enabled for older/Huawei devices.
- Analysis uses `STRATEGY_KEEP_ONLY_LATEST` so it cannot build a stale-frame queue.
- Analyzer exceptions discard only the bad frame instead of stopping the stream.
- Center AF/AE metering is requested after startup but never gates capture.

## Toolchain
- JDK 17
- Gradle 8.9
- Android Gradle Plugin 8.7.3
- compileSdk / targetSdk 35
- minSdk 29 (Android 10)
- Kotlin 2.0.21
- CameraX 1.4.2
- OpenCV 4.13.0

## Open
1. Extract the archive into a new folder.
2. Open the **CUBECRAFT_v0.4.1** root folder in Android Studio.
3. Set Gradle JDK to Microsoft JDK 17.
4. Sync Gradle.
5. Connect the Huawei and Run `app`.

Do not copy v0.4 files over an already-open v0.3 `.idea` directory. Opening it as a fresh project avoids stale Android Studio project state.

## Scan workflow
Scan FRONT, RIGHT, BACK, LEFT, UP and DOWN while following the orientation text. The green moving frame means geometry tracking is active; it is not an unlock condition. White fixed geometry means guided mode. In either case, pressing CAPTURE is valid.

The review screen remains part of the workflow because even good live computer vision can be affected by reflections, unusual sticker shades or poor lighting.
