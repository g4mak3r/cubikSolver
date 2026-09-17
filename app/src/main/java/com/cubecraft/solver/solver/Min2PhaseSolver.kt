package com.cubecraft.solver.solver

import com.cubecraft.solver.model.CubeState
import com.cubecraft.solver.model.Move
import cs.min2phase.Search

/**
 * Verified 3x3 solver backed by min2phase.
 *
 * The first pass deliberately keeps searching after the first solution for a while so the user
 * normally gets a shorter practical sequence instead of the first valid sequence found. If that
 * tighter search budget is exhausted we fall back to a deeper reliability pass.
 */
class Min2PhaseSolver : CubeSolver {
    private data class SearchConfig(
        val maxDepth: Int,
        val probeMax: Long,
        val probeMin: Long
    )

    override fun solve(state: CubeState): SolverResult {
        if (state.size != 3) return SolverResult.Invalid("Two-phase solver expects 3x3")
        if (state.isSolved()) return SolverResult.Success(emptyList())

        val facelets = state.toMin2PhaseString()
        val configs = listOf(
            SearchConfig(maxDepth = 21, probeMax = 2_000_000L, probeMin = 120_000L),
            SearchConfig(maxDepth = 24, probeMax = 12_000_000L, probeMin = 0L)
        )

        var lastError = "Error 8"
        for (config in configs) {
            val text = try {
                Search().solution(
                    facelets,
                    config.maxDepth,
                    config.probeMax,
                    config.probeMin,
                    0
                )
            } catch (t: Throwable) {
                return SolverResult.Invalid("Solver failed: ${t.message ?: t.javaClass.simpleName}")
            }

            if (text.startsWith("Error")) {
                lastError = text
                val code = text.filter(Char::isDigit).toIntOrNull()
                if (code != 7 && code != 8) return SolverResult.Invalid(errorMeaning(text))
                continue
            }

            val moves = Move.parseAlgorithm(text.replace(Regex("\\([^)]*\\)"), ""))
            if (moves.isEmpty()) return SolverResult.Invalid("Solver returned no parseable moves")

            val check = state.deepCopy()
            check.applyAll(moves)
            return if (check.isSolved()) {
                SolverResult.Success(moves)
            } else {
                SolverResult.Invalid("Solution did not replay to solved state")
            }
        }

        return SolverResult.Invalid(errorMeaning(lastError))
    }

    fun validate(state: CubeState): String? = when (val r = solve(state)) {
        is SolverResult.Success -> null
        is SolverResult.Invalid -> r.reason
        is SolverResult.Unavailable -> r.reason
    }

    private fun errorMeaning(raw: String): String = when (raw.filter(Char::isDigit).toIntOrNull()) {
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
