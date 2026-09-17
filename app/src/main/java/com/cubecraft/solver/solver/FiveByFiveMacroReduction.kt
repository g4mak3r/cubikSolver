package com.cubecraft.solver.solver

import com.cubecraft.solver.model.CubeState
import com.cubecraft.solver.model.Face
import com.cubecraft.solver.model.Move
import com.cubecraft.solver.model.StickerKey
import java.util.PriorityQueue

/**
 * Table-free 5x5 reduction engine.
 *
 * The outer 3x3 skeleton is solved first by Min2PhaseSolver. The remaining 96 stickers (movable
 * centers + wings) are then manipulated only with commutator macros whose net effect fixes every
 * corner, fixed center and middle-edge sticker. No scramble history and no lookup-table pack is
 * used. Every emitted sequence is replay-verified by FiveByFiveSolver before it is exposed to UI.
 *
 * This deliberately favours reliability and zero external data over move optimality. Macro banks
 * are generated from legal 5x5 moves at runtime and cached for the process lifetime.
 */
internal object FiveByFiveMacroReduction {
    data class Result(
        val moves: List<Move>,
        val centerMacros: Int,
        val edgeMacros: Int,
        val finalMismatch: Int,
        val diagnostic: String
    )

    private const val N = 5
    private const val STICKERS = 150
    private val faces = Face.entries

    private val model by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { Model() }
    private val banks by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { MacroBanks(model) }

    fun solve(cubeWithSolvedSkeleton: CubeState): Result {
        require(cubeWithSolvedSkeleton.size == 5)
        val m = model
        var state = m.fromCube(cubeWithSolvedSkeleton)
        check(m.skeletonMismatch(state) == 0) { "5x5 macro reduction requires a solved outer skeleton" }

        val centerStart = m.centerMismatch(state)
        var centerMacroCount = 0
        if (centerStart != 0) {
            val phase = improve(
                start = state,
                macros = banks.centerMacros,
                score = m::centerMismatch,
                maxApplications = 96,
                beamWidth = 28,
                beamDepth = 3
            )
            state = phase.state
            centerMacroCount += phase.appliedMacros
        }

        if (m.centerMismatch(state) != 0) {
            // A wider all-remainder objective is a useful escape hatch for rare center local minima.
            // Centers are weighted strongly but the search may temporarily move one to unlock two.
            val phase = improve(
                start = state,
                macros = banks.centerMacros,
                score = m::weightedRemainderScore,
                maxApplications = 120,
                beamWidth = 36,
                beamDepth = 4
            )
            state = phase.state
            centerMacroCount += phase.appliedMacros
        }

        if (m.centerMismatch(state) != 0) {
            return Result(
                moves = emptyList(),
                centerMacros = centerMacroCount,
                edgeMacros = 0,
                finalMismatch = m.remainderMismatch(state),
                diagnostic = "Center reduction stalled at ${m.centerMismatch(state)} misplaced center stickers"
            )
        }

        val centerMoves = mutableListOf<Move>()
        // Re-run center phase once to recover its concrete path. The first passes above intentionally
        // kept only state/progress; path-bearing solve below is deterministic from the same input.
        // This keeps beam nodes small on a phone while still returning exact moves.
        state = m.fromCube(cubeWithSolvedSkeleton)
        val centerPathPhase = improveWithPath(
            start = state,
            macros = banks.centerMacros,
            score = m::centerMismatch,
            maxApplications = 120,
            beamWidth = 36,
            beamDepth = 4
        )
        state = centerPathPhase.state
        centerMoves += centerPathPhase.moves

        if (m.centerMismatch(state) != 0) {
            return Result(
                moves = emptyList(),
                centerMacros = centerPathPhase.appliedMacros,
                edgeMacros = 0,
                finalMismatch = m.remainderMismatch(state),
                diagnostic = "Center path reconstruction stalled at ${m.centerMismatch(state)}"
            )
        }

        var edgeMacroCount = 0
        val edgeMoves = mutableListOf<Move>()
        if (m.wingMismatch(state) != 0 && banks.centerSafeEdgeMacros.isNotEmpty()) {
            val edgePhase = improveWithPath(
                start = state,
                macros = banks.centerSafeEdgeMacros,
                score = m::wingMismatch,
                maxApplications = 160,
                beamWidth = 36,
                beamDepth = 4
            )
            state = edgePhase.state
            edgeMacroCount += edgePhase.appliedMacros
            edgeMoves += edgePhase.moves
        }

        if (m.remainderMismatch(state) != 0) {
            // Final joint cleanup. All center macros preserve the solved 3x3 skeleton. Give centers a
            // much larger penalty so beam search normally keeps them solved, but permit a temporary
            // disturbance when an edge parity/endgame requires it.
            val cleanup = improveWithPath(
                start = state,
                macros = banks.centerMacros,
                score = m::weightedRemainderScore,
                maxApplications = 220,
                beamWidth = 44,
                beamDepth = 4
            )
            state = cleanup.state
            edgeMacroCount += cleanup.appliedMacros
            edgeMoves += cleanup.moves
        }

        val all = simplify(centerMoves + edgeMoves)
        val mismatch = m.remainderMismatch(state)
        return Result(
            moves = if (mismatch == 0) all else emptyList(),
            centerMacros = centerPathPhase.appliedMacros,
            edgeMacros = edgeMacroCount,
            finalMismatch = mismatch,
            diagnostic = if (mismatch == 0) {
                "table-free reduction complete"
            } else {
                "reduction stalled: centers=${m.centerMismatch(state)}, wings=${m.wingMismatch(state)}"
            }
        )
    }

