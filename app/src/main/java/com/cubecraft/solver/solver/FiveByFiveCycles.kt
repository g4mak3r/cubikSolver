package com.cubecraft.solver.solver

import com.cubecraft.solver.model.CubeState
import com.cubecraft.solver.model.Face
import com.cubecraft.solver.model.Move

/**
 * Constructive reduction: independently permute the 24 wings and each 24-centre orbit.
 * Three verified commutators move exactly three pieces, leaving every other piece intact.
 * A small BFS over ordered triples supplies conjugations, not a search over cube states.
 * Each table has 24^3 entries and covers all 24*23*22 distinct ordered triples.
 */
internal object FiveByFiveCycles {
    private val geometry by lazy { Geometry() }
    private val tables by lazy {
        val g = geometry
        listOf(
            CycleTable(g, g.wings.map { it[0] }.toIntArray(), seed(1)),
            CycleTable(g, g.centers(diagonal = true), seed(2)),
            CycleTable(g, g.centers(diagonal = false), seed(3))
        )
    }

    fun prewarm() { tables }

    /** Validate movable pieces without building the three-cycle tables. */
    fun validatePieces(cube: CubeState): String? = try {
        require(cube.size == 5)
        val state = Face.entries.flatMap { cube.faceColors(it) }.map { it.ordinal }.toIntArray()
        geometry.wingPermutation(state)
        centerPermutation(state, geometry.centers(diagonal = true))
        centerPermutation(state, geometry.centers(diagonal = false))
        null
    } catch (e: IllegalArgumentException) {
        e.message ?: "Invalid 5x5 pieces"
    }

    /** Throws IllegalArgumentException for inconsistent scans; never uses scramble history. */
    fun reduce(cube: CubeState, checkCancelled: () -> Unit = {}): List<Move> {
        require(cube.size == 5)
        val g = geometry
        val state = Face.entries.flatMap { cube.faceColors(it) }.map { it.ordinal }.toIntArray()
        val out = ArrayList<Move>()
        checkCancelled()

        // Validate before constructing tables or changing the working state.
        val initialWings = g.wingPermutation(state)
        for (diagonal in listOf(true, false)) centerPermutation(state, g.centers(diagonal))
        if (odd(initialWings)) {
            // A quarter turn of one inner slice is an odd wing permutation and leaves the
            // fixed centres, corners and middle edges untouched. Centres are solved afterwards.
            val parityMove = Move(Face.R, depth = 2)
            g.apply(state, parityMove)
            out += parityMove
        }

        for ((orbit, table) in tables.withIndex()) {
            checkCancelled()
            val permutation = if (orbit == 0) g.wingPermutation(state)
                else centerPermutation(state, table.slots)
            check(!odd(permutation)) { "Expected an even piece permutation" }
            var cycles = 0
            while (true) {
                checkCancelled()
                val a = permutation.indices.firstOrNull { permutation[it] != it } ?: break
                val b = permutation[a]
                var c = permutation[b]
                if (c == a) {
                    // Join two transpositions; an even permutation cannot end in just one.
                    c = permutation.indices.first { it != a && it != b && permutation[it] != it }
                }
                out += table.algorithm(a, b, c)
                val oldC = permutation[c]
                permutation[c] = permutation[b]
                permutation[b] = permutation[a]
                permutation[a] = oldC
                check(++cycles <= 24) { "Piece reduction failed to make progress" }
            }
            // The cycle algorithms have disjoint support, so the other two orbits in state
            // remain valid. Only the parity prefix needed a full-state update above.
        }
        return FiveByFiveMoveOptimizer.optimize(out)
    }

    private fun seed(depth: Int): List<Move> = listOf(
        Move(Face.U), Move(Face.R, depth = depth), Move(Face.U, depth = 2),
        Move(Face.R, quarterTurns = 3, depth = depth), Move(Face.U, quarterTurns = 3),
        Move(Face.R, depth = depth), Move(Face.U, quarterTurns = 3, depth = 2),
        Move(Face.R, quarterTurns = 3, depth = depth)
    )

