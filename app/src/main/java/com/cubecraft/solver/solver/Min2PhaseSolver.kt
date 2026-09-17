package com.cubecraft.solver.solver

import com.cubecraft.solver.model.CubeState
import com.cubecraft.solver.model.Move
import cs.min2phase.Search

/**
 * 3x3 solver backed by Chen Shuang's min2phase implementation.
 *
 * IMPORTANT: validation and solving are deliberately separate.
 * Search.verify(facelets) is the library's native physical-state validator and does not build the
 * pruning/search tables. Review screens must use that instead of trying to solve the cube merely
 * to decide whether it is legal.
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
        val verifyCode = verifyFacelets(facelets)
        if (verifyCode != 0) return SolverResult.Invalid(errorMeaning(verifyCode))

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
                return SolverResult.Invalid("Solver engine failed: ${t.message ?: t.javaClass.simpleName}")
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

    /**
     * Native min2phase validation only. This is intentionally NOT implemented through solve().
     * The library returns 0 for a legal cube and negative error codes for invalid facelets.
     */
    fun validate(state: CubeState): String? {
        if (state.size != 3) return "Two-phase validator expects 3x3"
        val code = try {
            verifyFacelets(state.toMin2PhaseString())
        } catch (t: Throwable) {
            return "Validator engine failed: ${t.message ?: t.javaClass.simpleName}"
        }
        return if (code == 0) null else errorMeaning(code)
    }

    internal fun verifyFacelets(facelets: String): Int = Search().verify(facelets)

    private fun errorMeaning(raw: String): String =
        errorMeaning(raw.filter(Char::isDigit).toIntOrNull() ?: 0)

    private fun errorMeaning(rawCode: Int): String = when (kotlin.math.abs(rawCode)) {
        1 -> "Exactly nine stickers of each center identity are required."
        2 -> "One or more edge pieces are impossible. Check face orientation."
        3 -> "An edge is flipped in a physically impossible way. Check the scan."
        4 -> "One or more corner pieces are impossible. Check face orientation."
        5 -> "A corner is twisted in a physically impossible way. Check the scan."
        6 -> "Permutation parity is impossible. At least one scanned sticker/orientation is wrong."
        7 -> "No solution was found within the configured depth."
        8 -> "Search limit reached."
        else -> "Invalid cube state (error $rawCode)."
    }
}