    private data class Progress(val state: ByteArray, val appliedMacros: Int)
    private data class PathProgress(
        val state: ByteArray,
        val moves: List<Move>,
        val appliedMacros: Int
    )

    /** Lightweight phase used as a feasibility probe without retaining move paths in beam nodes. */
    private fun improve(
        start: ByteArray,
        macros: List<Macro>,
        score: (ByteArray) -> Int,
        maxApplications: Int,
        beamWidth: Int,
        beamDepth: Int
    ): Progress {
        var state = start
        var current = score(state)
        var applications = 0
        while (current > 0 && applications < maxApplications) {
            val direct = bestDirect(state, current, macros, score)
            if (direct != null) {
                state = direct.first
                current = direct.second
                applications++
                continue
            }
            val escape = beamImprovement(state, current, macros, score, beamWidth, beamDepth)
                ?: break
            state = escape.state
            current = escape.score
            applications += escape.macroIndices.size
        }
        return Progress(state, applications)
    }

    private fun improveWithPath(
        start: ByteArray,
        macros: List<Macro>,
        score: (ByteArray) -> Int,
        maxApplications: Int,
        beamWidth: Int,
        beamDepth: Int
    ): PathProgress {
        var state = start
        var current = score(state)
        var applications = 0
        val moves = ArrayList<Move>()

        while (current > 0 && applications < maxApplications) {
            val directIndex = bestDirectIndex(state, current, macros, score)
            if (directIndex >= 0) {
                val macro = macros[directIndex]
                state = macro.apply(state)
                current = score(state)
                moves += macro.moves
                applications++
                continue
            }

            val escape = beamImprovement(state, current, macros, score, beamWidth, beamDepth)
                ?: break
            for (index in escape.macroIndices) {
                val macro = macros[index]
                state = macro.apply(state)
                moves += macro.moves
                applications++
            }
            current = score(state)
        }
        return PathProgress(state, simplify(moves), applications)
    }

    private fun bestDirect(
        state: ByteArray,
        current: Int,
        macros: List<Macro>,
        score: (ByteArray) -> Int
    ): Pair<ByteArray, Int>? {
        val index = bestDirectIndex(state, current, macros, score)
        if (index < 0) return null
        val next = macros[index].apply(state)
        return next to score(next)
    }

