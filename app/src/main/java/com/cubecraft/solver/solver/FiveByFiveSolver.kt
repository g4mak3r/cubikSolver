package com.cubecraft.solver.solver

import com.cubecraft.solver.model.CubeState
import com.cubecraft.solver.model.Face
import com.cubecraft.solver.model.Move

/** Sticker-only constructive 5x5 reduction followed by min2phase and full replay verification. */
class FiveByFiveSolver(
    private val three: Min2PhaseSolver = Min2PhaseSolver()
) : CubeSolver {
    override fun solve(state: CubeState): SolverResult = solve(state) {}

    fun solve(state: CubeState, checkCancelled: () -> Unit): SolverResult {
        if (state.size != 5) return SolverResult.Invalid("5x5 solver expects 5x5")
        val counts = state.colorCounts()
        if (Face.entries.any { counts[it] != 25 }) {
            return SolverResult.Invalid("5x5 needs exactly 25 stickers of each color. Check the scan.")
        }
        for (face in Face.entries) {
            if (state.faceColors(face)[12] != face) {
                return SolverResult.Invalid("Fixed center of ${face.symbol} does not match its face identity")
            }
        }
        checkCancelled()
        // On an odd cube the corners and middle edges already form a legal 3x3. Invalid
        // skeleton parity is a scan error, not something to repair by random reduction passes.
        val skeleton = state.reducedSkeleton3x3()
        three.validate(skeleton)?.let { return SolverResult.Invalid(it) }
        if (state.isSolved()) return SolverResult.Success(emptyList())

        val reduction = try {
            FiveByFiveCycles.reduce(state, checkCancelled)
        } catch (e: IllegalArgumentException) {
            return SolverResult.Invalid(e.message ?: "Invalid 5x5 pieces")
        }
        checkCancelled()
        val tail = three.solve(skeleton)
        if (tail !is SolverResult.Success) return tail
        val moves = FiveByFiveMoveOptimizer.optimize(reduction + tail.moves)
        val replay = state.deepCopy()
        for (move in moves) {
            checkCancelled()
            replay.apply(move)
        }
        return if (replay.isSolved()) SolverResult.Success(moves)
        else SolverResult.Unavailable("5x5 solution failed full sticker replay verification")
    }

    /** Virtual-lab shortcut, accepted only after replay against the actual sticker state. */
    fun solveKnownHistory(
        state: CubeState,
        historyFromSolved: List<Move>,
        checkCancelled: () -> Unit = {}
    ): SolverResult {
        if (state.size != 5) return SolverResult.Invalid("5x5 solver expects 5x5")
        val candidate = historyFromSolved.asReversed().map { it.inverse() }
        val check = state.deepCopy()
        for (move in candidate) {
            checkCancelled()
            check.apply(move)
        }
        return if (check.isSolved()) SolverResult.Success(candidate) else solve(state, checkCancelled)
    }
}
