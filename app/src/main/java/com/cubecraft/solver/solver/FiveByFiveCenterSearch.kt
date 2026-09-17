package com.cubecraft.solver.solver

import com.cubecraft.solver.model.CubeState
import com.cubecraft.solver.model.Face
import com.cubecraft.solver.model.Move
import java.util.PriorityQueue

/**
 * First real arbitrary-sticker-state search stage for the 5x5 reduction pipeline.
 *
 * This is intentionally isolated from [FiveByFiveSolver] until the complete reduction pipeline is
 * ready. It receives only a CubeState(5): no scramble history is accepted or consulted.
 *
 * Search primitives are ordinary outer turns plus pure inner-slice turns. A pure inner slice is
 * emitted using notation the rest of cubikSolver already understands, e.g. `Rw R'`.
 * That lets the search move 5x5 centers without inventing a second move representation.
 *
 * The current implementation is a bounded weighted A* development engine. It is useful for
 * exercising the real center-state model and solving shallow/medium center states, but it is not
 * yet the production random-state center solver. The production engine can later replace the
 * search strategy while preserving this input/output contract and replay verification.
 */
class FiveByFiveCenterSearch(
    private val maxExpanded: Int = 60_000
) {
    sealed interface Result {
        data class Success(
            val moves: List<Move>,
            val expanded: Int
        ) : Result

        data class BudgetExceeded(
            val bestSolvedCenters: Int,
            val expanded: Int
        ) : Result
    }

    private enum class Kind { OUTER, INNER }

    private data class Action(
        val face: Face,
        val kind: Kind,
        val turns: Int,
        val emitted: List<Move>
    )

    private data class Node(
        val cube: CubeState,
        val path: List<Move>,
        val actions: Int,
        val solvedCenters: Int,
        val lastAction: Action?
    ) {
        val misplaced: Int get() = 48 - solvedCenters
        val lowerBound: Int get() = (misplaced + 11) / 12
        val priority: Int get() = actions + lowerBound * 3
    }

    private val actions: List<Action> = buildList {
        for (face in Face.entries) {
            for (turns in 1..3) {
                add(Action(face, Kind.OUTER, turns, listOf(Move(face, 1, turns))))

                // Wide(face) followed by inverse outer(face) leaves only the adjacent inner slice.
                val undoOuter = if (turns == 2) 2 else 4 - turns
                add(
                    Action(
                        face,
                        Kind.INNER,
                        turns,
                        listOf(Move(face, 2, turns), Move(face, 1, undoOuter))
                    )
                )
            }
        }
    }

    fun solve(start: CubeState): Result {
        require(start.size == 5) { "5x5 center search expects CubeState(5)" }
        if (FiveByFiveTopology.centersSolved(start)) return Result.Success(emptyList(), 0)

        val queue = PriorityQueue<Node>(
            compareBy<Node> { it.priority }
                .thenBy { it.misplaced }
                .thenBy { it.actions }
        )
        val first = start.deepCopy()
        val firstSolved = FiveByFiveTopology.solvedMovableCenterCount(first)
        queue += Node(first, emptyList(), 0, firstSolved, null)

        val bestDepthByState = HashMap<String, Int>()
        bestDepthByState[centerSignature(first)] = 0

        var expanded = 0
        var bestSolved = firstSolved

        while (queue.isNotEmpty() && expanded < maxExpanded) {
            val node = queue.remove()
            expanded++

            if (node.solvedCenters > bestSolved) bestSolved = node.solvedCenters
            if (node.solvedCenters == 48) {
                val verified = start.deepCopy().also { it.applyAll(node.path) }
                check(FiveByFiveTopology.centersSolved(verified)) {
                    "Center search returned a sequence that failed replay verification"
                }
                return Result.Success(node.path, expanded)
            }

            for (action in actions) {
                if (canMergeWithPrevious(node.lastAction, action)) continue

                val nextCube = node.cube.deepCopy()
                nextCube.applyAll(action.emitted)
                val nextActionDepth = node.actions + 1
                val key = centerSignature(nextCube)
                val previousDepth = bestDepthByState[key]
                if (previousDepth != null && previousDepth <= nextActionDepth) continue
                bestDepthByState[key] = nextActionDepth

                val solved = FiveByFiveTopology.solvedMovableCenterCount(nextCube)
                val nextPath = ArrayList<Move>(node.path.size + action.emitted.size).apply {
                    addAll(node.path)
                    addAll(action.emitted)
                }
                queue += Node(nextCube, nextPath, nextActionDepth, solved, action)
            }
        }

        return Result.BudgetExceeded(bestSolved, expanded)
    }

    private fun canMergeWithPrevious(previous: Action?, next: Action): Boolean {
        if (previous == null) return false
        // Consecutive turns of the exact same layer family can always be collapsed to one of the
        // three variants already present in the action set, so exploring them only adds loops.
        return previous.face == next.face && previous.kind == next.kind
    }

    private fun centerSignature(cube: CubeState): String = buildString(54) {
        for (face in Face.entries) {
            val values = cube.faceColors(face)
            for (r in 1..3) for (c in 1..3) append(values[r * 5 + c].symbol)
        }
    }
}
