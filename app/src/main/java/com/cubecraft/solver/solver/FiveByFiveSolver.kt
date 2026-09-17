package com.cubecraft.solver.solver

import com.cubecraft.solver.model.CubeState
import com.cubecraft.solver.model.Face
import com.cubecraft.solver.model.Move

/**
 * Offline arbitrary-state 5x5 solver.
 *
 * Pipeline:
 * 1. validate sticker counts and the scanned fixed-centre identities;
 * 2. solve the six 3x3 centre blocks and pair all twelve 5x5 edges;
 * 3. project the reduced cube onto a 3x3 skeleton and normalize its current centre frame;
 * 4. repair reduction parity if that 3x3 is not physically reachable;
 * 5. solve the normalized reduced 3x3 with min2phase;
 * 6. replay the complete candidate on the original 150-sticker state.
 *
 * solve() receives no scramble history. Camera and virtual states with the same 150 stickers take
 * the same arbitrary-state path.
 */
class FiveByFiveSolver(
    private val three: Min2PhaseSolver = Min2PhaseSolver()
) : CubeSolver {
    enum class Phase { CENTERS, EDGE_PAIRING, PARITY, REDUCED_3X3, VERIFY }

    private data class ReductionChoice(
        val state: CubeState,
        val moves: List<Move>,
        val diagnostic: String
    )

    override fun solve(state: CubeState): SolverResult {
        if (state.size != 5) return SolverResult.Invalid("5x5 solver expects 5x5")
        if (state.isUniformSolved()) return SolverResult.Success(emptyList())

        validateBasicState(state)?.let { return SolverResult.Invalid(it) }

        val original = state.deepCopy()
        var work = state.deepCopy()
        val allMoves = ArrayList<Move>()

        // A parity repair can unpair an edge. Reduction is therefore allowed to rebuild a few times
        // before the final outer-turn solve.
        for (pass in 0 until 4) {
            val reduction = reduceWithRestarts(work, pass)
                ?: return SolverResult.Unavailable(lastReductionFailure)

            work = reduction.state
            allMoves += reduction.moves

            val projected = try {
                normalizeProjection(work.reducedSkeleton3x3())
            } catch (t: Throwable) {
                return SolverResult.Invalid("Could not project reduced 5x5 to 3x3: ${t.message ?: t.javaClass.simpleName}")
            }

            when (val result = three.solve(projected)) {
                is SolverResult.Success -> {
                    work.applyAll(result.moves)
                    allMoves += result.moves

                    if (!work.isUniformSolved()) {
                        return SolverResult.Invalid(
                            "5x5 reduced 3x3 solved, but the full cube still has unpaired pieces"
                        )
                    }

                    val simplified = FiveByFiveMacroReduction.simplify(allMoves)
                    val check = original.deepCopy()
                    check.applyAll(simplified)
                    if (!check.isUniformSolved()) {
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
                }

                is SolverResult.Unavailable -> return SolverResult.Unavailable(result.reason)
            }
        }

        return SolverResult.Unavailable("5x5 solver exhausted its reduction/parity passes.")
    }

    private var lastReductionFailure: String = "5x5 reduction did not converge."

    /**
     * The reducer is a local staged search and some legal states sit on awkward plateaus. Retry the
     * exact same sticker state after a handful of short legal perturbations instead of returning a
     * partial/unverified sequence. Each prefix becomes part of the final candidate, so every path
     * is still replay-verified against the original 150 stickers before it can escape this class.
     */
    private fun reduceWithRestarts(state: CubeState, pass: Int): ReductionChoice? {
        val prefixes = reductionPrefixes(pass)
        var bestDiagnostic = ""

        for ((index, prefix) in prefixes.withIndex()) {
            val candidate = state.deepCopy()
            candidate.applyAll(prefix)

            val reduction = try {
                FiveByFiveMacroReduction.solve(
                    state = candidate,
                    budgetMillis = when {
                        index == 0 && pass == 0 -> 25_000L
                        index == 0 -> 14_000L
                        else -> 8_000L
                    }
                )
            } catch (t: Throwable) {
                bestDiagnostic = "5x5 reduction failed: ${t.message ?: t.javaClass.simpleName}"
                continue
            }

            bestDiagnostic = reduction.diagnostic
            if (!reduction.centresSolved || !reduction.edgesPaired) continue

            candidate.applyAll(reduction.moves)
            return ReductionChoice(
                state = candidate,
                moves = prefix + reduction.moves,
                diagnostic = reduction.diagnostic
            )
        }

        lastReductionFailure =
            "5x5 reduction could not finish this state after ${prefixes.size} verified search basins: " +
                "$bestDiagnostic. No unverified moves were returned."
        return null
    }

    private fun reductionPrefixes(pass: Int): List<List<Move>> {
        val rw = Move(Face.R, 2, 1)
        val rwi = rw.inverse()
        val uw = Move(Face.U, 2, 1)
        val uwi = uw.inverse()
        val fw = Move(Face.F, 2, 1)
        val fwi = fw.inverse()

        return if (pass == 0) {
            listOf(
                emptyList(),
                listOf(rw),
                listOf(uw),
                listOf(fw),
                listOf(rwi),
                listOf(uwi),
                listOf(fwi),
                listOf(rw, uw),
                listOf(uw, fw)
            )
        } else {
            listOf(
                emptyList(),
                listOf(rw),
                listOf(uw),
                listOf(fw),
                listOf(rw, fw),
                listOf(uw, rwi)
            )
        }
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
        return if (check.isUniformSolved()) SolverResult.Success(candidate) else solve(state)
    }

    /**
     * Reduction is allowed to use middle-layer turns, which can rotate the six centre identities as
     * a whole frame. min2phase names colours by the *current* U/R/F/D/L/B centres, so remap the
     * projected colours before handing it over. Returned moves still name physical faces and can be
     * replayed directly on the 5x5.
     */
    private fun normalizeProjection(projected: CubeState): CubeState {
        val colorToPosition = LinkedHashMap<Face, Face>()
        for (position in Face.entries) {
            val color = projected.faceColors(position)[4]
            require(colorToPosition.put(color, position) == null) {
                "Reduced cube has duplicate centre identity ${color.symbol}"
            }
        }
        require(colorToPosition.size == 6) { "Reduced cube does not expose six distinct centres" }

        val normalized = Face.entries.associateWith { position ->
            projected.faceColors(position).map { color ->
                colorToPosition[color] ?: error("No current centre for color ${color.symbol}")
            }
        }
        return CubeState(3).also { it.loadFaces(normalized) }
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

    private fun CubeState.isUniformSolved(): Boolean =
        Face.entries.all { face ->
            val colors = faceColors(face)
            colors.isNotEmpty() && colors.all { it == colors[0] }
        }
}