    private fun bestDirectIndex(
        state: ByteArray,
        current: Int,
        macros: List<Macro>,
        score: (ByteArray) -> Int
    ): Int {
        var bestIndex = -1
        var bestScore = current
        var bestMoveCost = Int.MAX_VALUE
        for (i in macros.indices) {
            val macro = macros[i]
            val candidateScore = macro.scoreAfter(state, score)
            if (candidateScore < bestScore ||
                (candidateScore == bestScore && candidateScore < current && macro.moves.size < bestMoveCost)
            ) {
                bestIndex = i
                bestScore = candidateScore
                bestMoveCost = macro.moves.size
            }
        }
        return bestIndex
    }

    private data class BeamNode(
        val state: ByteArray,
        val macroIndices: IntArray,
        val score: Int,
        val lastMacro: Int
    )

    private data class BeamCandidate(
        val parent: BeamNode,
        val macroIndex: Int,
        val score: Int
    )

    private data class BeamResult(
        val state: ByteArray,
        val macroIndices: IntArray,
        val score: Int
    )

    private fun beamImprovement(
        start: ByteArray,
        targetScore: Int,
        macros: List<Macro>,
        score: (ByteArray) -> Int,
        width: Int,
        maxDepth: Int
    ): BeamResult? {
        var frontier = listOf(BeamNode(start, IntArray(0), targetScore, -1))
        val seen = HashMap<Int, Int>()
        seen[start.contentHashCode()] = targetScore

        for (depth in 1..maxDepth) {
            // Max-heap: worst retained candidate at the head.
            val best = PriorityQueue<BeamCandidate>(
                compareByDescending<BeamCandidate> { it.score }
                    .thenByDescending { it.parent.macroIndices.size }
            )

            for (node in frontier) {
                for (mi in macros.indices) {
                    if (mi == node.lastMacro) continue
                    val macro = macros[mi]
                    val candidateScore = macro.scoreAfter(node.state, score)
                    val candidate = BeamCandidate(node, mi, candidateScore)
                    if (best.size < width) {
                        best += candidate
                    } else {
                        val worst = best.peek()
                        if (candidateScore < worst.score) {
                            best.poll()
                            best += candidate
                        }
                    }
                }
            }

            if (best.isEmpty()) return null
            val next = ArrayList<BeamNode>(best.size)
            while (best.isNotEmpty()) {
                val candidate = best.poll()
                val macro = macros[candidate.macroIndex]
                val nextState = macro.apply(candidate.parent.state)
                val exactScore = score(nextState)
                val hash = nextState.contentHashCode()
                val previous = seen[hash]
                if (previous != null && previous <= exactScore) continue
                seen[hash] = exactScore

                val path = candidate.parent.macroIndices.copyOf(candidate.parent.macroIndices.size + 1)
                path[path.lastIndex] = candidate.macroIndex
                if (exactScore < targetScore) {
                    return BeamResult(nextState, path, exactScore)
                }
                next += BeamNode(nextState, path, exactScore, candidate.macroIndex)
            }
            frontier = next.sortedBy { it.score }.take(width)
            if (frontier.isEmpty()) return null
        }
        return null
    }

    private class Macro(
        val moves: List<Move>,
        private val sourceForDest: IntArray,
        private val movedDestinations: IntArray
    ) {
        fun apply(state: ByteArray): ByteArray {
            val out = state.copyOf()
            for (dest in movedDestinations) out[dest] = state[sourceForDest[dest]]
            return out
        }

        /**
         * score() is not decomposable in general, so apply a compact copy. This method exists as a
         * seam for later delta-scoring/profiling without changing the search code.
         */
        fun scoreAfter(state: ByteArray, score: (ByteArray) -> Int): Int = score(apply(state))
    }

    private class MacroBanks(private val model: Model) {
        val centerMacros: List<Macro>
        val centerSafeEdgeMacros: List<Macro>

