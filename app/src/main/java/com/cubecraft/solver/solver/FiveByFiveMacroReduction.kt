package com.cubecraft.solver.solver

import com.cubecraft.solver.model.CubeState
import com.cubecraft.solver.model.Face
import com.cubecraft.solver.model.Move
import com.cubecraft.solver.model.StickerKey
import kotlin.random.Random

/**
 * Table-free 5x5 reduction engine.
 *
 * This is a staged reduction solver rather than a global 96-sticker hill-climb:
 *  1. make every 3x3 centre block one colour;
 *  2. pair the three physical pieces of every edge while preserving the centres;
 *  3. hand the reduced position back to FiveByFiveSolver for the final 3x3 solve.
 *
 * The search keeps only short facelet permutations in memory. There are no external pruning packs
 * and no scramble-history shortcuts. Candidate families, conjugates and endgame operators are
 * generated from CubeState's own move geometry, then every final solution is replay-verified by
 * FiveByFiveSolver.
 *
 * Search strategy and operator-pool structure are adapted from Praval's MIT-licensed
 * rubiks-cube-solver project (2026); see THIRD_PARTY_NOTICES.md.
 */
internal object FiveByFiveMacroReduction {
    data class Result(
        val moves: List<Move>,
        val centresSolved: Boolean,
        val edgesPaired: Boolean,
        val centreScore: Int,
        val edgeScore: Int,
        val diagnostic: String
    )

    private const val N = 5
    private const val FACELETS = 150
    private const val CENTRE_TARGET = 54
    private const val EDGE_TARGET = 24

    private const val STAGE_ATTEMPTS = 4
    private const val MAX_STAGE_MOVES = 360
    private const val MAX_STALLS = 80
    private const val MAX_PASSES = 5
    private const val DEEP_WINDOW = 8
    private const val NARROW_SLICE = 260

    private val model by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { Model() }
    private val pool by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { OperatorPool(model) }

    fun solve(state: CubeState, budgetMillis: Long = 25_000L): Result {
        require(state.size == 5) { "5x5 reduction expects CubeState(5)" }

        // Build the operator pool before starting the wall-clock budget. It is process-cached, so
        // first-use preparation is paid once and later solves start immediately.
        val p = pool
        val start = model.flatState(state)
        val seed = start.contentHashCode() xor 0x5A17_2026
        val session = SearchSession(
            model = model,
            pool = p,
            deadline = System.currentTimeMillis() + budgetMillis,
            random = Random(seed)
        )
        return session.reduce(start)
    }

    /** Long-known reduction parity algorithms expressed in cubikSolver's move vocabulary. */
    fun edgeFlipParity(): List<Move> = simplify(buildList {
        addAll(innerSlice(Face.R, 2))
        add(Move(Face.B, 1, 2))
        add(Move(Face.U, 1, 2))
        addAll(innerSlice(Face.L, 1))
        add(Move(Face.U, 1, 2))
        addAll(innerSlice(Face.R, 3))
        add(Move(Face.U, 1, 2))
        addAll(innerSlice(Face.R, 1))
        add(Move(Face.U, 1, 2))
        add(Move(Face.F, 1, 2))
        addAll(innerSlice(Face.R, 1))
        add(Move(Face.F, 1, 2))
        addAll(innerSlice(Face.L, 3))
        add(Move(Face.B, 1, 2))
        addAll(innerSlice(Face.R, 2))
    })

    fun edgeSwapParity(): List<Move> = simplify(buildList {
        addAll(innerSlice(Face.R, 2))
        add(Move(Face.U, 1, 2))
        addAll(innerSlice(Face.R, 2))
        add(Move(Face.U, 2, 2))
        addAll(innerSlice(Face.R, 2))
        add(Move(Face.U, 2, 2))
    })

