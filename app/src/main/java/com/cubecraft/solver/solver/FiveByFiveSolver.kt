package com.cubecraft.solver.solver

import com.cubecraft.solver.model.CubeState
import com.cubecraft.solver.model.Face
import com.cubecraft.solver.model.Move

/**
 * Offline arbitrary-state 5x5 solver.
 *
 * Pipeline:
 * 1. validate sticker counts and fixed centres;
 * 2. solve the six 3x3 centre blocks and pair all twelve 5x5 edges;
 * 3. project the reduced cube onto its 3x3 skeleton;
 * 4. repair reduction parity if the projected 3x3 is not physically reachable;
 * 5. solve the reduced 3x3 with min2phase;
 * 6. replay the complete candidate on the original 150-sticker state.
 *
 * solve() never receives or reconstructs scramble history. A camera scan and a virtual cube with
 * the same 150 stickers therefore take exactly the same path through the solver.
 */
class FiveByFiveSolver(
    private val three: Min2PhaseSolver = Min2PhaseSolver()
) : CubeSolver {
    enum class Phase { CENTERS, EDGE_PAIRING, PARITY, REDUCED_3X3, VERIFY }

    override fun solve(state: CubeState): SolverResult {
        if (state.size != 5) return SolverResult.Invalid("5x5 solver expects 5x5")
        if (state.isSolved()) return SolverResult.Success(emptyList())

        validateBasicState(state)?.let { return SolverResult.Invalid(it) }

        val original = state.deepCopy()
        val work = state.deepCopy()
        val allMoves = ArrayList<Move>()

        // A parity fix can disturb the just-built reduction, so the pipeline is allowed to rebuild
        // centres/edges a couple of times before the final outer-turn solve.
        for (pass in 0 until 4) {
            val reduction = try {
                FiveByFiveMacroReduction.solve(
                    state = work,
                    budgetMillis = if (pass == 0) 25_000L else 15_000L
                )
            } catch (t: Throwable) {
                return SolverResult.Invalid("5x5 reduction failed: ${t.message ?: t.javaClass.simpleName}")
            }

            if (!reduction.centresSolved || !reduction.edgesPaired) {
                return SolverResult.Unavailable(
                    "5x5 reduction could not finish this state: ${reduction.diagnostic}. " +
                        "No unverified moves were returned."
                )
            }

            work.applyAll(reduction.moves)
            allMoves += reduction.moves

            val projected = try {
                work.reducedSkeleton3x3()
            } catch (t: Throwable) {
                return SolverResult.Invalid("Could not project reduced 5x5 to 3x3: ${t.message ?: t.javaClass.simpleName}")
            }

            when (val result = three.solve(projected)) {
                is SolverResult.Success -> {
                    work.applyAll(result.moves)
                    allMoves += result.moves

                    if (!work.isSolved()) {
                        return SolverResult.Invalid(
                            "5x5 reduced 3x3 solved, but full cube replay still has unpaired pieces"
                        )
                    }

                    val simplified = FiveByFiveMacroReduction.simplify(allMoves)
                    val check = original.deepCopy()
                    check.applyAll(simplified)
                    if (!check.isSolved()) {
                        return SolverResult.Invalid("5x5 final replay verification failed")
                    }
                    return SolverResult.Success(simplified)
                }

                is SolverResult.Invalid -> {
                    val parity = parityFor(result.reason)
                    if (parity == null) {
                        return SolverResult.Invalid(
                            "Reduced 5x5 produced an invalid 3x3 state: ${result.reason}"
                        )
                    }
                    if (pass == 3) {
                        return SolverResult.Unavailable(
                            "5x5 parity repair did not converge after multiple verified reduction passes."
                        )
                    }
                    work.applyAll(parity)
                    allMoves += parity
                    // Loop: the parity algorithm is legal on the physical 5x5 but may unpair one
                    // edge, so rebuild the reduction before projecting again.
                }

                is SolverResult.Unavailable -> return SolverResult.Unavailable(result.reason)
            }
        }

        return SolverResult.Unavailable("5x5 solver exhausted its reduction/parity passes.")
    }

    /**
     * Virtual-lab convenience path. If history really describes the state, reversing it is instant;
     * otherwise fall through to the same arbitrary-state solver used for camera scans.
     */
    fun solveKnownHistory(state: CubeState, historyFromSolved: List<Move>): SolverResult {
        if (state.size != 5) return SolverResult.Invalid("5x5 solver expects 5x5")
        val candidate = historyFromSolved.asReversed().map { it.inverse() }
        val check = state.deepCopy()
        check.applyAll(candidate)
        return if (check.isSolved()) SolverResult.Success(candidate) else solve(state)
    }

    private fun parityFor(reason: String): List<Move>? = when {
        reason.contains("flipped", ignoreCase = true) -> FiveByFiveMacroReduction.edgeFlipParity()
        reason.contains("parity", ignoreCase = true) -> FiveByFiveMacroReduction.edgeSwapParity()
        else -> null
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
