package com.cubecraft.solver.solver

import com.cubecraft.solver.model.CubeState
import com.cubecraft.solver.model.Face
import com.cubecraft.solver.model.Move
import com.cubecraft.solver.model.StickerKey
import kotlin.random.Random

/**
 * Table-free 5x5 reduction engine.
 *
 * Stages:
 *  1. solve the six 3x3 centre blocks;
 *  2. pair the two wings around every fixed middle edge;
 *  3. return a reduced 5x5 which behaves like a 3x3 under outer turns.
 *
 * There are no downloaded/pruning packs and no scramble-history shortcuts. The process-cached
 * operator pool is generated from CubeState's own geometry. Search strategy and operator-pool
 * structure are adapted from Praval's MIT-licensed rubiks-cube-solver project; attribution is in
 * THIRD_PARTY_NOTICES.md.
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

    // Two wings per edge. An exactly oriented wing is worth four points, hence 12 * 2 * 4.
    private const val EDGE_TARGET = 96
    private const val STAGE_ATTEMPTS = 5
    private const val MAX_STAGE_MOVES = 420
    private const val MAX_STALLS = 95
    private const val MAX_PASSES = 6
    private const val DEEP_WINDOW = 20
    private const val NARROW_SLICE = 320

    private val model by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { Model() }
    private val pool by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { OperatorPool(model) }

    fun solve(state: CubeState, budgetMillis: Long = 25_000L): Result {
        require(state.size == 5) { "5x5 reduction expects CubeState(5)" }
        val p = pool // pool construction is paid once and is not part of per-solve budget
        val start = model.flatState(state)
        val session = SearchSession(
            model = model,
            pool = p,
            deadline = System.currentTimeMillis() + budgetMillis,
            random = Random(start.contentHashCode() xor 0x5A17_2026)
        )
        return session.reduce(start)
    }

    /** Long-known reduction parity algorithms, expressed only through legal cubikSolver moves. */
    fun edgeFlipParity(): List<Move> = simplify(buildList {
        addAll(innerLayer(Face.R, 2, 2)); add(Move(Face.B, 1, 2)); add(Move(Face.U, 1, 2))
        addAll(innerLayer(Face.L, 2, 1)); add(Move(Face.U, 1, 2))
        addAll(innerLayer(Face.R, 2, 3)); add(Move(Face.U, 1, 2))
        addAll(innerLayer(Face.R, 2, 1)); add(Move(Face.U, 1, 2)); add(Move(Face.F, 1, 2))
        addAll(innerLayer(Face.R, 2, 1)); add(Move(Face.F, 1, 2))
        addAll(innerLayer(Face.L, 2, 3)); add(Move(Face.B, 1, 2)); addAll(innerLayer(Face.R, 2, 2))
    })

    fun edgeSwapParity(): List<Move> = simplify(buildList {
        addAll(innerLayer(Face.R, 2, 2)); add(Move(Face.U, 1, 2))
        addAll(innerLayer(Face.R, 2, 2)); add(Move(Face.U, 2, 2))
        addAll(innerLayer(Face.R, 2, 2)); add(Move(Face.U, 2, 2))
    })

    private class SearchSession(
        private val model: Model,
        private val pool: OperatorPool,
        private val deadline: Long,
        private val random: Random
    ) {
        private val wrappersNone = listOf(Wrapper(emptyList(), null, null))
        private val wrappersOne = wrappersNone + pool.wrapperAtoms.map { wrapper(it.moves) }
        private val wrappersTwo: List<Wrapper> by lazy {
            buildList {
                for (a in pool.wrapperAtoms) for (b in pool.wrapperAtoms) {
                    if (a.key == b.key) continue
                    add(wrapper(a.moves + b.moves))
                }
            }
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

                if (model.edgesPaired(current)) return success(current, moves)

                val edges = runStage(
                    label = "edge pairing",
                    start = current,
                    target = EDGE_TARGET,
                    score = model::edgePairingScore,
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
            edgeScore = model.edgePairingScore(state),
            diagnostic = "centres solved and all 12 edges paired"
        )

        private fun failure(state: ByteArray, moves: List<Move>, why: String) = Result(
            moves = emptyList(),
            centresSolved = model.centresSolved(state),
            edgesPaired = model.edgesPaired(state),
            centreScore = model.centreScore(state),
            edgeScore = model.edgePairingScore(state),
            diagnostic = "$why; centres=${model.centreScore(state)}/$CENTRE_TARGET, edges=${model.edgePairingScore(state)}/$EDGE_TARGET, " +
                "pool=${pool.centreSafe.size}/${pool.edgeFinishers.size}, exploredMoves=${moves.size}"
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
                if (before >= target && legal(state)) return StageResult(state, simplify(out))

                var step = findBasic(state, pool.basicOperators) { legal(it) && score(it) > before }
                    ?: findOperator(state, operators, wrappersOne) { legal(it) && score(it) > before }

                val nearlyDone = before >= target - DEEP_WINDOW
                if (step == null && nearlyDone) {
                    step = findOperator(state, finishers, wrappersOne) { legal(it) && score(it) > before }
                    if (step == null && deep && !outOfTime()) {
                        step = findOperator(state, narrowSlice(operators), wrappersTwo) { legal(it) && score(it) > before }
                    }
                    if (step == null && !outOfTime()) {
                        step = findOperator(state, narrowSlice(finishers), wrappersTwo) { legal(it) && score(it) > before }
                    }
                }

                if (step == null) {
                    stalls++
                    val allowance = when {
                        stalls < 14 -> 0
                        stalls < 48 -> 2
                        else -> 4
                    }
                    step = bestSideways(state, operators, wrappersOne, before, allowance, legal, score)
                        ?: bestSideways(state, operators, wrappersNone, before, allowance, legal, score)
                        ?: run {
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

        private fun findBasic(state: ByteArray, operators: List<Operator>, accept: (ByteArray) -> Boolean): List<Move>? {
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
            if (wrap.moves.isEmpty()) op.moves else wrap.moves + op.moves + inverseSequence(wrap.moves)

        private fun wrapper(moves: List<Move>) = Wrapper(
            moves,
            model.permutation(moves),
            model.permutation(inverseSequence(moves))
        )

        private fun narrowSlice(operators: List<Operator>): List<Operator> =
            if (operators.size <= NARROW_SLICE) operators else operators.subList(0, NARROW_SLICE)

        private fun perturbation(): List<Move> {
            val a = pool.wrapperAtoms[random.nextInt(pool.wrapperAtoms.size)]
            val outer = pool.outerAtoms[random.nextInt(pool.outerAtoms.size)]
            var b = pool.wrapperAtoms[random.nextInt(pool.wrapperAtoms.size)]
            if (a.key == b.key) b = pool.wrapperAtoms[(pool.wrapperAtoms.indexOf(b) + 1) % pool.wrapperAtoms.size]
            return simplify(a.moves + outer.moves + b.moves)
        }

        private fun outOfTime() = System.currentTimeMillis() >= deadline
    }

    private data class Wrapper(val moves: List<Move>, val setup: ShortArray?, val undo: ShortArray?)
    private data class Atom(val moves: List<Move>, val key: String)
    private class Operator(
        val moves: List<Move>,
        val perm: ShortArray,
        val centreSupport: Int,
        val edgeSupport: Int
    )

    private class OperatorPool(private val model: Model) {
        val outerAtoms = buildWideAtoms(1, 1, "outer")
        private val wideAtoms = buildWideAtoms(2, 3, "wide")
        private val innerAtoms = buildInnerAtoms()
        val wrapperAtoms = outerAtoms + wideAtoms + innerAtoms

        val basicOperators: List<Operator>
        val centreSafe: List<Operator>
        val narrowCentre: List<Operator>
        val centreFine: List<Operator>
        val edgeFinishers: List<Operator>

        init {
            val reference = model.solvedFlat
            basicOperators = wrapperAtoms.map { toOperator(it.moves, reference) }

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
                if (!seen.add(PermKey(op.perm))) return
                if (op.centreSupport == 0 && op.edgeSupport > 0 && safe.size < MAX_PER_LIST) safe += op
                if (op.centreSupport in 1..NARROW_CENTRE_LIMIT && narrow.size < MAX_PER_LIST) narrow += op
            }

            // Four core families from big-cube reduction: slice/face commutators in both orders and
            // slice/slice commutators. The extra three-slot family provides useful four-piece tools
            // that the pure commutators cannot reach.
            for (a in carrier) for (b in outerAtoms) {
                consider(commutator(a.moves, b.moves))
                consider(commutator(b.moves, a.moves))
            }
            for (a in carrier) for (b in carrier) {
                if (a.key != b.key) consider(commutator(a.moves, b.moves))
            }
            for (a in carrier) for (b in outerAtoms) for (c in carrier) {
                if (a.key == c.key) continue
                consider(a.moves + b.moves + c.moves + inverseSequence(a.moves))
                if (safe.size >= MAX_PER_LIST && narrow.size >= MAX_PER_LIST) break
            }

            for (algorithm in listOf(edgeFlipParity(), edgeSwapParity())) {
                consider(algorithm)
                consider(inverseSequence(algorithm))
            }

            safe.sortWith(compareBy<Operator>({ it.edgeSupport }, { it.moves.size }))
            narrow.sortWith(compareBy<Operator>({ it.centreSupport }, { it.moves.size }))

            // Pairs of narrow centre-safe operators are crucial for the last-two-edges plateau.
            val safePairs = refineSafePairs(safe, reference)
            centreSafe = dedupe(safe + safePairs)
                .sortedWith(compareBy<Operator>({ it.edgeSupport }, { it.moves.size }))
            narrowCentre = narrow
            centreFine = refineCentres(narrow, reference)
            edgeFinishers = buildEdgeFinishers(centreSafe, reference)
        }

        private fun buildWideAtoms(from: Int, to: Int, label: String): List<Atom> = buildList {
            for (width in from..to) for (face in Face.entries) for (turns in 1..3) {
                add(Atom(listOf(Move(face, width, turns)), "$label-$width-${face.symbol}"))
            }
        }

        private fun buildInnerAtoms(): List<Atom> = buildList {
            for (face in Face.entries) for (turns in 1..3) {
                add(Atom(innerLayer(face, 2, turns), "inner-2-${face.symbol}"))
            }
            // The middle layer is the same physical slice when named from the opposite face, so keep
            // only U/R/F spellings to avoid searching every sequence twice.
            for (face in listOf(Face.U, Face.R, Face.F)) for (turns in 1..3) {
                add(Atom(innerLayer(face, 3, turns), "inner-3-${face.symbol}"))
            }
        }

        private fun toOperator(moves: List<Move>, reference: ByteArray): Operator {
            val perm = model.permutation(moves)
            val moved = ByteArray(FACELETS)
            model.applyPerm(reference, perm, moved)
            return Operator(
                moves = moves,
                perm = perm,
                centreSupport = CENTRE_TARGET - model.centreScore(moved),
                edgeSupport = model.edgeStickerMismatch(moved, reference)
            )
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

        private fun narrowerPairs(base: List<Operator>, reference: ByteArray, maxSupport: Int): List<Operator> {
            val width = minOf(REFINE_WIDTH, base.size)
            val out = ArrayList<Operator>()
            outer@ for (i in 0 until width) for (j in 0 until width) {
                if (i == j) continue
                val op = compose(base[i], base[j], reference)
                if (op.centreSupport in 1..maxSupport) {
                    out += op
                    if (out.size >= REFINE_CAP) break@outer
                }
            }
            return dedupe(out).sortedWith(compareBy<Operator>({ it.centreSupport }, { it.moves.size }))
        }

        private fun refineSafePairs(base: List<Operator>, reference: ByteArray): List<Operator> {
            val width = minOf(SAFE_PAIR_WIDTH, base.size)
            val out = ArrayList<Operator>()
            outer@ for (i in 0 until width) for (j in 0 until width) {
                if (i == j) continue
                val op = compose(base[i], base[j], reference)
                if (op.centreSupport == 0 && op.edgeSupport > 0) {
                    out += op
                    if (out.size >= SAFE_PAIR_CAP) break@outer
                }
            }
            return dedupe(out)
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
                if (model.unpairedEdgeCount(moved) !in 1..2) continue
                val inverse = toOperator(inverseSequence(algorithm), reference)
                out += op; out += inverse; seeds += op; seeds += inverse
            }

            outer@ for (a in seeds) {
                for (b in seeds + narrow.take(SEED_PARTNERS)) {
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

            outer@ for (a in narrow) for (b in narrow) {
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
            private const val NARROW_CENTRE_LIMIT = 11
            private const val MAX_PER_LIST = 7000
            private const val REFINE_WIDTH = 520
            private const val REFINE_CAP = 2600
            private const val REFINE_ROUNDS = 2
            private const val SEED_PARTNERS = 420
            private const val SAFE_PAIR_WIDTH = 220
            private const val SAFE_PAIR_CAP = 3200
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
            for (face in Face.entries.indices) for (r in 1..3) for (c in 1..3) add(face * 25 + r * 5 + c)
        }.toIntArray()

        private data class Coord(val x: Int, val y: Int, val z: Int)
        private data class EdgeSlot(val a: IntArray, val b: IntArray)
        private val edgeSlots = buildEdgeSlots()
        private val edgeStickerIndices = edgeSlots.flatMap { it.a.toList() + it.b.toList() }.distinct().toIntArray()
        val solvedFlat = ByteArray(FACELETS) { (it / 25).toByte() }

        init {
            check(centreIndices.size == CENTRE_TARGET)
            check(edgeSlots.size == 12) { "5x5 geometry produced ${edgeSlots.size} edge slots, expected 12" }
            check(edgeStickerIndices.size == 72) { "5x5 geometry produced ${edgeStickerIndices.size} edge stickers, expected 72" }
        }

        fun flatState(cube: CubeState): ByteArray {
            val out = ByteArray(FACELETS)
            var i = 0
            for (face in Face.entries) for (color in cube.faceColors(face)) out[i++] = color.ordinal.toByte()
            return out
        }

        fun centreScore(state: ByteArray): Int {
            var score = 0
            for (index in centreIndices) if (state[index].toInt() == index / 25) score++
            return score
        }

        fun centresSolved(state: ByteArray) = centreScore(state) == CENTRE_TARGET

        /**
         * Dense edge score. A full paired-edge-only score has huge flat plateaus: on a scramble it
         * can be 0/24 even when a useful operator has just placed one wing correctly. Here each wing
         * is scored against its fixed middle edge, so the search can see partial progress.
         */
        fun edgePairingScore(state: ByteArray): Int {
            var total = 0
            for (slot in edgeSlots) {
                val targetA = state[slot.a[1]]
                val targetB = state[slot.b[1]]
                for (k in intArrayOf(0, 2)) {
                    val a = state[slot.a[k]]
                    val b = state[slot.b[k]]
                    total += when {
                        a == targetA && b == targetB -> 4
                        a == targetB && b == targetA -> 2
                        else -> (if (a == targetA) 1 else 0) + (if (b == targetB) 1 else 0)
                    }
                }
            }
            return total
        }

        fun edgesPaired(state: ByteArray) = edgePairingScore(state) == EDGE_TARGET

        fun unpairedEdgeCount(state: ByteArray): Int {
            var bad = 0
            for (slot in edgeSlots) {
                val a = state[slot.a[1]]
                val b = state[slot.b[1]]
                if (slot.a.indices.any { k -> state[slot.a[k]] != a || state[slot.b[k]] != b }) bad++
            }
            return bad
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
                val swap = current; current = scratch; scratch = swap
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
                for (dest in 0 until FACELETS) next[dest] = combined[step[dest].toInt()]
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
                for (source in 0 until FACELETS) sourceForDestination[destinationForSource[source]] = source.toShort()
            }
        }

        private fun buildEdgeSlots(): List<EdgeSlot> {
            val cubies = LinkedHashMap<Coord, MutableList<Int>>()
            for (index in keys.indices) {
                val key = keys[index]
                val boundaries = listOf(key.x, key.y, key.z).count { it == 0 || it == N - 1 }
                if (boundaries == 2) cubies.getOrPut(Coord(key.x, key.y, key.z)) { ArrayList(2) } += index
            }

            data class Piece(val variable: Int, val a: Int, val b: Int)
            val byFaces = LinkedHashMap<Pair<Int, Int>, MutableList<Piece>>()
            for ((coord, indices) in cubies) {
                if (indices.size != 2) continue
                val sorted = indices.sortedBy { it / 25 }
                val variable = when {
                    coord.x != 0 && coord.x != N - 1 -> coord.x
                    coord.y != 0 && coord.y != N - 1 -> coord.y
                    else -> coord.z
                }
                byFaces.getOrPut((sorted[0] / 25) to (sorted[1] / 25)) { ArrayList(3) }
                    .add(Piece(variable, sorted[0], sorted[1]))
            }
            return byFaces.values.mapNotNull { pieces ->
                if (pieces.size != 3) null else {
                    val sorted = pieces.sortedBy { it.variable }
                    EdgeSlot(sorted.map { it.a }.toIntArray(), sorted.map { it.b }.toIntArray())
                }
            }
        }
    }

    private class PermKey(data: ShortArray) {
        private val values = data.copyOf()
        private val hash = values.contentHashCode()
        override fun hashCode() = hash
        override fun equals(other: Any?) = other is PermKey && values.contentEquals(other.values)
    }

    private fun commutator(a: List<Move>, b: List<Move>) = a + b + inverseSequence(a) + inverseSequence(b)

    /** Pure layer turn at 1-based depth [layer], represented through nested wide turns. */
    private fun innerLayer(face: Face, layer: Int, turns: Int): List<Move> {
        require(layer in 2..3)
        val inverse = if (turns == 2) 2 else 4 - turns
        return listOf(Move(face, layer, turns), Move(face, layer - 1, inverse))
    }

    private fun seedEdgeAlgorithms(): List<List<Move>> = listOf(
        Move.parseAlgorithm("Dw R F' U R' F Dw'"),
        innerLayer(Face.D, 2, 1) + Move.parseAlgorithm("R F' U R' F") + innerLayer(Face.D, 2, 3),
        Move.parseAlgorithm("Uw' R U R' F R' F' R Uw"),
        Move.parseAlgorithm("Uw R U R' F R' F' R Uw'"),
        Move.parseAlgorithm("Uw2 R U R' F R' F' R Uw2"),
        innerLayer(Face.U, 2, 3) + Move.parseAlgorithm("R U R' F R' F' R") + innerLayer(Face.U, 2, 1),
        Move.parseAlgorithm("Lw U F' U' F Lw'"),
        innerLayer(Face.L, 2, 1) + Move.parseAlgorithm("U F' U' F") + innerLayer(Face.L, 2, 3),
        Move.parseAlgorithm("3Uw' R U R' F R' F' R 3Uw"),
        Move.parseAlgorithm("3Uw R U R' F R' F' R 3Uw'"),
        Move.parseAlgorithm("Uw' R U2 R' F R' F' R Uw"),
        Move.parseAlgorithm("Dw R2 F' U R' F Dw'")
    ).map(::simplify)

    private fun inverseSequence(sequence: List<Move>) = sequence.asReversed().map { it.inverse() }

    internal fun simplify(moves: List<Move>): List<Move> {
        val out = ArrayList<Move>(moves.size)
        for (move in moves) {
            val previous = out.lastOrNull()
            if (previous != null && previous.face == move.face && previous.width == move.width) {
                val turns = (previous.quarterTurns + move.quarterTurns) % 4
                out.removeAt(out.lastIndex)
                if (turns != 0) out += Move(move.face, move.width, turns)
            } else out += move
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