    private class SearchSession(
        private val model: Model,
        private val pool: OperatorPool,
        private val deadline: Long,
        private val random: Random
    ) {
        private val wrappersNone = listOf(Wrapper(emptyList(), null, null))
        private val wrappersOne: List<Wrapper> = wrappersNone + pool.wrapperAtoms.map { atom ->
            wrapper(atom.moves)
        }
        private val wrappersTwo: List<Wrapper> by lazy {
            val out = ArrayList<Wrapper>()
            for (a in pool.wrapperAtoms) {
                for (b in pool.wrapperAtoms) {
                    if (a.key == b.key) continue
                    out += wrapper(a.moves + b.moves)
                }
            }
            out
        }

        private val scratchA = ByteArray(FACELETS)
        private val scratchB = ByteArray(FACELETS)
        private val scratchC = ByteArray(FACELETS)

        private var lastAttemptBest = 0
        private var lastStageReport = ""

        fun reduce(start: ByteArray): Result {
            var current = start.copyOf()
            val moves = ArrayList<Move>()

            for (pass in 0 until MAX_PASSES) {
                if (outOfTime()) break

                if (!model.centresSolved(current)) {
                    val centres = runStage(
                        label = "centres",
                        start = current,
                        target = CENTRE_TARGET,
                        score = model::centreScore,
                        legal = { true },
                        operators = pool.narrowCentre,
                        finishers = pool.centreFine,
                        deep = false
                    ) ?: return failure(current, moves, lastStageReport)
                    current = centres.state
                    moves += centres.moves
                }

                if (model.edgesPaired(current)) {
                    return success(current, moves)
                }

                val edges = runStage(
                    label = "edge pairing",
                    start = current,
                    target = EDGE_TARGET,
                    score = model::edgeQualityScore,
                    legal = model::centresSolved,
                    operators = pool.centreSafe,
                    finishers = pool.edgeFinishers,
                    deep = true
                )

                if (edges != null) {
                    current = edges.state
                    moves += edges.moves
                    if (model.centresSolved(current) && model.edgesPaired(current)) {
                        return success(current, moves)
                    }
                }

                if (pass == MAX_PASSES - 1 || outOfTime()) break

                // Deliberately break reduction and rebuild it from another nearby position. This is
                // much more reliable than letting the last-two-edges plateau wander forever.
                val breaker = perturbation()
                current = model.applyMoves(current, breaker)
                moves += breaker
            }

            return failure(
                current,
                moves,
                if (outOfTime()) "reduction time budget reached" else lastStageReport.ifBlank { "reduction stalled" }
            )
        }

        private fun success(state: ByteArray, moves: List<Move>) = Result(
            moves = simplify(moves),
            centresSolved = model.centresSolved(state),
            edgesPaired = model.edgesPaired(state),
            centreScore = model.centreScore(state),
            edgeScore = model.edgeQualityScore(state),
            diagnostic = "centres solved and all 12 edges paired"
        )

        private fun failure(state: ByteArray, moves: List<Move>, why: String) = Result(
            moves = emptyList(),
            centresSolved = model.centresSolved(state),
            edgesPaired = model.edgesPaired(state),
            centreScore = model.centreScore(state),
            edgeScore = model.edgeQualityScore(state),
            diagnostic = "$why; centres=${model.centreScore(state)}/$CENTRE_TARGET, edges=${model.edgeQualityScore(state)}/$EDGE_TARGET, exploredMoves=${moves.size}"
        )

        private data class StageResult(val state: ByteArray, val moves: List<Move>)

        private fun runStage(
            label: String,
            start: ByteArray,
            target: Int,
            score: (ByteArray) -> Int,
            legal: (ByteArray) -> Boolean,
            operators: List<Operator>,
            finishers: List<Operator>,
            deep: Boolean
        ): StageResult? {
            var best = score(start)
            for (attempt in 0 until STAGE_ATTEMPTS) {
                if (outOfTime()) break
                val result = attemptStage(start, target, score, legal, operators, finishers, deep)
                if (result != null) return result
                best = maxOf(best, lastAttemptBest)
            }
            lastStageReport = "$label reached $best of $target after $STAGE_ATTEMPTS attempts"
            return null
        }

        private fun attemptStage(
            start: ByteArray,
            target: Int,
            score: (ByteArray) -> Int,
            legal: (ByteArray) -> Boolean,
            operators: List<Operator>,
            finishers: List<Operator>,
            deep: Boolean
        ): StageResult? {
            var state = start.copyOf()
            val out = ArrayList<Move>()
            var best = score(state)
            lastAttemptBest = best
            var stalls = 0

            while (!outOfTime()) {
                val before = score(state)
                if (before >= target) return StageResult(state, simplify(out))

                var step = findBasic(state, pool.basicOperators) {
                    legal(it) && score(it) > before
                } ?: findOperator(state, operators, wrappersOne) {
                    legal(it) && score(it) > before
                }

                val nearlyDone = before >= target - DEEP_WINDOW
                if (step == null && nearlyDone) {
                    step = findOperator(state, finishers, wrappersOne) {
                        legal(it) && score(it) > before
                    }

                    if (step == null && deep && !outOfTime()) {
                        step = findOperator(state, narrowSlice(operators), wrappersTwo) {
                            legal(it) && score(it) > before
                        }
                    }
                    if (step == null && !outOfTime()) {
                        step = findOperator(state, narrowSlice(finishers), wrappersTwo) {
                            legal(it) && score(it) > before
                        }
                    }
                }

                if (step == null) {
                    stalls++
                    val allowance = when {
                        stalls < 12 -> 0
                        stalls < 42 -> 1
                        else -> 2
                    }
                    step = bestSideways(
                        state = state,
                        operators = operators,
                        wrappers = wrappersOne,
                        before = before,
                        allowance = allowance,
                        legal = legal,
                        score = score
                    ) ?: bestSideways(
                        state = state,
                        operators = operators,
                        wrappers = wrappersNone,
                        before = before,
                        allowance = allowance,
                        legal = legal,
                        score = score
                    ) ?: run {
                        lastAttemptBest = best
                        return null
                    }
                }

                state = model.applyMoves(state, step)
                out += step
                val after = score(state)
                if (after > best) {
                    best = after
                    lastAttemptBest = best
                    stalls = 0
                }

                if (stalls > MAX_STALLS || out.size > MAX_STAGE_MOVES) {
                    lastAttemptBest = best
                    return null
                }
            }

            lastAttemptBest = best
            return null
        }

        private fun findBasic(
            state: ByteArray,
            operators: List<Operator>,
            accept: (ByteArray) -> Boolean
        ): List<Move>? {
            for (op in operators) {
                model.applyPerm(state, op.perm, scratchB)
                if (accept(scratchB)) return op.moves
            }
            return null
        }

        private fun findOperator(
            state: ByteArray,
            operators: List<Operator>,
            wrappers: List<Wrapper>,
            accept: (ByteArray) -> Boolean
        ): List<Move>? {
            if (operators.isEmpty()) return null
            for (wrap in wrappers) {
                if (outOfTime()) return null
                val staged = stage(state, wrap.setup)
                for (op in operators) {
                    val result = finish(staged, op.perm, wrap.undo)
                    if (accept(result)) return buildSequence(wrap, op)
                }
            }
            return null
        }

        private fun bestSideways(
            state: ByteArray,
            operators: List<Operator>,
            wrappers: List<Wrapper>,
            before: Int,
            allowance: Int,
            legal: (ByteArray) -> Boolean,
            score: (ByteArray) -> Int
        ): List<Move>? {
            if (operators.isEmpty()) return null
            val floor = before - allowance
            var bestScore = Int.MIN_VALUE
            var picked: List<Move>? = null
            var ties = 0

            for (wrap in wrappers) {
                if (outOfTime()) return picked
                val staged = stage(state, wrap.setup)
                for (op in operators) {
                    val result = finish(staged, op.perm, wrap.undo)
                    if (!legal(result)) continue
                    val value = score(result)
                    if (value < floor) continue
                    if (value > bestScore) {
                        bestScore = value
                        ties = 1
                        picked = buildSequence(wrap, op)
                    } else if (value == bestScore) {
                        ties++
                        if (random.nextInt(ties) == 0) picked = buildSequence(wrap, op)
                    }
                }
            }
            return picked
        }

        private fun stage(state: ByteArray, setup: ShortArray?): ByteArray {
            if (setup == null) return state
            model.applyPerm(state, setup, scratchA)
            return scratchA
        }

        private fun finish(staged: ByteArray, op: ShortArray, undo: ShortArray?): ByteArray {
            model.applyPerm(staged, op, scratchB)
            if (undo == null) return scratchB
            model.applyPerm(scratchB, undo, scratchC)
            return scratchC
        }

        private fun buildSequence(wrap: Wrapper, op: Operator): List<Move> =
            if (wrap.moves.isEmpty()) op.moves
            else wrap.moves + op.moves + inverseSequence(wrap.moves)

        private fun wrapper(moves: List<Move>): Wrapper = Wrapper(
            moves = moves,
            setup = model.permutation(moves),
            undo = model.permutation(inverseSequence(moves))
        )

        private fun narrowSlice(operators: List<Operator>): List<Operator> =
            if (operators.size <= NARROW_SLICE) operators else operators.subList(0, NARROW_SLICE)

        private fun perturbation(): List<Move> {
            val a = pool.wrapperAtoms[random.nextInt(pool.wrapperAtoms.size)]
            var b = pool.wrapperAtoms[random.nextInt(pool.wrapperAtoms.size)]
            if (b.key == a.key) b = pool.wrapperAtoms[(pool.wrapperAtoms.indexOf(b) + 1) % pool.wrapperAtoms.size]
            val outer = pool.outerAtoms[random.nextInt(pool.outerAtoms.size)]
            return simplify(a.moves + outer.moves + b.moves)
        }

        private fun outOfTime(): Boolean = System.currentTimeMillis() >= deadline
    }

