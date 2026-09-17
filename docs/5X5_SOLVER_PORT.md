# 5x5 arbitrary-state solver port contract

The Android side already accepts and renders all legal outer and two-layer wide turns. A production arbitrary-state 5x5 engine only needs to implement `CubeSolver` and return standard `Move` values.

Required correctness gate:

1. Input is **stickers only**, not hidden scramble history.
2. Solve centers.
3. Pair the edge wings.
4. Handle reduction/parity cases.
5. Solve the reduced 3x3.
6. Replay the complete returned sequence against a copy of the original `CubeState`.
7. Return success only when `isSolved()` is true after replay.

A strong candidate for a later port is a deterministic Rust/WASM or Android-native reduction core. Keep it behind `FiveByFiveSolver`; camera and UI code should not know which implementation is used.
