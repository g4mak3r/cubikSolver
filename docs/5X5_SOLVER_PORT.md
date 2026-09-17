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

The UI receives the final sequence as ordinary `Move` values, so the same slider, highlighted layer, ghost animation and directional arrow used by 3x3 can guide a physical 5x5 solve.

## Upstream research

### Daniel Walton / rubiks-cube-NxNxN-solver

This is the strongest mature reference found so far.

- MIT licensed, so ideas/code can legally be ported with attribution.
- Has a dedicated `RubiksCube555` implementation.
- Uses staged center solving, edge pairing and a final 3x3 solve.
- Published history reports roughly 77-79 reduction moves for later 5x5 versions before the final ~3x3 solve.
- Contains an optimized C IDA*/lookup-table search core.

However, its production strategy relies heavily on precomputed pruning/lookup tables that are normally downloaded on demand.

A newer independent pure-Java port of this family documents about **2.2 GB** of decompressed tables for 4x4+5x5, with one 5x5 phase table alone around **184 MB**. That is evidence that blindly bundling the complete upstream table set would be a poor fit for a small offline Android app.

Therefore we should use Walton primarily as a correctness/reference source, not automatically copy its complete storage architecture.

### Table-light reduction references

Other public implementations demonstrate a different route: standard center insertions, conjugates/commutators, bounded local search for difficult endgames, explicit edge-triplet pairing, parity algorithms, then a 3x3 handoff. Some of those repositories do not expose a compatible license, so their code must not be copied; their high-level algorithm structure is still useful research.

This is much closer to what cubikSolver needs on Android because it can trade a little more CPU time for dramatically less bundled data.

### hkociemba / RubikNxNxNSolver

Useful research for center solving, but its README explicitly says development stopped after centers and the remaining edges/corners would not be added. It is therefore not a complete 5x5 engine.

## Current architecture decision

Do **not** commit to a 2 GB lookup-table bundle.

Preferred direction for the first production 5x5 engine:

`Kotlin UI/scanner -> FiveByFiveSolver -> compact reduction engine -> existing Min2PhaseSolver -> replay verifier`

The compact reduction engine may start in Kotlin/JVM for fast iteration and move to a small C/C++ core through JNI only if profiling on the Kirin 710A proves it necessary.

### Reduction strategy

#### Centers

Solve center groups around the six fixed centers using deterministic insertions/commutators first. Use bounded IDA*/transposition search only for local endgame cases instead of searching the entire 5x5 state space.

#### Edge triplets

A 5x5 edge consists of two movable wings plus the fixed middle-edge piece. Pair all 12 triplets while preserving solved centers. Treat the two wing orbits explicitly rather than flattening them into a fake 3x3 too early.

#### Parity

After centers and edge triplets are reduced, serialize the outer skeleton as a 3x3 and ask min2phase to verify it. If the reduced state has a 5x5 reduction parity case, apply a verified parity algorithm, re-check the reduced skeleton, then continue.

#### 3x3 finish

Reuse the existing warm `Min2PhaseSolver`; do not introduce another 3x3 engine.

#### Verification

No reduction phase is trusted blindly. The final move list is applied to a deep copy of the original 5x5. Success is returned only if all 150 stickers end solved.

## Integration milestones

### M1 - State bridge - STARTED

A stable URFDLB serializer is now part of `CubeState`:

- 3x3 -> 54 characters
- 5x5 -> 150 characters

Tests check solved order, length, color counts and legal scrambled 5x5 serialization.

Next: add explicit 5x5 piece-index helpers for movable centers, middle edges and both wing orbits.

### M2 - Center engine

Return a sequence that solves all six 3x3 center blocks on a scrambled 5x5. Replay every candidate through `CubeState(5)` and assert the center blocks are solved.

### M3 - Edge pairing

Starting from solved centers, pair all 12 edge triplets while preserving centers. Add tests for both wing orbits and middle edges.

### M4 - Reduction/parity

Detect whether the paired state maps to a legal 3x3. If not, apply the required 5x5 parity repair and re-check.

### M5 - Existing 3x3 handoff

Build the reduced 3x3 skeleton and solve it with the already-bundled min2phase engine.

### M6 - End-to-end arbitrary-state solve

Randomly scramble `CubeState(5)`, discard the scramble history, serialize stickers only, solve, replay every returned move, and require `isSolved()`.

## Performance target

The first goal is reliability, not an optimal move count. A practical offline mobile target is a deterministic solution in seconds rather than minutes, with memory use small enough for the Huawei P Smart 2021. Once correctness is stable, center/edge searches can be profiled and moved native or supplemented with a small number of compact pruning tables where they materially help.

## Non-negotiable QA

Before enabling arbitrary scanned 5x5 solving in the UI:

- thousands of generated legal scrambles must round-trip from stickers only;
- no arbitrary-state test may use inverse scramble history;
- every result must be replay-verified by `CubeState(5)`;
- invalid scans must fail cleanly instead of producing moves;
- full solving must work in airplane mode from first launch;
- lookup data, if any, must be bundled and intentionally size-budgeted rather than fetched at runtime.