        init {
            val raw = LinkedHashMap<PermutationKey, MacroSeed>()
            val baseCommutators = buildBaseCommutators()
            val setups = buildSetups()

            for (base in baseCommutators) {
                for (setup in setups) {
                    val sequence = if (setup.isEmpty()) {
                        base
                    } else {
                        setup + base + inverseSequence(setup)
                    }
                    addSeed(raw, sequence)
                }
            }

            // Standard 5x5 reduction parity tools. Keep them only if the generated permutation
            // actually fixes the outer skeleton; the model is the source of truth.
            val parityCandidates = listOf(
                Move.parseAlgorithm("Rw2 B2 U2 Lw U2 Rw' U2 Rw U2 F2 Rw F2 Lw' B2 Rw2"),
                Move.parseAlgorithm("Rw2 R2 U2 Rw2 R2 Uw2 Rw2 R2 Uw2")
            )
            for (alg in parityCandidates) {
                addSeed(raw, alg)
                for (setup in setups.filter { it.size <= 1 }) {
                    if (setup.isNotEmpty()) addSeed(raw, setup + alg + inverseSequence(setup))
                }
            }

            val seeds = raw.values.toList()
            centerMacros = seeds
                .filter { seed ->
                    model.skeletonIndices.all { seed.destForSource[it] == it } &&
                        model.centerIndices.any { seed.destForSource[it] != it }
                }
                .sortedWith(compareBy<MacroSeed> { it.movedRemainder }.thenBy { it.moves.size })
                .map(::toMacro)

            centerSafeEdgeMacros = buildCenterSafeEdgeMacros(seeds)
        }

        private fun buildBaseCommutators(): List<List<Move>> {
            val out = ArrayList<List<Move>>()
            for (innerFace in faces) {
                for (innerTurns in listOf(1, 3)) {
                    val inner = innerSlice(innerFace, innerTurns)
                    for (outerFace in faces) {
                        if (axis(innerFace) == axis(outerFace)) continue
                        for (outerTurns in listOf(1, 3)) {
                            val outer = listOf(Move(outerFace, 1, outerTurns))
                            out += inner + outer + inverseSequence(inner) + inverseSequence(outer)
                        }
                    }
                }
            }
            return out
        }

        private fun buildSetups(): List<List<Move>> {
            val setups = ArrayList<List<Move>>()
            setups += emptyList()
            for (face in faces) for (turns in 1..3) setups += listOf(Move(face, 1, turns))

            // Two quarter-turn setup moves make the commutator bank transitive across the full
            // center/wing orbits without exploding into a giant lookup table.
            for (a in faces) for (at in listOf(1, 3)) {
                for (b in faces) for (bt in listOf(1, 3)) {
                    if (axis(a) == axis(b)) continue
                    setups += listOf(Move(a, 1, at), Move(b, 1, bt))
                }
            }
            return setups
        }

        private fun addSeed(
            out: LinkedHashMap<PermutationKey, MacroSeed>,
            sequence: List<Move>
        ) {
            if (sequence.isEmpty()) return
            val permutation = model.permutation(sequence)
            if (!model.skeletonIndices.all { permutation[it] == it }) return
            val moved = model.remainderIndices.count { permutation[it] != it }
            if (moved == 0) return
            val key = PermutationKey(permutation)
            val existing = out[key]
            if (existing == null || sequence.size < existing.moves.size) {
                out[key] = MacroSeed(simplify(sequence), permutation, moved)
            }
        }

