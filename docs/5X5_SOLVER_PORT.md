# 5x5 arbitrary-state solver plan

The scanner, `CubeState(5)`, 3D renderer and move model are already 5x5-aware. What is still missing is the production arbitrary-state reduction engine behind `FiveByFiveSolver`.

## Required contract

The engine must solve from **150 scanned stickers only**. It must never depend on hidden scramble history.

1. Read the 5x5 facelets in URFDLB order.
2. Solve/stage the 48 movable center stickers around the six fixed centers.
3. Pair the two wing orbits with each fixed middle-edge piece so the 12 edge triplets behave as 3x3 edges.
4. Detect and repair 5x5 reduction parity when necessary.
5. Convert the reduced outer skeleton to a normal 3x3 state.
6. Hand that state to the existing `Min2PhaseSolver`.
7. Concatenate all moves.
8. Replay the complete sequence on a copy of the original `CubeState(5)` and return success only if `isSolved()` is true.

The UI should receive the final sequence as ordinary `Move` values, so the same slider, highlighted layer, ghost animation and directional arrow used by 3x3 can guide a physical 5x5 solve.

## Best upstream reference found

The strongest practical reference currently is Daniel Walton's `dwalton76/rubiks-cube-NxNxN-solver`.

Why it is useful:

- MIT licensed, so a port/reuse is legally straightforward with attribution.
- Has a dedicated `RubiksCube555` implementation rather than pretending 5x5 is just a larger 3x3.
- Uses staged center solving, edge pairing and a final 3x3 solve.
- Its published history reports roughly 77-79 reduction moves for 5x5 in later versions, before the final ~3x3 solve.
- It contains a native C IDA*/lookup-table search core, which is much more attractive for Android than running a large search in Kotlin.

Important constraint: upstream downloads precomputed lookup/pruning tables on demand. cubikSolver must remain fully offline, therefore any tables we depend on must be generated before release and bundled in the APK/assets. No runtime network fetch is acceptable.

## Android architecture decision

Preferred production path:

`Kotlin UI/scanner -> FiveByFiveSolver -> JNI bridge -> native 5x5 reduction core -> Min2PhaseSolver -> verified Move list`

Do not embed a web service and do not require Google Play Services.

### Why native instead of pure Kotlin first

The difficult part is not applying moves; `CubeState` already does that. The expensive part is the IDA*/pruning-table search for center and edge stages. A small native core can:

- memory-map bundled pruning tables,
- avoid GC pressure,
- use compact byte/int state representations,
- remain deterministic and offline,
- run acceptably on the Kirin 710A.

A full Kotlin rewrite remains possible later, but it is a larger correctness/performance risk for the first working 5x5 solver.

## Integration milestones

### M1 - State bridge

Add a stable 150-character URFDLB serialization for `CubeState(5)` and tests that every legal 5x5 move preserves the mapping. Also add native move notation conversion for outer and wide turns.

### M2 - Centers only

Native engine returns a move sequence that solves all 5x5 centers. Replay it through `CubeState(5)` and assert that every 3x3 center block matches its fixed center color.

### M3 - Edge pairing

Starting from solved centers, pair all 12 edge triplets while preserving centers. Add explicit tests for both wing orbits and middle edges.

### M4 - Reduction/parity

Detect whether the paired state maps to a legal 3x3. If not, apply the required 5x5 parity repair and re-check.

### M5 - Existing 3x3 handoff

Build the reduced 3x3 skeleton and solve it with the already-bundled min2phase engine. Do not add a second 3x3 solver.

### M6 - End-to-end scan solve

Randomly scramble `CubeState(5)`, discard the scramble history, serialize only stickers, solve from that state, replay every returned move, and require `isSolved()`.

## Non-negotiable QA

Before enabling the 5x5 SOLVE button for scanned cubes:

- thousands of generated legal scrambles must round-trip from stickers only;
- no test may use inverse scramble history as the arbitrary-state solution;
- every native result must be replay-verified by Kotlin `CubeState`;
- invalid scans must fail cleanly instead of producing moves;
- the final solver must work with airplane mode enabled from first launch.

## Sources evaluated

- `dwalton76/rubiks-cube-NxNxN-solver`: mature 5x5/NxN reduction solver, MIT, strongest port candidate.
- `hkociemba/RubikNxNxNSolver`: useful center-solving research, but the author explicitly stopped before implementing remaining edges/corners, so it is not a complete 5x5 engine.
- Several newer TypeScript/Rust projects contain useful ideas, but code without a clear compatible license should not be copied into cubikSolver.
