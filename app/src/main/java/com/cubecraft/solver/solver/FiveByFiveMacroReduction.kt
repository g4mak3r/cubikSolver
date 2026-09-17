package com.cubecraft.solver.solver

import com.cubecraft.solver.model.CubeState
import com.cubecraft.solver.model.Face
import com.cubecraft.solver.model.Move
import com.cubecraft.solver.model.StickerKey

/**
 * Compact table-free reduction of a 5x5 whose outer 3x3 skeleton is already solved.
 *
 * Instead of shipping gigabytes of pruning tables, this engine generates a reusable bank of legal
 * commutators. Every macro fixes all 54 skeleton stickers point-for-point, so search only has to
 * arrange the remaining 96 stickers (48 movable centers + 48 wings). Search works on a 96-byte
 * state and scores a macro by touching only the destinations it actually moves.
 *
 * This is deliberately a non-optimal reduction solver: correctness and offline size matter more
 * than minimal move count. FiveByFiveSolver always replays the complete result on CubeState(5)
 * before exposing it to the UI.
 */
internal object FiveByFiveMacroReduction {
    data class Result(
        val moves: List<Move>,
        val finalMismatch: Int,
        val macrosApplied: Int,
        val macroBankSize: Int,
        val diagnostic: String
    )

    private const val SIZE = 5
    private const val STICKERS = 150
    private val faces = Face.entries

    private val model by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { Model() }
    private val macros by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { buildMacroBank(model) }

    fun solve(cubeWithSolvedSkeleton: CubeState): Result {
        require(cubeWithSolvedSkeleton.size == 5) { "5x5 reduction expects CubeState(5)" }
        val m = model
        check(m.skeletonMismatch(cubeWithSolvedSkeleton) == 0) {
            "5x5 reduction requires a solved outer 3x3 skeleton"
        }

        if (macros.isEmpty()) {
            return Result(emptyList(), 96, 0, 0, "commutator bank is empty")
        }

        var state = m.compactState(cubeWithSolvedSkeleton)
        var score = m.mismatch(state)
        if (score == 0) return Result(emptyList(), 0, 0, macros.size, "already reduced")

        val path = ArrayList<Int>()
        var applications = 0
        val maxApplications = 260

        while (score > 0 && applications < maxApplications) {
            val direct = bestDirect(state, score)
            if (direct != null) {
                state = macros[direct.index].apply(state)
                score = direct.score
                path += direct.index
                applications++
                continue
            }

            // Near the end we intentionally look deeper. Commutator endgames often need one or two
            // setup macros that temporarily make the sticker-count score worse before it improves.
            val depth = when {
                score <= 8 -> 6
                score <= 18 -> 5
                else -> 4
            }
            val width = when {
                score <= 8 -> 72
                score <= 18 -> 56
                else -> 36
            }
            val escape = findEscape(state, score, depth, width) ?: break
            for (index in escape.path) {
                state = macros[index].apply(state)
                path += index
                applications++
            }
            score = m.mismatch(state)
        }

        val moves = if (score == 0) {
            simplify(path.flatMap { macros[it].moves })
        } else {
            emptyList()
        }

        return Result(
            moves = moves,
            finalMismatch = score,
            macrosApplied = applications,
            macroBankSize = macros.size,
            diagnostic = if (score == 0) {
                "table-free reduction complete"
            } else {
                "table-free search stalled with $score / 96 reduction stickers misplaced"
            }
        )
    }

    private data class Candidate(val index: Int, val score: Int, val moveCost: Int)

    private fun bestDirect(state: ByteArray, current: Int): Candidate? {
        var best: Candidate? = null
        for (i in macros.indices) {
            val macro = macros[i]
            val next = macro.scoreAfter(state, current, model.goalCompact)
            if (next >= current) continue
            val candidate = Candidate(i, next, macro.moves.size)
            val old = best
            if (old == null || next < old.score || (next == old.score && candidate.moveCost < old.moveCost)) {
                best = candidate
            }
        }
        return best
    }