    private data class Wrapper(
        val moves: List<Move>,
        val setup: ShortArray?,
        val undo: ShortArray?
    )

    private data class Atom(val moves: List<Move>, val key: String)

    private class Operator(
        val moves: List<Move>,
        val perm: ShortArray,
        val centreSupport: Int,
        val edgeSupport: Int
    )

    /**
     * Reusable candidate operators. The lists are deliberately capped: breadth is useful, giant
     * tables are not. On a 5x5 a few thousand short permutations are enough to give the staged
     * search a gradient plus dedicated centre/edge endgames.
     */
    private class OperatorPool(private val model: Model) {
        val outerAtoms: List<Atom> = buildAtoms(width = 1, name = "outer")
        private val wideAtoms: List<Atom> = buildAtoms(width = 2, name = "wide")
        private val innerAtoms: List<Atom> = buildList {
            for (face in Face.entries) for (turns in 1..3) {
                add(Atom(innerSlice(face, turns), "inner-${face.symbol}"))
            }
        }

        val wrapperAtoms: List<Atom> = outerAtoms + wideAtoms + innerAtoms

        val basicOperators: List<Operator>
        val centreSafe: List<Operator>
        val narrowCentre: List<Operator>
        val centreFine: List<Operator>
        val edgeFinishers: List<Operator>

