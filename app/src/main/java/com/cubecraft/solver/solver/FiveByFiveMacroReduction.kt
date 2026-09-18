package com.cubecraft.solver.solver

import com.cubecraft.solver.model.CubeState
import com.cubecraft.solver.model.Face
import com.cubecraft.solver.model.Move
import com.cubecraft.solver.model.StickerKey
import kotlin.random.Random

/**
 * Table-free 5x5 reduction engine.
 *
 * The search is intentionally staged: solve the 3x3 centre blocks, pair all twelve three-piece
 * edges, then let FiveByFiveSolver project the reduced position to 3x3. Candidate operators are
 * generated once from legal cube geometry; no external pruning-table pack and no scramble history
 * are used.
 *
 * The shaped-search/operator-pool strategy is adapted from Praval's MIT-licensed
 * Vortezler/rubiks-cube-solver. See THIRD_PARTY_NOTICES.md.
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
    private const val EDGE_SEARCH_TARGET = 396

    private const val STAGE_ATTEMPTS = 3
    private const val MAX_STAGE_MOVES = 520
    private const val MAX_STALLS = 96
    private const val MAX_PASSES = 5
    private const val DEEP_WINDOW = 8
    private const val NARROW_SLICE = 260

    private val model by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { Model() }
    private val pool by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { OperatorPool(model) }

    fun prewarm() {
        pool.prewarmNative()
    }

    fun solve(state: CubeState, budgetMillis: Long = 25_000L): Result {
        require(state.size == N) { "5x5 reduction expects CubeState(5)" }
        val prepared = pool
        val start = model.flatState(state)
        return Session(
            model = model,
            pool = prepared,
            deadline = System.currentTimeMillis() + budgetMillis,
            random = Random(start.contentHashCode() xor 0x5A17_2026)
        ).reduce(start)
    }

    /** Standard odd-big-cube edge parity repairs, represented with legal layer turns. */
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

    private class Session(
        private val model: Model,
        private val pool: OperatorPool,
        private val deadline: Long,
        private val random: Random
    ) {
        private data class Wrapper(
            val moves: List<Move>,
            val setup: ShortArray?,
            val undo: ShortArray?
        )

        private data class StageResult(
            val state: ByteArray,
            val moves: List<Move>,
            val solved: Boolean
        )

        /**
         * Best verified progress reached by one local-search attempt.
         *
         * Older builds threw this state away unless the whole stage reached its target. On a real
         * scan that meant repeatedly climbing from e.g. 29/54 centres to 52/54, discarding the
         * useful 52/54 position, and starting the next basin from scratch.
         */
        private data class StageAttempt(
            val state: ByteArray,
            val moves: List<Move>,
            val score: Int,
            val solved: Boolean
        )

        private val wrappersNone = listOf(Wrapper(emptyList(), null, null))
        private val wrappersOne: List<Wrapper> = wrappersNone + pool.wrapperAtoms.map { atom ->
            wrapper(atom.moves)
        }
        private val wrappersTwo: List<Wrapper> by lazy {
            buildList {
                for (first in pool.wrapperAtoms) {
                    for (second in pool.wrapperAtoms) {
                        if (first.layerKey == second.layerKey) continue
                        add(wrapper(first.moves + second.moves))
                    }
                }
            }
        }

        private val nativeWrappersNone by lazy { nativeWrappers(wrappersNone) }
        private val nativeWrappersOne by lazy { nativeWrappers(wrappersOne) }
        private val nativeWrappersTwo by lazy { nativeWrappers(wrappersTwo) }

        private fun nativeWrappers(
            wrappers: List<Wrapper>
        ): NativeFiveByFiveKernel.WrapperPool? =
            NativeFiveByFiveKernel.createWrapperPool(
                wrappers.map { it.setup },
                wrappers.map { it.undo }
            )

        private fun nativeWrappersFor(
            wrappers: List<Wrapper>
        ): NativeFiveByFiveKernel.WrapperPool? =
            when {
                wrappers === wrappersNone -> nativeWrappersNone
                wrappers === wrappersOne -> nativeWrappersOne
                wrappers === wrappersTwo -> nativeWrappersTwo
                else -> null
            }

        private val scratchA = ByteArray(FACELETS)
        private val scratchB = ByteArray(FACELETS)
        private val scratchC = ByteArray(FACELETS)
        private val shortSearch = DirectSearch(pool.shortAtoms, model, 3)

        private var lastAttemptBest = 0
        private var lastStageReport = ""

        fun reduce(start: ByteArray): Result {
            var current = start.copyOf()
            val allMoves = ArrayList<Move>()

            for (pass in 0..MAX_PASSES) {
                if (outOfTime()) break

                if (!model.centresSolved(current)) {
                    val centreResult = runStage(
                        label = "centres",
                        start = current,
                        target = CENTRE_TARGET,
                        score = model::centreScore,
                        legal = { true },
                        operators = pool.narrowCentre,
                        finishers = pool.centreFine,
                        deep = false
                    )
                    current = centreResult.state
                    allMoves += centreResult.moves
                    if (!centreResult.solved) {
                        return failure(current, allMoves, lastStageReport)
                    }
                }

                if (model.edgesPaired(current)) return success(current, allMoves)

                val edgeResult = runStage(
                    label = "edge pairing",
                    start = current,
                    target = EDGE_SEARCH_TARGET,
                    score = model::edgeSearchScore,
                    legal = model::centresSolved,
                    operators = pool.centreSafe,
                    finishers = pool.edgeFinishers,
                    deep = true
                )

                current = edgeResult.state
                allMoves += edgeResult.moves
                if (edgeResult.solved && model.centresSolved(current) && model.edgesPaired(current)) {
                    return success(current, allMoves)
                }

                if (pass >= MAX_PASSES || outOfTime()) break
                val breaker = perturbation()
                current = model.applyMoves(current, breaker)
                allMoves += breaker
            }

            return failure(
                current,
                allMoves,
                if (outOfTime()) "reduction time budget reached" else lastStageReport.ifBlank { "reduction stalled" }
            )
        }

        private fun runStage(
            label: String,
            start: ByteArray,
            target: Int,
            score: (ByteArray) -> Int,
            legal: (ByteArray) -> Boolean,
            operators: List<Operator>,
            finishers: List<Operator>,
            deep: Boolean
        ): StageResult {
            var current = start.copyOf()
            val accumulated = ArrayList<Move>()
            var best = score(current)
            var attemptsRun = 0

            for (attempt in 0 until STAGE_ATTEMPTS) {
                if (outOfTime()) break
                attemptsRun++

                val progress = attemptStage(
                    current, target, score, legal, operators, finishers, deep
                )

                // Crucial: carry the best *state* into the next attempt instead of keeping only its
                // numeric score. Equal-score plateau states are useful too because they expose a
                // different arrangement to the randomized sideways search.
                if (progress.score >= best && (progress.moves.isNotEmpty() || progress.solved)) {
                    current = progress.state
                    accumulated += progress.moves
                    best = progress.score
                }

                if (progress.solved) {
                    return StageResult(current, simplify(accumulated), solved = true)
                }
            }

            lastStageReport = "$label reached $best of $target after $attemptsRun attempts"
            return StageResult(current, simplify(accumulated), solved = false)
        }

        private fun attemptStage(
            start: ByteArray,
            target: Int,
            score: (ByteArray) -> Int,
            legal: (ByteArray) -> Boolean,
            operators: List<Operator>,
            finishers: List<Operator>,
            deep: Boolean
        ): StageAttempt {
            var state = start.copyOf()
            val out = ArrayList<Move>()
            var best = score(state)
            var bestState = state.copyOf()
            var bestMoves: List<Move> = emptyList()
            lastAttemptBest = best
            var stalls = 0

            fun snapshotBest(after: Int) {
                if (!legal(state) || after < best) return
                if (after > best) {
                    best = after
                    lastAttemptBest = best
                    stalls = 0
                }
                bestState = state.copyOf()
                bestMoves = simplify(out.toList())
            }

            fun partial(): StageAttempt =
                StageAttempt(bestState, bestMoves, best, solved = false)

            while (!outOfTime()) {
                val before = score(state)
                if (before >= target && legal(state)) {
                    return StageAttempt(state, simplify(out), before, solved = true)
                }

                var step = shortSearch.find(state) { candidate ->
                    legal(candidate) && score(candidate) > before
                } ?: findImprovingOperator(
                    state = state,
                    operators = operators,
                    wrappers = wrappersOne,
                    before = before,
                    deep = deep,
                    fallback = { candidate -> legal(candidate) && score(candidate) > before }
                )

                val rescueWindow = if (deep) 240 else DEEP_WINDOW
                if (step == null && before >= target - rescueWindow && finishers.isNotEmpty()) {
                    step = findImprovingOperator(
                        state = state,
                        operators = finishers,
                        wrappers = wrappersOne,
                        before = before,
                        deep = deep,
                        fallback = { candidate -> legal(candidate) && score(candidate) > before }
                    ) ?: findOperator(
                        state,
                        narrowSlice(finishers),
                        wrappersTwo
                    ) { candidate ->
                        legal(candidate) && score(candidate) > before
                    }
                }

                if (step == null && deep && before >= target - rescueWindow) {
                    step = findOperator(
                        state,
                        narrowSlice(operators),
                        wrappersTwo
                    ) { candidate ->
                        legal(candidate) && score(candidate) > before
                    }
                }

                if (step == null && before >= target - rescueWindow) {
                    step = beamRescue(
                        state,
                        target,
                        score,
                        legal,
                        operators,
                        finishers,
                        deep
                    )
                }

                if (step == null) {
                    stalls++
                    val allowance = when {
                        stalls < 12 -> 0
                        stalls < 40 -> 1
                        else -> 2
                    }
                    step = bestSidewaysFast(
                        state,
                        operators,
                        wrappersOne,
                        before,
                        allowance,
                        deep,
                        legal,
                        score
                    ) ?: bestSidewaysFast(
                        state,
                        finishers,
                        wrappersOne,
                        before,
                        allowance,
                        deep,
                        legal,
                        score
                    ) ?: bestSidewaysFast(
                        state,
                        operators,
                        wrappersNone,
                        before,
                        allowance,
                        deep,
                        legal,
                        score
                    ) ?: run {
                        lastAttemptBest = best
                        return partial()
                    }
                }

                state = model.applyMoves(state, step)
                out += step
                snapshotBest(score(state))

                if (stalls > MAX_STALLS || out.size > MAX_STAGE_MOVES) {
                    lastAttemptBest = best
                    return partial()
                }
            }

            lastAttemptBest = best
            return partial()
        }

        private fun findImprovingOperator(
            state: ByteArray,
            operators: List<Operator>,
            wrappers: List<Wrapper>,
            before: Int,
            deep: Boolean,
            fallback: (ByteArray) -> Boolean
        ): List<Move>? {
            val native = pool.nativeFor(operators)
            val nativeWrappers = nativeWrappersFor(wrappers)
            if (native != null && nativeWrappers != null) {
                val mode = if (deep) {
                    NativeFiveByFiveKernel.MODE_EDGES
                } else {
                    NativeFiveByFiveKernel.MODE_CENTERS
                }
                val result = native.findFirstWrapped(
                    nativeWrappers,
                    state,
                    mode,
                    before,
                    deep
                ) ?: return null
                return buildSequence(
                    wrappers[result.wrapper],
                    operators[result.index]
                )
            }
            return findOperator(state, operators, wrappers, fallback)
        }

        private fun bestSidewaysFast(
            state: ByteArray,
            operators: List<Operator>,
            wrappers: List<Wrapper>,
            before: Int,
            allowance: Int,
            deep: Boolean,
            legal: (ByteArray) -> Boolean,
            score: (ByteArray) -> Int
        ): List<Move>? {
            val native = pool.nativeFor(operators)
            val nativeWrappers = nativeWrappersFor(wrappers)
            if (native != null && nativeWrappers != null) {
                val mode = if (deep) {
                    NativeFiveByFiveKernel.MODE_EDGES
                } else {
                    NativeFiveByFiveKernel.MODE_CENTERS
                }
                val result = native.findBestWrapped(
                    nativeWrappers,
                    state,
                    mode,
                    before - allowance,
                    deep
                ) ?: return null
                return buildSequence(
                    wrappers[result.wrapper],
                    operators[result.index]
                )
            }
            return bestSideways(
                state,
                operators,
                wrappers,
                before,
                allowance,
                legal,
                score
            )
        }

        private data class BeamNode(
            val state: ByteArray,
            val moves: List<Move>,
            val value: Int
        )

        /** Bounded look-ahead for the last centres / last paired edges. */
        private fun beamRescue(
            start: ByteArray,
            target: Int,
            score: (ByteArray) -> Int,
            legal: (ByteArray) -> Boolean,
            operators: List<Operator>,
            finishers: List<Operator>,
            deep: Boolean
        ): List<Move>? {
            val base = score(start)

            if (NativeFiveByFiveKernel.available) {
                val mode = if (deep) {
                    NativeFiveByFiveKernel.MODE_EDGES
                } else {
                    NativeFiveByFiveKernel.MODE_CENTERS
                }
                val nativeBudget = (deadline - System.currentTimeMillis())
                    .coerceIn(0L, if (deep) 1_800L else 1_500L)
                    .toInt()

                if (nativeBudget > 100) {
                    val preferred = if (deep) {
                        pool.edgeRescue
                    } else {
                        pool.narrowFor(if (finishers.isNotEmpty()) finishers else operators)
                    }
                    val native = pool.nativeFor(preferred)
                    val floor = base - if (deep) 4 else 5

                    val broadBudget = (nativeBudget * 0.58f).toInt().coerceAtLeast(120)
                    val broad = native?.bestFirstSearch(
                        state = start,
                        mode = mode,
                        target = target,
                        floor = floor,
                        requireCenters = deep,
                        maxNodes = if (deep) 42_000 else 34_000,
                        budgetMillis = broadBudget
                    )
                    if (broad != null && broad.isNotEmpty()) {
                        return simplify(broad.flatMap { preferred[it].moves })
                    }

                    val beamBudget = (nativeBudget - broadBudget).coerceAtLeast(100)
                    val indices = native?.beamSearch(
                        state = start,
                        mode = mode,
                        target = target,
                        floor = floor,
                        requireCenters = deep,
                        maxDepth = if (deep) 5 else 6,
                        beamWidth = if (deep) 224 else 288,
                        budgetMillis = beamBudget
                    )
                    if (indices != null && indices.isNotEmpty()) {
                        return simplify(indices.flatMap { preferred[it].moves })
                    }
                }
            }

            val alphabet = buildList {
                addAll(finishers.take(if (deep) 260 else 420))
                addAll(operators.take(if (deep) 220 else 300))
            }.distinctBy { PermKey(it.perm) }
            if (alphabet.isEmpty()) return null

            var frontier = listOf(BeamNode(start.copyOf(), emptyList(), base))
            var best: BeamNode? = null
            val seen = HashSet<Int>()
            seen += start.contentHashCode()

            repeat(if (deep) 3 else 4) {
                if (outOfTime()) return best?.moves
                val next = ArrayList<BeamNode>()
                for (node in frontier) for (op in alphabet) {
                    if (outOfTime()) return best?.moves
                    val candidate = ByteArray(FACELETS)
                    model.applyPerm(node.state, op.perm, candidate)
                    if (!legal(candidate)) continue
                    val value = score(candidate)
                    if (value < base - if (deep) 2 else 3) continue
                    if (!seen.add(candidate.contentHashCode())) continue
                    val moves = simplify(node.moves + op.moves)
                    if (value >= target) return moves
                    val item = BeamNode(candidate, moves, value)
                    if (value > base && (best == null || value > best!!.value ||
                            (value == best!!.value && moves.size < best!!.moves.size))) {
                        best = item
                    }
                    next += item
                }
                if (next.isEmpty()) return best?.moves
                frontier = next.sortedWith(
                    compareByDescending<BeamNode> { it.value }.thenBy { it.moves.size }
                ).take(if (deep) 72 else 96)
            }
            return best?.moves
        }

        private fun findOperator(
            state: ByteArray,
            operators: List<Operator>,
            wrappers: List<Wrapper>,
            accept: (ByteArray) -> Boolean
        ): List<Move>? {
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
            val floor = before - allowance
            var bestScore = Int.MIN_VALUE
            var selected: List<Move>? = null
            var ties = 0

            for (wrap in wrappers) {
                if (outOfTime()) return selected
                val staged = stage(state, wrap.setup)
                for (op in operators) {
                    val result = finish(staged, op.perm, wrap.undo)
                    if (!legal(result)) continue
                    val value = score(result)
                    if (value < floor) continue
                    if (value > bestScore) {
                        bestScore = value
                        selected = buildSequence(wrap, op)
                        ties = 1
                    } else if (value == bestScore) {
                        ties++
                        if (random.nextInt(ties) == 0) selected = buildSequence(wrap, op)
                    }
                }
            }
            return selected
        }

        private fun wrapper(moves: List<Move>) = Wrapper(
            moves = moves,
            setup = model.permutation(moves),
            undo = model.permutation(inverseSequence(moves))
        )

        private fun buildSequence(wrap: Wrapper, op: Operator): List<Move> =
            if (wrap.moves.isEmpty()) op.moves else wrap.moves + op.moves + inverseSequence(wrap.moves)

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

        private fun narrowSlice(operators: List<Operator>): List<Operator> =
            pool.narrowFor(operators)

        private fun perturbation(): List<Move> = listOf(
            pool.carrierAtoms[random.nextInt(pool.carrierAtoms.size)].moves,
            pool.outerAtoms[random.nextInt(pool.outerAtoms.size)].moves,
            pool.carrierAtoms[random.nextInt(pool.carrierAtoms.size)].moves
        ).flatten()

        private fun success(state: ByteArray, moves: List<Move>) = Result(
            moves = simplify(moves),
            centresSolved = model.centresSolved(state),
            edgesPaired = model.edgesPaired(state),
            centreScore = model.centreScore(state),
            edgeScore = model.edgeQualityScore(state),
            diagnostic = "centres solved and all 12 edges paired"
        )

        private fun failure(state: ByteArray, moves: List<Move>, reason: String) = Result(
            // Partial moves are still a fully legal, replayable sequence. Exposing them lets the
            // top-level solver hand a near-complete centre state to a deterministic tail search
            // instead of throwing away verified progress such as 52/54.
            moves = simplify(moves),
            centresSolved = model.centresSolved(state),
            edgesPaired = model.edgesPaired(state),
            centreScore = model.centreScore(state),
            edgeScore = model.edgeQualityScore(state),
            diagnostic = "$reason; centres=${model.centreScore(state)}/$CENTRE_TARGET, " +
                "edges=${model.edgeQualityScore(state)}/$EDGE_TARGET, " +
                "pool=${pool.centreSafe.size}/${pool.narrowCentre.size}/${pool.edgeFinishers.size}, " +
                "exploredMoves=${moves.size}"
        )

        private fun outOfTime(): Boolean = System.currentTimeMillis() > deadline
    }

    /** Cheap depth-1/2/3 search over outer turns and isolated inner slices. */
    private class DirectSearch(
        private val alphabet: List<Atom>,
        private val model: Model,
        private val maxDepth: Int
    ) {
        private val levels = Array(maxDepth + 1) { ByteArray(FACELETS) }
        private val chosen = arrayOfNulls<Atom>(maxDepth)

        fun find(start: ByteArray, accept: (ByteArray) -> Boolean): List<Move>? {
            start.copyInto(levels[0])
            for (depth in 1..maxDepth) {
                val result = dfs(0, depth, -1, accept)
                if (result) return (0 until depth).flatMap { chosen[it]!!.moves }
            }
            return null
        }

        private fun dfs(level: Int, depth: Int, lastLayer: Int, accept: (ByteArray) -> Boolean): Boolean {
            for (atom in alphabet) {
                if (atom.layerKey == lastLayer) continue
                model.applyPerm(levels[level], atom.perm, levels[level + 1])
                chosen[level] = atom
                if (level == depth - 1) {
                    if (accept(levels[level + 1])) return true
                } else if (dfs(level + 1, depth, atom.layerKey, accept)) {
                    return true
                }
            }
            return false
        }
    }

    private data class Atom(
        val moves: List<Move>,
        val perm: ShortArray,
        val inverseMoves: List<Move>,
        val inversePerm: ShortArray,
        val layerKey: Int
    )

    private class Operator(
        val moves: List<Move>,
        val perm: ShortArray,
        val centreSupport: Int,
        val edgeSupport: Int
    )

    private class Shape(
        val slots: List<List<Atom?>>,
        val mirrors: IntArray = IntArray(slots.size) { -1 }
    )

    /**
     * Incremental enumerator for shaped families. A mirrored slot reuses the inverse permutation of
     * an earlier choice, so commutators/conjugates are searched without multiplying their closing
     * moves into the search space.
     */
    private class FamilySearch(private val model: Model, maxDepth: Int) {
        private val levels = Array(maxDepth + 2) { ByteArray(FACELETS) }
        private val chosen = arrayOfNulls<Atom>(maxDepth + 2)
        private val chosenMoves = arrayOfNulls<List<Move>>(maxDepth + 2)

        fun walk(
            start: ByteArray,
            shape: Shape,
            leaf: (ByteArray, List<Move>) -> Boolean
        ): Boolean {
            require(shape.slots.size + 1 < levels.size)
            start.copyInto(levels[0])
            return dfs(0, shape, -1, leaf)
        }

        private fun dfs(
            level: Int,
            shape: Shape,
            lastLayer: Int,
            leaf: (ByteArray, List<Move>) -> Boolean
        ): Boolean {
            val mirror = shape.mirrors[level]
            if (mirror >= 0) {
                val sourceAtom = chosen[mirror]
                val dst = levels[level + 1]
                val layer = sourceAtom?.layerKey ?: lastLayer
                if (sourceAtom == null) {
                    levels[level].copyInto(dst)
                    chosenMoves[level] = null
                } else {
                    if (sourceAtom.layerKey == lastLayer) return false
                    model.applyPerm(levels[level], sourceAtom.inversePerm, dst)
                    chosenMoves[level] = sourceAtom.inverseMoves
                }
                return descend(level, shape, layer, leaf)
            }

            for (atom in shape.slots[level]) {
                val dst = levels[level + 1]
                val nextLayer: Int
                if (atom == null) {
                    levels[level].copyInto(dst)
                    nextLayer = lastLayer
                    chosen[level] = null
                    chosenMoves[level] = null
                } else {
                    if (atom.layerKey == lastLayer) continue
                    model.applyPerm(levels[level], atom.perm, dst)
                    nextLayer = atom.layerKey
                    chosen[level] = atom
                    chosenMoves[level] = atom.moves
                }
                if (descend(level, shape, nextLayer, leaf)) return true
            }
            return false
        }

        private fun descend(
            level: Int,
            shape: Shape,
            nextLayer: Int,
            leaf: (ByteArray, List<Move>) -> Boolean
        ): Boolean {
            if (level == shape.slots.lastIndex) {
                val moves = (0..level).flatMap { chosenMoves[it] ?: emptyList() }
                return leaf(levels[level + 1], moves)
            }
            return dfs(level + 1, shape, nextLayer, leaf)
        }
    }

    /** Builds and caches the few-thousand useful big-cube operators. */
    private class OperatorPool(private val model: Model) {
        val outerAtoms: List<Atom> = buildOuterAtoms()
        private val sliceAtoms: List<Atom> = buildSliceAtoms()
        private val wideAtoms: List<Atom> = buildWideAtoms()
        val carrierAtoms: List<Atom> = sliceAtoms + wideAtoms
        val wrapperAtoms: List<Atom> = outerAtoms + carrierAtoms
        val shortAtoms: List<Atom> = outerAtoms + sliceAtoms

        val centreSafe: List<Operator>
        val narrowCentre: List<Operator>
        val centreFine: List<Operator>
        val edgeFinishers: List<Operator>
        val edgeRescue: List<Operator>

        init {
            val reference = model.solvedFlat
            val outerSlot = outerAtoms.map { it as Atom? }
            val carrierSlot = carrierAtoms.map { it as Atom? }
            val skip: List<Atom?> = listOf(null)

            val families = listOf(
                conjugate(skip, carrierSlot, outerSlot, carrierSlot),
                commutator(skip, carrierSlot, outerSlot),
                commutator(skip, outerSlot, carrierSlot),
                commutator(skip, carrierSlot, carrierSlot),
                conjugate(skip, carrierSlot, carrierSlot, outerSlot, carrierSlot),
                conjugate(skip, carrierSlot, outerSlot, outerSlot, carrierSlot),
                conjugate(skip, carrierSlot, outerSlot, outerSlot, outerSlot, carrierSlot)
            )

            val safe = ArrayList<Operator>()
            val narrow = ArrayList<Operator>()
            val seen = HashSet<PermKey>()
            val search = FamilySearch(model, 8)

            for (family in families) {
                if (safe.size >= MAX_PER_LIST && narrow.size >= MAX_PER_LIST) break
                search.walk(reference, family) { candidate, moves ->
                    val centreSupport = model.centreMismatch(candidate, reference)
                    val edgeSupport = model.edgeStickerMismatch(candidate, reference)
                    if ((centreSupport == 0 && edgeSupport > 0) || centreSupport in 1..NARROW_CENTRE_LIMIT) {
                        val compact = simplify(moves)
                        if (compact.isNotEmpty()) {
                            val perm = model.permutation(compact)
                            if (seen.add(PermKey(perm))) {
                                val op = Operator(compact, perm, centreSupport, edgeSupport)
                                if (centreSupport == 0 && edgeSupport > 0 && safe.size < MAX_PER_LIST) safe += op
                                if (centreSupport in 1..NARROW_CENTRE_LIMIT && narrow.size < MAX_PER_LIST) narrow += op
                            }
                        }
                    }
                    safe.size >= MAX_PER_LIST && narrow.size >= MAX_PER_LIST
                }
            }

            for (algorithm in listOf(
                edgeFlipParity(), inverseSequence(edgeFlipParity()),
                edgeSwapParity(), inverseSequence(edgeSwapParity())
            )) {
                val op = operatorFor(algorithm, reference)
                if (op.centreSupport == 0 && op.edgeSupport > 0 && seen.add(PermKey(op.perm))) safe += op
            }

            safe.sortWith(compareBy<Operator>({ it.edgeSupport }, { it.moves.size }))
            narrow.sortWith(compareBy<Operator>({ it.centreSupport }, { it.moves.size }))
            centreSafe = safe
            narrowCentre = narrow
            centreFine = refineCentre(narrow, reference)
            edgeFinishers = buildEdgeFinishers(safe, reference)
            edgeRescue = dedupe(
                edgeFinishers.take(EDGE_RESCUE_FINISHERS) +
                    centreSafe.take(EDGE_RESCUE_SAFE)
            ).sortedWith(
                compareBy<Operator>({ it.moves.size }, { it.edgeSupport })
            )
        }

        private val nativeCentreSafe by lazy {
            NativeFiveByFiveKernel.createPool(centreSafe.map { it.perm })
        }
        private val nativeNarrowCentre by lazy {
            NativeFiveByFiveKernel.createPool(narrowCentre.map { it.perm })
        }
        private val nativeCentreFine by lazy {
            NativeFiveByFiveKernel.createPool(centreFine.map { it.perm })
        }
        private val narrowCentreSafeOps by lazy {
            if (centreSafe.size <= NARROW_SLICE) centreSafe else centreSafe.subList(0, NARROW_SLICE)
        }
        private val narrowNarrowCentreOps by lazy {
            if (narrowCentre.size <= NARROW_SLICE) narrowCentre else narrowCentre.subList(0, NARROW_SLICE)
        }
        private val narrowCentreFineOps by lazy {
            if (centreFine.size <= NARROW_SLICE) centreFine else centreFine.subList(0, NARROW_SLICE)
        }
        private val narrowEdgeFinishersOps by lazy {
            if (edgeFinishers.size <= NARROW_SLICE) edgeFinishers else edgeFinishers.subList(0, NARROW_SLICE)
        }

        private val nativeEdgeFinishers by lazy {
            NativeFiveByFiveKernel.createPool(edgeFinishers.map { it.perm })
        }
        private val nativeEdgeRescue by lazy {
            NativeFiveByFiveKernel.createPool(edgeRescue.map { it.perm })
        }
        private val nativeNarrowCentreSafe by lazy {
            NativeFiveByFiveKernel.createPool(narrowCentreSafeOps.map { it.perm })
        }
        private val nativeNarrowNarrowCentre by lazy {
            NativeFiveByFiveKernel.createPool(narrowNarrowCentreOps.map { it.perm })
        }
        private val nativeNarrowCentreFine by lazy {
            NativeFiveByFiveKernel.createPool(narrowCentreFineOps.map { it.perm })
        }
        private val nativeNarrowEdgeFinishers by lazy {
            NativeFiveByFiveKernel.createPool(narrowEdgeFinishersOps.map { it.perm })
        }

        fun narrowFor(operators: List<Operator>): List<Operator> =
            when {
                operators === centreSafe -> narrowCentreSafeOps
                operators === narrowCentre -> narrowNarrowCentreOps
                operators === centreFine -> narrowCentreFineOps
                operators === edgeFinishers -> narrowEdgeFinishersOps
                operators.size <= NARROW_SLICE -> operators
                else -> operators.subList(0, NARROW_SLICE)
            }

        fun nativeFor(operators: List<Operator>): NativeFiveByFiveKernel.Pool? =
            when {
                operators === centreSafe -> nativeCentreSafe
                operators === narrowCentre -> nativeNarrowCentre
                operators === centreFine -> nativeCentreFine
                operators === edgeFinishers -> nativeEdgeFinishers
                operators === edgeRescue -> nativeEdgeRescue
                operators === narrowCentreSafeOps -> nativeNarrowCentreSafe
                operators === narrowNarrowCentreOps -> nativeNarrowNarrowCentre
                operators === narrowCentreFineOps -> nativeNarrowCentreFine
                operators === narrowEdgeFinishersOps -> nativeNarrowEdgeFinishers
                else -> null
            }

        fun prewarmNative() {
            if (!NativeFiveByFiveKernel.available) return
            nativeCentreSafe
            nativeNarrowCentre
            nativeCentreFine
            nativeEdgeFinishers
            nativeEdgeRescue
            nativeNarrowCentreSafe
            nativeNarrowNarrowCentre
            nativeNarrowCentreFine
            nativeNarrowEdgeFinishers
        }


        private fun buildOuterAtoms(): List<Atom> = buildList {
            for (face in Face.entries) for (turns in 1..3) add(atom(listOf(Move(face, 1, turns)), layerKey(face, 1, false)))
        }

        private fun buildWideAtoms(): List<Atom> = buildList {
            for (width in 2..3) for (face in Face.entries) for (turns in 1..3) {
                add(atom(listOf(Move(face, width, turns)), layerKey(face, width, true)))
            }
        }

        private fun buildSliceAtoms(): List<Atom> = buildList {
            for (face in Face.entries) for (turns in 1..3) {
                add(atom(innerLayer(face, 2, turns), layerKey(face, 2, false)))
            }
            // The middle layer is the same physical slice when named from the opposite face.
            for (face in listOf(Face.U, Face.R, Face.F)) for (turns in 1..3) {
                add(atom(innerLayer(face, 3, turns), layerKey(face, 3, false)))
            }
        }

        private fun atom(moves: List<Move>, layerKey: Int): Atom {
            val inverse = inverseSequence(moves)
            return Atom(moves, model.permutation(moves), inverse, model.permutation(inverse), layerKey)
        }

        private fun operatorFor(moves: List<Move>, reference: ByteArray): Operator {
            val compact = simplify(moves)
            val perm = model.permutation(compact)
            val moved = ByteArray(FACELETS)
            model.applyPerm(reference, perm, moved)
            return Operator(
                compact,
                perm,
                model.centreMismatch(moved, reference),
                model.edgeStickerMismatch(moved, reference)
            )
        }

        private fun refineCentre(narrow: List<Operator>, reference: ByteArray): List<Operator> {
            if (narrow.isEmpty()) return emptyList()
            val collected = ArrayList<Operator>()
            var current = narrow
            repeat(REFINE_ROUNDS) {
                val floor = current.first().centreSupport
                if (floor <= 2) return@repeat
                val refined = narrowerPairs(current, reference, floor - 1)
                if (refined.isEmpty()) return@repeat
                collected.addAll(0, refined)
                current = refined
            }
            collected += narrow.take(REFINE_WIDTH)
            return dedupe(collected).sortedWith(compareBy<Operator>({ it.centreSupport }, { it.moves.size }))
        }

        private fun narrowerPairs(base: List<Operator>, reference: ByteArray, maxSupport: Int): List<Operator> {
            val width = minOf(REFINE_WIDTH, base.size)
            val out = ArrayList<Operator>()
            outer@ for (i in 0 until width) {
                for (j in 0 until width) {
                    if (i == j) continue
                    val op = compose(base[i], base[j], reference)
                    if (op.centreSupport in 1..maxSupport) {
                        out += op
                        if (out.size >= REFINE_CAP) break@outer
                    }
                }
            }
            return dedupe(out).sortedWith(compareBy<Operator>({ it.centreSupport }, { it.moves.size }))
        }

        private fun buildEdgeFinishers(centreSafe: List<Operator>, reference: ByteArray): List<Operator> {
            val narrow = centreSafe.take(minOf(REFINE_WIDTH, centreSafe.size))
            val out = ArrayList<Operator>()
            val seeds = ArrayList<Operator>()

            for (algorithm in seedEdgeAlgorithms()) {
                val forward = operatorFor(algorithm, reference)
                if (forward.centreSupport != 0) continue
                val moved = ByteArray(FACELETS)
                model.applyPerm(reference, forward.perm, moved)
                val unpaired = model.unpairedEdgeCount(moved)
                if (unpaired !in 1..2) continue
                val backward = operatorFor(inverseSequence(algorithm), reference)
                out += forward; out += backward
                seeds += forward; seeds += backward
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

            outer@ for (a in narrow) {
                for (b in narrow) {
                    if (a === b) continue
                    val op = compose(a, b, reference)
                    if (op.centreSupport != 0) continue
                    val moved = ByteArray(FACELETS)
                    model.applyPerm(reference, op.perm, moved)
                    if (model.unpairedEdgeCount(moved) in 1..2) {
                        out += op
                        out += operatorFor(inverseSequence(op.moves), reference)
                        if (out.size >= REFINE_CAP) break@outer
                    }
                }
            }

            return dedupe(out).sortedBy { it.moves.size }
        }

        private fun compose(a: Operator, b: Operator, reference: ByteArray): Operator {
            val perm = ShortArray(FACELETS) { destination -> a.perm[b.perm[destination].toInt()] }
            val moved = ByteArray(FACELETS)
            model.applyPerm(reference, perm, moved)
            return Operator(
                simplify(a.moves + b.moves),
                perm,
                model.centreMismatch(moved, reference),
                model.edgeStickerMismatch(moved, reference)
            )
        }

        private fun dedupe(input: List<Operator>): List<Operator> {
            val seen = HashSet<PermKey>()
            val out = ArrayList<Operator>()
            for (operator in input) if (seen.add(PermKey(operator.perm))) out += operator
            return out
        }

        private fun conjugate(vararg slots: List<Atom?>): Shape {
            val mirrors = IntArray(slots.size) { -1 }
            mirrors[slots.lastIndex] = 1
            return Shape(slots.toList(), mirrors)
        }

        private fun commutator(setup: List<Atom?>, first: List<Atom?>, second: List<Atom?>): Shape =
            Shape(
                slots = listOf(setup, first, second, first, second),
                mirrors = intArrayOf(-1, -1, -1, 1, 2)
            )

        companion object {
            private const val NARROW_CENTRE_LIMIT = 9
            private const val MAX_PER_LIST = 9000
            private const val REFINE_WIDTH = 800
            private const val REFINE_CAP = 3000
            private const val REFINE_ROUNDS = 2
            private const val SEED_PARTNERS = 400
            private const val EDGE_RESCUE_FINISHERS = 360
            private const val EDGE_RESCUE_SAFE = 360
        }
    }

    private class Model {
        private val template = CubeState(N)
        private val keys: List<StickerKey> = buildList(FACELETS) {
            for (face in Face.entries) for (row in 0 until N) for (col in 0 until N) {
                add(template.keyFromFaceCell(face, row, col))
            }
        }
        private val indexByKey = keys.withIndex().associate { it.value to it.index }
        private val movePermCache = HashMap<Move, ShortArray>()

        private val centreIndicesByFace: Array<IntArray> = Array(6) { face ->
            buildList {
                for (row in 1..3) for (col in 1..3) add(face * 25 + row * 5 + col)
            }.toIntArray()
        }
        private val allCentreIndices = centreIndicesByFace.flatMap { it.toList() }.toIntArray()
        private val middleIndex = IntArray(6) { face -> face * 25 + 12 }

        private data class Coord(val x: Int, val y: Int, val z: Int)
        private data class EdgeSlot(val a: IntArray, val b: IntArray)
        private val edgeSlots = buildEdgeSlots()
        private val edgeStickerIndices = edgeSlots.flatMap { it.a.toList() + it.b.toList() }.distinct().toIntArray()

        val solvedFlat = ByteArray(FACELETS) { index -> (index / 25).toByte() }

        init {
            check(allCentreIndices.size == CENTRE_TARGET)
            check(edgeSlots.size == 12) { "5x5 geometry produced ${edgeSlots.size} edge slots, expected 12" }
            check(edgeStickerIndices.size == 72) { "5x5 geometry produced ${edgeStickerIndices.size} edge stickers, expected 72" }
        }

        fun flatState(cube: CubeState): ByteArray {
            val out = ByteArray(FACELETS)
            var index = 0
            for (face in Face.entries) for (color in cube.faceColors(face)) out[index++] = color.ordinal.toByte()
            return out
        }

        /** Each block is scored against its live fixed middle, so whole-frame turns remain valid. */
        fun centreScore(state: ByteArray): Int {
            var score = 0
            for (face in 0 until 6) {
                val target = state[middleIndex[face]]
                for (index in centreIndicesByFace[face]) if (state[index] == target) score++
            }
            return score
        }

        fun centresSolved(state: ByteArray): Boolean {
            for (face in 0 until 6) {
                val indices = centreIndicesByFace[face]
                val color = state[indices[0]]
                for (index in indices) if (state[index] != color) return false
            }
            return true
        }

        fun centreMismatch(a: ByteArray, b: ByteArray): Int {
            var count = 0
            for (index in allCentreIndices) if (a[index] != b[index]) count++
            return count
        }

        /**
         * Smooth edge-pairing score: each of the 24 movable wing cubies earns one point when its
         * ordered color pair matches the fixed middle edge cubie in that slot.
         *
         * The previous score was effectively 0/1/2 per whole edge. Pairing only one wing therefore
         * looked like "no progress", which made the reducer wander on real random states. Scoring
         * the two wings independently gives the search the same incremental signal a human
         * reduction method uses: pair one wing, then the other.
         */
        fun edgeQualityScore(state: ByteArray): Int {
            var total = 0
            for (slot in edgeSlots) total += edgeQuality(state, slot)
            return total
        }

        fun edgeSearchScore(state: ByteArray): Int {
            var wings = 0
            var complete = 0
            for (slot in edgeSlots) {
                val value = edgeQuality(state, slot)
                wings += value
                if (value == 2) complete++
            }
            return wings * 16 + complete
        }

        fun edgesPaired(state: ByteArray): Boolean = edgeQualityScore(state) == EDGE_TARGET

        fun unpairedEdgeCount(state: ByteArray): Int {
            var count = 0
            for (slot in edgeSlots) if (edgeQuality(state, slot) < 2) count++
            return count
        }

        private fun edgeQuality(state: ByteArray, slot: EdgeSlot): Int {
            // buildEdgeSlots() sorts the three physical pieces by their variable coordinate,
            // so index 1 is the fixed middle edge cubie on an odd 5x5.
            val middleA = state[slot.a[1]]
            val middleB = state[slot.b[1]]
            var score = 0
            for (k in intArrayOf(0, 2)) {
                if (state[slot.a[k]] == middleA && state[slot.b[k]] == middleB) score++
            }
            return score
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
            for (index in 0 until FACELETS) target[index] = source[perm[index].toInt()]
        }

        fun permutation(moves: List<Move>): ShortArray {
            var combined = ShortArray(FACELETS) { it.toShort() }
            for (move in moves) {
                val step = movePermutation(move)
                val next = ShortArray(FACELETS)
                for (destination in 0 until FACELETS) next[destination] = combined[step[destination].toInt()]
                combined = next
            }
            return combined
        }

        private fun movePermutation(move: Move): ShortArray = movePermCache.getOrPut(move) {
            val destinationForSource = IntArray(FACELETS)
            for (source in 0 until FACELETS) {
                var key = keys[source]
                if (key.inSlab5(move.face, move.width, move.depth, N)) {
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
        override fun hashCode(): Int = hash
        override fun equals(other: Any?): Boolean = other is PermKey && values.contentEquals(other.values)
    }

    private fun layerKey(face: Face, layerOrWidth: Int, wide: Boolean): Int =
        face.ordinal * 16 + layerOrWidth * 2 + if (wide) 1 else 0

    /** Pure single layer at 1-based depth [layer], expressed through nested wide turns. */
    private fun innerLayer(face: Face, layer: Int, turns: Int): List<Move> {
        require(layer in 2..3)
        return listOf(Move(face, 1, turns, layer))
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
        innerLayer(Face.U, 3, 3) + Move.parseAlgorithm("R U R' F R' F' R") + innerLayer(Face.U, 3, 1),
        innerLayer(Face.D, 3, 1) + Move.parseAlgorithm("R F' U R' F") + innerLayer(Face.D, 3, 3),
        Move.parseAlgorithm("3Dw R F' U R' F 3Dw'"),
        Move.parseAlgorithm("Uw' R U2 R' F R' F' R Uw"),
        Move.parseAlgorithm("Dw R2 F' U R' F Dw'"),
        edgeFlipParity(),
        inverseSequence(edgeFlipParity()),
        edgeSwapParity(),
        inverseSequence(edgeSwapParity())
    ).map(::simplify)

    private fun inverseSequence(sequence: List<Move>): List<Move> = sequence.asReversed().map { it.inverse() }

    internal fun simplify(moves: List<Move>): List<Move> =
        FiveByFiveMoveOptimizer.optimize(moves)
}

private fun StickerKey.inSlab5(face: Face, width: Int, depth: Int, n: Int): Boolean {
    val low = depth - 1
    val high = depth + width - 2
    return when (face) {
        Face.R -> x in (n - 1 - high)..(n - 1 - low)
        Face.L -> x in low..high
        Face.U -> y in (n - 1 - high)..(n - 1 - low)
        Face.D -> y in low..high
        Face.F -> z in (n - 1 - high)..(n - 1 - low)
        Face.B -> z in low..high
    }
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