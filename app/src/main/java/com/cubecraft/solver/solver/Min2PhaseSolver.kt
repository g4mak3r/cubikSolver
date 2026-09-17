package com.cubecraft.solver.solver

import com.cubecraft.solver.model.CubeState
import com.cubecraft.solver.model.Move
import cs.min2phase.Search

class Min2PhaseSolver : CubeSolver {
    override fun solve(state: CubeState): SolverResult {
        if (state.size != 3) return SolverResult.Invalid("Two-phase solver expects 3x3")
        if (state.isSolved()) return SolverResult.Success(emptyList())
        val text = try { Search().solution(state.toMin2PhaseString(), 24, 100_000_000, 0, 0) }
        catch (t: Throwable) { return SolverResult.Invalid("Solver failed: ${t.message ?: t.javaClass.simpleName}") }
        if (text.startsWith("Error")) return SolverResult.Invalid(errorMeaning(text))
        val moves = Move.parseAlgorithm(text.replace(Regex("\\([^)]*\\)"), ""))
        if (moves.isEmpty()) return SolverResult.Invalid("Solver returned no parseable moves")
        val check=state.deepCopy(); check.applyAll(moves)
        return if(check.isSolved()) SolverResult.Success(moves) else SolverResult.Invalid("Solution did not replay to solved state")
    }

    fun validate(state: CubeState): String? = when(val r=solve(state)) {
        is SolverResult.Success -> null
        is SolverResult.Invalid -> r.reason
        is SolverResult.Unavailable -> r.reason
    }

    private fun errorMeaning(raw:String):String = when(raw.filter(Char::isDigit).toIntOrNull()) {
        1 -> "Exactly nine stickers of each center identity are required."
        2 -> "One or more edge pieces are impossible. Check face orientation."
        3 -> "An edge is flipped in a physically impossible way. Check the scan."
        4 -> "One or more corner pieces are impossible. Check face orientation."
        5 -> "A corner is twisted in a physically impossible way. Check the scan."
        6 -> "Permutation parity is impossible. At least one scanned sticker/orientation is wrong."
        7 -> "No solution was found within the configured depth."
        8 -> "Search limit reached."
        else -> "Invalid cube state ($raw)."
    }
}
