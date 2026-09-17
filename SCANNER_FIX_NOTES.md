# Scanner fix notes - v0.4.0

## Root cause of the FACE 2 stall

`CameraPreview` is an `AndroidView`. Its `factory` block is executed when the native `PreviewView` is created, not once per Compose recomposition.

v0.3 created `FaceAnalyzer` inside that factory and directly captured the first `onObservation` lambda. FACE 1 worked. When `scanIndex` changed, Compose created new screen state for FACE 2 but the existing analyzer could still call the old lambda. FACE 2 therefore appeared frozen even though camera analysis continued.

v0.4 keeps the camera alive but wraps callbacks in `rememberUpdatedState`. The long-lived analyzer now dispatches each frame to the current scan step.

## No-lock capture contract

Contour detection is never consulted by the Capture button.

1. A recent live observation exists -> capture immediately.
2. No recent observation exists -> set `pendingCapture=true`.
3. The next analyzer result is consumed automatically.
4. After the ViewModel advances to the next face, a 280 ms fresh-frame guard prevents accidental reuse of the previous face.

## AR overlay

When a quadrilateral is detected it is exponentially smoothed and retained through a 520 ms brief-loss window. The N x N grid is bilinearly interpolated inside that quadrilateral. When there is no tracked quadrilateral, the same visualization falls back to the fixed center guide.

Each cell displays the actual sampled RGB chip plus a provisional physical-color label. Low-confidence cells are heat-highlighted. Final cube classification still uses six-center Lab calibration + balanced assignment.
