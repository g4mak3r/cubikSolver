# CUBECRAFT architecture

```text
CameraX -> FaceAnalyzer(OpenCV) -> FaceObservation(Lab + RGB)
                                  |
                                  v
                         BalancedClassifier
                    (6 centers + exact capacities)
                                  |
                                  v
                              CubeState
                   /               |               \
             Validator         3D renderer          Solver
                |                  |              /      \
        3x3 skeleton          guide highlight   3x3     5x5 boundary
                |                                  |        |
            min2phase                          two-phase  reduction port
```

`CubeState` is the source of truth. The renderer never owns puzzle state, and the scanner never writes directly to the renderer. A solver result is independently replayed on a copy of the input state before it is exposed to guidance.

The six photographed center stickers define logical face identities F/R/B/L/U/D. Their measured RGB values are kept only as the rendering palette. Solver logic uses center identity rather than assuming that, for example, F must be green.