        init {
            val reference = model.solvedFlat
            basicOperators = (outerAtoms + wideAtoms + innerAtoms).map { atom ->
                toOperator(atom.moves, reference)
            }

            val safe = ArrayList<Operator>()
            val narrow = ArrayList<Operator>()
            val seen = HashSet<PermKey>()
            val carrier = wideAtoms + innerAtoms

            fun consider(sequence: List<Move>) {
                if (safe.size >= MAX_PER_LIST && narrow.size >= MAX_PER_LIST) return
                val moves = simplify(sequence)
                if (moves.isEmpty()) return
                val op = toOperator(moves, reference)
                if (op.centreSupport == 0 && op.edgeSupport == 0) return
                val key = PermKey(op.perm)
                if (!seen.add(key)) return
                if (op.centreSupport == 0 && op.edgeSupport > 0 && safe.size < MAX_PER_LIST) safe += op
                if (op.centreSupport in 1..NARROW_CENTRE_LIMIT && narrow.size < MAX_PER_LIST) narrow += op
            }

            // Core commutator families. These are cheap to enumerate and already provide the
            // three-/four-piece operators that do most of reduction work.
            for (a in carrier) for (b in outerAtoms) {
                consider(commutator(a.moves, b.moves))
                consider(commutator(b.moves, a.moves))
            }
            for (a in carrier) for (b in carrier) {
                if (a.key == b.key) continue
                consider(commutator(a.moves, b.moves))
            }

            // A modest sandwich family fills holes in the basic commutator bank without turning the
            // APK into a table dump. Runtime wrappers relocate these operators further as needed.
            outer@ for (a in carrier) {
                for (b in outerAtoms) {
                    for (c in carrier) {
                        consider(a.moves + b.moves + c.moves + inverseSequence(a.moves))
                        if (safe.size >= MAX_PER_LIST && narrow.size >= MAX_PER_LIST) break@outer
                    }
                }
            }

            // Parity operators are measured against solved geometry before being admitted.
            consider(edgeFlipParity())
            consider(edgeSwapParity())
            consider(inverseSequence(edgeFlipParity()))
            consider(inverseSequence(edgeSwapParity()))

            safe.sortWith(compareBy<Operator>({ it.edgeSupport }, { it.moves.size }))
            narrow.sortWith(compareBy<Operator>({ it.centreSupport }, { it.moves.size }))
            centreSafe = safe
            narrowCentre = narrow
            centreFine = refineCentres(narrow, reference)
            edgeFinishers = buildEdgeFinishers(safe, reference)
        }