        private fun buildCenterSafeEdgeMacros(seeds: List<MacroSeed>): List<Macro> {
            val groups = HashMap<PermutationKey, MutableList<MacroSeed>>()
            for (seed in seeds) {
                if (!model.skeletonIndices.all { seed.destForSource[it] == it }) continue
                val signature = IntArray(model.centerIndices.size) { i ->
                    model.centerSlotByGlobal.getValue(seed.destForSource[model.centerIndices[i]])
                }
                groups.getOrPut(PermutationKey(signature)) { mutableListOf() } += seed
            }

            val edgeSeeds = LinkedHashMap<PermutationKey, MacroSeed>()
            for (group in groups.values) {
                if (group.size < 2) continue
                val shortlist = group.sortedBy { it.moves.size }.take(8)
                for (a in shortlist.indices) for (b in shortlist.indices) {
                    if (a == b) continue
                    val sequence = shortlist[a].moves + inverseSequence(shortlist[b].moves)
                    val permutation = model.permutation(sequence)
                    if (!model.skeletonIndices.all { permutation[it] == it }) continue
                    if (!model.centerIndices.all { permutation[it] == it }) continue
                    val movedWings = model.wingIndices.count { permutation[it] != it }
                    if (movedWings == 0) continue
                    val key = PermutationKey(permutation)
                    val seed = MacroSeed(simplify(sequence), permutation, movedWings)
                    val existing = edgeSeeds[key]
                    if (existing == null || seed.moves.size < existing.moves.size) edgeSeeds[key] = seed
                }
            }

            // Also accept any directly generated macro/parity algorithm that happens to fix centers.
            for (seed in seeds) {
                if (!model.skeletonIndices.all { seed.destForSource[it] == it }) continue
                if (!model.centerIndices.all { seed.destForSource[it] == it }) continue
                if (!model.wingIndices.any { seed.destForSource[it] != it }) continue
                val key = PermutationKey(seed.destForSource)
                val existing = edgeSeeds[key]
                if (existing == null || seed.moves.size < existing.moves.size) edgeSeeds[key] = seed
            }

            return edgeSeeds.values
                .sortedWith(compareBy<MacroSeed> { it.movedRemainder }.thenBy { it.moves.size })
                .map(::toMacro)
        }

        private fun toMacro(seed: MacroSeed): Macro {
            val sourceForDest = IntArray(STICKERS)
            for (src in 0 until STICKERS) sourceForDest[seed.destForSource[src]] = src
            val moved = IntArray(STICKERS) { it }.filter { sourceForDest[it] != it }.toIntArray()
            return Macro(seed.moves, sourceForDest, moved)
        }

        private data class MacroSeed(
            val moves: List<Move>,
            val destForSource: IntArray,
            val movedRemainder: Int
        )
    }

    private class Model {
        private val template = CubeState(5)
        private val keys: List<StickerKey> = buildList(STICKERS) {
            for (face in faces) for (r in 0 until N) for (c in 0 until N) {
                add(template.keyFromFaceCell(face, r, c))
            }
        }
        private val indexByKey = keys.withIndex().associate { it.value to it.index }
        private val movePermutationCache = HashMap<Move, IntArray>()

        val centerIndices: IntArray = buildList {
            for (f in faces.indices) for (r in 1..3) for (c in 1..3) {
                if (r == 2 && c == 2) continue
                add(f * 25 + r * 5 + c)
            }
        }.toIntArray()

        val skeletonIndices: IntArray = buildList {
            for (f in faces.indices) for (r in 0 until N) for (c in 0 until N) {
                val corner = (r == 0 || r == 4) && (c == 0 || c == 4)
                val middleEdge = ((r == 0 || r == 4) && c == 2) ||
                    ((c == 0 || c == 4) && r == 2)
                val fixedCenter = r == 2 && c == 2
                if (corner || middleEdge || fixedCenter) add(f * 25 + r * 5 + c)
            }
        }.toIntArray()

        private val skeletonSet = skeletonIndices.toHashSet()
        val remainderIndices: IntArray = (0 until STICKERS).filter { it !in skeletonSet }.toIntArray()
        private val centerSet = centerIndices.toHashSet()
        val wingIndices: IntArray = remainderIndices.filter { it !in centerSet }.toIntArray()
        val centerSlotByGlobal: Map<Int, Int> = centerIndices.withIndex().associate { it.value to it.index }

        private val goal = ByteArray(STICKERS) { index -> (index / 25).toByte() }

        fun fromCube(cube: CubeState): ByteArray {
            require(cube.size == 5)
            val out = ByteArray(STICKERS)
            var index = 0
            for (face in faces) {
                for (color in cube.faceColors(face)) out[index++] = color.ordinal.toByte()
            }
            return out
        }

