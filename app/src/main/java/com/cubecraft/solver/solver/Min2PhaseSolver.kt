package com.cubecraft.solver.solver

import com.cubecraft.solver.model.CubeState
import com.cubecraft.solver.model.Move
import cs.min2phase.Search

/**
 * 3x3 solver backed by Chen Shuang's min2phase implementation.
 *
 * Validation and solving are deliberately separate. Search.verify(facelets) is the library's
 * native physical-state validator and does not build the pruning/search tables.
 *
 * The UI wants a good solution quickly, not a near-optimal solution after a long refinement pass.
 * probeMin=0 therefore returns as soon as min2phase finds a valid short solution. The pruning
 * tables are also warmed in the background when the app starts so first-solve latency is normally
 * paid while the user is scanning the cube rather than after pressing OPEN 3D CUBE.
 */
class Min2PhaseSolver : CubeSolver {
    private data class SearchConfig(
        val maxDepth: Int,
        val probeMax: Long,
        val probeMin: Long
    )

    companion object {
        private val initLock = Any()
        @Volatile private var tablesReady = false

        /** Safe to call repeatedly and from a background thread. */
        fun warmUp() {
            if (tablesReady) return
            synchronized(initLock) {
                if (tablesReady) return
                Search.init()
                tablesReady = true
            }
        }
    }

    override fun solve(state: CubeState): SolverResult {
        if (state.size != 3) return SolverResult.Invalid("Two-phase solver expects 3x3")
        if (state.isSolved()) return SolverResult.Success(emptyList())

        val facelets = state.toMin2PhaseString()
        val verifyCode = verifyFacelets(facelets)
        if (verifyCode != 0) return SolverResult.Invalid(errorMeaning(verifyCode))

        // If app-start prewarming is still running, wait for the same one-time init here.
        // Subsequent solves skip this immediately.
        try {
            warmUp()
        } catch (t: Throwable) {
            return SolverResult.Invalid("Solver initialization failed: ${t.message ?: t.javaClass.simpleName}")
        }

        // probeMin used to be 120,000, deliberately forcing the engine to keep searching after it
        // had already found a solution. That produced slightly shorter sequences but could cost tens
        // of seconds on the Kirin 710A. We now return the first good <=21 move solution immediately.
        val configs = listOf(
            SearchConfig(maxDepth = 21, probeMax = 100_000L, probeMin = 0L),
            // Reliability fallback for unusually difficult states. Still return on first solution.
            SearchConfig(maxDepth = 24, probeMax = 1_000_000L, probeMin = 0L)
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

    /** Native min2phase validation only. This intentionally does not call solve(). */
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