        private fun buildAtoms(width: Int, name: String): List<Atom> = buildList {
            for (face in Face.entries) for (turns in 1..3) {
                add(Atom(listOf(Move(face, width, turns)), "$name-${face.symbol}"))
            }
        }

        private fun toOperator(moves: List<Move>, reference: ByteArray): Operator {
            val perm = model.permutation(moves)
            val moved = ByteArray(FACELETS)
            model.applyPerm(reference, perm, moved)
            val centreSupport = CENTRE_TARGET - model.centreScore(moved)
            val edgeSupport = model.edgeStickerMismatch(moved, reference)
            return Operator(moves, perm, centreSupport, edgeSupport)
        }

        private fun refineCentres(base: List<Operator>, reference: ByteArray): List<Operator> {
            if (base.isEmpty()) return emptyList()
            val collected = ArrayList<Operator>()
            var current = base
            repeat(REFINE_ROUNDS) {
                val floor = current.firstOrNull()?.centreSupport ?: return@repeat
                if (floor <= 3) return@repeat
                val refined = narrowerPairs(current, reference, floor - 1)
                if (refined.isEmpty()) return@repeat
                collected.addAll(0, refined)
                current = refined
            }
            collected += base.take(REFINE_WIDTH)
            return dedupe(collected).sortedWith(compareBy<Operator>({ it.centreSupport }, { it.moves.size }))
        }

        private fun narrowerPairs(
            base: List<Operator>,
            reference: ByteArray,
            maxSupport: Int
        ): List<Operator> {
            val width = minOf(REFINE_WIDTH, base.size)
            val out = ArrayList<Operator>()
            outer@ for (i in 0 until width) {
                for (j in 0 until width) {
                    if (i == j) continue
                    val composed = compose(base[i], base[j], reference)
                    if (composed.centreSupport in 1..maxSupport) {
                        out += composed
                        if (out.size >= REFINE_CAP) break@outer
                    }
                }
            }
            return dedupe(out).sortedWith(compareBy<Operator>({ it.centreSupport }, { it.moves.size }))
        }