    private fun odd(p: IntArray): Boolean {
        var parity = false
        for (i in p.indices) for (j in i + 1 until p.size) if (p[i] > p[j]) parity = !parity
        return parity
    }

    private fun centerPermutation(state: IntArray, slots: IntArray): IntArray {
        val p = IntArray(24) { -1 }
        val used = BooleanArray(24)
        // Preserve already placed centres; identical centres may exchange labels freely.
        for (i in slots.indices) if (state[slots[i]] == slots[i] / 25) {
            p[i] = i
            used[i] = true
        }
        for (i in slots.indices) if (p[i] < 0) {
            val target = slots.indices.firstOrNull { !used[it] && slots[it] / 25 == state[slots[i]] }
            require(target != null) { "5x5 centre orbit must contain four centres of each color. Check the scan." }
            p[i] = target
            used[target] = true
        }
        if (odd(p)) {
            val a = p.indices.first { p[it] != it }
            val b = p.indices.first { it != a && state[slots[it]] == state[slots[a]] }
            val swap = p[a]; p[a] = p[b]; p[b] = swap
        }
        return p
    }

    private class CycleTable(val geometry: Geometry, val slots: IntArray, val seed: List<Move>) {
        private val parents = IntArray(24 * 24 * 24) { -1 }
        private val steps = ByteArray(parents.size)
        private val root: Int

        init {
            val local = IntArray(150) { -1 }
            slots.forEachIndexed { i, sticker -> local[sticker] = i }
            val seedPerm = geometry.permutation(seed)
            val a = slots.first { seedPerm[it] != it }
            val b = seedPerm[a]
            val c = seedPerm[b]
            check(seedPerm[c] == a && a != b && b != c)
            val support = (0 until 150).filter { seedPerm[it] != it }
            // Wings carry two stickers. No seed may disturb another orbit or the 3x3.
            val expected = if (slots.contentEquals(geometry.wings.map { it[0] }.toIntArray())) {
                geometry.wings.filter { it[0] in listOf(a, b, c) }.flatMap { it.take(2) }.toSet()
            } else setOf(a, b, c)
            check(support.toSet() == expected) { "Invalid three-cycle seed support" }
            root = encode(local[a], local[b], local[c])
            val transitions = geometry.generators.map { move ->
                val p = geometry.permutation(listOf(move))
                IntArray(24) { local[p[slots[it]]].also { i -> check(i >= 0) } }
            }
            val queue = IntArray(parents.size)
            var head = 0
            var tail = 1
            queue[0] = root
            parents[root] = root
            while (head < tail) {
                val node = queue[head++]
                val x = node / 576; val y = node / 24 % 24; val z = node % 24
                transitions.forEachIndexed { m, p ->
                    val next = encode(p[x], p[y], p[z])
                    if (parents[next] < 0) {
                        parents[next] = node
                        steps[next] = m.toByte()
                        queue[tail++] = next
                    }
                }
            }
            check(tail == 24 * 23 * 22) { "Incomplete three-cycle table: $tail" }
        }

        fun algorithm(a: Int, b: Int, c: Int): List<Move> {
            var node = encode(a, b, c)
            check(parents[node] >= 0)
            val setup = ArrayList<Move>(7)
            while (node != root) {
                setup += geometry.generators[steps[node].toInt()]
                node = parents[node]
            }
            // Parent traversal is target-to-root; reverse it to get the conjugating setup.
            setup.reverse()
            return setup.asReversed().map { it.inverse() } + seed + setup
        }

        private fun encode(a: Int, b: Int, c: Int) = (a * 24 + b) * 24 + c
    }