    private data class BeamNode(
        val state: ByteArray,
        val score: Int,
        val path: IntArray,
        val last: Int
    )

    private data class Escape(val path: IntArray, val score: Int)

    /**
     * Bounded best-first beam used only when no single macro improves the current score.
     * It may cross a short uphill plateau but returns only after finding a state strictly better
     * than the state at entry.
     */
    private fun findEscape(
        start: ByteArray,
        target: Int,
        maxDepth: Int,
        width: Int
    ): Escape? {
        var frontier = listOf(BeamNode(start, target, IntArray(0), -1))
        val seen = HashMap<StateKey, Int>()
        seen[StateKey(start)] = target

        for (depth in 1..maxDepth) {
            val expanded = ArrayList<BeamNode>(frontier.size * 16)
            for (node in frontier) {
                val local = topCandidates(node.state, node.score, node.last, 18)
                for (candidate in local) {
                    val nextState = macros[candidate.index].apply(node.state)
                    val exact = model.mismatch(nextState)
                    val key = StateKey(nextState)
                    val old = seen[key]
                    if (old != null && old <= exact) continue
                    seen[key] = exact

                    val nextPath = node.path.copyOf(node.path.size + 1)
                    nextPath[nextPath.lastIndex] = candidate.index
                    if (exact < target) return Escape(nextPath, exact)
                    expanded += BeamNode(nextState, exact, nextPath, candidate.index)
                }
            }
            if (expanded.isEmpty()) return null
            frontier = expanded
                .sortedWith(compareBy<BeamNode> { it.score }.thenBy { it.path.size })
                .take(width)
        }
        return null
    }

    private fun topCandidates(
        state: ByteArray,
        current: Int,
        last: Int,
        limit: Int
    ): List<Candidate> {
        val best = ArrayList<Candidate>(limit + 1)
        for (i in macros.indices) {
            if (i == last) continue
            val macro = macros[i]
            val candidate = Candidate(
                index = i,
                score = macro.scoreAfter(state, current, model.goalCompact),
                moveCost = macro.moves.size
            )
            var pos = best.binarySearch(candidate, candidateComparator)
            if (pos < 0) pos = -pos - 1
            if (pos <= limit) {
                best.add(pos, candidate)
                if (best.size > limit) best.removeAt(best.lastIndex)
            }
        }
        return best
    }

    private val candidateComparator = compareBy<Candidate> { it.score }.thenBy { it.moveCost }

    private class Macro(
        val moves: List<Move>,
        private val sourceForDestination: IntArray,
        private val movedDestinations: IntArray
    ) {
        fun apply(state: ByteArray): ByteArray {
            val out = state.copyOf()
            for (dest in movedDestinations) out[dest] = state[sourceForDestination[dest]]
            return out
        }

        fun scoreAfter(state: ByteArray, current: Int, goal: ByteArray): Int {
            var score = current
            for (dest in movedDestinations) {
                val beforeWrong = state[dest] != goal[dest]
                val after = state[sourceForDestination[dest]]
                val afterWrong = after != goal[dest]
                if (beforeWrong && !afterWrong) score--
                else if (!beforeWrong && afterWrong) score++
            }
            return score
        }
    }

    private data class MacroSeed(val moves: List<Move>, val destinationForSource: IntArray)