        private fun buildEdgeFinishers(base: List<Operator>, reference: ByteArray): List<Operator> {
            val narrow = base.take(minOf(REFINE_WIDTH, base.size))
            val out = ArrayList<Operator>()
            val seeds = ArrayList<Operator>()

            for (algorithm in seedEdgeAlgorithms()) {
                val op = toOperator(algorithm, reference)
                if (op.centreSupport != 0) continue
                val moved = ByteArray(FACELETS)
                model.applyPerm(reference, op.perm, moved)
                val unpaired = model.unpairedEdgeCount(moved)
                if (unpaired !in 1..2) continue
                val backward = toOperator(inverseSequence(algorithm), reference)
                out += op
                out += backward
                seeds += op
                seeds += backward
            }

            outer@ for (a in seeds) {
                val partners = seeds + narrow.take(SEED_PARTNERS)
                for (b in partners) {
                    if (a === b) continue
                    val op = compose(a, b, reference)
                    if (op.centreSupport != 0) continue
                    val moved = ByteArray(FACELETS)
                    model.applyPerm(reference, op.perm, moved)
                    if (model.unpairedEdgeCount(moved) in 1..2) {
                        out += op
                        if (out.size >= REFINE_CAP) break@outer
                    }
                }
            }

            outer@ for (a in narrow) {
                for (b in narrow) {
                    if (a === b) continue
                    val op = compose(a, b, reference)
                    if (op.centreSupport != 0) continue
                    val moved = ByteArray(FACELETS)
                    model.applyPerm(reference, op.perm, moved)
                    if (model.unpairedEdgeCount(moved) in 1..2) {
                        out += op
                        out += toOperator(inverseSequence(op.moves), reference)
                        if (out.size >= REFINE_CAP) break@outer
                    }
                }
            }

            return dedupe(out).sortedWith(compareBy<Operator>({ it.moves.size }, { it.edgeSupport }))
        }

        private fun compose(a: Operator, b: Operator, reference: ByteArray): Operator {
            val perm = ShortArray(FACELETS) { dest -> a.perm[b.perm[dest].toInt()] }
            val moved = ByteArray(FACELETS)
            model.applyPerm(reference, perm, moved)
            return Operator(
                moves = simplify(a.moves + b.moves),
                perm = perm,
                centreSupport = CENTRE_TARGET - model.centreScore(moved),
                edgeSupport = model.edgeStickerMismatch(moved, reference)
            )
        }

        private fun dedupe(input: List<Operator>): List<Operator> {
            val seen = HashSet<PermKey>()
            val out = ArrayList<Operator>()
            for (op in input) if (seen.add(PermKey(op.perm))) out += op
            return out
        }

        companion object {
            private const val NARROW_CENTRE_LIMIT = 10
            private const val MAX_PER_LIST = 5000
            private const val REFINE_WIDTH = 480
            private const val REFINE_CAP = 2200
            private const val REFINE_ROUNDS = 2
            private const val SEED_PARTNERS = 320
        }
    }

    private class Model {
        private val template = CubeState(N)
        private val keys: List<StickerKey> = buildList(FACELETS) {
            for (face in Face.entries) for (r in 0 until N) for (c in 0 until N) {
                add(template.keyFromFaceCell(face, r, c))
            }
        }
        private val indexByKey = keys.withIndex().associate { it.value to it.index }
        private val movePermCache = HashMap<Move, ShortArray>()

        private val centreIndices = buildList {
            for (face in Face.entries.indices) for (r in 1..3) for (c in 1..3) {
                add(face * 25 + r * 5 + c)
            }
        }.toIntArray()

        private data class Coord(val x: Int, val y: Int, val z: Int)
        private data class EdgeSlot(val a: IntArray, val b: IntArray)

