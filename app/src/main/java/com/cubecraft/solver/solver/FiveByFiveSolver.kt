package com.cubecraft.solver.solver

import com.cubecraft.solver.model.CubeState
import com.cubecraft.solver.model.Move

/**
 * Honest boundary for v0.2: state, scanner, validation, rendering and guidance are fully 5x5-aware.
 * An arbitrary sticker-state 5x5 reduction engine must not be faked with inverse history.
 * This class handles verified history-based solves for virtual cubes and exposes the reduction phases
 * which a native/WASM engine can plug into next without touching UI or camera code.
 */
class FiveByFiveSolver : CubeSolver {
    enum class Phase { CENTERS, EDGE_PAIRING, REDUCED_3X3, PARITY, VERIFY }
    override fun solve(state: CubeState): SolverResult = when {
        state.size != 5 -> SolverResult.Invalid("5x5 solver expects 5x5")
        state.isSolved() -> SolverResult.Success(emptyList())
        else -> SolverResult.Unavailable("Arbitrary-state 5x5 reduction core is not bundled in v0.2 yet. Scanner, state model and guide are ready for it.")
    }

    fun solveKnownHistory(state: CubeState, historyFromSolved: List<Move>): SolverResult {
        if(state.size!=5) return SolverResult.Invalid("5x5 solver expects 5x5")
        val candidate=historyFromSolved.asReversed().map { it.inverse() }
        val check=state.deepCopy(); check.applyAll(candidate)
        return if(check.isSolved()) SolverResult.Success(candidate) else SolverResult.Invalid("History replay verification failed")
    }
}
