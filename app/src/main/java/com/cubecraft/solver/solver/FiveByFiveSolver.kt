package com.cubecraft.solver.solver

import com.cubecraft.solver.model.CubeState
import com.cubecraft.solver.model.Face
import com.cubecraft.solver.model.Move

/**
 * Offline arbitrary-state 5x5 solver.
 *
 * Pipeline:
 * 1. Validate sticker counts/fixed centers.
 * 2. Extract the 3x3 skeleton (corners + middle edges + fixed centers) and solve it with the same
 *    min2phase engine used by cubikSolver's 3x3 mode.
 * 3. Apply those outer turns to the real 5x5.
 * 4. Solve the remaining movable centers and wings using table-free commutator macros.
 * 5. Replay the entire candidate on the original 150-sticker state and return success only if the
 *    real CubeState(5) is completely solved.
 *
 * No hidden scramble history and no external lookup-table pack are used by solve().
 */
class FiveByFiveSolver(
    private val three: Min2PhaseSolver = Min2PhaseSolver()
) : CubeSolver {
    enum class Phase { REDUCED_3X3, CENTERS, EDGE_PAIRING, VERIFY }

    override fun solve(state: CubeState): SolverResult {
        if (state.size != 5) return SolverResult.Invalid("5x5 solver expects 5x5")
        if (state.isSolved()) return SolverResult.Success(emptyList())

        validateBasicState(state)?.let { return SolverResult.Invalid(it) }

        val original = state.deepCopy()
        val work = state.deepCopy()
        val allMoves = ArrayList<Move>()

        // The odd-cube fixed centers, corners and middle-edge pieces form an ordinary legal 3x3.
        // Solving it first gives the table-free macro stage a fixed reference frame and all later
        // macros are required to preserve those 54 stickers pointwise.
        val skeleton = try {
            work.reducedSkeleton3x3()
        } catch (t: Throwable) {
            return SolverResult.Invalid("Could not extract 5x5 outer skeleton: ${t.message ?: t.javaClass.simpleName}")
        }

        when (val skeletonResult = three.solve(skeleton)) {
            is SolverResult.Success -> {
                work.applyAll(skeletonResult.moves)
                allMoves += skeletonResult.moves
            }
            is SolverResult.Invalid -> return SolverResult.Invalid("5x5 outer-piece state is invalid: ${skeletonResult.reason}")
            is SolverResult.Unavailable -> return SolverResult.Unavailable(skeletonResult.reason)
        }

        if (!work.reducedSkeleton3x3().isSolved()) {
            return SolverResult.Invalid("5x5 outer skeleton did not replay to solved state")
        }

        val reduced = try {
            FiveByFiveMacroReduction.solve(work)
        } catch (t: Throwable) {
            return SolverResult.Invalid("5x5 reduction failed: ${t.message ?: t.javaClass.simpleName}")
        }

        if (reduced.finalMismatch != 0) {
            return SolverResult.Unavailable(
                "5x5 table-free reduction could not finish this state yet: ${reduced.diagnostic}. " +
                    "No unverified moves were returned."
            )
        }

        work.applyAll(reduced.moves)
        allMoves += reduced.moves

        if (!work.isSolved()) {
            return SolverResult.Invalid("5x5 reduction reported complete but internal replay is not solved")
        }

        val simplified = FiveByFiveMacroReduction.simplify(allMoves)
        val check = original.deepCopy()
        check.applyAll(simplified)
        if (!check.isSolved()) {
            return SolverResult.Invalid("5x5 final replay verification failed")
        }

        return SolverResult.Success(simplified)
    }

    /**
     * Kept only for the virtual-lab convenience path where the app itself created every move.
     * Scanned cubes never call this method; arbitrary scanned states always go through solve().
     */
    fun solveKnownHistory(state: CubeState, historyFromSolved: List<Move>): SolverResult {
        if (state.size != 5) return SolverResult.Invalid("5x5 solver expects 5x5")
        val candidate = historyFromSolved.asReversed().map { it.inverse() }
        val check = state.deepCopy()
        check.applyAll(candidate)
        return if (check.isSolved()) {
            SolverResult.Success(candidate)
        } else {
            // If history does not describe the current cube anymore (for example after sticker
            // editing), fall through to the real arbitrary-state solver instead of lying.
            solve(state)
        }
    }

    private fun validateBasicState(state: CubeState): String? {
        val counts = state.colorCounts()
        val bad = Face.entries.filter { counts[it] != 25 }
        if (bad.isNotEmpty()) {
            return bad.joinToString(prefix = "5x5 needs exactly 25 stickers of each color: ") { face ->
                "${face.symbol}=${counts[face] ?: 0}"
            }
        }

        for (face in Face.entries) {
            val center = state.faceColors(face)[12]
            if (center != face) return "Fixed center of ${face.symbol} does not match its face identity"
        }
        return null
    }
}