        private val edgeSlots: List<EdgeSlot> = buildEdgeSlots()
        private val edgeStickerIndices: IntArray = edgeSlots.flatMap { slot ->
            slot.a.toList() + slot.b.toList()
        }.distinct().toIntArray()

        val solvedFlat: ByteArray = ByteArray(FACELETS) { (it / 25).toByte() }

        init {
            check(centreIndices.size == CENTRE_TARGET)
            check(edgeSlots.size == 12) { "5x5 geometry produced ${edgeSlots.size} edge slots, expected 12" }
            check(edgeStickerIndices.size == 72) {
                "5x5 geometry produced ${edgeStickerIndices.size} edge stickers, expected 72"
            }
        }

        fun flatState(cube: CubeState): ByteArray {
            val out = ByteArray(FACELETS)
            var i = 0
            for (face in Face.entries) for (color in cube.faceColors(face)) {
                out[i++] = color.ordinal.toByte()
            }
            return out
        }

        fun centreScore(state: ByteArray): Int {
            var score = 0
            for (index in centreIndices) {
                if (state[index].toInt() == index / 25) score++
            }
            return score
        }

        fun centresSolved(state: ByteArray): Boolean = centreScore(state) == CENTRE_TARGET

        fun edgeQualityScore(state: ByteArray): Int {
            var score = 0
            for (slot in edgeSlots) score += edgeQuality(state, slot)
            return score
        }

        fun edgesPaired(state: ByteArray): Boolean = edgeQualityScore(state) == EDGE_TARGET

        fun unpairedEdgeCount(state: ByteArray): Int {
            var bad = 0
            for (slot in edgeSlots) if (edgeQuality(state, slot) < 2) bad++
            return bad
        }

        private fun edgeQuality(state: ByteArray, slot: EdgeSlot): Int {
            val middle = 1
            val firstA = state[slot.a[middle]]
            val firstB = state[slot.b[middle]]
            var exact = true
            var samePair = true
            for (k in 0..2) {
                val a = state[slot.a[k]]
                val b = state[slot.b[k]]
                if (a != firstA || b != firstB) exact = false
                if (!((a == firstA && b == firstB) || (a == firstB && b == firstA))) samePair = false
            }
            return if (exact) 2 else if (samePair) 1 else 0
        }

        fun edgeStickerMismatch(a: ByteArray, b: ByteArray): Int {
            var count = 0
            for (index in edgeStickerIndices) if (a[index] != b[index]) count++
            return count
        }

        fun applyMoves(start: ByteArray, moves: List<Move>): ByteArray {
            var current = start.copyOf()
            var scratch = ByteArray(FACELETS)
            for (move in moves) {
                applyPerm(current, movePermutation(move), scratch)
                val swap = current
                current = scratch
                scratch = swap
            }
            return current
        }

        fun applyPerm(source: ByteArray, perm: ShortArray, target: ByteArray) {
            for (i in 0 until FACELETS) target[i] = source[perm[i].toInt()]
        }

        fun permutation(moves: List<Move>): ShortArray {
            var combined = ShortArray(FACELETS) { it.toShort() }
            for (move in moves) {
                val step = movePermutation(move)
                val next = ShortArray(FACELETS)
                for (dest in 0 until FACELETS) {
                    next[dest] = combined[step[dest].toInt()]
                }
                combined = next
            }
            return combined
        }

        private fun movePermutation(move: Move): ShortArray = movePermCache.getOrPut(move) {
            val destinationForSource = IntArray(FACELETS)
            for (source in 0 until FACELETS) {
                var key = keys[source]
                if (key.inSlab5(move.face, move.width, N)) {
                    repeat(move.quarterTurns) { key = key.rotateClockwise5(move.face, N) }
                }
                destinationForSource[source] = indexByKey.getValue(key)
            }
            ShortArray(FACELETS).also { sourceForDestination ->
                for (source in 0 until FACELETS) {
                    sourceForDestination[destinationForSource[source]] = source.toShort()
                }
            }
        }