    private fun buildMacroBank(model: Model): List<Macro> {
        val unique = LinkedHashMap<PermutationKey, MacroSeed>()
        val setups = buildSetups()

        for (innerFace in faces) {
            for (innerTurns in 1..3) {
                val a = innerSlice(innerFace, innerTurns)
                val aInv = inverseSequence(a)
                for (outerFace in faces) {
                    if (axis(innerFace) == axis(outerFace)) continue
                    for (outerTurns in 1..3) {
                        val b = listOf(Move(outerFace, 1, outerTurns))
                        val commutator = a + b + aInv + inverseSequence(b)
                        for (setup in setups) {
                            val sequence = if (setup.isEmpty()) {
                                commutator
                            } else {
                                setup + commutator + inverseSequence(setup)
                            }
                            addSeed(model, unique, sequence)
                        }
                    }
                }
            }
        }

        // Explicit 5x5 reduction parity sequences are tiny compared with pruning tables. They are
        // accepted into the bank only if the geometry model proves that they fix the outer skeleton.
        val parity = listOf(
            Move.parseAlgorithm("Rw2 B2 U2 Lw U2 Rw' U2 Rw U2 F2 Rw F2 Lw' B2 Rw2"),
            Move.parseAlgorithm("Rw2 R2 U2 Rw2 R2 Uw2 Rw2 R2 Uw2")
        )
        for (algorithm in parity) {
            addSeed(model, unique, algorithm)
            for (setup in setups.filter { it.size <= 1 }) {
                if (setup.isNotEmpty()) addSeed(model, unique, setup + algorithm + inverseSequence(setup))
            }
        }

        return unique.values
            .map { seed -> model.toMacro(seed) }
            .sortedWith(compareBy<Macro> { it.moves.size })
    }

    private fun buildSetups(): List<List<Move>> {
        val result = ArrayList<List<Move>>()
        result.add(emptyList())
        for (face in faces) for (turns in 1..3) {
            result.add(listOf(Move(face, 1, turns)))
        }
        // Quarter-turn two-move setups spread the basic commutator over all center/wing locations.
        for (a in faces) for (at in listOf(1, 3)) {
            for (b in faces) for (bt in listOf(1, 3)) {
                if (axis(a) != axis(b)) result.add(listOf(Move(a, 1, at), Move(b, 1, bt)))
            }
        }
        return result
    }

    private fun addSeed(
        model: Model,
        unique: LinkedHashMap<PermutationKey, MacroSeed>,
        sequence: List<Move>
    ) {
        val simplified = simplify(sequence)
        if (simplified.isEmpty()) return
        val permutation = model.permutation(simplified)
        if (!model.skeletonIndices.all { permutation[it] == it }) return
        if (!model.remainderIndices.any { permutation[it] != it }) return
        val key = PermutationKey(permutation)
        val old = unique[key]
        if (old == null || simplified.size < old.moves.size) {
            unique[key] = MacroSeed(simplified, permutation)
        }
    }

    private class Model {
        private val template = CubeState(5)
        private val keys: List<StickerKey> = buildList(STICKERS) {
            for (face in faces) for (r in 0 until SIZE) for (c in 0 until SIZE) {
                add(template.keyFromFaceCell(face, r, c))
            }
        }
        private val indexByKey = keys.withIndex().associate { it.value to it.index }
        private val moveCache = HashMap<Move, IntArray>()

        val skeletonIndices: IntArray = buildList {
            for (f in faces.indices) for (r in 0 until SIZE) for (c in 0 until SIZE) {
                val corner = (r == 0 || r == 4) && (c == 0 || c == 4)
                val middleEdge = ((r == 0 || r == 4) && c == 2) ||
                    ((c == 0 || c == 4) && r == 2)
                val fixedCenter = r == 2 && c == 2
                if (corner || middleEdge || fixedCenter) add(f * 25 + r * 5 + c)
            }
        }.toIntArray()

        private val skeletonSet = skeletonIndices.toHashSet()
        val remainderIndices: IntArray = (0 until STICKERS).filter { it !in skeletonSet }.toIntArray()
        private val compactByGlobal = remainderIndices.withIndex().associate { it.value to it.index }
        val goalCompact = ByteArray(remainderIndices.size) { compact ->
            (remainderIndices[compact] / 25).toByte()
        }

        fun compactState(cube: CubeState): ByteArray {
            val all = ByteArray(STICKERS)
            var i = 0
            for (face in faces) for (color in cube.faceColors(face)) all[i++] = color.ordinal.toByte()
            return ByteArray(remainderIndices.size) { compact -> all[remainderIndices[compact]] }
        }