    private class Geometry {
        private val cube = CubeState(5)
        private val keys = Face.entries.flatMap { f ->
            (0..4).flatMap { r -> (0..4).map { c -> cube.keyFromFaceCell(f, r, c) } }
        }
        private val indices = keys.withIndex().associate { it.value to it.index }
        val generators = Face.entries.flatMap { f ->
            (1..2).flatMap { d -> (1..3).map { t -> Move(f, quarterTurns = t, depth = d) } }
        }
        private val perms = (generators + seed(3)).distinct().associateWith(::movePermutation)
        val wings: List<IntArray> = keys.indices
            .filter { i ->
                val k = keys[i]
                listOf(k.x, k.y, k.z).count { it == 0 || it == 4 } == 2 &&
                    listOf(k.x, k.y, k.z).any { it == 1 || it == 3 }
            }
            .groupBy { i -> keys[i].let { Triple(it.x, it.y, it.z) } }
            .values.map { pair ->
                var a = pair[0]; var b = pair[1]
                val u = keys[a]; val v = keys[b]
                // Rotation-invariant handedness chooses a consistent first sticker on each
                // wing. The resulting 24 ordered color pairs identify all wings uniquely.
                val handedness = (u.ny * v.nz - u.nz * v.ny) * (u.x - 2) +
                    (u.nz * v.nx - u.nx * v.nz) * (u.y - 2) +
                    (u.nx * v.ny - u.ny * v.nx) * (u.z - 2)
                if (handedness < 0) { val swap = a; a = b; b = swap }
                intArrayOf(a, b, middle(a), middle(b))
            }

        fun centers(diagonal: Boolean) = (0 until 150).filter { i ->
            val r = i % 25 / 5; val c = i % 5
            r in 1..3 && c in 1..3 && (r != 2 || c != 2) &&
                ((r != 2 && c != 2) == diagonal)
        }.toIntArray()

        private fun middle(i: Int): Int {
            val k = keys[i]
            return indices.getValue(k.copy(
                x = if (k.x in 1..3) 2 else k.x,
                y = if (k.y in 1..3) 2 else k.y,
                z = if (k.z in 1..3) 2 else k.z
            ))
        }

        fun wingPermutation(state: IntArray): IntArray {
            val targets = IntArray(36) { -1 }
            wings.forEachIndexed { i, w ->
                val code = state[w[2]] * 6 + state[w[3]]
                require(targets[code] < 0) { "5x5 middle edges are duplicated. Check the scan." }
                targets[code] = i
            }
            val used = BooleanArray(24)
            return IntArray(24) { i ->
                val w = wings[i]
                val target = targets[state[w[0]] * 6 + state[w[1]]]
                require(target >= 0 && !used[target]) {
                    "5x5 wing pieces are duplicated or flipped. Check the scan."
                }
                used[target] = true
                target
            }
        }

        fun apply(state: IntArray, move: Move) {
            val source = state.copyOf()
            val p = perms.getValue(move)
            for (i in source.indices) state[p[i]] = source[i]
        }

        fun permutation(moves: List<Move>): IntArray {
            var p = IntArray(150) { it }
            for (move in moves) {
                val step = perms.getValue(move)
                p = IntArray(150) { step[p[it]] }
            }
            return p
        }

        private fun movePermutation(move: Move): IntArray = IntArray(150) { i ->
            var k = keys[i]
            val distance = when (move.face) {
                Face.U -> 4 - k.y; Face.R -> 4 - k.x; Face.F -> 4 - k.z
                Face.D -> k.y; Face.L -> k.x; Face.B -> k.z
            }
            if (distance == move.depth - 1) repeat(move.quarterTurns) {
                k = when (move.face) {
                    Face.U -> k.copy(x = 4 - k.z, z = k.x, nx = -k.nz, nz = k.nx)
                    Face.R -> k.copy(y = k.z, z = 4 - k.y, ny = k.nz, nz = -k.ny)
                    Face.F -> k.copy(x = k.y, y = 4 - k.x, nx = k.ny, ny = -k.nx)
                    Face.D -> k.copy(x = k.z, z = 4 - k.x, nx = k.nz, nz = -k.nx)
                    Face.L -> k.copy(y = 4 - k.z, z = k.y, ny = -k.nz, nz = k.ny)
                    Face.B -> k.copy(x = 4 - k.y, y = k.x, nx = -k.ny, ny = k.nx)
                }
            }
            indices.getValue(k)
        }
    }
}