        private fun buildEdgeSlots(): List<EdgeSlot> {
            val cubies = LinkedHashMap<Coord, MutableList<Int>>()
            for (index in keys.indices) {
                val key = keys[index]
                val boundaries = listOf(key.x, key.y, key.z).count { it == 0 || it == N - 1 }
                if (boundaries == 2) {
                    cubies.getOrPut(Coord(key.x, key.y, key.z)) { ArrayList(2) } += index
                }
            }

            data class Piece(val variable: Int, val a: Int, val b: Int)
            val byFaces = LinkedHashMap<Pair<Int, Int>, MutableList<Piece>>()

            for ((coord, indices) in cubies) {
                if (indices.size != 2) continue
                val sorted = indices.sortedBy { it / 25 }
                val faceA = sorted[0] / 25
                val faceB = sorted[1] / 25
                val variable = when {
                    coord.x != 0 && coord.x != N - 1 -> coord.x
                    coord.y != 0 && coord.y != N - 1 -> coord.y
                    else -> coord.z
                }
                byFaces.getOrPut(faceA to faceB) { ArrayList(3) } += Piece(variable, sorted[0], sorted[1])
            }

            return byFaces.values.mapNotNull { pieces ->
                if (pieces.size != 3) return@mapNotNull null
                val sorted = pieces.sortedBy { it.variable }
                EdgeSlot(
                    a = sorted.map { it.a }.toIntArray(),
                    b = sorted.map { it.b }.toIntArray()
                )
            }
        }
    }

    private class PermKey(data: ShortArray) {
        private val values = data.copyOf()
        private val hash = values.contentHashCode()
        override fun hashCode(): Int = hash
        override fun equals(other: Any?): Boolean = other is PermKey && values.contentEquals(other.values)
    }

    private fun commutator(a: List<Move>, b: List<Move>): List<Move> =
        a + b + inverseSequence(a) + inverseSequence(b)

    private fun seedEdgeAlgorithms(): List<List<Move>> = listOf(
        Move.parseAlgorithm("Dw R F' U R' F Dw'"),
        innerSlice(Face.D, 1) + Move.parseAlgorithm("R F' U R' F") + innerSlice(Face.D, 3),
        Move.parseAlgorithm("Uw' R U R' F R' F' R Uw"),
        Move.parseAlgorithm("Uw R U R' F R' F' R Uw'"),
        Move.parseAlgorithm("Uw2 R U R' F R' F' R Uw2"),
        innerSlice(Face.U, 3) + Move.parseAlgorithm("R U R' F R' F' R") + innerSlice(Face.U, 1),
        Move.parseAlgorithm("Lw U F' U' F Lw'"),
        innerSlice(Face.L, 1) + Move.parseAlgorithm("U F' U' F") + innerSlice(Face.L, 3),
        Move.parseAlgorithm("Uw' R U2 R' F R' F' R Uw"),
        Move.parseAlgorithm("Dw R2 F' U R' F Dw'")
    ).map(::simplify)

    private fun innerSlice(face: Face, turns: Int): List<Move> = listOf(
        Move(face, 2, turns),
        Move(face, 1, if (turns == 2) 2 else 4 - turns)
    )

    private fun inverseSequence(sequence: List<Move>): List<Move> =
        sequence.asReversed().map { it.inverse() }

    /** Merge adjacent turns of the same physical layer family and drop cancellations. */
    internal fun simplify(moves: List<Move>): List<Move> {
        if (moves.isEmpty()) return emptyList()
        val out = ArrayList<Move>(moves.size)
        for (move in moves) {
            val previous = out.lastOrNull()
            if (previous != null && previous.face == move.face && previous.width == move.width) {
                val turns = (previous.quarterTurns + move.quarterTurns) % 4
                out.removeAt(out.lastIndex)
                if (turns != 0) out += Move(move.face, move.width, turns)
            } else {
                out += move
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