        fun mismatch(state: ByteArray): Int {
            var bad = 0
            for (i in state.indices) if (state[i] != goalCompact[i]) bad++
            return bad
        }

        fun skeletonMismatch(cube: CubeState): Int {
            var bad = 0
            val facesNow = cube.snapshot()
            for (global in skeletonIndices) {
                val face = faces[global / 25]
                val local = global % 25
                if (facesNow.getValue(face)[local] != face) bad++
            }
            return bad
        }

        fun permutation(sequence: List<Move>): IntArray {
            val result = IntArray(STICKERS) { it }
            for (move in sequence) {
                val p = movePermutation(move)
                for (source in 0 until STICKERS) result[source] = p[result[source]]
            }
            return result
        }

        fun toMacro(seed: MacroSeed): Macro {
            val sourceForDestination = IntArray(remainderIndices.size)
            for (sourceCompact in remainderIndices.indices) {
                val sourceGlobal = remainderIndices[sourceCompact]
                val destinationGlobal = seed.destinationForSource[sourceGlobal]
                val destinationCompact = compactByGlobal.getValue(destinationGlobal)
                sourceForDestination[destinationCompact] = sourceCompact
            }
            val moved = sourceForDestination.indices.filter { sourceForDestination[it] != it }.toIntArray()
            return Macro(seed.moves, sourceForDestination, moved)
        }

        private fun movePermutation(move: Move): IntArray = moveCache.getOrPut(move) {
            IntArray(STICKERS) { source ->
                var key = keys[source]
                if (key.inSlab5(move.face, move.width, SIZE)) {
                    repeat(move.quarterTurns) { key = key.rotateClockwise5(move.face, SIZE) }
                }
                indexByKey.getValue(key)
            }
        }
    }

    private class StateKey(state: ByteArray) {
        private val data = state.copyOf()
        private val hash = data.contentHashCode()
        override fun hashCode(): Int = hash
        override fun equals(other: Any?): Boolean = other is StateKey && data.contentEquals(other.data)
    }

    private class PermutationKey(permutation: IntArray) {
        private val data = permutation.copyOf()
        private val hash = data.contentHashCode()
        override fun hashCode(): Int = hash
        override fun equals(other: Any?): Boolean = other is PermutationKey && data.contentEquals(other.data)
    }

    private fun innerSlice(face: Face, turns: Int): List<Move> = listOf(
        Move(face, 2, turns),
        Move(face, 1, if (turns == 2) 2 else 4 - turns)
    )

    private fun inverseSequence(sequence: List<Move>): List<Move> =
        sequence.asReversed().map { it.inverse() }

    private fun axis(face: Face): Int = when (face) {
        Face.R, Face.L -> 0
        Face.U, Face.D -> 1
        Face.F, Face.B -> 2
    }

    /** Merge only adjacent turns of exactly the same physical layer family. */
    internal fun simplify(moves: List<Move>): List<Move> {
        if (moves.isEmpty()) return emptyList()
        val out = ArrayList<Move>(moves.size)
        for (move in moves) {
            val previous = out.lastOrNull()
            if (previous != null && previous.face == move.face && previous.width == move.width) {
                val turns = (previous.quarterTurns + move.quarterTurns) % 4
                out.removeAt(out.lastIndex)
                if (turns != 0) out.add(Move(move.face, move.width, turns))
            } else {
                out.add(move)
            }
        }
        return out
    }
}

private fun StickerKey.inSlab5(face: Face, width: Int, n: Int): Boolean = when (face) {
    Face.R -> x >= n - width
    Face.L -> x < width
    Face.U -> y >= n - width
    Face.D -> y < width
    Face.F -> z >= n - width
    Face.B -> z < width
}

private fun StickerKey.rotateClockwise5(face: Face, n: Int): StickerKey = when (face) {
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