        fun skeletonMismatch(state: ByteArray): Int = mismatch(state, skeletonIndices)
        fun centerMismatch(state: ByteArray): Int = mismatch(state, centerIndices)
        fun wingMismatch(state: ByteArray): Int = mismatch(state, wingIndices)
        fun remainderMismatch(state: ByteArray): Int = mismatch(state, remainderIndices)

        fun weightedRemainderScore(state: ByteArray): Int = centerMismatch(state) * 8 + wingMismatch(state)

        private fun mismatch(state: ByteArray, indices: IntArray): Int {
            var n = 0
            for (index in indices) if (state[index] != goal[index]) n++
            return n
        }

        fun permutation(sequence: List<Move>): IntArray {
            var result = IntArray(STICKERS) { it }
            for (move in sequence) {
                val p = movePermutation(move)
                for (src in 0 until STICKERS) result[src] = p[result[src]]
            }
            return result
        }

        private fun movePermutation(move: Move): IntArray =
            movePermutationCache.getOrPut(move) {
                IntArray(STICKERS) { src ->
                    var key = keys[src]
                    if (key.inSlab(move.face, move.width, N)) {
                        repeat(move.quarterTurns) { key = key.rotateClockwise(move.face, N) }
                    }
                    indexByKey.getValue(key)
                }
            }
    }

    private class PermutationKey(values: IntArray) {
        private val data = values.copyOf()
        private val hash = data.contentHashCode()
        override fun hashCode(): Int = hash
        override fun equals(other: Any?): Boolean = other is PermutationKey && data.contentEquals(other.data)
    }

    private fun innerSlice(face: Face, turns: Int): List<Move> = listOf(
        Move(face, 2, turns),
        Move(face, 1, inverseTurns(turns))
    )

    private fun inverseSequence(moves: List<Move>): List<Move> = moves.asReversed().map { it.inverse() }

    private fun inverseTurns(turns: Int): Int = if (turns == 2) 2 else 4 - turns

    private fun axis(face: Face): Int = when (face) {
        Face.R, Face.L -> 0
        Face.U, Face.D -> 1
        Face.F, Face.B -> 2
    }

    /** Collapse adjacent turns of the same exact layer family. */
    internal fun simplify(moves: List<Move>): List<Move> {
        if (moves.isEmpty()) return emptyList()
        val out = ArrayList<Move>(moves.size)
        for (move in moves) {
            val previous = out.lastOrNull()
            if (previous != null && previous.face == move.face && previous.width == move.width) {
                val merged = (previous.quarterTurns + move.quarterTurns) % 4
                out.removeAt(out.lastIndex)
                if (merged != 0) out += Move(move.face, move.width, merged)
            } else {
                out += move
            }
        }
        return out
    }
}

private fun StickerKey.inSlab(face: Face, width: Int, n: Int): Boolean = when (face) {
    Face.R -> x >= n - width
    Face.L -> x < width
    Face.U -> y >= n - width
    Face.D -> y < width
    Face.F -> z >= n - width
    Face.B -> z < width
}

private fun StickerKey.rotateClockwise(face: Face, n: Int): StickerKey = when (face) {
    Face.R -> rotX5(-1, n)
    Face.L -> rotX5(+1, n)
    Face.U -> rotY5(-1, n)
    Face.D -> rotY5(+1, n)
    Face.F -> rotZ5(-1, n)
    Face.B -> rotZ5(+1, n)
}

private fun StickerKey.rotX5(sign: Int, n: Int) = if (sign > 0)
    copy(y = n - 1 - z, z = y, ny = -nz, nz = ny)
else copy(y = z, z = n - 1 - y, ny = nz, nz = -ny)

private fun StickerKey.rotY5(sign: Int, n: Int) = if (sign > 0)
    copy(x = z, z = n - 1 - x, nx = nz, nz = -nx)
else copy(x = n - 1 - z, z = x, nx = -nz, nz = nx)

private fun StickerKey.rotZ5(sign: Int, n: Int) = if (sign > 0)
    copy(x = n - 1 - y, y = x, nx = -ny, ny = nx)
else copy(x = y, y = n - 1 - x, nx = ny, ny = -nx)
